package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class DiaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvEditDiary)?.setOnClickListener {
            startActivity(Intent(this, EditDiaryActivity::class.java))
        }

        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadDiaryData()
    }

    private fun loadDiaryData() {
        val sharedPref = getSharedPreferences("DiaryPrefs", Context.MODE_PRIVATE)
        
        // GET DATA - Added null checks and default values
        val calStr = sharedPref.getString("cal", "0")?.replace(",", "")?.ifEmpty { "0" } ?: "0"
        val proStr = sharedPref.getString("pro", "0")?.ifEmpty { "0" } ?: "0"
        val carbStr = sharedPref.getString("carb", "0")?.ifEmpty { "0" } ?: "0"
        val fatStr = sharedPref.getString("fat", "0")?.ifEmpty { "0" } ?: "0"
        val sodStr = sharedPref.getString("sod", "0")?.ifEmpty { "0" } ?: "0"
        val sugStr = sharedPref.getString("sug", "0")?.ifEmpty { "0" } ?: "0"

        // UPDATE TEXT VIEWS - Using safe calls to prevent crash if layout IDs don't match
        findViewById<TextView>(R.id.tvRemainingValueRaw)?.text = calStr
        findViewById<TextView>(R.id.tvProteinValue)?.text = "${proStr}g"
        findViewById<TextView>(R.id.tvCarbsValue)?.text = "${carbStr}g"
        findViewById<TextView>(R.id.tvFatsValue)?.text = "${fatStr}g"
        findViewById<TextView>(R.id.tvSodiumValue)?.text = "${sodStr}mg"
        findViewById<TextView>(R.id.tvSugarValue)?.text = "${sugStr}g"

        // UPDATE PROGRESS BAR
        val calValue = calStr.toIntOrNull() ?: 0
        val pb = findViewById<ProgressBar>(R.id.pbCalories)
        val progress = if (calValue > 0) ((calValue / 2500f) * 100).toInt() else 0
        pb?.progress = progress.coerceIn(0, 100)
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