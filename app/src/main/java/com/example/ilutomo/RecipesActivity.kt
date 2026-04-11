package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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

        // Initialize Views
        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        val btnChoose = findViewById<Button>(R.id.btnChooseRecipe)
        val btnMacros = findViewById<Button>(R.id.btnViewMacros)
        val btnSteps = findViewById<Button>(R.id.btnViewSteps)
        val btnAddToPantry = findViewById<Button>(R.id.btnAddToPantry)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvAvailable.layoutManager = LinearLayoutManager(this)
        loadIngredientLibrary()

        // Navigation Logic
        bottomNav.selectedItemId = R.id.nav_recipes
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_recipes -> true
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
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
                addedRecipes.add(recipe)
            }

            if (addedRecipes.isEmpty()) {
                Toast.makeText(this, "No recipes in your list!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            addedRecipes.sortBy { it.title }
            val titles = addedRecipes.map { it.title }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Select Recipe")
                .setItems(titles) { _, which ->
                    val selected = addedRecipes[which]
                    currentRecipe = selected
                    btnChoose.text = selected.title
                    loadRecipeDataIntoUI(selected)
                }
                .setNeutralButton("Delete") { _, _ -> showDeleteRecipeDialog(addedRecipes, btnChoose) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showDeleteRecipeDialog(recipes: List<Recipe>, btnChoose: Button) {
        val uid = auth.currentUser?.uid ?: return
        val titles = recipes.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Remove Recipe")
            .setItems(titles) { _, which ->
                val selected = recipes[which]
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
        var currentPos = 0
        currentIngredients.forEach { ing ->
            val segment = "${ing.name} (${ing.amount})\n\n"
            if (!ing.isChecked) {
                spannable.setSpan(StrikethroughSpan(), currentPos, currentPos + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.parseColor("#888888")), currentPos, currentPos + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            currentPos += segment.length
        }
        tvNeeded.text = spannable
    }

    private fun showAccurateMacrosDialog(recipe: Recipe) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_info, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val tvContent = dialogView.findViewById<TextView>(R.id.dialogContent)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotalValue)
        val llTotal = dialogView.findViewById<LinearLayout>(R.id.llTotalSection)
        val btnDone = dialogView.findViewById<Button>(R.id.btnDialogDone)

        tvTitle.text = "Detailed Nutrition"
        llTotal.visibility = View.VISIBLE

        val contentBuilder = StringBuilder()
        var totalCals = 0.0; var totalCarbs = 0.0; var totalProt = 0.0; var totalSug = 0.0; var totalPrice = 0.0

        recipe.ingredients?.forEach { (fullName, amount) ->
            // Cleans "Pork Belly (200)" to "Pork Belly" for database lookup
            val cleanName = fullName.split("(")[0].trim()
            val qtyString = amount.toString()
            val qtyNumeric = qtyString.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0

            val entry = ingredientLibrary.entries.find { it.key.equals(cleanName, true) }?.value

            if (entry != null) {
                val factor = if (cleanName.contains("Egg", true)) qtyNumeric else (qtyNumeric / 50.0)

                // Matching Firebase keys: cal, carb, pro, sugar, price
                val kcal = factor * (entry["cal"] ?: 0.0)
                val carbs = factor * (entry["carb"] ?: 0.0)
                val prot = factor * (entry["pro"] ?: 0.0)
                val sugar = factor * (entry["sugar"] ?: 0.0)
                val price = factor * (entry["price"] ?: 0.0)

                totalCals += kcal; totalCarbs += carbs; totalProt += prot; totalSug += sugar; totalPrice += price

                contentBuilder.append("• $fullName\n")
                contentBuilder.append("   ${kcal.toInt()} kcal | P: ${prot.toInt()}g | C: ${carbs.toInt()}g | S: ${sugar.toInt()}g\n")
                contentBuilder.append("   Cost: ₱${"%.2f".format(price)}\n\n")
            } else {
                contentBuilder.append("• $fullName\n   (Nutrition data unavailable)\n\n")
            }
        }

        tvContent.text = contentBuilder.toString().trim()
        tvTotal.text = "Total: ${totalCals.toInt()} kcal | ₱${"%.2f".format(totalPrice)}\n" +
                "P: ${totalProt.toInt()}g | C: ${totalCarbs.toInt()}g | S: ${totalSug.toInt()}g"

        btnDone.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_info, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val tvContent = dialogView.findViewById<TextView>(R.id.dialogContent)
        val llTotal = dialogView.findViewById<LinearLayout>(R.id.llTotalSection)
        val btnDone = dialogView.findViewById<Button>(R.id.btnDialogDone)

        tvTitle.text = "Cooking Steps"
        llTotal.visibility = View.GONE

        val steps = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps."
        tvContent.text = steps

        btnDone.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun addToPantry() {
        val uid = auth.currentUser?.uid ?: return
        if (currentRecipe == null) return
        val selected = currentIngredients.filter { it.isChecked }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Check ingredients first", Toast.LENGTH_SHORT).show()
            return
        }

        val pantryRef = database.child("Users").child(uid).child("Pantry")
        selected.forEach { ing ->
            val key = pantryRef.push().key ?: return@forEach
            val item = mapOf("id" to key, "name" to ing.name, "amount" to ing.amount, "recipeTitle" to currentRecipe!!.title, "isChecked" to true)
            pantryRef.child(key).setValue(item)
        }
        Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, PantryActivity::class.java))
    }
}