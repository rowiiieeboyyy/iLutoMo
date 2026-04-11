package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ilutomo.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import kotlin.math.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allRecipes = mutableListOf<Recipe>()
    private val filteredList = mutableListOf<Recipe>()
    private val inventoryMap = mutableMapOf<String, MutableList<InventoryItem>>()
    private val businessDetailsMap = mutableMapOf<String, BusinessLocation>()
    private val ingredientLibrary = mutableMapOf<String, DataSnapshot>()
    private lateinit var recipeAdapter: RecipeAdapter

    private var userDiet = "Standard"
    private var activeAllergens = mutableListOf<String>()
    private var customAllergen = ""
    private var searchQuery = ""

    private var budgetMin = 0.0; var budgetMax = 10000.0
    private var proteinMin = 0.0; var proteinMax = 1000.0
    private var carbsMax = 1000.0
    private var sugarMax = 1000.0
    private var caloriesMax = 10000.0

    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    data class BusinessLocation(val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBottomNavigation()
        setupSearch()
        loadIngredientLibrary()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.setOnItemSelectedListener(null)
        binding.bottomNav.selectedItemId = R.id.nav_home
        setupBottomNavigation()
    }

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                ingredientLibrary.clear()
                for (child in s.children) ingredientLibrary[child.key ?: ""] = child
                fetchUserLocationAndData()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun fetchUserLocationAndData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("name") ?: "User"
                    updateWelcomeMessage(fullName.split(" ").firstOrNull() ?: fullName)
                    userLat = document.getDouble("latitude") ?: 0.0
                    userLng = document.getDouble("longitude") ?: 0.0
                }
                loadBusinessData()
            }
            .addOnFailureListener { loadBusinessData() }
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

    private fun loadBusinessData() {
        database.child("Businesses").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inventoryMap.clear()
                businessDetailsMap.clear()
                for (businessSnapshot in snapshot.children) {
                    val bizName = businessSnapshot.key ?: continue
                    val details = businessSnapshot.child("details")
                    val lat = details.child("latitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = details.child("longitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    businessDetailsMap[bizName] = BusinessLocation(lat, lng, calculateDistance(userLat, userLng, lat, lng))

                    val items = mutableListOf<InventoryItem>()
                    for (itemSnapshot in businessSnapshot.child("inventory").children) {
                        itemSnapshot.getValue(InventoryItem::class.java)?.let { items.add(it) }
                    }
                    inventoryMap[bizName] = items
                }
                fetchPreferences()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun fetchPreferences() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.exists()) {
                        userDiet = s.child("dietary_type").value?.toString() ?: "Standard"
                        budgetMin = s.child("budget_min").value?.toString()?.toDoubleOrNull() ?: 0.0
                        budgetMax = s.child("budget_max").value?.toString()?.toDoubleOrNull() ?: 10000.0
                        proteinMin = s.child("protein_min").value?.toString()?.toDoubleOrNull() ?: 0.0
                        proteinMax = s.child("protein_max").value?.toString()?.toDoubleOrNull() ?: 1000.0

                        carbsMax = s.child("carbs_max").value?.toString()?.toDoubleOrNull() ?: 1000.0
                        sugarMax = s.child("sugar_max").value?.toString()?.toDoubleOrNull() ?: 1000.0
                        caloriesMax = s.child("calories_max").value?.toString()?.toDoubleOrNull() ?: 10000.0

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
                val sortedByDist = businessDetailsMap.entries.sortedBy { it.value.distance }

                for (child in s.children) {
                    if (child.key?.startsWith("recipe_") == true) {
                        val r = child.getValue(Recipe::class.java) ?: continue
                        r.id = child.key!!
                        r.category = child.child("category").value?.toString() ?: "Standard"

                        var pro = 0.0; var carb = 0.0; var sug = 0.0; var cal = 0.0; var sod = 0.0
                        var totalPrice = 0.0

                        r.ingredients?.forEach { (name, amt) ->
                            val qty = amt.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                            val lib = ingredientLibrary[name]

                            if (lib != null) {
                                val factor = if (name.contains("Egg", true)) qty else (qty / 50.0)

                                pro += factor * (lib.child("pro").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                carb += factor * (lib.child("carb").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                sug += factor * (lib.child("sugar").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                cal += factor * (lib.child("cal").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                sod += factor * (lib.child("sodium").value?.toString()?.toDoubleOrNull() ?: 0.0)
                            }

                            for (biz in sortedByDist) {
                                val matches = inventoryMap[biz.key]?.filter { it.ingredient.equals(name, true) }
                                if (!matches.isNullOrEmpty()) {
                                    totalPrice += (matches.minOf { it.price } * (qty / 100.0))
                                    break
                                }
                            }
                        }

                        r.calculatedPrice = totalPrice

                        r.calculatedMacros["Protein"] = pro.toInt()
                        r.calculatedMacros["Carbs"] = carb.toInt()
                        r.calculatedMacros["Sugar"] = sug.toInt()
                        r.calculatedMacros["Calories"] = cal.toInt()
                        r.calculatedMacros["Sodium"] = sod.toInt()

                        allRecipes.add(r)
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

            val pVal = r.calculatedMacros["Protein"]?.toDouble() ?: 0.0
            val cVal = r.calculatedMacros["Carbs"]?.toDouble() ?: 0.0
            val sVal = r.calculatedMacros["Sugar"]?.toDouble() ?: 0.0
            val calVal = r.calculatedMacros["Calories"]?.toDouble() ?: 0.0

            val matchesBudget = r.calculatedPrice in budgetMin..budgetMax
            val matchesProtein = pVal in proteinMin..proteinMax
            val matchesCarbs = cVal <= carbsMax
            val matchesSugar = sVal <= sugarMax
            val matchesCalories = calVal <= caloriesMax

            val matchesSearch = if (searchQuery.isEmpty()) true else r.title.contains(searchQuery, true)

            matchesBudget && matchesProtein && matchesCarbs && matchesSugar && matchesCalories && matchesSearch
        }
        filteredList.addAll(baseFiltered)
        recipeAdapter.notifyDataSetChanged()
    }

    /**
     * Updated safety logic: Checks both the ingredients list AND explicit allergen tags.
     */
    private fun isRecipeSafeForUser(recipe: Recipe): Boolean {
        // 1. Dietary Type check
        if (userDiet != "Standard" && !recipe.category.equals(userDiet, ignoreCase = true)) return false

        // 2. Build a search list of all recipe contents
        val recipeContents = mutableListOf<String>()

        // Add all ingredient names
        recipe.ingredients?.keys?.forEach { recipeContents.add(it.lowercase()) }

        // Add all explicit allergen tags from the database (e.g., "Eggs", "Dairy")
        recipe.allergens?.forEach { recipeContents.add(it.lowercase()) }

        // 3. Check against user's active checkboxes (Soy, Gluten, Dairy)
        activeAllergens.forEach { allergen ->
            if (recipeContents.any { it.contains(allergen.lowercase()) }) return false
        }

        // 4. Check against "Others" custom input (e.g., user typed "eggs")
        if (customAllergen.isNotBlank() && customAllergen != "null") {
            // Checks if the user's typed word exists in any ingredient name or allergen tag
            if (recipeContents.any { it.contains(customAllergen.lowercase()) }) return false
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
                .addOnSuccessListener { Toast.makeText(this, "${r.title} added!", Toast.LENGTH_SHORT).show() }
        }
        binding.rvHomeRecipes.layoutManager = GridLayoutManager(this, 2)
        binding.rvHomeRecipes.adapter = recipeAdapter
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_home) return@setOnItemSelectedListener true
            val intent = when (item.itemId) {
                R.id.nav_recipes -> Intent(this, RecipesActivity::class.java)
                R.id.nav_pantry -> Intent(this, PantryActivity::class.java)
                R.id.nav_profile -> Intent(this, ProfileActivity::class.java)
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