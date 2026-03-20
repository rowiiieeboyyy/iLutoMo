package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity() {

    private lateinit var rvHome: RecyclerView
    private lateinit var adapter: RecipeAdapter
    private val recipeList = mutableListOf<Recipe>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // ID must match activity_home.xml
        rvHome = findViewById(R.id.rvHomeRecipes)
        rvHome.layoutManager = GridLayoutManager(this, 2)

        adapter = RecipeAdapter(recipeList) { recipe ->
            saveToUserSelection(recipe)
        }
        rvHome.adapter = adapter

        loadRecipesFromFirebase()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recipes -> {
                    startActivity(Intent(this, RecipesActivity::class.java))
                    finish()
                    false
                }
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    finish()
                    false
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    false
                }
                else -> false
            }
        }
    }

    private fun loadRecipesFromFirebase() {
        val database = FirebaseDatabase.getInstance().reference
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                recipeList.clear()
                for (data in snapshot.children) {
                    // Safety check: Don't treat library or selections as recipes
                    if (data.key == "ingredient_library" || data.key == "UserSelection") continue

                    try {
                        val recipe = data.getValue(Recipe::class.java)
                        if (recipe != null) {
                            // If title field is empty in JSON, use the key as the name
                            if (recipe.title.isEmpty()) {
                                recipe.title = data.key?.replace("recipe_", " ")
                                    ?.replace("_", " ")?.trim()
                                    ?.replaceFirstChar { it.uppercase() } ?: "Dish"
                            }
                            recipeList.add(recipe)
                        }
                    } catch (e: Exception) {
                        Log.e("iLutoMo", "Data mismatch in ${data.key}: ${e.message}")
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveToUserSelection(recipe: Recipe) {
        val ref = FirebaseDatabase.getInstance().getReference("UserSelection")
        ref.child(recipe.title).setValue(true).addOnSuccessListener {
            Toast.makeText(this, "Added ${recipe.title} to your list!", Toast.LENGTH_SHORT).show()
        }
    }
}