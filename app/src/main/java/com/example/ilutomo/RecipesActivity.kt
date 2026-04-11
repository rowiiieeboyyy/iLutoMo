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

        btnChoose.setOnClickListener { showAllRecipesDiscovery(btnChoose) }

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
                Log.e("RecipesActivity", "Error loading library: ${error.message}")
            }
        })
    }

    private fun showAllRecipesDiscovery(btnChoose: Button) {
        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid).child("AddedRecipes").get().addOnSuccessListener { recipeSnap ->
            val addedRecipes = mutableListOf<Recipe>()

            for (snap in recipeSnap.children) {
                val recipe = snap.getValue(Recipe::class.java) ?: continue
                recipe.id = snap.key ?: ""
                if (recipe.title.isEmpty()) {
                    recipe.title = snap.child("title").value?.toString() ?: "Untitled"
                }
                addedRecipes.add(recipe)
            }

            if (addedRecipes.isEmpty()) {
                Toast.makeText(this, "No recipes in your list!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            addedRecipes.sortBy { it.title }
            val titles = addedRecipes.map { it.title }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Your Added Recipes")
                .setItems(titles) { _, which ->
                    val selected = addedRecipes[which]
                    currentRecipe = selected
                    btnChoose.text = selected.title
                    loadRecipeDataIntoUI(selected)
                }
                .setNeutralButton("Delete a Recipe") { _, _ ->
                    showDeleteRecipeDialog(addedRecipes, btnChoose)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showDeleteRecipeDialog(recipes: List<Recipe>, btnChoose: Button) {
        val uid = auth.currentUser?.uid ?: return
        val titles = recipes.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Recipe to Remove")
            .setItems(titles) { _, which ->
                val selected = recipes[which]

                AlertDialog.Builder(this)
                    .setTitle("Delete ${selected.title}?")
                    .setMessage("Are you sure you want to remove this from your recipes?")
                    .setPositiveButton("Delete") { _, _ ->
                        database.child("Users").child(uid).child("AddedRecipes")
                            .child(selected.id).removeValue()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Removed ${selected.title}", Toast.LENGTH_SHORT).show()
                                if (currentRecipe?.id == selected.id) {
                                    currentRecipe = null
                                    btnChoose.text = "Select from your added recipes"
                                    currentIngredients.clear()
                                    updateUI()
                                }
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun loadRecipeDataIntoUI(recipe: Recipe) {
        currentIngredients.clear()
        recipe.ingredients?.forEach { (name, amount) ->
            currentIngredients.add(DisplayIngredient(name, amount.toString(), isChecked = true))
        }
        updateUI()
    }

    private fun updateUI() {
        rvAvailable.adapter = IngredientCheckAdapter(currentIngredients) { renderNeededList() }
        renderNeededList()
    }

    private fun renderNeededList() {
        if (currentIngredients.isEmpty()) {
            tvNeeded.text = "No recipe selected"
            return
        }
        val fullText = StringBuilder()
        currentIngredients.forEach { fullText.append("${it.name} (${it.amount})\n\n") }
        val spannable = SpannableString(fullText.toString())
        var p = 0
        currentIngredients.forEach { ing ->
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
            Toast.makeText(this, "Select ingredients to add", Toast.LENGTH_SHORT).show()
            return
        }

        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val updates = mutableMapOf<String, Any>()

        selected.forEach { ing ->
            val key = pantryRef.push().key ?: return@forEach
            updates[key] = mapOf(
                "id" to key,
                "name" to ing.name,
                "amount" to ing.amount,
                "recipeTitle" to (currentRecipe?.title ?: "Unknown"),
                "isChecked" to true
            )
        }
        pantryRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PantryActivity::class.java))
        }
    }

    private fun showAccurateMacrosDialog(recipe: Recipe) {
        val builder = StringBuilder("NUTRITION BREAKDOWN:\n---\n")
        var totalCals = 0.0; var totalPrice = 0.0
        recipe.ingredients?.forEach { (name, amount) ->
            val qty = amount.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0
            val entry = ingredientLibrary.entries.find { it.key.contains(name, true) }?.value
            if (entry != null) {
                val factor = if (name.contains("Egg", true)) qty else (qty / 50.0)
                val kcal = factor * (entry["cal"] ?: 0.0)
                val p = factor * (entry["price"] ?: 0.0)
                totalCals += kcal; totalPrice += p
                builder.append("• $name: ${kcal.toInt()} kcal | ₱${"%.2f".format(p)}\n\n")
            }
        }
        builder.append("---\nTOTAL: ${totalCals.toInt()} kcal | ₱${"%.2f".format(totalPrice)}")
        AlertDialog.Builder(this).setTitle(recipe.title).setMessage(builder.toString()).setPositiveButton("Done", null).show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val steps = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps available."
        AlertDialog.Builder(this).setTitle("Steps").setMessage(steps).setPositiveButton("Done", null).show()
    }
}