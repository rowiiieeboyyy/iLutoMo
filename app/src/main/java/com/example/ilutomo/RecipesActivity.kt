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
import com.google.firebase.database.FirebaseDatabase

class RecipesActivity : AppCompatActivity() {

    private lateinit var tvNeeded: TextView
    private lateinit var rvAvailable: RecyclerView
    private var currentIngredients = mutableListOf<DisplayIngredient>()
    private var currentRecipe: Recipe? = null

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

        // Setup Navigation
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
            FirebaseDatabase.getInstance().getReference("UserSelection").get().addOnSuccessListener { snapshot ->
                val selections = snapshot.children.map { it.key ?: "" }
                if (selections.isEmpty()) {
                    Toast.makeText(this, "Add recipes from Home first!", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this).setTitle("Select Recipe").setItems(selections.toTypedArray()) { _, which ->
                        val selectedName = selections[which]
                        btnChoose.text = selectedName
                        loadRecipeData(selectedName)
                    }.show()
                }
            }
        }

        btnMacros.setOnClickListener {
            currentRecipe?.let { showMacrosDialog(it) } ?: Toast.makeText(this, "Select a recipe!", Toast.LENGTH_SHORT).show()
        }

        btnSteps.setOnClickListener {
            currentRecipe?.let { showStepsDialog(it) } ?: Toast.makeText(this, "Select a recipe!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRecipeData(name: String) {
        FirebaseDatabase.getInstance().reference.get().addOnSuccessListener { snapshot ->
            for (data in snapshot.children) {
                if (data.key == "UserSelection") continue
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

    private fun showMacrosDialog(recipe: Recipe) {
        val macrosText = recipe.macros.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        AlertDialog.Builder(this).setTitle("Macros").setMessage(macrosText).setPositiveButton("Close", null).show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val stepsText = recipe.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n")
        AlertDialog.Builder(this).setTitle("Steps").setMessage(stepsText).setPositiveButton("Done", null).show()
    }
}