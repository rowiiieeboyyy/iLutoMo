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
import java.util.Locale
import kotlin.math.*

class RecipeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailsBinding
    private val database = FirebaseDatabase.getInstance().reference
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
                    val inventoryNode = bizSnapshot.child("inventory")
                    if (inventoryNode.exists()) {
                        for (itemSnap in inventoryNode.children) {
                            try {
                                val itm = InventoryItem().apply {
                                    id = itemSnap.key ?: ""
                                    name = itemSnap.child("name").value?.toString()
                                        ?: itemSnap.child("itemName").value?.toString() ?: ""
                                    ingredient = itemSnap.child("ingredient").value?.toString() ?: ""
                                    ingredientTag = itemSnap.child("ingredientTag").value?.toString() ?: ""
                                    itemGrade = itemSnap.child("itemGrade").value?.toString() ?: "Budget"
                                    size = itemSnap.child("size").value?.toString() ?: ""
                                    img = itemSnap.child("img").value?.toString()
                                        ?: itemSnap.child("imageUrl").value?.toString() ?: ""
                                    price = itemSnap.child("price").value?.toString()?.toDoubleOrNull() ?: 0.0
                                    stock = itemSnap.child("stock").value?.toString()?.toIntOrNull() ?: 0
                                }
                                if (itm.name.isNotBlank()) allStoreItems.add(itm)
                            } catch (e: Exception) {
                                Log.e("DATA_PARSE", "Error parsing ${itemSnap.key}: ${e.message}")
                            }
                        }
                    }
                }
                setupUI(recipe)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DB_ERROR", error.message)
            }
        })
    }

    private fun findCheapestMatch(ingredientName: String): InventoryItem? {
        val queryClean = ingredientName.lowercase().trim().replace(Regex("[^a-z0-9 ]"), " ")
        val queryWords = queryClean.split(" ").map { it.removeSuffix("s") }.filter { it.isNotBlank() }

        if (queryWords.isEmpty()) return null

        val scoredItems = allStoreItems.mapNotNull { item ->
            if (item.stock <= 0 || item.price <= 0) return@mapNotNull null

            val itemName = item.name.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
            val itemIng = item.ingredient.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
            val itemTag = item.ingredientTag.lowercase().replace(Regex("[^a-z0-9 ]"), " ")

            val combined = "$itemName $itemIng $itemTag"

            var score = 0
            if (itemName.trim() == queryClean || itemIng.trim() == queryClean) {
                score = 1000
            } else {
                val matchCount = queryWords.count { qWord -> combined.contains(qWord) }
                if (matchCount == 0) return@mapNotNull null
                score = matchCount
            }

            item to score
        }

        if (scoredItems.isEmpty()) return null

        val maxScore = scoredItems.maxOf { it.second }
        val bestMatches = scoredItems.filter { it.second == maxScore }.map { it.first }

        return bestMatches.minByOrNull { it.price }
    }

    private fun setupUI(recipe: Recipe) {
        val multiplier = recipe.servings
        binding.tvDetailTitle.text = recipe.title
        binding.tvDetailDescription.text = recipe.description
        binding.tvDetailServings.text = multiplier.toString()

        // Match your XML IDs: tvDetailPrepTime and tvDetailTags
        binding.tvDetailPrepTime.text = "🕒 ${recipe.totalTime} mins"

        val activeTags = recipe.tasteProfile.filter { it.value }.keys
        binding.tvDetailTags.text = if (activeTags.isNotEmpty()) {
            "🏷️ ${activeTags.joinToString(", ")}"
        } else {
            "🏷️ ${recipe.category}"
        }

        val imageResId = resources.getIdentifier(recipe.imageResourceName, "drawable", packageName)
        binding.ivRecipeDetailImage.setImageResource(if (imageResId != 0) imageResId else R.drawable.placeholder_food)

        val macros = recipe.calculatedMacros
        binding.tvDetailCalories.text = ((macros["Calories"] ?: 0) * multiplier).toString()
        binding.tvDetailProtein.text = "${(macros["Protein"] ?: 0) * multiplier}g"
        binding.tvDetailCarbs.text = "${(macros["Carbs"] ?: 0) * multiplier}g"
        binding.tvDetailSugar.text = "${(macros["Sugar"] ?: 0) * multiplier}g"
        binding.tvDetailSodium.text = "${(macros["Sodium"] ?: 0) * multiplier}mg"

        binding.llIngredientsList.removeAllViews()
        var estimatedTotalPrice = 0.0

        recipe.ingredients?.forEach { (name, rawAmount) ->
            val amountStr = rawAmount.toString()
            val scaledAmount = scaleAmount(amountStr, multiplier)

            val cheapestItem = findCheapestMatch(name)

            val priceText: String
            if (cheapestItem != null) {
                val orderCount = calculateOrderCount(amountStr, cheapestItem.size, multiplier)
                val cost = cheapestItem.price * orderCount

                val quantityText = if (orderCount > 1) " (x$orderCount)" else ""
                priceText = " - ₱${String.format("%.2f", cost)} (${cheapestItem.name})$quantityText"
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
        binding.tvStepsList.text = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No cooking steps provided."
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

    private fun extractNumericValue(input: String): Double {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        return numberRegex.find(input)?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun extractUnit(input: String): String {
        return input.replace(Regex("[0-9]*\\.?[0-9]+"), "").trim()
    }

    private fun calculateOrderCount(requiredPerServing: String, itemSize: String, multiplier: Int): Int {
        val reqValue = extractNumericValue(requiredPerServing)
        val sizeValue = extractNumericValue(itemSize)
        if (sizeValue <= 0 || reqValue <= 0) return multiplier
        val totalNeeded = reqValue * multiplier
        return ceil(totalNeeded / sizeValue).toInt().coerceAtLeast(1)
    }

    private fun addToPantry(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val multiplier = recipe.servings

        pantryRef.get().addOnSuccessListener { snapshot ->
            val updates = mutableMapOf<String, Any?>()

            val currentPantryItems = mutableMapOf<String, PantryIngredient>()
            snapshot.children.forEach { child ->
                child.getValue(PantryIngredient::class.java)?.let {
                    it.id = child.key ?: ""
                    val tag = it.ingredientTag.lowercase().trim()
                    if (tag.isNotEmpty()) {
                        currentPantryItems[tag] = it
                    }
                }
            }

            recipe.ingredients?.forEach { (name, amount) ->
                val cheapestItem = findCheapestMatch(name) ?: return@forEach
                val amountStr = amount.toString()

                val standardTag = cheapestItem.ingredient.lowercase().trim()
                val existing = currentPantryItems[standardTag]

                if (existing != null) {
                    val unitSize = extractNumericValue(existing.size)
                    if (unitSize <= 0) return@forEach

                    val totalVolumePurchased = unitSize * existing.count
                    val volumeAlreadyNeeded = extractNumericValue(existing.amount)
                    val volumeAvailable = (totalVolumePurchased - volumeAlreadyNeeded).coerceAtLeast(0.0)

                    val volumeNeededNow = extractNumericValue(amountStr) * multiplier

                    if (volumeAvailable >= volumeNeededNow) {
                        val newTotalAmount = volumeAlreadyNeeded + volumeNeededNow
                        val unit = extractUnit(existing.amount)
                        updates["${existing.id}/amount"] = "${newTotalAmount.toInt()}${unit}"
                    } else {
                        val gap = volumeNeededNow - volumeAvailable
                        val extraPacks = ceil(gap / unitSize).toInt().coerceAtLeast(1)

                        val newCount = existing.count + extraPacks
                        val newPrice = existing.price + (cheapestItem.price * extraPacks)
                        val newTotalAmount = volumeAlreadyNeeded + volumeNeededNow
                        val unit = extractUnit(existing.amount)

                        updates["${existing.id}/count"] = newCount
                        updates["${existing.id}/price"] = newPrice
                        updates["${existing.id}/amount"] = "${newTotalAmount.toInt()}${unit}"
                    }
                } else {
                    val key = pantryRef.push().key ?: return@forEach
                    val orderCount = calculateOrderCount(amountStr, cheapestItem.size, multiplier)
                    val linePrice = cheapestItem.price * orderCount

                    val pantryItem = mapOf(
                        "id" to key,
                        "name" to cheapestItem.name,
                        "amount" to scaleAmount(amountStr, multiplier),
                        "recipeTitle" to recipe.title,
                        "isChecked" to true,
                        "count" to orderCount,
                        "price" to linePrice,
                        "size" to cheapestItem.size,
                        "imageUrl" to cheapestItem.img,
                        "ingredientTag" to cheapestItem.ingredient
                    )
                    updates[key] = pantryItem

                    val newItem = PantryIngredient(
                        id = key,
                        name = cheapestItem.name,
                        amount = scaleAmount(amountStr, multiplier),
                        count = orderCount,
                        price = linePrice,
                        size = cheapestItem.size,
                        ingredientTag = cheapestItem.ingredient
                    )
                    currentPantryItems[standardTag] = newItem
                }
            }

            if (updates.isNotEmpty()) {
                pantryRef.updateChildren(updates).addOnSuccessListener {
                    Toast.makeText(this, "Optimized items added to Pantry!", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            "totalTime" to recipe.totalTime,
            "tasteProfile" to recipe.tasteProfile,
            "servings" to 1
        )
        savedRecipesRef.child(key).setValue(saveMap)
            .addOnSuccessListener { Toast.makeText(this, "Saved to My Recipes!", Toast.LENGTH_SHORT).show() }
    }
}