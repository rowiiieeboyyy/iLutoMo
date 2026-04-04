package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ilutomo.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
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
    private var searchQuery = ""
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    private var bMin = 0; private var bMax = 10000

    data class BusinessLocation(val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivHomeProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
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
                userLat = document.getDouble("latitude") ?: 0.0
                userLng = document.getDouble("longitude") ?: 0.0
                loadBusinessData()
            }
            .addOnFailureListener {
                loadBusinessData()
            }
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

                    val inventorySnapshot = businessSnapshot.child("inventory")
                    val items = mutableListOf<InventoryItem>()
                    for (itemSnapshot in inventorySnapshot.children) {
                        val item = itemSnapshot.getValue(InventoryItem::class.java)
                        if (item != null) {
                            items.add(item)
                        }
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
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
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
        recipeAdapter = RecipeAdapter(filteredList) { r -> saveSelection(r) }
        binding.rvHomeRecipes.layoutManager = GridLayoutManager(this, 2)
        binding.rvHomeRecipes.adapter = recipeAdapter
    }

    private fun fetchPreferences() {
        database.child("UserPreferences").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (s.exists()) {
                    userDiet = s.child("dietary_type").value?.toString() ?: "Standard"
                    val min = s.child("budget_min").value?.toString()?.toDouble()?.toInt() ?: 0
                    val max = s.child("budget_max").value?.toString()?.toDouble()?.toInt() ?: 10000
                    bMin = min; bMax = max
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
                        
                        var cal = 0.0; var pro = 0.0; var carb = 0.0; var sug = 0.0; var sod = 0.0
                        r.ingredients?.forEach { (name, amt) ->
                            val qty = amt.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                            val lib = ingredientLibrary[name]
                            if (lib != null) {
                                val isPieceBased = name.contains("Egg", true) || name.contains("Wrapper", true) || name.contains("Banana", true)
                                val factor = if (isPieceBased) qty else (qty / 50.0)
                                cal += factor * (lib.child("cal").getValue(Double::class.java) ?: 0.0)
                                pro += factor * (lib.child("pro").getValue(Double::class.java) ?: 0.0)
                                carb += factor * (lib.child("carb").getValue(Double::class.java) ?: 0.0)
                                sug += factor * (lib.child("sugar").getValue(Double::class.java) ?: 0.0)
                                sod += factor * (lib.child("sodium").getValue(Double::class.java) ?: 0.0)
                            }
                        }

                        // REFINED BUDGET ALGORITHM
                        // For each ingredient, find the closest business that has it.
                        // Then, in that specific nearest business, find the absolute cheapest brand/item.
                        var totalPrice = 0.0
                        r.ingredients?.keys?.forEach { ingName ->
                            for (biz in sortedByDist) {
                                val matches = inventoryMap[biz.key]?.filter { it.ingredient.equals(ingName, ignoreCase = true) }
                                if (!matches.isNullOrEmpty()) {
                                    totalPrice += matches.minOf { it.price }
                                    break
                                }
                            }
                        }
                        r.calculatedPrice = totalPrice

                        r.macros = mapOf(
                            "Calories" to "${cal.toInt()}",
                            "Protein" to "${pro.toInt()}g",
                            "Carbs" to "${carb.toInt()}g",
                            "Sugar" to "${sug.toInt()}g",
                            "Sodium" to "${sod.toInt()}mg"
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
            val matchesSearch = r.title.contains(searchQuery, ignoreCase = true)
            val matchesDiet = userDiet == "Standard" || r.category.equals(userDiet, ignoreCase = true)
            val withinBudget = r.calculatedPrice <= bMax
            matchesSearch && matchesDiet && withinBudget
        }

        val fulfilled = baseFiltered.filter { r -> countFoundIngredients(r) == (r.ingredients?.size ?: 0) }
        val incomplete = baseFiltered.filter { r -> countFoundIngredients(r) < (r.ingredients?.size ?: 0) }

        filteredList.addAll(fulfilled)
        filteredList.addAll(incomplete)
        recipeAdapter.notifyDataSetChanged()
    }

    private fun countFoundIngredients(r: Recipe): Int {
        var count = 0
        r.ingredients?.keys?.forEach { name ->
            if (inventoryMap.values.any { items -> items.any { it.ingredient.equals(name, ignoreCase = true) } }) count++
        }
        return count
    }

    private fun saveSelection(r: Recipe) {
        database.child("UserSelection").child(r.title.replace(" ", "_")).setValue(r)
            .addOnSuccessListener { Toast.makeText(this, "Added to My Recipes!", Toast.LENGTH_SHORT).show() }
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