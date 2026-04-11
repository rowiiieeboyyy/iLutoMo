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

    private fun setupInteractiveListeners() {
        // Diet Selection (Radio behavior)
        val dietChecks = listOf(binding.cbStandard, binding.cbVegetarian, binding.cbKeto, binding.cbPescatarian)
        dietChecks.forEach { cb ->
            cb.setOnClickListener { dietChecks.filter { it != cb }.forEach { it.isChecked = false } }
        }

        binding.cbOthers.setOnCheckedChangeListener { _, isChecked ->
            binding.etOtherAllergen.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) binding.etOtherAllergen.setText("")
        }

        // Sync all sliders
        syncSliderAndInput(binding.checkBudget, binding.rangeBudget, binding.etBudgetInput)
        syncSliderAndInput(binding.checkProtein, binding.rangeProtein, binding.etProteinInput)
        syncSliderAndInput(binding.checkCarbs, binding.rangeCarbs, binding.etCarbsInput)
        syncSliderAndInput(binding.checkSugar, binding.rangeSugar, binding.etSugarInput)
    }

    private fun syncSliderAndInput(checkBox: CheckBox, slider: RangeSlider, editText: EditText) {
        slider.labelBehavior = LabelFormatter.LABEL_FLOATING
        slider.setLabelFormatter { it.toInt().toString() }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            slider.isEnabled = isChecked
            editText.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSummaryUI()
        }

        slider.addOnChangeListener { s, _, _ ->
            updateSummaryUI()
            // Optional: Show the upper bound in the EditText
            editText.setText(s.values[1].toInt().toString())
        }
    }

    private fun updateSummaryUI() {
        val selectedDiet = when {
            binding.cbVegetarian.isChecked -> "Vegetarian"
            binding.cbKeto.isChecked -> "Keto"
            binding.cbPescatarian.isChecked -> "Pescatarian"
            else -> "Standard"
        }
        binding.tvSummaryDiet.text = "Diet: $selectedDiet"

        fun getRangeText(check: CheckBox, slider: RangeSlider, unit: String): String {
            return if (check.isChecked) "${slider.values[0].toInt()}$unit - ${slider.values[1].toInt()}$unit" else "Not set"
        }

        binding.tvSummaryBudget.text = "Budget: ${getRangeText(binding.checkBudget, binding.rangeBudget, "₱")}"
        binding.tvSummaryProtein.text = "Protein: ${getRangeText(binding.checkProtein, binding.rangeProtein, "g")}"
        binding.tvSummaryCarbs.text = "Carbs: ${getRangeText(binding.checkCarbs, binding.rangeCarbs, "g")}"
        binding.tvSummarySugar.text = "Sugar: ${getRangeText(binding.checkSugar, binding.rangeSugar, "g")}"
    }

    private fun savePreferences() {
        val uid = auth.currentUser?.uid ?: return
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
                "Others_Value" to binding.etOtherAllergen.text.toString()
            )
        )

        // SAVE BOTH MIN AND MAX
        if (binding.checkBudget.isChecked) {
            prefs["budget_min"] = binding.rangeBudget.values[0].toInt()
            prefs["budget_max"] = binding.rangeBudget.values[1].toInt()
        }
        if (binding.checkProtein.isChecked) {
            prefs["protein_min"] = binding.rangeProtein.values[0].toInt()
            prefs["protein_max"] = binding.rangeProtein.values[1].toInt()
        }
        if (binding.checkCarbs.isChecked) {
            prefs["carbs_min"] = binding.rangeCarbs.values[0].toInt()
            prefs["carbs_max"] = binding.rangeCarbs.values[1].toInt()
        }
        if (binding.checkSugar.isChecked) {
            prefs["sugar_min"] = binding.rangeSugar.values[0].toInt()
            prefs["sugar_max"] = binding.rangeSugar.values[1].toInt()
        }

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
                // Restore Diet
                val diet = snapshot.child("dietary_type").value.toString()
                binding.cbKeto.isChecked = diet == "Keto"
                binding.cbVegetarian.isChecked = diet == "Vegetarian"
                binding.cbPescatarian.isChecked = diet == "Pescatarian"
                binding.cbStandard.isChecked = (diet == "Standard" || diet == "null")

                // Restore Sliders with 2 points
                fun restore(slider: RangeSlider, check: CheckBox, keyMin: String, keyMax: String) {
                    val minV = snapshot.child(keyMin).value?.toString()?.toFloatOrNull()
                    val maxV = snapshot.child(keyMax).value?.toString()?.toFloatOrNull()
                    if (minV != null && maxV != null) {
                        check.isChecked = true
                        slider.isEnabled = true
                        slider.values = listOf(minV, maxV)
                    }
                }
                restore(binding.rangeBudget, binding.checkBudget, "budget_min", "budget_max")
                restore(binding.rangeProtein, binding.checkProtein, "protein_min", "protein_max")
                restore(binding.rangeCarbs, binding.checkCarbs, "carbs_min", "carbs_max")
                restore(binding.rangeSugar, binding.checkSugar, "sugar_min", "sugar_max")
                updateSummaryUI()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); true }
                else -> false
            }
        }
    }
}