package com.example.ilutomo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityRecipeDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.*

class RecipeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailsBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val inventoryMap = mutableMapOf<String, MutableList<InventoryItem>>()
    private val businessDetailsMap = mutableMapOf<String, BusinessLocation>()
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    data class BusinessLocation(val address: String, val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieving the Serialized recipe object passed from RecipeAdapter
        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe

        if (recipe == null) {
            Log.e("PANTRY_DEBUG", "Recipe object is NULL from Intent!")
            Toast.makeText(this, "Error: Recipe data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.detailToolbar.setNavigationOnClickListener { finish() }
        binding.fabAddToPantry.setOnClickListener { addToPantry(recipe) }

        fetchUserLocationAndData(recipe)
    }

    private fun fetchUserLocationAndData(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                userLat = document.getDouble("latitude") ?: 0.0
                userLng = document.getDouble("longitude") ?: 0.0
                loadBusinessData(recipe)
            }
            .addOnFailureListener { loadBusinessData(recipe) }
    }

    private fun loadBusinessData(recipe: Recipe) {
        database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inventoryMap.clear()
                businessDetailsMap.clear()
                for (bizSnapshot in snapshot.children) {
                    val bizName = bizSnapshot.key ?: continue
                    val details = bizSnapshot.child("details")
                    val lat = details.child("latitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = details.child("longitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val distance = calculateDistance(userLat, userLng, lat, lng)
                    businessDetailsMap[bizName] = BusinessLocation(
                        details.child("address").value?.toString() ?: "",
                        lat, lng, distance
                    )

                    val items = mutableListOf<InventoryItem>()
                    bizSnapshot.child("inventory").children.forEach { itemSnap ->
                        itemSnap.getValue(InventoryItem::class.java)?.let { items.add(it) }
                    }
                    inventoryMap[bizName] = items
                }
                setupUI(recipe)
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

    private fun setupUI(recipe: Recipe) {
        binding.tvDetailTitle.text = recipe.title
        binding.tvDetailDescription.text = recipe.description

        val imageResId = resources.getIdentifier(recipe.imageResourceName, "drawable", packageName)
        binding.ivRecipeDetailImage.setImageResource(if (imageResId != 0) imageResId else R.drawable.placeholder_food)

        // --- UPDATED: POPULATE NUTRITION FACTS FROM CALCULATED DATA ---
        // Instead of reading the 'macros' string map from Firebase, we use our local Int map.
        val macros = recipe.calculatedMacros
        binding.tvDetailCalories.text = (macros["Calories"] ?: 0).toString()
        binding.tvDetailProtein.text = "${macros["Protein"] ?: 0}g"
        binding.tvDetailCarbs.text = "${macros["Carbs"] ?: 0}g"
        binding.tvDetailSugar.text = "${macros["Sugar"] ?: 0}g"
        binding.tvDetailSodium.text = "${macros["Sodium"] ?: 0}mg"

        // --- POPULATE INGREDIENTS ---
        binding.llIngredientsList.removeAllViews()
        recipe.ingredients?.forEach { (name, amount) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val tvName = TextView(this).apply {
                text = "• $name ($amount)"
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            row.addView(tvName)
            binding.llIngredientsList.addView(row)
        }

        // --- POPULATE STEPS ---
        binding.tvStepsList.text = recipe.steps?.mapIndexed { i, s ->
            "${i + 1}. $s"
        }?.joinToString("\n\n") ?: "No cooking steps provided."
    }

    private fun addToPantry(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val ingredients = recipe.ingredients

        if (ingredients.isNullOrEmpty()) {
            Toast.makeText(this, "No ingredients found", Toast.LENGTH_SHORT).show()
            return
        }

        ingredients.forEach { (name, amount) ->
            val key = pantryRef.push().key ?: return@forEach
            val pantryItem = PantryIngredient(
                id = key,
                name = name,
                amount = amount.toString(),
                price = 0.0,
                recipeTitle = recipe.title,
                isChecked = true
            )
            pantryRef.child(key).setValue(pantryItem)
        }
        Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
    }
}