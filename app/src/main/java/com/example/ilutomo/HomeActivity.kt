package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ilutomo.databinding.ActivityHomeBinding
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val allRecipes = mutableListOf<Recipe>()
    private val filteredList = mutableListOf<Recipe>()
    private val ingredientLibrary = mutableMapOf<String, Double>()
    private lateinit var recipeAdapter: RecipeAdapter

    // Preference Flags
    private var userDiet = "Standard"
    private var userAllergens = mutableListOf<String>()
    private var useBudget = false; private var budgetMax = 1000
    private var useProtein = false; private var proteinMin = 0
    private var useCarbs = false; private var carbsMax = 100
    private var useSugar = false; private var sugarMax = 100
    private var useSodium = false; private var sodiumMax = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBottomNavigation()
        loadIngredientLibrary()
    }

    private fun setupRecyclerView() {
        // Updated: The click now triggers the selection save
        recipeAdapter = RecipeAdapter(filteredList) { recipe ->
            saveToUserSelection(recipe)
        }
        binding.rvHomeRecipes.layoutManager = GridLayoutManager(this, 2)
        binding.rvHomeRecipes.adapter = recipeAdapter
    }

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val price = child.child("price").getValue(Double::class.java) ?: 0.0
                    ingredientLibrary[child.key ?: ""] = price
                }
                fetchUserPreferences()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchUserPreferences() {
        database.child("UserPreferences").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    userDiet = snapshot.child("dietary_type").value?.toString() ?: "Standard"
                    useBudget = snapshot.child("use_budget").getValue(Boolean::class.java) ?: false
                    useProtein = snapshot.child("use_protein").getValue(Boolean::class.java) ?: false
                    useCarbs = snapshot.child("use_carbs").getValue(Boolean::class.java) ?: false
                    useSugar = snapshot.child("use_sugar").getValue(Boolean::class.java) ?: false
                    useSodium = snapshot.child("use_sodium").getValue(Boolean::class.java) ?: false

                    budgetMax = (snapshot.child("budget_limit").value as? Long)?.toInt() ?: 1000
                    proteinMin = (snapshot.child("protein_goal").value as? Long)?.toInt() ?: 0
                    carbsMax = (snapshot.child("carbs_limit").value as? Long)?.toInt() ?: 100
                    sugarMax = (snapshot.child("sugar_limit").value as? Long)?.toInt() ?: 100
                    sodiumMax = (snapshot.child("sodium_limit").value as? Long)?.toInt() ?: 1000

                    userAllergens.clear()
                    snapshot.child("allergens").children.forEach { it.value?.let { v -> userAllergens.add(v.toString()) } }
                }
                loadRecipesFromFirebase()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadRecipesFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allRecipes.clear()
                for (child in snapshot.children) {
                    if (child.key?.startsWith("recipe_") == true) {
                        val recipe = child.getValue(Recipe::class.java)
                        recipe?.let {
                            it.id = child.key!!

                            // CALCULATE PRICE BREAKDOWN
                            var priceSum = 0.0
                            it.ingredients?.forEach { (name, amt) ->
                                val qty = amt.toString().toDoubleOrNull() ?: 0.0
                                priceSum += (qty * (ingredientLibrary[name] ?: 0.0))
                            }
                            it.calculatedPrice = priceSum
                            allRecipes.add(it)
                        }
                    }
                }
                applyFilters()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun applyFilters() {
        filteredList.clear()
        val results = allRecipes.filter { recipe ->
            val matchesDiet = userDiet == "Standard" || recipe.category.equals(userDiet, ignoreCase = true)
            val isSafe = !(recipe.allergens?.any { it in userAllergens } ?: false)

            val rProtein = getMacroValue(recipe.macros, "Protein")
            val rCarbs   = getMacroValue(recipe.macros, "Carbs")
            val rSugar   = getMacroValue(recipe.macros, "Sugar")
            val rSodium  = getMacroValue(recipe.macros, "Sodium")

            val budgetOk = if (useBudget) recipe.calculatedPrice <= budgetMax else true
            val proteinOk = if (useProtein) rProtein >= proteinMin else true
            val carbsOk = if (useCarbs) rCarbs <= carbsMax else true
            val sugarOk = if (useSugar) rSugar <= sugarMax else true
            val sodiumOk = if (useSodium) rSodium <= sodiumMax else true

            matchesDiet && isSafe && budgetOk && proteinOk && carbsOk && sugarOk && sodiumOk
        }
        filteredList.addAll(results)
        recipeAdapter.notifyDataSetChanged()
    }

    private fun getMacroValue(map: Map<String, String>?, key: String): Int {
        val entry = map?.entries?.find { it.key.equals(key, ignoreCase = true) }
        return entry?.value?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    }

    private fun saveToUserSelection(recipe: Recipe) {
        val recipeKey = recipe.title.ifEmpty { "Unnamed_Recipe" }.replace(" ", "_")
        // Saving the recipe with its CALCULATED PRICE included
        database.child("UserSelection").child(recipeKey).setValue(recipe)
            .addOnSuccessListener {
                Toast.makeText(this, "Added to Recipes!", Toast.LENGTH_SHORT).show()
            }
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