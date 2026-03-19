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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        val btnChoose = findViewById<Button>(R.id.btnChooseRecipe)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvAvailable.layoutManager = LinearLayoutManager(this)

        btnChoose.setOnClickListener {
            FirebaseDatabase.getInstance().getReference("UserSelection").get().addOnSuccessListener { snapshot ->
                val selections = snapshot.children.map { it.key ?: "" }
                if (selections.isEmpty()) {
                    Toast.makeText(this, "Add recipes from Home first!", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Select Recipe")
                        .setItems(selections.toTypedArray()) { _, which ->
                            val selectedName = selections[which]
                            btnChoose.text = selectedName
                            loadRecipeData(selectedName)
                        }.show()
                }
            }
        }

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
    }

    private fun loadRecipeData(name: String) {
        FirebaseDatabase.getInstance().getReference("admin_recipes").orderByChild("title").equalTo(name)
            .get().addOnSuccessListener { snapshot ->
                val recipeNode = snapshot.children.firstOrNull()
                val recipe = recipeNode?.getValue(Recipe::class.java)

                currentIngredients.clear()
                recipe?.ingredients?.forEach { (ing, amt) ->
                    // .trim() handles database entries with accidental spaces
                    currentIngredients.add(DisplayIngredient(ing.trim(), amt))
                }
                updateUI()
            }
    }

    private fun updateUI() {
        rvAvailable.adapter = IngredientCheckAdapter(currentIngredients) {
            renderNeededList()
        }
        renderNeededList()
    }

    private fun renderNeededList() {
        if (currentIngredients.isEmpty()) return

        val fullText = StringBuilder()
        currentIngredients.forEach { fullText.append("${it.name}\n\n") }

        val spannableString = SpannableString(fullText.toString())
        var pointer = 0

        currentIngredients.forEach { ingredient ->
            val entryText = "${ingredient.name}\n\n"
            if (ingredient.isChecked) {
                // Apply Strikethrough
                spannableString.setSpan(StrikethroughSpan(), pointer, pointer + ingredient.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                // Apply Grey Color
                spannableString.setSpan(ForegroundColorSpan(Color.GRAY), pointer, pointer + ingredient.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                // Apply Italics
                spannableString.setSpan(StyleSpan(Typeface.ITALIC), pointer, pointer + ingredient.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            pointer += entryText.length
        }
        tvNeeded.text = spannableString
    }
}