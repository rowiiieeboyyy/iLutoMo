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
import kotlin.math.*

class RecipeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailsBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allStoreItems = mutableListOf<InventoryItem>()
    private var currentRecipe: Recipe? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentRecipe = intent.getSerializableExtra("RECIPE") as? Recipe

        val recipe = currentRecipe
        if (recipe == null) {
            Toast.makeText(this, "Error: Recipe data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

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

        loadBusinessData(recipe)
    }

    private fun updateServingUI() {
        val recipe = currentRecipe ?: return
        binding.tvDetailServings.text = recipe.servings.toString()
        setupUI(recipe)
    }

    private fun loadBusinessData(recipe: Recipe) {
        database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allStoreItems.clear()
                for (bizSnapshot in snapshot.children) {
                    bizSnapshot.child("inventory").children.forEach { itemSnap ->
                        itemSnap.getValue(InventoryItem::class.java)?.let { allStoreItems.add(it) }
                    }
                }
                setupUI(recipe)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DB_ERROR", error.message)
            }
        })
    }

    private fun setupUI(recipe: Recipe) {
        val multiplier = recipe.servings
        binding.tvDetailTitle.text = recipe.title
        binding.tvDetailDescription.text = recipe.description
        binding.tvDetailServings.text = multiplier.toString()

        val imageResId = resources.getIdentifier(recipe.imageResourceName, "drawable", packageName)
        binding.ivRecipeDetailImage.setImageResource(if (imageResId != 0) imageResId else R.drawable.placeholder_food)

        // Macros
        val macros = recipe.calculatedMacros
        binding.tvDetailCalories.text = ((macros["Calories"] ?: 0) * multiplier).toString()
        binding.tvDetailProtein.text = "${(macros["Protein"] ?: 0) * multiplier}g"
        binding.tvDetailCarbs.text = "${(macros["Carbs"] ?: 0) * multiplier}g"
        binding.tvDetailSugar.text = "${(macros["Sugar"] ?: 0) * multiplier}g"
        binding.tvDetailSodium.text = "${(macros["Sodium"] ?: 0) * multiplier}mg"

        // Ingredients with LIVE STORE PRICES
        binding.llIngredientsList.removeAllViews()
        var estimatedTotalPrice = 0.0

        recipe.ingredients?.forEach { (name, rawAmount) ->
            val scaledAmount = scaleAmount(rawAmount.toString(), multiplier)
            
            // IMPROVED MATCHING: Checks for partial matches and tag matches
            val cheapestItem = allStoreItems
                .filter { inv ->
                    val itemName = inv.name.lowercase()
                    val ingredient = inv.ingredient.lowercase()
                    val tag = inv.ingredientTag.lowercase()
                    val query = name.lowercase()
                    
                    itemName.contains(query) || query.contains(itemName) || 
                    ingredient.contains(query) || query.contains(ingredient) ||
                    tag.contains(query) || query.contains(tag)
                }
                .minByOrNull { it.price }

            val priceText: String
            if (cheapestItem != null) {
                val cost = cheapestItem.price * multiplier
                priceText = " - ₱${String.format("%.2f", cost)}"
                estimatedTotalPrice += cost
            } else {
                priceText = " - Not Available"
            }

            val tvName = TextView(this).apply {
                text = "• $name ($scaledAmount)$priceText"
                setTextColor(if (cheapestItem != null) Color.BLACK else Color.RED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, 8, 0, 8)
            }
            binding.llIngredientsList.addView(tvName)
        }

        binding.tvDetailPrice.text = "₱${String.format("%.2f", estimatedTotalPrice)}"

        binding.tvStepsList.text = recipe.steps?.mapIndexed { i, s ->
            "${i + 1}. $s"
        }?.joinToString("\n\n") ?: "No cooking steps provided."
    }

    private fun scaleAmount(amount: String, multiplier: Int): String {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        val match = numberRegex.find(amount)
        return if (match != null) {
            val scaledValue = match.value.toDouble() * multiplier
            val formattedValue = if (scaledValue % 1 == 0.0) scaledValue.toInt().toString() else "%.1f".format(scaledValue)
            amount.replaceFirst(match.value, formattedValue)
        } else amount
    }

    private fun addToPantry(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val multiplier = recipe.servings

        recipe.ingredients?.forEach { (name, amount) ->
            val key = pantryRef.push().key ?: return@forEach
            
            val cheapestItem = allStoreItems
                .filter { inv ->
                    val itemName = inv.name.lowercase()
                    val ingredient = inv.ingredient.lowercase()
                    val tag = inv.ingredientTag.lowercase()
                    val query = name.lowercase()
                    
                    itemName.contains(query) || query.contains(itemName) || 
                    ingredient.contains(query) || query.contains(ingredient) ||
                    tag.contains(query) || query.contains(tag)
                }
                .minByOrNull { it.price }

            val pantryItem = mutableMapOf<String, Any>(
                "id" to key,
                "name" to (cheapestItem?.getDisplayName() ?: name),
                "amount" to scaleAmount(amount.toString(), multiplier),
                "recipeTitle" to recipe.title,
                "isChecked" to true,
                "count" to multiplier,
                "price" to ((cheapestItem?.price ?: 0.0) * multiplier),
                "imageUrl" to (cheapestItem?.getDisplayImg() ?: ""),
                "ingredientTag" to (cheapestItem?.ingredient ?: name)
            )
            pantryRef.child(key).setValue(pantryItem)
        }
        Toast.makeText(this, "Added ingredients to Pantry!", Toast.LENGTH_SHORT).show()
    }

    private fun saveRecipeToMyList(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val savedRecipesRef = database.child("Users").child(uid).child("AddedRecipes")
        val key = savedRecipesRef.push().key ?: return
        val saveMap = mapOf(
            "id" to key,
            "title" to recipe.title,
            "description" to recipe.description,
            "category" to recipe.category,
            "imageResourceName" to recipe.imageResourceName,
            "ingredients" to recipe.ingredients,
            "steps" to recipe.steps,
            "servings" to 1
        )
        savedRecipesRef.child(key).setValue(saveMap)
            .addOnSuccessListener { Toast.makeText(this, "Saved to My Recipes!", Toast.LENGTH_SHORT).show() }
    }
}