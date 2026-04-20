package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityProfileBinding
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupInteractiveListeners()
        loadExistingPreferences()

        binding.ivProfile.setOnClickListener { startActivity(Intent(this, EditProfileActivity::class.java)) }
        binding.btnViewDiary.setOnClickListener { startActivity(Intent(this, DiaryActivity::class.java)) }
        binding.btnViewOrders.setOnClickListener { startActivity(Intent(this, OrdersActivity::class.java)) }
        binding.btnSave.setOnClickListener { savePreferences() }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.setOnItemSelectedListener(null)
        binding.bottomNav.selectedItemId = R.id.nav_profile
        setupBottomNavigation()
    }

    private fun setupInteractiveListeners() {
        val dietChecks = listOf(binding.cbStandard, binding.cbVegetarian, binding.cbKeto, binding.cbPescatarian)
        dietChecks.forEach { cb ->
            cb.setOnClickListener { dietChecks.filter { it != cb }.forEach { it.isChecked = false } }
        }

        binding.cbOthers.setOnCheckedChangeListener { _, isChecked ->
            binding.etOtherAllergen.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) binding.etOtherAllergen.setText("")
        }

        syncRangeSlider(binding.checkBudget, binding.rangeBudget, binding.etBudgetInput)
        syncRangeSlider(binding.checkProtein, binding.rangeProtein, binding.etProteinInput)
        syncSingleSlider(binding.checkCarbs, binding.rangeCarbs, binding.etCarbsInput)
        syncSingleSlider(binding.checkSugar, binding.rangeSugar, binding.etSugarInput)
        syncSingleSlider(binding.checkCalories, binding.rangeCalories, binding.etCaloriesInput)
    }

    private fun syncRangeSlider(checkBox: CheckBox, slider: RangeSlider, editText: EditText) {
        slider.labelBehavior = LabelFormatter.LABEL_FLOATING
        slider.setLabelFormatter { it.toInt().toString() }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            slider.isEnabled = isChecked
            editText.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSummaryUI()
        }

        slider.addOnChangeListener { s, _, fromUser ->
            if (fromUser) {
                editText.setText(s.values[1].toInt().toString())
                updateSummaryUI()
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toFloatOrNull() ?: 0f
                if (input in slider.valueFrom..slider.valueTo) {
                    slider.values = listOf(slider.values[0], input)
                    updateSummaryUI()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun syncSingleSlider(checkBox: CheckBox, slider: Slider, editText: EditText) {
        slider.labelBehavior = LabelFormatter.LABEL_FLOATING
        slider.setLabelFormatter { it.toInt().toString() }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            slider.isEnabled = isChecked
            editText.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSummaryUI()
        }

        slider.addOnChangeListener { s, _, fromUser ->
            if (fromUser) {
                editText.setText(s.value.toInt().toString())
                updateSummaryUI()
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toFloatOrNull() ?: 0f
                if (input in slider.valueFrom..slider.valueTo) {
                    slider.value = input
                    updateSummaryUI()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateSummaryUI() {
        val selectedDiet = when {
            binding.cbVegetarian.isChecked -> "Vegetarian"
            binding.cbKeto.isChecked -> "Keto"
            binding.cbPescatarian.isChecked -> "Pescatarian"
            else -> "Standard"
        }
        binding.tvSummaryDiet.text = "Diet: $selectedDiet"

        binding.tvSummaryBudget.text = if (binding.checkBudget.isChecked)
            "Budget: ₱${binding.rangeBudget.values[0].toInt()} - ₱${binding.rangeBudget.values[1].toInt()}" else "Budget: Not set"

        binding.tvSummaryProtein.text = if (binding.checkProtein.isChecked)
            "Protein: ${binding.rangeProtein.values[0].toInt()}g - ${binding.rangeProtein.values[1].toInt()}g" else "Protein: Not set"

        binding.tvSummaryCarbs.text = if (binding.checkCarbs.isChecked) "Carbs: Max ${binding.rangeCarbs.value.toInt()}g" else "Carbs: Not set"
        binding.tvSummarySugar.text = if (binding.checkSugar.isChecked) "Sugar: Max ${binding.rangeSugar.value.toInt()}g" else "Sugar: Not set"
        binding.tvSummaryCalories.text = if (binding.checkCalories.isChecked) "Calories: Max ${binding.rangeCalories.value.toInt()}kcal" else "Calories: Not set"
    }

    private fun savePreferences() {
        val uid = auth.currentUser?.uid ?: return

        // Capture Taste Preferences
        val preferredTastes = mutableListOf<String>()
        if (binding.cbSweet.isChecked) preferredTastes.add("sweet")
        if (binding.cbSavory.isChecked) preferredTastes.add("savory")
        if (binding.cbSpicy.isChecked) preferredTastes.add("spicy")
        if (binding.cbSour.isChecked) preferredTastes.add("sour")

        // Capture Prep Time Preference
        val prefersShortPrep = binding.rbShortPrep.isChecked

        val prefs = mutableMapOf<String, Any>(
            "dietary_type" to when {
                binding.cbVegetarian.isChecked -> "Vegetarian"
                binding.cbKeto.isChecked -> "Keto"
                binding.cbPescatarian.isChecked -> "Pescatarian"
                else -> "Standard"
            },
            "allergens" to mapOf(
                "Soy" to binding.cbSoy.isChecked,
                "Gluten" to binding.cbGluten.isChecked,
                "Dairy" to binding.cbDairy.isChecked,
                "Others" to binding.cbOthers.isChecked,
                "Others_Value" to binding.etOtherAllergen.text.toString().trim()
            ),
            "preferred_tastes" to preferredTastes,
            "prefers_short_prep" to prefersShortPrep
        )

        prefs["budget_min"] = if (binding.checkBudget.isChecked) binding.rangeBudget.values[0].toInt() else 0
        prefs["budget_max"] = if (binding.checkBudget.isChecked) (binding.etBudgetInput.text.toString().toIntOrNull() ?: binding.rangeBudget.values[1].toInt()) else 10000

        prefs["protein_min"] = if (binding.checkProtein.isChecked) binding.rangeProtein.values[0].toInt() else 0
        prefs["protein_max"] = if (binding.checkProtein.isChecked) (binding.etProteinInput.text.toString().toIntOrNull() ?: binding.rangeProtein.values[1].toInt()) else 1000

        prefs["carbs_max"] = if (binding.checkCarbs.isChecked) (binding.etCarbsInput.text.toString().toIntOrNull() ?: 1000) else 1000
        prefs["sugar_max"] = if (binding.checkSugar.isChecked) (binding.etSugarInput.text.toString().toIntOrNull() ?: 1000) else 1000
        prefs["calories_max"] = if (binding.checkCalories.isChecked) (binding.etCaloriesInput.text.toString().toIntOrNull() ?: 10000) else 10000

        database.child("Users").child(uid).child("Preferences").setValue(prefs)
            .addOnSuccessListener {
                Toast.makeText(this, "Preferences Saved!", Toast.LENGTH_SHORT).show()
                updateSummaryUI()
            }
    }

    private fun loadExistingPreferences() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Restore Dietary Type
                val diet = snapshot.child("dietary_type").value.toString()
                binding.cbKeto.isChecked = diet == "Keto"
                binding.cbVegetarian.isChecked = diet == "Vegetarian"
                binding.cbPescatarian.isChecked = diet == "Pescatarian"
                binding.cbStandard.isChecked = (diet == "Standard" || diet == "null")

                // Restore Taste Preferences
                val tastes = snapshot.child("preferred_tastes").children.map { it.value.toString() }
                binding.cbSweet.isChecked = tastes.contains("sweet")
                binding.cbSavory.isChecked = tastes.contains("savory")
                binding.cbSpicy.isChecked = tastes.contains("spicy")
                binding.cbSour.isChecked = tastes.contains("sour")

                // Restore Prep Time
                val shortPrep = snapshot.child("prefers_short_prep").value as? Boolean ?: false
                if (shortPrep) binding.rbShortPrep.isChecked = true else binding.rbLongPrep.isChecked = true

                // Restore Sliders
                fun restoreRange(slider: RangeSlider, check: CheckBox, editText: EditText, key: String, defaultMax: Float) {
                    val min = snapshot.child("${key}_min").value?.toString()?.toFloatOrNull() ?: 0f
                    val max = snapshot.child("${key}_max").value?.toString()?.toFloatOrNull() ?: defaultMax
                    if (max < defaultMax) {
                        check.isChecked = true
                        slider.values = listOf(min, max)
                        editText.setText(max.toInt().toString())
                    }
                }
                restoreRange(binding.rangeBudget, binding.checkBudget, binding.etBudgetInput, "budget", 10000f)
                restoreRange(binding.rangeProtein, binding.checkProtein, binding.etProteinInput, "protein", 1000f)

                fun restoreSingle(slider: Slider, check: CheckBox, editText: EditText, key: String, defaultMax: Float) {
                    val max = snapshot.child("${key}_max").value?.toString()?.toFloatOrNull() ?: defaultMax
                    if (max < defaultMax) {
                        check.isChecked = true
                        slider.value = max
                        editText.setText(max.toInt().toString())
                    }
                }
                restoreSingle(binding.rangeCarbs, binding.checkCarbs, binding.etCarbsInput, "carbs", 1000f)
                restoreSingle(binding.rangeSugar, binding.checkSugar, binding.etSugarInput, "sugar", 1000f)
                restoreSingle(binding.rangeCalories, binding.checkCalories, binding.etCaloriesInput, "calories", 10000f)

                // Restore Allergens
                val alg = snapshot.child("allergens")
                binding.cbSoy.isChecked = alg.child("Soy").value == true
                binding.cbGluten.isChecked = alg.child("Gluten").value == true
                binding.cbDairy.isChecked = alg.child("Dairy").value == true
                binding.cbOthers.isChecked = alg.child("Others").value == true
                binding.etOtherAllergen.setText(alg.child("Others_Value").value?.toString() ?: "")

                updateSummaryUI()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_profile) return@setOnItemSelectedListener true
            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(this, HomeActivity::class.java)
                R.id.nav_recipes -> Intent(this, RecipesActivity::class.java)
                R.id.nav_pantry -> Intent(this, PantryActivity::class.java)
                else -> null
            }
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(it)
                overridePendingTransition(0, 0)
            }
            true
        }
    }
}