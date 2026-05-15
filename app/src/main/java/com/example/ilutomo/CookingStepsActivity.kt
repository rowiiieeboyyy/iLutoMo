package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class CookingStepsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooking_steps)

        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe
        
        // 1. UI Population
        val tvTitle = findViewById<TextView>(R.id.tvRecipeTitle)
        val tvSteps = findViewById<TextView>(R.id.tvRecipeSteps)
        
        if (recipe != null) {
            tvTitle.text = "Steps for ${recipe.title}"
            val stepsText = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n")
            tvSteps.text = stepsText ?: "No steps provided for this recipe."
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
                R.id.nav_recipes -> {
                    // Already here, but reset if needed
                    true
                }
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
}
