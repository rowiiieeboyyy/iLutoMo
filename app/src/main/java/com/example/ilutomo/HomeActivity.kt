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
    private lateinit var recipeAdapter: RecipeAdapter

    private var userDiet = "Standard"
    private var userAllergens = mutableListOf<String>()
    private var sugarMax = 100
    private var sodiumMax = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBottomNavigation()
        fetchUserPreferences()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(filteredList) { recipe ->
            saveToUserSelection(recipe)
        }
        binding.rvHomeRecipes.layoutManager = GridLayoutManager(this, 2)
        binding.rvHomeRecipes.adapter = recipeAdapter
    }

    private fun saveToUserSelection(recipe: Recipe) {
        val recipeKey = recipe.title.ifEmpty { "Unnamed_Recipe" }.replace(" ", "_")
        database.child("UserSelection").child(recipeKey).setValue(recipe)
            .addOnSuccessListener {
                Toast.makeText(this, "Added ${recipe.title} to Recipes!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchUserPreferences() {
        database.child("UserPreferences").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    userDiet = snapshot.child("dietary_type").value?.toString() ?: "Standard"
                    sugarMax = (snapshot.child("sugar_limit").value as? Long)?.toInt() ?: 100
                    sodiumMax = (snapshot.child("sodium_limit").value as? Long)?.toInt() ?: 1000
                    userAllergens.clear()
                    snapshot.child("allergens").children.forEach {
                        it.value?.toString()?.let { userAllergens.add(it) }
                    }
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
                    val key = child.key ?: ""
                    // FIX: Only treat nodes starting with "recipe_" as recipes
                    // This ignores ingredient_library, UserSelection, etc.
                    if (key.startsWith("recipe_")) {
                        val recipe = child.getValue(Recipe::class.java)
                        recipe?.let {
                            if (it.title.isEmpty()) it.title = key.replace("_", " ")
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

            // Safe calls for Macros
            val recipeSugar = recipe.macros?.get("Sugar")?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val recipeSodium = recipe.macros?.get("Sodium")?.filter { it.isDigit() }?.toIntOrNull() ?: 0

            matchesDiet && isSafe && recipeSugar <= sugarMax && recipeSodium <= sodiumMax
        }
        filteredList.addAll(results)
        recipeAdapter.notifyDataSetChanged()
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