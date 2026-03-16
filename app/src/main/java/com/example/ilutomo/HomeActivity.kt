package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity() {

    private lateinit var adapter: RecipeAdapter
    private val recipeList = mutableListOf<Recipe>()
    private val dbRef = FirebaseDatabase.getInstance().getReference("admin_recipes")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val rvRecipes = findViewById<RecyclerView>(R.id.rvRecipes)
        rvRecipes.layoutManager = GridLayoutManager(this, 2)

        adapter = RecipeAdapter(recipeList) { recipe ->
            val intent = Intent(this, GetIngredientsActivity::class.java)
            intent.putExtra("RECIPE_TITLE", recipe.title)
            startActivity(intent)
        }
        rvRecipes.adapter = adapter

        // Setup Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recipes -> {
                    startActivity(Intent(this, RecipesActivity::class.java))
                    true
                }
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        fetchData()
    }

    private fun fetchData() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                recipeList.clear()
                for (data in snapshot.children) {
                    val recipe = data.getValue(Recipe::class.java)
                    if (recipe != null) recipeList.add(recipe)
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}