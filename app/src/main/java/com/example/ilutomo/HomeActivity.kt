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

    // Strictly defined ranges fetched from ProfileActivity
    private var budgetMin = 0.0
    private var budgetMax = Double.MAX_VALUE
    private var proteinMin = 0.0
    private var proteinMax = Double.MAX_VALUE
    private var carbsMin = 0.0
    private var carbsMax = Double.MAX_VALUE
    private var sugarMin = 0.0
    private var sugarMax = Double.MAX_VALUE

    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    data class BusinessLocation(val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivHomeProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        setupRecyclerView()
        setupBottomNavigation()
        setupSearch()
        loadIngredientLibrary()
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
                    val firstName = fullName.split(" ").firstOrNull() ?: fullName
                    updateWelcomeMessage(firstName)
                    userLat = document.getDouble("latitude") ?: 0.0
                    userLng = document.getDouble("longitude") ?: 0.0
                }
                loadBusinessData()
            }
            .addOnFailureListener {
                updateWelcomeMessage("User")
                loadBusinessData()
            }
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
                    val businessName = businessSnapshot.key ?: continue
                    val details = businessSnapshot.child("details")
                    val lat = details.child("latitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = details.child("longitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val distance = calculateDistance(userLat, userLng, lat, lng)
                    businessDetailsMap[businessName] = BusinessLocation(lat, lng, distance)

                    val items = mutableListOf<InventoryItem>()
                    for (itemSnapshot in businessSnapshot.child("inventory").children) {
                        itemSnapshot.getValue(InventoryItem::class.java)?.let { items.add(it) }
                    }
                    inventoryMap[businessName] = items
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

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                applyFilters()
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(filteredList) { r -> saveRecipeToUserList(r) }
        binding.rvHomeRecipes.layoutManager = GridLayoutManager(this, 2)
        binding.rvHomeRecipes.adapter = recipeAdapter
    }

    private fun saveRecipeToUserList(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("AddedRecipes").child(recipe.id).setValue(recipe)
            .addOnSuccessListener { Toast.makeText(this, "${recipe.title} added!", Toast.LENGTH_SHORT).show() }
    }

    private fun fetchPreferences() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.exists()) {
                        userDiet = s.child("dietary_type").value?.toString() ?: "Standard"

                        // Budget Ranges
                        budgetMin = s.child("budget_min").value?.toString()?.toDoubleOrNull() ?: 0.0
                        budgetMax = s.child("budget_max").value?.toString()?.toDoubleOrNull() ?: Double.MAX_VALUE

                        // Protein Ranges
                        proteinMin = s.child("protein_min").value?.toString()?.toDoubleOrNull() ?: 0.0
                        proteinMax = s.child("protein_max").value?.toString()?.toDoubleOrNull() ?: Double.MAX_VALUE

                        // Carbs Ranges
                        carbsMin = s.child("carbs_min").value?.toString()?.toDoubleOrNull() ?: 0.0
                        carbsMax = s.child("carbs_max").value?.toString()?.toDoubleOrNull() ?: Double.MAX_VALUE

                        // Sugar Ranges
                        sugarMin = s.child("sugar_min").value?.toString()?.toDoubleOrNull() ?: 0.0
                        sugarMax = s.child("sugar_max").value?.toString()?.toDoubleOrNull() ?: Double.MAX_VALUE

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

                        var pro = 0.0; var carb = 0.0; var sug = 0.0
                        var totalPrice = 0.0

                        r.ingredients?.forEach { (name, amt) ->
                            val qty = amt.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                            val lib = ingredientLibrary[name]
                            if (lib != null) {
                                val factor = if (name.contains("Egg", true)) qty else (qty / 50.0)
                                pro += factor * (lib.child("pro").getValue(Double::class.java) ?: 0.0)
                                carb += factor * (lib.child("carb").getValue(Double::class.java) ?: 0.0)
                                sug += factor * (lib.child("sug").getValue(Double::class.java) ?: 0.0)
                            }
                            for (biz in sortedByDist) {
                                val matches = inventoryMap[biz.key]?.filter { it.ingredient.equals(name, true) }
                                if (!matches.isNullOrEmpty()) {
                                    totalPrice += matches.minOf { it.price }
                                    break
                                }
                            }
                        }

                        r.calculatedPrice = totalPrice
                        r.macros = mapOf(
                            "Protein" to "${pro.toInt()}",
                            "Carbs" to "${carb.toInt()}",
                            "Sugar" to "${sug.toInt()}"
                        )
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
            // 1. Dietary Safety Check
            if (!isRecipeSafeForUser(r)) return@filter false

            // 2. Search Text
            if (searchQuery.isNotEmpty() && !r.title.contains(searchQuery, ignoreCase = true)) return@filter false

            // 3. Extract Macro/Price Values
            val pVal = r.macros?.get("Protein")?.toDoubleOrNull() ?: 0.0
            val cVal = r.macros?.get("Carbs")?.toDoubleOrNull() ?: 0.0
            val sVal = r.macros?.get("Sugar")?.toDoubleOrNull() ?: 0.0
            val price = r.calculatedPrice

            // 4. THE STRICT "ON POINT" FILTERING
            // Using Kotlin range (in min..max) ensures strict boundaries.

            val inBudget = price in budgetMin..budgetMax
            val inProtein = pVal in proteinMin..proteinMax

            // For example: if range is 25-30, anything 24 or 31 is hidden.
            val inCarbs = cVal in carbsMin..carbsMax
            val inSugar = sVal in sugarMin..sugarMax

            // Result: All 4 conditions must be true
            inBudget && inProtein && inCarbs && inSugar
        }

        filteredList.addAll(baseFiltered)
        recipeAdapter.notifyDataSetChanged()
    }

    private fun isRecipeSafeForUser(recipe: Recipe): Boolean {
        if (userDiet != "Standard" && !recipe.category.equals(userDiet, ignoreCase = true)) return false

        recipe.ingredients?.keys?.forEach { ing ->
            val ingLower = ing.lowercase()
            if (activeAllergens.any { ingLower.contains(it.lowercase()) }) return false
            if (customAllergen.isNotBlank() && customAllergen != "null" && ingLower.contains(customAllergen)) return false
        }
        return true
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); true }
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
                else -> false
            }
        }
    }
}