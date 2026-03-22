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
        binding.rangeBudget.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkBudget.text = "Budget  ₱$min-$max"
            binding.tvSummaryBudget.text = "₱$max"
        }

        binding.rangeProtein.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkProtein.text = "Protein  G$min-$max"
            binding.tvSummaryProtein.text = "${max}g"
        }

        binding.rangeCarbs.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkCarbs.text = "Carbs   G$min-$max"
            binding.tvSummaryCarbs.text = "${max}g"
        }

        binding.rangeSugar.addOnChangeListener { slider, _, _ ->
            val min = slider.values[0].toInt()
            val max = slider.values[1].toInt()
            binding.checkSugar.text = "Sugar   G$min-$max"
            binding.tvSummarySugar.text = "${max}g"
        }

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

        // We now save the 'isChecked' state so HomeActivity knows if it should filter
        val prefs = mapOf(
            "dietary_type" to diet,
            "allergens" to allergens,
            "use_budget" to binding.checkBudget.isChecked,
            "budget_limit" to binding.rangeBudget.values[1].toInt(),
            "use_protein" to binding.checkProtein.isChecked,
            "protein_goal" to binding.rangeProtein.values[1].toInt(),
            "use_carbs" to binding.checkCarbs.isChecked,
            "carbs_limit" to binding.rangeCarbs.values[1].toInt(),
            "use_sugar" to binding.checkSugar.isChecked,
            "sugar_limit" to binding.rangeSugar.values[1].toInt(),
            "use_sodium" to binding.checkSodium.isChecked,
            "sodium_limit" to binding.rangeSodium.values[1].toInt()
        )

        database.child("UserPreferences").setValue(prefs).addOnSuccessListener {
            updateSummaryUI(diet, allergens, prefs)
            Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSummaryUI(diet: String, allergens: List<String>, prefs: Map<String, Any>) {
        binding.tvSummaryDiet.text = diet
        binding.tvSummaryAllergens.text = if (allergens.isEmpty()) "None" else allergens.joinToString(", ")

        // Only show values in summary if the filter is enabled
        binding.tvSummaryBudget.text = if (binding.checkBudget.isChecked) "₱${prefs["budget_limit"]}" else "Off"
        binding.tvSummaryProtein.text = if (binding.checkProtein.isChecked) "${prefs["protein_goal"]}g" else "Off"
        binding.tvSummaryCarbs.text = if (binding.checkCarbs.isChecked) "${prefs["carbs_limit"]}g" else "Off"
        binding.tvSummarySugar.text = if (binding.checkSugar.isChecked) "${prefs["sugar_limit"]}g" else "Off"
        binding.tvSummarySodium.text = if (binding.checkSodium.isChecked) "${prefs["sodium_limit"]}mg" else "Off"
    }

    private fun loadExistingPreferences() {
        database.child("UserPreferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Restore Checkbox States
                binding.checkBudget.isChecked = snapshot.child("use_budget").getValue(Boolean::class.java) ?: false
                binding.checkProtein.isChecked = snapshot.child("use_protein").getValue(Boolean::class.java) ?: false
                binding.checkCarbs.isChecked = snapshot.child("use_carbs").getValue(Boolean::class.java) ?: false
                binding.checkSugar.isChecked = snapshot.child("use_sugar").getValue(Boolean::class.java) ?: false
                binding.checkSodium.isChecked = snapshot.child("use_sodium").getValue(Boolean::class.java) ?: false

                // Restore Summary UI
                val diet = snapshot.child("dietary_type").value.toString()
                binding.tvSummaryDiet.text = diet
                binding.tvSummaryBudget.text = "₱${snapshot.child("budget_limit").value ?: 0}"
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