package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Summary Column References (Right)
        val tvSummaryDiet = findViewById<TextView>(R.id.tvSummaryDiet)
        val tvSummaryAllergens = findViewById<TextView>(R.id.tvSummaryAllergens)
        val tvSummaryBudget = findViewById<TextView>(R.id.tvSummaryBudget)

        // Selection References (Left)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val tvBudgetText = findViewById<TextView>(R.id.tvBudgetText)
        val seekBarBudget = findViewById<SeekBar>(R.id.seekBarBudget)

        // Groups for Dietary Type
        val dietChecks = listOf(
            findViewById<CheckBox>(R.id.cbStandard), findViewById<CheckBox>(R.id.cbVegan),
            findViewById<CheckBox>(R.id.cbKeto), findViewById<CheckBox>(R.id.cbVegetarian),
            findViewById<CheckBox>(R.id.cbPaleo), findViewById<CheckBox>(R.id.cbPescatarian)
        )

        // Groups for Allergens
        val allergenChecks = listOf(
            findViewById<CheckBox>(R.id.cbPeanuts), findViewById<CheckBox>(R.id.cbShellfish),
            findViewById<CheckBox>(R.id.cbDairy), findViewById<CheckBox>(R.id.cbGluten),
            findViewById<CheckBox>(R.id.cbSoy), findViewById<CheckBox>(R.id.cbEggs)
        )

        // Bottom Navigation Logic
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile

        // Load Saved Data
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        tvSummaryDiet.text = sharedPref.getString("diet", "Standard")
        tvSummaryAllergens.text = sharedPref.getString("allergens", "None")
        tvSummaryBudget.text = sharedPref.getString("budget_text", "0")

        // 1. Realistic Budget Slider (Max 10,000)
        seekBarBudget.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // progress (0-1000) * 10 = 0 to 10,000
                val currentVal = progress * 10
                val formattedPrice = String.format("%,d", currentVal)

                // Real-time labels
                tvBudgetText.text = "₱0-₱$formattedPrice"
                tvSummaryBudget.text = formattedPrice
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 2. Save Button Logic
        btnSave.setOnClickListener {
            val editor = sharedPref.edit()

            // Filter checked items and format as a list
            val dietText = dietChecks.filter { it.isChecked }.joinToString("\n") { it.text }
            tvSummaryDiet.text = if (dietText.isEmpty()) "Standard" else dietText

            val allergenText = allergenChecks.filter { it.isChecked }.joinToString("\n") { it.text }
            tvSummaryAllergens.text = if (allergenText.isEmpty()) "None" else allergenText

            // Save for future use
            editor.putString("diet", tvSummaryDiet.text.toString())
            editor.putString("allergens", tvSummaryAllergens.text.toString())
            editor.putString("budget_text", tvSummaryBudget.text.toString())
            editor.apply()

            Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); finish(); true }
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); finish(); true }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}