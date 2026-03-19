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
    private lateinit var database: DatabaseReference
    private var recipeList = mutableListOf<Recipe>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        rvHome = findViewById(R.id.rvHomeRecipes)
        rvHome.layoutManager = GridLayoutManager(this, 2)

        // Points to the root of your Firebase as seen in your screenshot
        database = FirebaseDatabase.getInstance().reference

        fetchRecipes()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recipes -> {
                    startActivity(Intent(this, RecipesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_pantry -> {
                    startActivity(Intent(this, PantryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun fetchRecipes() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                recipeList.clear()
                for (data in snapshot.children) {
                    if (data.key == "UserSelection") continue
                    val recipe = data.getValue(Recipe::class.java)
                    recipe?.let {
                        if (it.title.isEmpty()) it.title = data.child("imageResourceName").value.toString().replace("_", " ").capitalize()
                        recipeList.add(it)
                    }
                }
                rvHome.adapter = RecipeAdapter(recipeList) { selectedRecipe ->
                    saveToUserSelection(selectedRecipe)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DATABASE", error.message)
            }
        })
    }

    private fun saveToUserSelection(recipe: Recipe) {
        FirebaseDatabase.getInstance().getReference("UserSelection")
            .child(recipe.title).setValue(true)
            .addOnSuccessListener {
                Toast.makeText(this, "${recipe.title} added!", Toast.LENGTH_SHORT).show()
            }
    }
}