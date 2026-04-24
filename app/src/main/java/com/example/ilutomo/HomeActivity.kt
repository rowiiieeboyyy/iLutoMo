package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ilutomo.databinding.ActivityHomeBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allRecipes = mutableListOf<Recipe>()
    private val filteredList = mutableListOf<Recipe>()
    private val ingredientLibrary = mutableMapOf<String, DataSnapshot>()
    private lateinit var recipeAdapter: RecipeAdapter

    // Persistent Ingredients List for the "Fridge" feature
    private val currentFridgeIngredients = mutableListOf<String>()

    private var userDiet = "Standard"
    private var activeAllergens = mutableListOf<String>()
    private var customAllergen = ""
    private var searchQuery = ""

    // Filter Variables
    private var maxtotalTime = 0
    private var selectedTaste = ""
    private var budgetMin = 0.0; var budgetMax = 10000.0
    private var proteinMin = 0.0; var proteinMax = 1000.0
    private var carbsMax = 1000.0
    private var sugarMax = 1000.0
    private var caloriesMax = 10000.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBottomNavigation()
        setupSearch()

        // Trigger the recommendation dialog
        binding.btnRecommend.setOnClickListener {
            showIngredientRecommendationDialog()
        }

        loadIngredientLibrary()
    }

    /**
     * DIALOG LOGIC: Handles the "What's in my fridge?" UI and persistence
     */
    private fun showIngredientRecommendationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ingredient_search, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.cgIngredients)
        val input = dialogView.findViewById<TextInputEditText>(R.id.etIngredientInput)
        val til = dialogView.findViewById<TextInputLayout>(R.id.tilIngredient)

        // RESTORE: Add chips for ingredients already in our global list
        currentFridgeIngredients.forEach { ingredient ->
            addChipToUI(ingredient, chipGroup)
        }

        // Logic to add a new ingredient
        til.setEndIconOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty() && !currentFridgeIngredients.contains(text)) {
                currentFridgeIngredients.add(text) // Save to global list
                addChipToUI(text, chipGroup)
                input.text?.clear()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Recommend") { _, _ ->
                if (currentFridgeIngredients.isNotEmpty()) {
                    runRecommendationAlgorithm(currentFridgeIngredients)
                }
            }
            .setNegativeButton("Clear All") { _, _ ->
                currentFridgeIngredients.clear()
                applyFilters() // Reset the view to show all recipes
            }
            .show()
    }

    /**
     * HELPER: Creates a visual Chip and handles the delete logic
     */
    private fun addChipToUI(text: String, chipGroup: ChipGroup) {
        val chip = Chip(this)
        chip.text = text
        chip.isCloseIconVisible = true
        chip.setOnCloseIconClickListener {
            chipGroup.removeView(chip)
            currentFridgeIngredients.remove(text) // Remove from global list when 'X' is clicked
        }
        chipGroup.addView(chip)
    }

    /**
     * ALGORITHM: Ranks recipes based on how many ingredients the user has
     */
    private fun runRecommendationAlgorithm(userIngredients: List<String>) {
        val results = allRecipes.map { recipe ->
            val recipeIngredients = recipe.ingredients?.keys?.map { it.lowercase() } ?: emptyList()
            val searchItems = userIngredients.map { it.lowercase() }

            // Count how many recipe ingredients are found in the user's fridge
            val matchCount = recipeIngredients.count { rIng ->
                searchItems.any { sIng -> rIng.contains(sIng) || sIng.contains(rIng) }
            }

            val percentage = if (recipeIngredients.isNotEmpty()) {
                (matchCount.toDouble() / recipeIngredients.size) * 100
            } else 0.0

            recipe.matchScore = percentage
            recipe
        }
            .filter { it.matchScore > 0 } // Only show recipes with at least one match
            .sortedByDescending { it.matchScore } // Rank by highest match first

        filteredList.clear()
        filteredList.addAll(results)
        recipeAdapter.notifyDataSetChanged()

        if (results.isEmpty()) {
            Toast.makeText(this, "No recipes found with these ingredients.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Found ${results.size} matches!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- DATA LOADING & PREFERENCES (Existing Logic) ---

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                ingredientLibrary.clear()
                for (child in s.children) {
                    val key = child.key ?: continue
                    ingredientLibrary[key] = child
                }
                fetchUserLocationAndData()
            }
            override fun onCancelled(e: DatabaseError) {
                Log.e("HOME_ERROR", "Lib Load Cancelled: ${e.message}")
            }
        })
    }

    private fun fetchUserLocationAndData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("name") ?: "User"
                    updateWelcomeMessage(fullName.split(" ").firstOrNull() ?: fullName)
                }
                fetchPreferences()
            }
            .addOnFailureListener { fetchPreferences() }
    }

    private fun updateWelcomeMessage(name: String) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        binding.tvWelcome.text = "$greeting, $name!"
    }

    private fun toFilterDouble(value: Any?, default: Double): Double {
        val stringVal = value?.toString()?.trim() ?: ""
        return if (stringVal.isEmpty()) default else stringVal.toDoubleOrNull() ?: default
    }

    private fun fetchPreferences() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.exists()) {
                        userDiet = s.child("dietary_type").value?.toString() ?: "Standard"
                        budgetMin = toFilterDouble(s.child("budget_min").value, 0.0)
                        budgetMax = toFilterDouble(s.child("budget_max").value, 10000.0)
                        proteinMin = toFilterDouble(s.child("protein_min").value, 0.0)
                        proteinMax = toFilterDouble(s.child("protein_max").value, 1000.0)
                        carbsMax = toFilterDouble(s.child("carbs_max").value, 1000.0).let { if (it <= 0) 1000.0 else it }
                        sugarMax = toFilterDouble(s.child("sugar_max").value, 1000.0).let { if (it <= 0) 1000.0 else it }
                        caloriesMax = toFilterDouble(s.child("calories_max").value, 10000.0).let { if (it <= 0) 10000.0 else it }

                        maxtotalTime = s.child("max_total_time").value?.toString()?.toIntOrNull() ?: 0

                        activeAllergens.clear()
                        val algNode = s.child("allergens")
                        if (algNode.child("Soy").value == true) activeAllergens.add("Soy")
                        if (algNode.child("Gluten").value == true) activeAllergens.add("Gluten")
                        if (algNode.child("Dairy").value == true) activeAllergens.add("Dairy")
                        customAllergen = algNode.child("Others_Value").value?.toString()?.lowercase() ?: ""
                    }
                    loadRecipes()
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    private fun loadRecipes() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                allRecipes.clear()
                for (child in s.children) {
                    if (child.key?.startsWith("recipe_") == true) {
                        try {
                            val r = child.getValue(Recipe::class.java) ?: continue
                            r.id = child.key!!

                            r.totalTime = child.child("totalTime").value?.toString()?.toIntOrNull() ?: 0
                            val tasteMap = mutableMapOf<String, Boolean>()
                            child.child("tasteProfile").children.forEach { t ->
                                tasteMap[t.key ?: ""] = t.value == true
                            }
                            r.tasteProfile = tasteMap

                            var pro = 0.0; var carb = 0.0; var sug = 0.0; var cal = 0.0; var sod = 0.0
                            var totalPrice = 0.0
                            val individualPrices = mutableMapOf<String, Double>()

                            r.ingredients?.forEach { (name, amt) ->
                                val lib = ingredientLibrary[name]
                                if (lib != null) {
                                    val qty = amt.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                    val factor = if (name.contains("Egg", true)) qty else (qty / 50.0)
                                    pro += factor * toFilterDouble(lib.child("pro").value, 0.0)
                                    carb += factor * toFilterDouble(lib.child("carb").value, 0.0)
                                    sug += factor * toFilterDouble(lib.child("sugar").value, 0.0)
                                    cal += factor * toFilterDouble(lib.child("cal").value, 0.0)
                                    sod += factor * toFilterDouble(lib.child("sodium").value, 0.0)
                                    val standardPrice = toFilterDouble(lib.child("price").value, 0.0)
                                    val cost = standardPrice * (qty / 100.0)
                                    totalPrice += cost
                                    individualPrices[name] = cost
                                }
                            }
                            r.calculatedPrice = totalPrice
                            r.ingredientPrices = individualPrices
                            r.calculatedMacros["Protein"] = pro.toInt()
                            r.calculatedMacros["Carbs"] = carb.toInt()
                            r.calculatedMacros["Sugar"] = sug.toInt()
                            r.calculatedMacros["Calories"] = cal.toInt()
                            r.calculatedMacros["Sodium"] = sod.toInt()

                            allRecipes.add(r)
                        } catch (e: Exception) {
                            Log.e("RECIPE_LOAD", "Error parsing ${child.key}: ${e.message}")
                        }
                    }
                }
                applyFilters()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun applyFilters() {
        filteredList.clear()
        val baseFiltered = allRecipes.filter { r ->
            if (!isRecipeSafeForUser(r)) return@filter false

            val p = r.calculatedMacros["Protein"]?.toDouble() ?: 0.0
            val c = r.calculatedMacros["Carbs"]?.toDouble() ?: 0.0
            val s = r.calculatedMacros["Sugar"]?.toDouble() ?: 0.0
            val cal = r.calculatedMacros["Calories"]?.toDouble() ?: 0.0

            val matchesBudget = r.calculatedPrice in budgetMin..budgetMax
            val matchesMacros = (p >= proteinMin && p <= proteinMax) && (c <= carbsMax) && (s <= sugarMax) && (cal <= caloriesMax)
            val matchesSearch = if (searchQuery.isEmpty()) true else r.title.contains(searchQuery, true)
            val matchestotalTime = if (maxtotalTime > 0) r.totalTime <= maxtotalTime else true
            val matchesTaste = if (selectedTaste.isNotEmpty()) r.tasteProfile[selectedTaste.lowercase()] == true else true

            // Reset matchScore when returning to normal filter mode
            r.matchScore = 0.0

            matchesBudget && matchesMacros && matchesSearch && matchestotalTime && matchesTaste
        }
        filteredList.addAll(baseFiltered)
        recipeAdapter.notifyDataSetChanged()
    }

    private fun isRecipeSafeForUser(recipe: Recipe): Boolean {
        if (userDiet != "Standard" && !recipe.category.equals(userDiet, ignoreCase = true)) return false
        val recipeContents = mutableListOf<String>().apply {
            recipe.ingredients?.keys?.forEach { add(it.lowercase()) }
            recipe.allergens?.forEach { add(it.lowercase()) }
        }
        activeAllergens.forEach { if (recipeContents.any { rc -> rc.contains(it.lowercase()) }) return false }
        if (customAllergen.isNotBlank() && customAllergen != "null") {
            if (recipeContents.any { it.contains(customAllergen) }) return false
        }
        return true
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty(); applyFilters(); return true
            }
        })
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(filteredList) { r ->
            val uid = auth.currentUser?.uid ?: return@RecipeAdapter
            database.child("Users").child(uid).child("AddedRecipes").child(r.id).setValue(r)
                .addOnSuccessListener { Toast.makeText(this, "Added to Recipes!", Toast.LENGTH_SHORT).show() }
        }
        binding.rvHomeRecipes.layoutManager = GridLayoutManager(this, 2)
        binding.rvHomeRecipes.adapter = recipeAdapter
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_home) return@setOnItemSelectedListener true
            val intent = when (item.itemId) {
                R.id.nav_recipes -> Intent(this, RecipesActivity::class.java)
                R.id.nav_pantry -> Intent(this, PantryActivity::class.java)
                R.id.nav_profile -> Intent(this, ProfileActivity::class.java)
                else -> null
            }
            intent?.let { startActivity(it); overridePendingTransition(0,0); finish() }
            true
        }
    }
}