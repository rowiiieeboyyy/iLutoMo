package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    true
                }
                R.id.nav_recipes -> {
                    // THIS WAS MISSING
                    startActivity(Intent(this, RecipesActivity::class.java))
                    true
                }
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}