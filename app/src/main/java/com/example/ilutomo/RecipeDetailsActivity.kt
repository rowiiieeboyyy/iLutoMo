package com.example.ilutomo

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityRecipeDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
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
    private var budgetMax: Double = 10000.0

    data class BusinessLocation(val address: String, val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recipe = intent.getSerializableExtra("RECIPE") as? Recipe
        if (recipe == null) {
            Toast.makeText(this, "Error: Recipe not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.detailToolbar.setNavigationOnClickListener { finish() }
        
        binding.fabAddToPantry.setOnClickListener {
            addToPantry(recipe)
        }

        fetchUserLocationAndData(recipe)
    }

    private fun fetchUserLocationAndData(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                userLat = document.getDouble("latitude") ?: 0.0
                userLng = document.getDouble("longitude") ?: 0.0
                loadUserPreferences(recipe)
            }
            .addOnFailureListener {
                loadUserPreferences(recipe)
            }
    }

    private fun loadUserPreferences(recipe: Recipe) {
        database.child("UserPreferences").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                budgetMax = snapshot.child("budget_max").value?.toString()?.toDouble() ?: 10000.0
                loadBusinessData(recipe)
            }
            override fun onCancelled(error: DatabaseError) {
                loadBusinessData(recipe)
            }
        })
    }

    private fun loadBusinessData(recipe: Recipe) {
        database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inventoryMap.clear()
                businessDetailsMap.clear()
                
                if (!snapshot.exists()) {
                    setupUI(recipe)
                    return
                }

                for (businessSnapshot in snapshot.children) {
                    val businessName = businessSnapshot.key ?: continue
                    val details = businessSnapshot.child("details")
                    val address = details.child("address").value?.toString() ?: "No Address"
                    val lat = details.child("latitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = details.child("longitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    
                    val distance = calculateDistance(userLat, userLng, lat, lng)
                    businessDetailsMap[businessName] = BusinessLocation(address, lat, lng, distance)

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
                setupUI(recipe)
            }

            override fun onCancelled(error: DatabaseError) {
                setupUI(recipe)
            }
        })
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun setupUI(recipe: Recipe) {
        binding.tvDetailTitle.text = recipe.title
        binding.tvDetailDescription.text = recipe.description.ifEmpty { "A delicious ${recipe.category} dish." }
        
        // Setup Macros
        binding.tvDetailCalories.text = recipe.macros?.get("Calories") ?: "0"
        binding.tvDetailProtein.text = recipe.macros?.get("Protein") ?: "0g"
        binding.tvDetailCarbs.text = recipe.macros?.get("Carbs") ?: "0g"
        binding.tvDetailSugar.text = recipe.macros?.get("Sugar") ?: "0g"
        binding.tvDetailSodium.text = recipe.macros?.get("Sodium") ?: "0mg"

        // Image
        val imageResId = resources.getIdentifier(recipe.imageResourceName, "drawable", packageName)
        binding.ivRecipeDetailImage.setImageResource(if (imageResId != 0) imageResId else R.drawable.placeholder_food)

        // Sort businesses by distance
        val sortedByDist = businessDetailsMap.entries.sortedBy { it.value.distance }

        // ALGORITHM: BUDGET OPTIMIZATION CHECK (Mirroring HomeActivity)
        var nearestTotal = 0.0
        recipe.ingredients?.keys?.forEach { ingName ->
            for (biz in sortedByDist) {
                val matches = inventoryMap[biz.key]?.filter { it.ingredient.equals(ingName, ignoreCase = true) }
                if (!matches.isNullOrEmpty()) {
                    nearestTotal += matches.minOf { it.price }
                    break
                }
            }
        }
        val useGlobalLowest = nearestTotal > budgetMax

        // Ingredients with availability
        binding.llIngredientsList.removeAllViews()
        recipe.ingredients?.forEach { (name, amount) ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 8, 0, 8)

            val tvName = TextView(this)
            tvName.text = "• $name ($amount)"
            tvName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tvName.setTextColor(Color.BLACK)

            val tvStatus = TextView(this)
            var foundPrice = -1.0
            
            if (useGlobalLowest) {
                // Find global lowest price for this ingredient
                var globalCheapest = Double.MAX_VALUE
                inventoryMap.values.forEach { items ->
                    val matches = items.filter { it.ingredient.equals(name, ignoreCase = true) }
                    if (matches.isNotEmpty()) {
                        val cheapestInBiz = matches.minOf { it.price }
                        if (cheapestInBiz < globalCheapest) {
                            foundPrice = cheapestInBiz
                            globalCheapest = cheapestInBiz
                        }
                    }
                }
            } else {
                // Search in nearest stores
                for (bizEntry in sortedByDist) {
                    val match = inventoryMap[bizEntry.key]?.filter { it.ingredient.equals(name, ignoreCase = true) }
                    if (!match.isNullOrEmpty()) {
                        foundPrice = match.minOf { it.price }
                        break
                    }
                }
            }

            if (foundPrice >= 0) {
                tvStatus.text = String.format(Locale.US, "₱%.2f", foundPrice)
                tvStatus.setTextColor(Color.parseColor("#2D5A27"))
            } else {
                tvStatus.text = "Not Available"
                tvStatus.setTextColor(Color.RED)
            }
            tvStatus.gravity = Gravity.END

            row.addView(tvName)
            row.addView(tvStatus)
            binding.llIngredientsList.addView(row)
        }

        // Steps
        val stepsText = recipe.steps?.mapIndexed { index, step ->
            "${index + 1}. $step"
        }?.joinToString("\n\n") ?: "No steps provided."
        binding.tvStepsList.text = stepsText
    }

    private fun addToPantry(recipe: Recipe) {
        val pantryRef = database.child("Pantry")
        val updates = mutableMapOf<String, Any>()

        recipe.ingredients?.forEach { (name, amount) ->
            val key = pantryRef.push().key ?: return@forEach
            val data = mapOf(
                "name" to name,
                "amount" to amount.toString(),
                "recipeTitle" to recipe.title,
                "price" to 0.0,
                "isChecked" to true
            )
            updates[key] = data
        }

        pantryRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Ingredients added to Pantry!", Toast.LENGTH_SHORT).show()
        }
    }
}