package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // --- 1. UI REFERENCES ---

        // Summary Column (Right Side)
        val tvSummaryDiet = findViewById<TextView>(R.id.tvSummaryDiet)
        val tvSummaryAllergens = findViewById<TextView>(R.id.tvSummaryAllergens)
        val tvSummaryBudget = findViewById<TextView>(R.id.tvSummaryBudget)

        // Inputs (Left Side)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val tvBudgetText = findViewById<TextView>(R.id.tvBudgetText)
        val seekBarBudget = findViewById<SeekBar>(R.id.seekBarBudget)
        val cvDiary = findViewById<CardView>(R.id.cvDiary) // "View diary" button

        // Dietary Checkboxes
        val dietChecks = listOf(
            findViewById<CheckBox>(R.id.cbStandard), findViewById<CheckBox>(R.id.cbVegan),
            findViewById<CheckBox>(R.id.cbKeto), findViewById<CheckBox>(R.id.cbVegetarian),
            findViewById<CheckBox>(R.id.cbPaleo), findViewById<CheckBox>(R.id.cbPescatarian)
        )

        // Allergen Checkboxes
        val allergenChecks = listOf(
            findViewById<CheckBox>(R.id.cbPeanuts), findViewById<CheckBox>(R.id.cbShellfish),
            findViewById<CheckBox>(R.id.cbDairy), findViewById<CheckBox>(R.id.cbGluten),
            findViewById<CheckBox>(R.id.cbSoy), findViewById<CheckBox>(R.id.cbEggs)
        )

        // --- 2. LOAD SAVED DATA ---
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        tvSummaryDiet.text = sharedPref.getString("diet", "Standard")
        tvSummaryAllergens.text = sharedPref.getString("allergens", "None")
        tvSummaryBudget.text = sharedPref.getString("budget_text", "0")

        // --- 3. INTERACTIVE BUDGET SLIDER (0 - 10,000) ---
        // Assuming android:max="1000" in XML
        seekBarBudget.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentVal = progress * 10 // Multiplier for realistic 10k range
                val formatted = String.format("%,d", currentVal)

                // Update label on the left
                tvBudgetText.text = "₱0-₱$formatted"

                // Update summary on the right
                tvSummaryBudget.text = formatted
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // --- 4. SAVE BUTTON LOGIC ---
        btnSave.setOnClickListener {
            val editor = sharedPref.edit()

            // Gather selected diets
            val selectedDiets = dietChecks.filter { it.isChecked }.joinToString("\n") { it.text }
            tvSummaryDiet.text = if (selectedDiets.isEmpty()) "Standard" else selectedDiets

            // Gather selected allergens
            val selectedAllergens = allergenChecks.filter { it.isChecked }.joinToString("\n") { it.text }
            tvSummaryAllergens.text = if (selectedAllergens.isEmpty()) "None" else selectedAllergens

            // Save to SharedPreferences
            editor.putString("diet", tvSummaryDiet.text.toString())
            editor.putString("allergens", tvSummaryAllergens.text.toString())
            editor.putString("budget_text", tvSummaryBudget.text.toString())
            editor.apply()

            Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
        }

        // --- 5. NAVIGATION TO DIARY PAGE ---
        cvDiary.setOnClickListener {
            val intent = Intent(this, DiaryActivity::class.java)
            startActivity(intent)
        }

        // --- 6. BOTTOM NAVIGATION ---
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_recipes -> {
                    startActivity(Intent(this, RecipesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}