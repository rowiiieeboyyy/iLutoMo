package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var recipeList: MutableList<Recipe>
    private lateinit var adapter: RecipeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val rvHome = findViewById<RecyclerView>(R.id.rvHomeRecipes)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvHome.layoutManager = LinearLayoutManager(this)
        recipeList = mutableListOf()

        adapter = RecipeAdapter(recipeList,
            onAddClick = { recipe -> saveToUserSelection(recipe) },
            onItemClick = { /* View Details */ }
        )
        rvHome.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference("admin_recipes")
        fetchRecipes()

        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); true }
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun fetchRecipes() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                recipeList.clear()
                for (data in snapshot.children) {
                    val recipe = data.getValue(Recipe::class.java)
                    recipe?.let { recipeList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveToUserSelection(recipe: Recipe) {
        val selectionRef = FirebaseDatabase.getInstance().getReference("UserSelection")
        selectionRef.child(recipe.title).setValue(recipe)
            .addOnSuccessListener { Toast.makeText(this, "Saved to Recipes!", Toast.LENGTH_SHORT).show() }
    }
}