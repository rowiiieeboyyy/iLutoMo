package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CookingStepsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val currentPantryItems = mutableListOf<PantryIngredient>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooking_steps)

        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe
        
        // 1. UI Population
        val tvTitle = findViewById<TextView>(R.id.tvRecipeTitle)
        val tvSteps = findViewById<TextView>(R.id.tvRecipeSteps)
        val llIngredients = findViewById<LinearLayout>(R.id.llStepsIngredients)
        
        if (recipe != null) {
            tvTitle.text = "Steps for ${recipe.title}"
            val stepsText = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n")
            tvSteps.text = stepsText ?: "No steps provided for this recipe."
            
            loadPantryAndDisplayIngredients(recipe, llIngredients)
        }

        // 2. Back Arrow Logic
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        // 3. Bottom Navigation Logic
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_recipes

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_recipes -> true
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPantryAndDisplayIngredients(recipe: Recipe, container: LinearLayout) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Pantry").get().addOnSuccessListener { snapshot ->
            currentPantryItems.clear()
            snapshot.children.forEach { child ->
                child.getValue(PantryIngredient::class.java)?.let {
                    it.id = child.key ?: ""
                    currentPantryItems.add(it)
                }
            }
            displayIngredients(recipe, container)
        }.addOnFailureListener {
            displayIngredients(recipe, container)
        }
    }

    private fun displayIngredients(recipe: Recipe, container: LinearLayout) {
        container.removeAllViews()
        val multiplier = recipe.servings
        val pool = PriceCalculator.buildAvailablePool(currentPantryItems)

        recipe.ingredients?.forEach { (name, rawAmount) ->
            val amountStr = rawAmount.toString()
            val scaledAmount = PriceCalculator.scaleAmount(amountStr, multiplier)
            
            // Check availability in pantry pool (Unit Aware)
            val neededVal = PriceCalculator.extractNumericValue(amountStr) * multiplier
            val tag = name.lowercase().trim()
            val avail = pool[tag] ?: 0.0
            
            // Fuzzy check for sub-parts if direct tag doesn't match
            var actualAvail = avail
            if (actualAvail < neededVal) {
                pool.forEach { (pantryTag, pAvail) ->
                    if (tag.contains(pantryTag) || pantryTag.contains(tag)) {
                        actualAvail = pAvail
                    }
                }
            }
            
            val isInPantry = actualAvail >= neededVal

            val horizontalLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER_VERTICAL
            }

            val checkBox = CheckBox(this).apply {
                isChecked = isInPantry
                isEnabled = true
            }

            val tvIngredient = TextView(this).apply {
                val status = if (isInPantry) " [In Pantry]" else ""
                text = "• $name ($scaledAmount)$status"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (isInPantry) Color.parseColor("#2D5A27") else Color.BLACK)
                if (isInPantry) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            horizontalLayout.addView(checkBox)
            horizontalLayout.addView(tvIngredient)
            container.addView(horizontalLayout)
        }
    }
}
