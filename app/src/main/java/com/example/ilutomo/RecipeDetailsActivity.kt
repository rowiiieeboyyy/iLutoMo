package com.example.ilutomo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
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

    private var currentRecipe: Recipe? = null

    data class BusinessLocation(val address: String, val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentRecipe = intent.getSerializableExtra("RECIPE") as? Recipe
        val recipe = currentRecipe ?: return finish()

        logActivity("View Recipe", "User is viewing details for: ${recipe.title}")

        binding.detailToolbar.setNavigationOnClickListener { finish() }

        binding.btnDetailPlus.setOnClickListener {
            recipe.servings++
            updateServingUI()
        }

        binding.btnDetailMinus.setOnClickListener {
            if (recipe.servings > 1) {
                recipe.servings--
                updateServingUI()
            }
        }

        binding.fabAddToPantry.setOnClickListener { addToPantry(recipe) }
        binding.btnSaveRecipe.setOnClickListener { saveRecipeToMyList(recipe) }

        fetchUserLocationAndData(recipe)
    }

    private fun logActivity(action: String, details: String) {
        val userEmail = auth.currentUser?.email ?: "Guest User"
        val log = hashMapOf(
            "userEmail" to userEmail,
            "action" to action,
            "details" to details,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("UserActivities").add(log)
    }

    private fun updateServingUI() {
        val recipe = currentRecipe ?: return
        binding.tvDetailServings.text = recipe.servings.toString()
        setupUI(recipe)
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
        val multiplier = recipe.servings
        binding.tvDetailTitle.text = recipe.title
        binding.tvDetailDescription.text = recipe.description
        binding.tvDetailServings.text = multiplier.toString()
        binding.tvDetailPrepTime.text = if (recipe.totalTime > 0) "🕒 ${recipe.totalTime} mins" else "🕒 N/A"

        val activeTags = recipe.tasteProfile.filter { it.value }.keys.map { it.capitalize() }
        binding.tvDetailTags.text = if (activeTags.isNotEmpty()) "🏷️ ${activeTags.joinToString(", ")}" else "🏷️ No tags"

        val totalPrice = recipe.calculatedPrice * multiplier
        binding.tvDetailPrice.text = "₱${String.format("%.2f", totalPrice)}"

        val imageResId = resources.getIdentifier(recipe.imageResourceName, "drawable", packageName)
        binding.ivRecipeDetailImage.setImageResource(if (imageResId != 0) imageResId else android.R.drawable.ic_menu_gallery)

        val macros = recipe.calculatedMacros
        binding.tvDetailCalories.text = ((macros["Calories"] ?: 0) * multiplier).toString()
        binding.tvDetailProtein.text = "${(macros["Protein"] ?: 0) * multiplier}g"
        binding.tvDetailCarbs.text = "${(macros["Carbs"] ?: 0) * multiplier}g"
        binding.tvDetailSugar.text = "${(macros["Sugar"] ?: 0) * multiplier}g"
        binding.tvDetailSodium.text = "${(macros["Sodium"] ?: 0) * multiplier}mg"

        binding.llIngredientsList.removeAllViews()
        recipe.ingredients?.forEach { (name, rawAmount) ->
            val scaledAmount = scaleAmount(rawAmount.toString(), multiplier)
            val ingredientBasePrice = recipe.ingredientPrices?.get(name) ?: 0.0
            val ingredientTotalPrice = ingredientBasePrice * multiplier
            val priceText = if (ingredientTotalPrice > 0) " - ₱${String.format("%.2f", ingredientTotalPrice)}" else ""

            val tvName = TextView(this).apply {
                text = "• $name ($scaledAmount)$priceText"
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, 8, 0, 8)
            }
            binding.llIngredientsList.addView(tvName)
        }

        binding.tvStepsList.text = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps."
    }

    private fun scaleAmount(amount: String, multiplier: Int): String {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        val match = numberRegex.find(amount)
        return if (match != null) {
            val scaledValue = match.value.toDouble() * multiplier
            val formatted = if (scaledValue % 1 == 0.0) scaledValue.toInt().toString() else "%.1f".format(scaledValue)
            amount.replaceFirst(match.value, formatted)
        } else amount
    }

    private fun addToPantry(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val pantryRef = database.child("Users").child(uid).child("Pantry")
        logActivity("Pantry Update", "Added ingredients for ${recipe.title} to pantry.")

        recipe.ingredients?.forEach { (name, amount) ->
            val key = pantryRef.push().key ?: return@forEach
            val pantryItem = mapOf(
                "id" to key,
                "name" to name,
                "amount" to scaleAmount(amount.toString(), recipe.servings),
                "price" to (recipe.ingredientPrices?.get(name) ?: 0.0) * recipe.servings,
                "recipeTitle" to recipe.title,
                "isChecked" to true
            )
            pantryRef.child(key).setValue(pantryItem)
        }
        Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
    }

    private fun saveRecipeToMyList(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        logActivity("Saved Recipe", "User added ${recipe.title} to their saved list.")
        val saveMap = mapOf(
            "id" to recipe.id,
            "title" to recipe.title,
            "description" to recipe.description,
            "category" to recipe.category,
            "imageResourceName" to recipe.imageResourceName,
            "ingredients" to recipe.ingredients,
            "steps" to recipe.steps,
            "totalTime" to recipe.totalTime,
            "tasteProfile" to recipe.tasteProfile,
            "servings" to 1
        )
        database.child("Users").child(uid).child("AddedRecipes").child(recipe.id).setValue(saveMap)
            .addOnSuccessListener { Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show() }
    }
}