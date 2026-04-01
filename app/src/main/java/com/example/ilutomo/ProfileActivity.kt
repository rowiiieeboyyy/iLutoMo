package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityProfileBinding
import com.google.android.material.slider.RangeSlider
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.ceil

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- NEW: CLICK LISTENER FOR PROFILE ICON ---
        binding.ivProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        setupBottomNavigation()
        setupRangeListeners()
        setupCheckboxListeners()
        loadIngredientLibrary()

        binding.btnSave.setOnClickListener { savePreferences() }
        binding.btnViewDiary.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
        
        // --- NEW: CLICK LISTENER FOR VIEW ORDERS ---
        binding.btnViewOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }
    }

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                for (data in snapshot.children) {
                    val stats = data.children.associate {
                        it.key!! to (it.value?.toString()?.toDoubleOrNull() ?: 0.0)
                    }
                    ingredientLibrary[data.key!!] = stats
                }
                calculateAndSetSliderRanges()
            } else {
                loadExistingPreferences()
            }
        }.addOnFailureListener { loadExistingPreferences() }
    }

    private fun calculateAndSetSliderRanges() {
        database.get().addOnSuccessListener { snapshot ->
            // Apply Dynamic Max Values safely
            binding.rangeBudget.valueTo = 1000f
            binding.rangeProtein.valueTo = 100f
            loadExistingPreferences()
        }
    }

    private fun setupRangeListeners() {
        val listener = RangeSlider.OnChangeListener { slider, _, _ ->
            val min = slider.values.getOrNull(0)?.toInt() ?: 0
            val max = slider.values.getOrNull(1)?.toInt() ?: slider.valueTo.toInt()

            when(slider.id) {
                R.id.rangeBudget -> {
                    binding.checkBudget.text = "Budget ₱$min-$max"
                    binding.tvSummaryBudget.text = "₱$min-$max"
                }
                R.id.rangeProtein -> {
                    binding.checkProtein.text = "Protein ${min}g-${max}g"
                    binding.tvSummaryProtein.text = "${min}g-${max}g"
                }
                R.id.rangeCarbs -> {
                    binding.checkCarbs.text = "Carbs ${min}g-${max}g"
                    binding.tvSummaryCarbs.text = "${min}g-${max}g"
                }
                R.id.rangeSugar -> {
                    binding.checkSugar.text = "Sugar ${min}g-${max}g"
                    binding.tvSummarySugar.text = "${min}g-${max}g"
                }
                R.id.rangeSodium -> {
                    binding.checkSodium.text = "Sodium ${min}mg-${max}mg"
                    binding.tvSummarySodium.text = "${min}mg-${max}mg"
                }
            }
        }

        binding.rangeBudget.addOnChangeListener(listener)
        binding.rangeProtein.addOnChangeListener(listener)
        binding.rangeCarbs.addOnChangeListener(listener)
        binding.rangeSugar.addOnChangeListener(listener)
        binding.rangeSodium.addOnChangeListener(listener)
    }

    private fun setupCheckboxListeners() {
        val dietChecks = listOf(binding.cbStandard, binding.cbVegetarian, binding.cbKeto, binding.cbPescatarian)
        dietChecks.forEach { cb ->
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    dietChecks.filter { it != cb }.forEach { it.isChecked = false }
                    binding.tvSummaryDiet.text = cb.text
                }
            }
        }

        val allergenChecks = listOf(binding.cbPeanuts, binding.cbShellfish, binding.cbDairy, binding.cbOthers)
        allergenChecks.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ ->
                val selected = allergenChecks.filter { it.isChecked }.joinToString(", ") { it.text }
                binding.tvSummaryAllergens.text = if (selected.isEmpty()) "None" else selected
            }
        }
    }

    private fun savePreferences() {
        val diet = when {
            binding.cbKeto.isChecked -> "Keto"
            binding.cbVegetarian.isChecked -> "Vegetarian"
            binding.cbPescatarian.isChecked -> "Pescatarian"
            else -> "Standard"
        }

        val prefs = mapOf(
            "dietary_type" to diet,
            "budget_min" to (binding.rangeBudget.values.getOrNull(0)?.toInt() ?: 0),
            "budget_max" to (binding.rangeBudget.values.getOrNull(1)?.toInt() ?: 1000)
        )

        database.child("UserPreferences").setValue(prefs).addOnSuccessListener {
            Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadExistingPreferences() {
        database.child("UserPreferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val diet = snapshot.child("dietary_type").value.toString()
                binding.cbKeto.isChecked = diet == "Keto"
                binding.cbVegetarian.isChecked = diet == "Vegetarian"
                binding.cbPescatarian.isChecked = diet == "Pescatarian"
                binding.cbStandard.isChecked = diet == "Standard" || diet == "null"

                fun updateS(slider: RangeSlider, minK: String, maxK: String) {
                    val min = snapshot.child(minK).value?.toString()?.toFloat() ?: 0f
                    val max = snapshot.child(maxK).value?.toString()?.toFloat() ?: slider.valueTo
                    slider.values = listOf(min.coerceIn(0f, slider.valueTo), max.coerceIn(0f, slider.valueTo))
                }
                updateS(binding.rangeBudget, "budget_min", "budget_max")
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