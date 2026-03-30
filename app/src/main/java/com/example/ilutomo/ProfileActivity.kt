package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityProfileBinding
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

        setupBottomNavigation()
        setupRangeListeners()
        loadIngredientLibrary()

        binding.btnSave.setOnClickListener { savePreferences() }
        binding.btnViewDiary.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
    }

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").get().addOnSuccessListener { snapshot ->
            for (data in snapshot.children) {
                val stats = data.children.associate { it.key!! to (it.value?.toString()?.toDoubleOrNull() ?: 0.0) }
                ingredientLibrary[data.key!!] = stats
            }
            calculateAndSetSliderRanges()
        }
    }

    private fun calculateAndSetSliderRanges() {
        database.get().addOnSuccessListener { snapshot ->
            val prices = mutableListOf<Float>()
            val proteins = mutableListOf<Float>()
            val carbs = mutableListOf<Float>()
            val sugars = mutableListOf<Float>()
            val sodiums = mutableListOf<Float>()

            for (child in snapshot.children) {
                if (child.key?.startsWith("recipe_") == true) {
                    var pT = 0.0; var proT = 0.0; var cT = 0.0; var sT = 0.0; var naT = 0.0
                    val ingredients = child.child("ingredients").value as? Map<String, Any>

                    ingredients?.forEach { (name, amt) ->
                        val qty = amt.toString().toDoubleOrNull() ?: 0.0
                        val lib = ingredientLibrary[name]
                        if (lib != null) {
                            val isPiece = name.contains("Egg", true) || name.contains("Wrapper", true) || name.contains("Banana", true)
                            val factor = if (isPiece) qty else (qty / 50.0)
                            pT += factor * (lib["price"] ?: 0.0)
                            proT += factor * (lib["pro"] ?: 0.0)
                            cT += factor * (lib["carb"] ?: 0.0)
                            sT += factor * (lib["sugar"] ?: 0.0)
                            naT += factor * (lib["sodium"] ?: 0.0)
                        }
                    }
                    prices.add(pT.toFloat()); proteins.add(proT.toFloat())
                    carbs.add(cT.toFloat()); sugars.add(sT.toFloat()); sodiums.add(naT.toFloat())
                }
            }

            // Dynamic Max with Buffer (Rounding up to nearest 10 or 100)
            binding.rangeBudget.valueTo = (ceil((prices.maxOrNull() ?: 1000f) / 100.0) * 100.0).toFloat().coerceAtLeast(500f)
            binding.rangeProtein.valueTo = (ceil((proteins.maxOrNull() ?: 100f) / 10.0) * 10.0).toFloat().coerceAtLeast(50f)
            binding.rangeCarbs.valueTo = (ceil((carbs.maxOrNull() ?: 200f) / 10.0) * 10.0).toFloat().coerceAtLeast(100f)
            binding.rangeSugar.valueTo = (ceil((sugars.maxOrNull() ?: 100f) / 10.0) * 10.0).toFloat().coerceAtLeast(50f)
            binding.rangeSodium.valueTo = (ceil((sodiums.maxOrNull() ?: 2000f) / 500.0) * 500.0).toFloat().coerceAtLeast(1000f)

            loadExistingPreferences()
        }
    }

    private fun setupRangeListeners() {
        binding.rangeBudget.addOnChangeListener { s, _, _ -> binding.checkBudget.text = "Budget ₱${s.values[0].toInt()}-${s.values[1].toInt()}" }
        binding.rangeProtein.addOnChangeListener { s, _, _ -> binding.checkProtein.text = "Protein ${s.values[0].toInt()}g-${s.values[1].toInt()}g" }
        binding.rangeCarbs.addOnChangeListener { s, _, _ -> binding.checkCarbs.text = "Carbs ${s.values[0].toInt()}g-${s.values[1].toInt()}g" }
        binding.rangeSugar.addOnChangeListener { s, _, _ -> binding.checkSugar.text = "Sugar ${s.values[0].toInt()}g-${s.values[1].toInt()}g" }
        binding.rangeSodium.addOnChangeListener { s, _, _ -> binding.checkSodium.text = "Sodium ${s.values[0].toInt()}mg-${s.values[1].toInt()}mg" }
    }

    private fun savePreferences() {
        val diet = when {
            binding.cbKeto.isChecked -> "Keto"; binding.cbVegetarian.isChecked -> "Vegetarian"
            binding.cbPescatarian.isChecked -> "Pescatarian"; else -> "Standard"
        }
        val prefs = mapOf(
            "dietary_type" to diet,
            "use_budget" to binding.checkBudget.isChecked,
            "budget_min" to binding.rangeBudget.values[0].toInt(), "budget_max" to binding.rangeBudget.values[1].toInt(),
            "use_protein" to binding.checkProtein.isChecked,
            "protein_min" to binding.rangeProtein.values[0].toInt(), "protein_max" to binding.rangeProtein.values[1].toInt(),
            "use_carbs" to binding.checkCarbs.isChecked,
            "carbs_min" to binding.rangeCarbs.values[0].toInt(), "carbs_max" to binding.rangeCarbs.values[1].toInt(),
            "use_sugar" to binding.checkSugar.isChecked,
            "sugar_min" to binding.rangeSugar.values[0].toInt(), "sugar_max" to binding.rangeSugar.values[1].toInt(),
            "use_sodium" to binding.checkSodium.isChecked,
            "sodium_min" to binding.rangeSodium.values[0].toInt(), "sodium_max" to binding.rangeSodium.values[1].toInt()
        )
        database.child("UserPreferences").setValue(prefs).addOnSuccessListener {
            Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadExistingPreferences() {
        database.child("UserPreferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                fun updateS(slider: com.google.android.material.slider.RangeSlider, minK: String, maxK: String) {
                    val min = snapshot.child(minK).value?.toString()?.toFloat() ?: 0f
                    val max = snapshot.child(maxK).value?.toString()?.toFloat() ?: slider.valueTo
                    slider.values = listOf(min, max.coerceAtMost(slider.valueTo))
                }
                updateS(binding.rangeBudget, "budget_min", "budget_max")
                updateS(binding.rangeProtein, "protein_min", "protein_max")
                updateS(binding.rangeCarbs, "carbs_min", "carbs_max")
                updateS(binding.rangeSugar, "sugar_min", "sugar_max")
                updateS(binding.rangeSodium, "sodium_min", "sodium_max")

                binding.checkBudget.isChecked = snapshot.child("use_budget").getValue(Boolean::class.java) ?: false
                binding.checkProtein.isChecked = snapshot.child("use_protein").getValue(Boolean::class.java) ?: false
                binding.checkCarbs.isChecked = snapshot.child("use_carbs").getValue(Boolean::class.java) ?: false
                binding.checkSugar.isChecked = snapshot.child("use_sugar").getValue(Boolean::class.java) ?: false
                binding.checkSodium.isChecked = snapshot.child("use_sodium").getValue(Boolean::class.java) ?: false
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