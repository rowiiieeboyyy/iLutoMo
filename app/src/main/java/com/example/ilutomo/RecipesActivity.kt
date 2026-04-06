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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class RecipesActivity : AppCompatActivity() {

    private lateinit var tvNeeded: TextView
    private lateinit var rvAvailable: RecyclerView
    private var currentIngredients = mutableListOf<DisplayIngredient>()
    private var currentRecipe: Recipe? = null
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        // UI Initialization
        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        val btnChoose = findViewById<Button>(R.id.btnChooseRecipe)
        val btnMacros = findViewById<Button>(R.id.btnViewMacros)
        val btnSteps = findViewById<Button>(R.id.btnViewSteps)
        val btnAddToPantry = findViewById<Button>(R.id.btnAddToPantry)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvAvailable.layoutManager = LinearLayoutManager(this)
        loadIngredientLibrary()

        // Navigation
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

        btnAddToPantry.setOnClickListener { addToPantry() }
    }

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ingredientLibrary.clear()
                for (data in snapshot.children) {
                    val stats = data.children.associate { it.key!! to (it.value?.toString()?.toDoubleOrNull() ?: 0.0) }
                    ingredientLibrary[data.key!!] = stats
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("RecipesActivity", "Library Load Failed: ${error.message}")
            }
        })
    }

    private fun showRecipeSelectionDialog(btnChoose: Button) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("SelectedRecipes").get().addOnSuccessListener { snapshot ->
            val selections = snapshot.children.map { it.key?.replace("_", " ") ?: "" }
            if (selections.isEmpty()) {
                Toast.makeText(this, "No recipes added to your account yet!", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            AlertDialog.Builder(this)
                .setTitle("Your Saved Recipes")
                .setItems(selections.toTypedArray()) { _, which ->
                    val selectedName = selections[which]
                    showActionDialog(selectedName, btnChoose)
                }.setNegativeButton("Close", null).show()
        }
    }

    private fun showActionDialog(selectedName: String, btnChoose: Button) {
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
    }

    private fun loadRecipeData(name: String) {
        val uid = auth.currentUser?.uid ?: return
        val key = name.replace(" ", "_")
        database.child("Users").child(uid).child("SelectedRecipes").child(key).get().addOnSuccessListener { snapshot ->
            val recipe = snapshot.getValue(Recipe::class.java)
            if (recipe != null) {
                currentRecipe = recipe
                currentRecipe?.title = name
                currentIngredients.clear()
                recipe.ingredients?.forEach { (n, a) ->
                    // Initialized as 'isChecked = true' so they are ready to be added to Pantry
                    currentIngredients.add(DisplayIngredient(n, a.toString(), isChecked = true))
                }
                updateUI()
            }
        }
    }

    private fun deleteRecipe(name: String, btn: Button) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("SelectedRecipes").child(name.replace(" ", "_")).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "$name removed from your list.", Toast.LENGTH_SHORT).show()
                if (btn.text == name) resetUI(btn)
            }
    }

    private fun resetUI(btn: Button) {
        btn.text = "Select from your added recipes"
        currentRecipe = null
        currentIngredients.clear()
        updateUI()
    }

    private fun updateUI() {
        rvAvailable.adapter = IngredientCheckAdapter(currentIngredients) { renderNeededList() }
        renderNeededList()
    }

    private fun renderNeededList() {
        if (currentIngredients.isEmpty()) {
            tvNeeded.text = "No ingredients to display."
            return
        }

        val fullText = StringBuilder()
        currentIngredients.forEach { ing ->
            fullText.append("${ing.name} (${ing.amount})\n\n")
        }

        val spannable = SpannableString(fullText.toString())
        var p = 0

        currentIngredients.forEach { ing ->
            // Strikethrough logic for the summary text
            if (!ing.isChecked) {
                spannable.setSpan(StrikethroughSpan(), p, p + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.GRAY), p, p + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            p += "${ing.name} (${ing.amount})\n\n".length
        }
        tvNeeded.text = spannable
    }

    private fun addToPantry() {
        val uid = auth.currentUser?.uid ?: return
        if (currentRecipe == null) {
            Toast.makeText(this, "Please select a recipe first", Toast.LENGTH_SHORT).show()
            return
        }

        val selected = currentIngredients.filter { it.isChecked }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Please check the ingredients you need to buy!", Toast.LENGTH_SHORT).show()
            return
        }

        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val updates = mutableMapOf<String, Any>()

        selected.forEach { ing ->
            val entry = ingredientLibrary.entries.find { it.key.equals(ing.name, true) || it.key.contains(ing.name, true) }
            val lib = entry?.value
            val qty = ing.amount.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0

            var calculatedPrice = 0.0
            if (lib != null) {
                val isPieceBased = entry?.key?.contains("Egg", true) == true || entry?.key?.contains("Banana", true) == true
                val factor = if (isPieceBased) qty else (qty / 50.0)
                calculatedPrice = factor * (lib["price"] ?: 0.0)
            }

            val key = pantryRef.push().key ?: return@forEach

            // Saving as a Map to ensure Firebase creates exactly what PantryActivity expects
            val pantryItem = mapOf(
                "id" to key,
                "name" to ing.name,
                "amount" to ing.amount,
                "price" to calculatedPrice,
                "recipeTitle" to (currentRecipe?.title ?: "Unknown"),
                "isChecked" to true
            )
            updates[key] = pantryItem
        }

        pantryRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Added to Pantry! Check the Pantry tab.", Toast.LENGTH_SHORT).show()
            // Clear current selection after successful transfer
            resetUI(findViewById(R.id.btnChooseRecipe))
            startActivity(Intent(this, PantryActivity::class.java))
        }
    }

    private fun showAccurateMacrosDialog(recipe: Recipe) {
        val builder = StringBuilder("NUTRITION BREAKDOWN:\n---\n")
        var totalCals = 0.0; var totalPrice = 0.0

        recipe.ingredients?.forEach { (name, amount) ->
            val qty = amount.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0
            val entry = ingredientLibrary.entries.find { it.key.equals(name, true) || it.key.contains(name, true) }
            val lib = entry?.value
            if (lib != null) {
                val isPieceBased = entry.key.contains("Egg", true) || entry.key.contains("Banana", true)
                val factor = if (isPieceBased) qty else (qty / 50.0)
                val itemKcal = factor * (lib["cal"] ?: 0.0)
                val itemPrice = factor * (lib["price"] ?: 0.0)
                totalCals += itemKcal; totalPrice += itemPrice
                builder.append("• ${entry.key}: ${itemKcal.toInt()} kcal | ₱${"%.2f".format(itemPrice)}\n\n")
            }
        }
        builder.append("---\nTOTAL: ${totalCals.toInt()} kcal | ₱${"%.2f".format(totalPrice)}")
        AlertDialog.Builder(this).setTitle(recipe.title).setMessage(builder.toString()).setPositiveButton("Done", null).show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val steps = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps available."
        AlertDialog.Builder(this).setTitle("Cooking Steps").setMessage(steps).setPositiveButton("Done", null).show()
    }
}