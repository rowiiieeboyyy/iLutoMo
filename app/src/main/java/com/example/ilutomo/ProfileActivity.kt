package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityProfileBinding
import com.google.firebase.database.FirebaseDatabase

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupRangeListeners()
        loadExistingPreferences()

        binding.btnSave.setOnClickListener {
            savePreferences()
        }

        binding.btnViewDiary.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
    }

    private fun setupRangeListeners() {
        // Budget Range
        binding.rangeBudget.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkBudget.text = "Budget  ₱$min-$max"
            binding.tvSummaryBudget.text = "₱$max"
        }

        // Protein Range
        binding.rangeProtein.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkProtein.text = "Protein  G$min-$max"
            binding.tvSummaryProtein.text = "${max}g"
        }

        // Carbs Range
        binding.rangeCarbs.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkCarbs.text = "Carbs   G$min-$max"
            binding.tvSummaryCarbs.text = "${max}g"
        }

        // NEW: Sugar Range
        binding.rangeSugar.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkSugar.text = "Sugar   G$min-$max"
            binding.tvSummarySugar.text = "${max}g"
        }

        // NEW: Sodium Range
        binding.rangeSodium.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkSodium.text = "Sodium  G$min-$max"
            binding.tvSummarySodium.text = "${max}mg"
        }
    }

    private fun savePreferences() {
        val diet = when {
            binding.cbKeto.isChecked -> "Keto"
            binding.cbVegetarian.isChecked -> "Vegetarian"
            binding.cbPescatarian.isChecked -> "Pescatarian"
            else -> "Standard"
        }

        val allergens = mutableListOf<String>()
        if (binding.cbPeanuts.isChecked) allergens.add("Peanuts")
        if (binding.cbShellfish.isChecked) allergens.add("Shellfish")
        if (binding.cbDairy.isChecked) allergens.add("Dairy")

        val prefs = mapOf(
            "dietary_type" to diet,
            "allergens" to allergens,
            "budget_limit" to binding.rangeBudget.values[1].toInt(),
            "protein_goal" to binding.rangeProtein.values[1].toInt(),
            "carbs_limit" to binding.rangeCarbs.values[1].toInt(),
            "sugar_limit" to binding.rangeSugar.values[1].toInt(),
            "sodium_limit" to binding.rangeSodium.values[1].toInt()
        )

        database.child("UserPreferences").setValue(prefs).addOnSuccessListener {
            // Update the Summary labels in real-time
            binding.tvSummaryDiet.text = diet
            binding.tvSummaryAllergens.text = if (allergens.isEmpty()) "None" else allergens.joinToString(", ")
            binding.tvSummaryBudget.text = "₱${prefs["budget_limit"]}"
            binding.tvSummaryProtein.text = "${prefs["protein_goal"]}g"
            binding.tvSummaryCarbs.text = "${prefs["carbs_limit"]}g"
            binding.tvSummarySugar.text = "${prefs["sugar_limit"]}g"
            binding.tvSummarySodium.text = "${prefs["sodium_limit"]}mg"

            Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadExistingPreferences() {
        database.child("UserPreferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val diet = snapshot.child("dietary_type").value.toString()
                binding.tvSummaryDiet.text = diet

                val budget = (snapshot.child("budget_limit").value as? Long)?.toInt() ?: 0
                binding.tvSummaryBudget.text = "₱$budget"

                // Load and set other summary values
                binding.tvSummaryProtein.text = "${snapshot.child("protein_goal").value ?: 0}g"
                binding.tvSummaryCarbs.text = "${snapshot.child("carbs_limit").value ?: 0}g"
                binding.tvSummarySugar.text = "${snapshot.child("sugar_limit").value ?: 0}g"
                binding.tvSummarySodium.text = "${snapshot.child("sodium_limit").value ?: 0}mg"

                val allergensList = snapshot.child("allergens").children.map { it.value.toString() }
                binding.tvSummaryAllergens.text = if (allergensList.isEmpty()) "None" else allergensList.joinToString(", ")
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
        binding.bottomNav.setOnItemSelectedListener { item ->
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