package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class RecipesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        // Find the sub-navigation buttons
        val btnMacros = findViewById<Button>(R.id.btnMacros)
        val btnSteps = findViewById<Button>(R.id.btnSteps)

        // Redirect to Macronutrients
        btnMacros.setOnClickListener {
            try {
                val intent = Intent(this, MacrosActivity::class.java)
                startActivity(intent)
                // We don't use finish() here so the user can come back
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Redirect to Cooking Steps
        btnSteps.setOnClickListener {
            try {
                val intent = Intent(this, CookingStepsActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Bottom Navigation
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
}