package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityProfileBinding
import com.google.android.material.slider.RangeSlider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.ceil

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance() // Added Auth
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        setupBottomNavigation()
        setupRangeListeners()
        setupCheckboxListeners()
        loadIngredientLibrary()

        binding.btnSave.setOnClickListener { savePreferences() }
        binding.btnViewDiary.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }

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
        // Safe defaults for sliders
        binding.rangeBudget.valueTo = 2000f
        binding.rangeProtein.valueTo = 200f
        loadExistingPreferences()
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
                // Add Carbs, Sugar, Sodium cases if they exist in your layout
            }
        }

        binding.rangeBudget.addOnChangeListener(listener)
        binding.rangeProtein.addOnChangeListener(listener)
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
    }

    private fun savePreferences() {
        val uid = auth.currentUser?.uid ?: return

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

        // FIXED PATH: Now writes to Users/[UID]/Preferences
        database.child("Users").child(uid).child("Preferences").setValue(prefs)
            .addOnSuccessListener {
                Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadExistingPreferences() {
        val uid = auth.currentUser?.uid ?: return

        // FIXED PATH: Now reads from Users/[UID]/Preferences
        database.child("Users").child(uid).child("Preferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val diet = snapshot.child("dietary_type").value.toString()
                binding.cbKeto.isChecked = diet == "Keto"
                binding.cbVegetarian.isChecked = diet == "Vegetarian"
                binding.cbPescatarian.isChecked = diet == "Pescatarian"
                binding.cbStandard.isChecked = diet == "Standard" || diet == ""

                // Update Diet Summary Text
                binding.tvSummaryDiet.text = if (diet == "") "Standard" else diet

                // Helper to update slider values
                fun updateSlider(slider: RangeSlider, minKey: String, maxKey: String) {
                    val min = snapshot.child(minKey).value?.toString()?.toFloat() ?: 0f
                    val max = snapshot.child(maxKey).value?.toString()?.toFloat() ?: slider.valueTo
                    slider.values = listOf(min.coerceIn(0f, slider.valueTo), max.coerceIn(0f, slider.valueTo))
                }

                updateSlider(binding.rangeBudget, "budget_min", "budget_max")
                updateSlider(binding.rangeProtein, "protein_min", "protein_max")
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