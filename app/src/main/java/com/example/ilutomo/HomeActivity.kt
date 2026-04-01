package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ilutomo.databinding.ActivityHomeBinding
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val allRecipes = mutableListOf<Recipe>()
    private val filteredList = mutableListOf<Recipe>()
    private val ingredientLibrary = mutableMapOf<String, DataSnapshot>()
    private lateinit var recipeAdapter: RecipeAdapter

    private var userDiet = "Standard"
    private var searchQuery = ""

    // Preference States
    private var useB = false; private var bMin = 0; private var bMax = 10000
    private var useP = false; private var pMin = 0; private var pMax = 5000
    private var useC = false; private var cMin = 0; private var cMax = 5000
    private var useS = false; private var sMin = 0; private var sMax = 5000
    private var useNa = false; private var naMin = 0; private var naMax = 20000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- FIXED: PROFILE ICON CLICK LISTENER ---
        // This matches your XML ID: android:id="@+id/ivHomeProfile"
        binding.ivHomeProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        setupRecyclerView()
        setupBottomNavigation()
        setupSearch()
        loadIngredientLibrary()
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

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                ingredientLibrary.clear()
                for (child in s.children) ingredientLibrary[child.key ?: ""] = child
                fetchPreferences()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun fetchPreferences() {
        database.child("UserPreferences").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (s.exists()) {
                    userDiet = s.child("dietary_type").value?.toString() ?: "Standard"
                    useB = s.child("use_budget").getValue(Boolean::class.java) ?: false
                    useP = s.child("use_protein").getValue(Boolean::class.java) ?: false
                    useC = s.child("use_carbs").getValue(Boolean::class.java) ?: false
                    useS = s.child("use_sugar").getValue(Boolean::class.java) ?: false
                    useNa = s.child("use_sodium").getValue(Boolean::class.java) ?: false

                    fun getVal(key: String, default: Int) = s.child(key).value?.toString()?.toDouble()?.toInt() ?: default
                    bMin = getVal("budget_min", 0); bMax = getVal("budget_max", 10000)
                    pMin = getVal("protein_min", 0); pMax = getVal("protein_max", 5000)
                    cMin = getVal("carbs_min", 0); cMax = getVal("carbs_max", 5000)
                    sMin = getVal("sugar_min", 0); sMax = getVal("sugar_max", 5000)
                    naMin = getVal("sodium_min", 0); naMax = getVal("sodium_max", 20000)
                }
                loadRecipes()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun loadRecipes() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                allRecipes.clear()
                for (child in s.children) {
                    if (child.key?.startsWith("recipe_") == true) {
                        val r = child.getValue(Recipe::class.java) ?: continue
                        r.id = child.key!!
                        var price = 0.0; var pro = 0.0; var carb = 0.0; var sug = 0.0; var sod = 0.0

                        r.ingredients?.forEach { (name, amt) ->
                            val qty = amt.toString().toDoubleOrNull() ?: 0.0
                            val lib = ingredientLibrary[name]
                            if (lib != null) {
                                val factor = if (name.contains("Egg", true) || name.contains("Wrapper", true)) qty else (qty / 50.0)
                                price += factor * (lib.child("price").getValue(Double::class.java) ?: 0.0)
                                pro += factor * (lib.child("pro").getValue(Double::class.java) ?: 0.0)
                                carb += factor * (lib.child("carb").getValue(Double::class.java) ?: 0.0)
                                sug += factor * (lib.child("sugar").getValue(Double::class.java) ?: 0.0)
                                sod += factor * (lib.child("sodium").getValue(Double::class.java) ?: 0.0)
                            }
                        }
                        r.calculatedPrice = price
                        r.macros = mapOf(
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
        val results = allRecipes.filter { r ->
            val matchesSearch = r.title.contains(searchQuery, ignoreCase = true)
            val matchesDiet = userDiet == "Standard" || r.category.equals(userDiet, ignoreCase = true)
            fun getM(key: String) = r.macros?.get(key)?.filter { it.isDigit() }?.toIntOrNull() ?: 0

            val bOk = if (useB) (r.calculatedPrice >= bMin && r.calculatedPrice <= bMax) else true
            val pOk = if (useP) (getM("Protein") >= pMin && getM("Protein") <= pMax) else true
            val cOk = if (useC) (getM("Carbs") >= cMin && getM("Carbs") <= cMax) else true
            val sOk = if (useS) (getM("Sugar") >= sMin && getM("Sugar") <= sMax) else true
            val naOk = if (useNa) (getM("Sodium") >= naMin && getM("Sodium") <= naMax) else true

            matchesSearch && matchesDiet && bOk && pOk && cOk && sOk && naOk
        }
        filteredList.addAll(results)
        recipeAdapter.notifyDataSetChanged()
    }

    private fun saveSelection(r: Recipe) {
        database.child("UserSelection").child(r.title.replace(" ", "_")).setValue(r)
            .addOnSuccessListener { Toast.makeText(this, "Added!", Toast.LENGTH_SHORT).show() }
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