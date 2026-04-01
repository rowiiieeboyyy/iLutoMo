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
    // Changed to Map<String, Double> to handle price, cal, pro, etc.
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        val btnChoose = findViewById<Button>(R.id.btnChooseRecipe)
        val btnMacros = findViewById<Button>(R.id.btnViewMacros)
        val btnSteps = findViewById<Button>(R.id.btnViewSteps)
        val btnAddToPantry = findViewById<Button>(R.id.btnAddToPantry)
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
        btnAddToPantry.setOnClickListener {
            addToPantry()
        }
    }

    private fun addToPantry() {
        if (currentRecipe == null) {
            Toast.makeText(this, "Please select a recipe first", Toast.LENGTH_SHORT).show()
            return
        }

        val selected = currentIngredients.filter { it.isChecked }
        if (selected.isEmpty()) {
            Toast.makeText(this, "No ingredients selected to add", Toast.LENGTH_SHORT).show()
            return
        }

        val pantryRef = FirebaseDatabase.getInstance().getReference("Pantry")
        val updates = mutableMapOf<String, Any>()

        selected.forEach { ing ->
            val entry = ingredientLibrary.entries.find {
                it.key.equals(ing.name, ignoreCase = true) || it.key.contains(ing.name, ignoreCase = true)
            }
            val lib = entry?.value
            val actualName = entry?.key ?: ing.name
            
            val qty = ing.amount.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            
            var calculatedPrice = 0.0
            if (lib != null) {
                val isPieceBased = actualName.contains("Egg", ignoreCase = true) ||
                        actualName.contains("Wrapper", ignoreCase = true) ||
                        actualName.contains("Banana", ignoreCase = true)

                val factor = if (isPieceBased) qty else (qty / 50.0)
                calculatedPrice = factor * (lib["price"] ?: 0.0)
            }
            
            val ingredientData = mapOf(
                "name" to ing.name,
                "amount" to ing.amount,
                "price" to calculatedPrice,
                "recipeTitle" to (currentRecipe?.title ?: "Unknown")
            )
            val key = pantryRef.push().key ?: return@forEach
            updates[key] = ingredientData
        }

        pantryRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
            
            // Reset page after success
            currentRecipe = null
            currentIngredients.clear()
            findViewById<Button>(R.id.btnChooseRecipe).text = "Select from your added recipes"
            updateUI()

        }.addOnFailureListener {
            Toast.makeText(this, "Failed to add: ${it.message}", Toast.LENGTH_SHORT).show()
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
        var totalProtein = 0.0
        var totalSugar = 0.0
        var totalPrice = 0.0

        val ings = recipe.ingredients

        if (ings.isNullOrEmpty()) {
            builder.append("No ingredients listed.")
        } else {
            ings.forEach { (name, amount) ->
                val qty = amount.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0

                // Flexible matching for library keys
                val entry = ingredientLibrary.entries.find {
                    it.key.equals(name, ignoreCase = true) || it.key.contains(name, ignoreCase = true)
                }
                val lib = entry?.value
                val actualName = entry?.key ?: name

                if (lib != null) {
                    // CRITICAL FIX: Portion Factor Logic
                    val isPieceBased = actualName.contains("Egg", ignoreCase = true) ||
                            actualName.contains("Wrapper", ignoreCase = true) ||
                            actualName.contains("Banana", ignoreCase = true)

                    val factor = if (isPieceBased) qty else (qty / 50.0)

                    val itemKcal = factor * (lib["cal"] ?: 0.0)
                    val itemPro = factor * (lib["pro"] ?: 0.0)
                    val itemSugar = factor * (lib["sugar"] ?: 0.0)
                    val itemPrice = factor * (lib["price"] ?: 0.0)

                    totalCals += itemKcal
                    totalProtein += itemPro
                    totalSugar += itemSugar
                    totalPrice += itemPrice

                    builder.append("• $actualName ($qty${if(isPieceBased) "pcs" else "g"}):\n")
                    builder.append("  ${itemKcal.toInt()} kcal | ₱${"%.2f".format(itemPrice)}\n\n")
                } else {
                    builder.append("• $name: (Not in Library)\n\n")
                }
            }
        }

        builder.append("---\n")
        builder.append("TOTAL CALORIES: ${totalCals.toInt()} kcal\n")
        builder.append("TOTAL PROTEIN: ${"%.1f".format(totalProtein)}g\n")
        builder.append("TOTAL SUGAR: ${"%.1f".format(totalSugar)}g\n")
        builder.append("TOTAL PRICE: ₱${"%.2f".format(totalPrice)}")

        AlertDialog.Builder(this)
            .setTitle(recipe.title)
            .setMessage(builder.toString())
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val steps = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps."
        AlertDialog.Builder(this).setTitle("Steps").setMessage(steps).setPositiveButton("Done", null).show()
    }
}