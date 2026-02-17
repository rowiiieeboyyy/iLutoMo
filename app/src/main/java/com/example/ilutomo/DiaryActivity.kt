package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class DiaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        // Back button
        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        // Open Edit Diary screen
        findViewById<TextView>(R.id.tvEditDiary)?.setOnClickListener {
            startActivity(Intent(this, EditDiaryActivity::class.java))
        }

        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        // Load saved values from SharedPreferences
        val sharedPref = getSharedPreferences("DiaryPrefs", Context.MODE_PRIVATE)

        findViewById<TextView>(R.id.tvRemainingValue)?.text = "${sharedPref.getString("cal", "0")}\nRemaining"
        findViewById<TextView>(R.id.tvProteinValue)?.text = "${sharedPref.getString("pro", "0")}g\nRemaining"
        findViewById<TextView>(R.id.tvCarbsValue)?.text = "${sharedPref.getString("carb", "0")}g\nRemaining"
        findViewById<TextView>(R.id.tvFatsValue)?.text = "${sharedPref.getString("fat", "0")}g\nRemaining"
        findViewById<TextView>(R.id.tvSodiumValue)?.text = "${sharedPref.getString("sod", "0")}mg\nRemaining"
        findViewById<TextView>(R.id.tvSugarValue)?.text = "${sharedPref.getString("sug", "0")}g\nRemaining"
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav?.selectedItemId = R.id.nav_profile
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); finish(); true }
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}