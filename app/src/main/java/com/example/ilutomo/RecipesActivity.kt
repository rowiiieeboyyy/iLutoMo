package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*

class RecipesActivity : AppCompatActivity() {

    private lateinit var tvNeeded: TextView
    private lateinit var rvAvailable: RecyclerView
    private var currentIngredients = mutableListOf<DisplayIngredient>()
    private var currentRecipe: Recipe? = null
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        val btnChoose = findViewById<Button>(R.id.btnChooseRecipe)
        val btnMacros = findViewById<Button>(R.id.btnViewMacros)
        val btnSteps = findViewById<Button>(R.id.btnViewSteps)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvAvailable.layoutManager = LinearLayoutManager(this)

        loadIngredientLibrary()

        bottomNav.selectedItemId = R.id.nav_recipes
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> true
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }

        btnChoose.setOnClickListener {
            showRecipeSelectionDialog(btnChoose)
        }

        btnMacros.setOnClickListener {
            if (currentRecipe == null) {
                Toast.makeText(this, "Select a recipe first!", Toast.LENGTH_SHORT).show()
            } else {
                showAccurateMacrosDialog(currentRecipe!!)
            }
        }

        btnSteps.setOnClickListener {
            currentRecipe?.let { showStepsDialog(it) } ?: Toast.makeText(this, "Select a recipe first!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecipeSelectionDialog(btnChoose: Button) {
        val ref = FirebaseDatabase.getInstance().getReference("UserSelection")
        ref.get().addOnSuccessListener { snapshot ->
            val selections = snapshot.children.map { it.key ?: "" }

            if (selections.isEmpty()) {
                Toast.makeText(this, "No recipes added yet!", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Create a dialog with two options: Select or Delete
            AlertDialog.Builder(this)
                .setTitle("Manage Recipes")
                .setItems(selections.toTypedArray()) { _, which ->
                    val selectedName = selections[which]

                    // Ask the user what they want to do with the selected recipe
                    AlertDialog.Builder(this)
                        .setTitle(selectedName)
                        .setMessage("What would you like to do?")
                        .setPositiveButton("Select") { _, _ ->
                            btnChoose.text = selectedName
                            loadRecipeData(selectedName)
                        }
                        .setNegativeButton("Delete from List") { _, _ ->
                            deleteRecipeFromUserSelection(selectedName, btnChoose)
                        }
                        .setNeutralButton("Cancel", null)
                        .show()
                }
                .show()
        }
    }

    private fun deleteRecipeFromUserSelection(recipeName: String, btnChoose: Button) {
        FirebaseDatabase.getInstance().getReference("UserSelection")
            .child(recipeName)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "$recipeName removed.", Toast.LENGTH_SHORT).show()

                // If the deleted recipe was the one currently viewed, clear the UI
                if (btnChoose.text == recipeName) {
                    btnChoose.text = "Choose Recipe"
                    currentRecipe = null
                    currentIngredients.clear()
                    updateUI()
                }
            }
    }

    private fun loadIngredientLibrary() {
        FirebaseDatabase.getInstance().getReference("ingredient_library")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    ingredientLibrary.clear()
                    for (data in snapshot.children) {
                        val stats = mutableMapOf<String, Double>()
                        data.children.forEach { child ->
                            val value = child.value?.toString()?.toDoubleOrNull() ?: 0.0
                            stats[child.key!!] = value
                        }
                        ingredientLibrary[data.key!!] = stats
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadRecipeData(name: String) {
        FirebaseDatabase.getInstance().reference.get().addOnSuccessListener { snapshot ->
            for (data in snapshot.children) {
                if (data.key == "UserSelection" || data.key == "ingredient_library") continue

                val tempRecipe = data.getValue(Recipe::class.java)
                val recipeTitle = tempRecipe?.title ?: data.child("imageResourceName").value.toString().replace("_", " ")

                if (recipeTitle.equals(name, ignoreCase = true)) {
                    currentRecipe = tempRecipe
                    currentRecipe?.title = recipeTitle
                    currentIngredients.clear()
                    tempRecipe?.ingredients?.forEach { (ingName, amount) ->
                        currentIngredients.add(DisplayIngredient(ingName, amount.toString()))
                    }
                    updateUI()
                    break
                }
            }
        }
    }

    private fun showAccurateMacrosDialog(recipe: Recipe) {
        val builder = StringBuilder()
        var totalCals = 0.0
        var totalPro = 0.0
        var totalCarbs = 0.0
        var totalFat = 0.0

        builder.append("DETAILED BREAKDOWN:\n")
        builder.append("----------------------------\n")

        recipe.ingredients.forEach { (name, amount) ->
            val qty = amount.toString().toDoubleOrNull() ?: 0.0
            val libraryData = ingredientLibrary[name]

            if (libraryData != null) {
                val cal = qty * (libraryData["cal"] ?: 0.0)
                val pro = qty * (libraryData["pro"] ?: 0.0)
                val carb = qty * (libraryData["carb"] ?: 0.0)
                val fat = qty * (libraryData["fat"] ?: 0.0)

                totalCals += cal
                totalPro += pro
                totalCarbs += carb
                totalFat += fat

                builder.append("• $name ($qty):\n")
                builder.append("  ${cal.toInt()} kcal | P: ${"%.1f".format(pro)}g | C: ${"%.1f".format(carb)}g\n\n")
            } else {
                builder.append("• $name: (Data missing)\n\n")
            }
        }

        builder.append("----------------------------\n")
        builder.append("TOTALS:\n")
        builder.append("Calories: ${totalCals.toInt()} kcal\n")
        builder.append("Protein: ${"%.1f".format(totalPro)}g\n")
        builder.append("Carbs: ${"%.1f".format(totalCarbs)}g\n")
        builder.append("Fats: ${"%.1f".format(totalFat)}g\n")

        AlertDialog.Builder(this).setTitle(recipe.title).setMessage(builder.toString()).setPositiveButton("Close", null).show()
    }

    private fun updateUI() {
        rvAvailable.adapter = IngredientCheckAdapter(currentIngredients) { renderNeededList() }
        renderNeededList()
    }

    private fun renderNeededList() {
        if (currentIngredients.isEmpty()) { tvNeeded.text = ""; return }
        val fullText = StringBuilder()
        currentIngredients.forEach { fullText.append("${it.name} (${it.amount})\n\n") }
        val spannableString = SpannableString(fullText.toString())
        var pointer = 0
        currentIngredients.forEach { ingredient ->
            val entryText = "${ingredient.name} (${ingredient.amount})\n\n"
            if (ingredient.isChecked) {
                spannableString.setSpan(StrikethroughSpan(), pointer, pointer + ingredient.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannableString.setSpan(ForegroundColorSpan(Color.GRAY), pointer, pointer + ingredient.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannableString.setSpan(StyleSpan(Typeface.ITALIC), pointer, pointer + ingredient.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            pointer += entryText.length
        }
        tvNeeded.text = spannableString
    }

    private fun showStepsDialog(recipe: Recipe) {
        val stepsText = recipe.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n")
        AlertDialog.Builder(this).setTitle("Steps").setMessage(stepsText).setPositiveButton("Done", null).show()
    }
}