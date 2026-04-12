package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ilutomo.databinding.ActivityHomeBinding
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

    private var userDiet = "Standard"
    private var activeAllergens = mutableListOf<String>()
    private var customAllergen = ""
    private var searchQuery = ""

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
        loadIngredientLibrary()
    }

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
                for (child in s.children) {
                    if (child.key?.startsWith("recipe_") == true) {
                        try {
                            val r = child.getValue(Recipe::class.java) ?: continue
                            r.id = child.key!!

                            var pro = 0.0; var carb = 0.0; var sug = 0.0; var cal = 0.0; var sod = 0.0
                            var totalPrice = 0.0

                            // Initialize new maps for individual tracking
                            val individualPrices = mutableMapOf<String, Double>()

                            r.ingredients?.forEach { (name, amt) ->
                                val lib = ingredientLibrary[name]
                                if (lib != null) {
                                    val qty = amt.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                    val factor = if (name.contains("Egg", true)) qty else (qty / 50.0)

                                    pro += factor * (lib.child("pro").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                    carb += factor * (lib.child("carb").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                    sug += factor * (lib.child("sugar").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                    cal += factor * (lib.child("cal").value?.toString()?.toDoubleOrNull() ?: 0.0)
                                    sod += factor * (lib.child("sodium").value?.toString()?.toDoubleOrNull() ?: 0.0)

                                    // CALCULATE PRICE
                                    val standardPrice = lib.child("price").value?.toString()?.toDoubleOrNull() ?: 0.0
                                    val ingredientCost = standardPrice * (qty / 100.0)

                                    totalPrice += ingredientCost
                                    individualPrices[name] = ingredientCost // Store individual price
                                }
                            }

                            r.calculatedPrice = totalPrice
                            r.ingredientPrices = individualPrices // Save the breakdown to the recipe object

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
            val matchesBudget = r.calculatedPrice in budgetMin..budgetMax
            val matchesSearch = if (searchQuery.isEmpty()) true else r.title.contains(searchQuery, true)
            matchesBudget && matchesSearch
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