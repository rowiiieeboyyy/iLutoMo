package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.Log
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

        btnChoose.setOnClickListener { showRecipeSelectionDialog(btnChoose) }
        btnMacros.setOnClickListener {
            if (currentRecipe == null) Toast.makeText(this, "Select a recipe first!", Toast.LENGTH_SHORT).show()
            else showAccurateMacrosDialog(currentRecipe!!)
        }
        btnSteps.setOnClickListener {
            if (currentRecipe == null) Toast.makeText(this, "Select a recipe first!", Toast.LENGTH_SHORT).show()
            else showStepsDialog(currentRecipe!!)
        }
    }

    private fun showRecipeSelectionDialog(btnChoose: Button) {
        val ref = FirebaseDatabase.getInstance().getReference("UserSelection")
        ref.get().addOnSuccessListener { snapshot ->
            val selections = snapshot.children.map { it.key?.replace("_", " ") ?: "" }
            if (selections.isEmpty()) {
                Toast.makeText(this, "No recipes added yet!", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            AlertDialog.Builder(this)
                .setTitle("Manage Recipes")
                .setItems(selections.toTypedArray()) { _, which ->
                    val selectedName = selections[which]
                    AlertDialog.Builder(this)
                        .setTitle(selectedName)
                        .setMessage("What would you like to do?")
                        .setPositiveButton("Select") { _, _ ->
                            btnChoose.text = selectedName
                            loadRecipeData(selectedName)
                        }
                        .setNegativeButton("Delete") { _, _ -> deleteRecipe(selectedName, btnChoose) }
                        .setNeutralButton("Cancel", null)
                        .show()
                }.setNegativeButton("Close", null).show()
        }
    }

    private fun loadRecipeData(name: String) {
        val key = name.replace(" ", "_")
        FirebaseDatabase.getInstance().getReference("UserSelection").child(key).get().addOnSuccessListener { snapshot ->
            try {
                val recipe = snapshot.getValue(Recipe::class.java)
                if (recipe != null) {
                    currentRecipe = recipe
                    currentRecipe?.title = name
                    currentIngredients.clear()
                    recipe.ingredients?.forEach { (n, a) ->
                        currentIngredients.add(DisplayIngredient(n, a.toString()))
                    }
                    updateUI()
                }
            } catch (e: Exception) {
                Log.e("RECIPE_ERROR", "Crash prevented for $name: ${e.message}")
                Toast.makeText(this, "Format error in Firebase for $name", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteRecipe(name: String, btn: Button) {
        FirebaseDatabase.getInstance().getReference("UserSelection").child(name.replace(" ", "_")).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "$name removed.", Toast.LENGTH_SHORT).show()
                if (btn.text == name) {
                    btn.text = "Choose Recipe"
                    currentRecipe = null
                    currentIngredients.clear()
                    updateUI()
                }
            }
    }

    private fun loadIngredientLibrary() {
        FirebaseDatabase.getInstance().getReference("ingredient_library").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ingredientLibrary.clear()
                for (data in snapshot.children) {
                    val stats = data.children.associate { it.key!! to (it.value?.toString()?.toDoubleOrNull() ?: 0.0) }
                    ingredientLibrary[data.key!!] = stats
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUI() {
        rvAvailable.adapter = IngredientCheckAdapter(currentIngredients) { renderNeededList() }
        renderNeededList()
    }

    private fun renderNeededList() {
        if (currentIngredients.isEmpty()) { tvNeeded.text = ""; return }
        val fullText = StringBuilder()
        currentIngredients.forEach { fullText.append("${it.name} (${it.amount})\n\n") }
        val spannable = SpannableString(fullText.toString())
        var p = 0
        currentIngredients.forEach { ing ->
            if (ing.isChecked) {
                spannable.setSpan(StrikethroughSpan(), p, p + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.GRAY), p, p + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            p += "${ing.name} (${ing.amount})\n\n".length
        }
        tvNeeded.text = spannable
    }

    private fun showAccurateMacrosDialog(recipe: Recipe) {
        val builder = StringBuilder("DETAILED BREAKDOWN:\n---\n")
        var totalCals = 0.0
        val ings = recipe.ingredients

        if (ings.isNullOrEmpty()) {
            builder.append("No ingredients listed.")
        } else {
            ings.forEach { (name, amount) ->
                val qty = amount.toString().toDoubleOrNull() ?: 0.0
                val searchKey = name.trim().lowercase()

                // FLEXIBLE MATCH: Checks if keys contain the name (fixes Pepper/Peppercorn)
                val lib = ingredientLibrary.entries.find {
                    val key = it.key.trim().lowercase()
                    key == searchKey || key.contains(searchKey) || searchKey.contains(key)
                }?.value

                if (lib != null) {
                    val cal = qty * (lib["cal"] ?: 0.0)
                    totalCals += cal
                    builder.append("• $name: ${cal.toInt()} kcal\n")
                } else {
                    builder.append("• $name: (Not in Library)\n")
                }
            }
        }
        builder.append("---\nTotal: ${totalCals.toInt()} kcal")
        AlertDialog.Builder(this).setTitle(recipe.title).setMessage(builder.toString())
            .setPositiveButton("Done", null).show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val steps = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps."
        AlertDialog.Builder(this).setTitle("Steps").setMessage(steps).setPositiveButton("Done", null).show()
    }
}