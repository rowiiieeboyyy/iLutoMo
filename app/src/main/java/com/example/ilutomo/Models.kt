package com.example.ilutomo

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable
import kotlin.math.ceil

@IgnoreExtraProperties
data class Recipe(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var category: String = "Standard",
    var imageResourceName: String = "",
    var ingredients: Map<String, Any>? = mutableMapOf(),
    var allergens: List<String>? = mutableListOf(),
    var steps: List<String>? = mutableListOf(),
    var macros: Map<String, String>? = mutableMapOf(),

    // These must match the keys in your Firebase exactly
    var totalTime: Int = 0,
    var tasteProfile: Map<String, Boolean> = mutableMapOf(),

    @get:Exclude
    var servings: Int = 1,

    @get:Exclude
    var calculatedPrice: Double = 0.0,

    @get:Exclude
    var isMissingIngredients: Boolean = false,

    @get:Exclude
    var ingredientPrices: MutableMap<String, Double> = mutableMapOf(),

    @get:Exclude
    var calculatedMacros: MutableMap<String, Int> = mutableMapOf(
        "Protein" to 0,
        "Carbs" to 0,
        "Sugar" to 0,
        "Calories" to 0,
        "Sodium" to 0
    ),

    @get:Exclude
    var matchScore: Double = 0.0
) : Serializable

/**
 * Model for User Preferences (Saved from activity_profile.xml)
 */
@IgnoreExtraProperties
data class UserProfile(
    var userId: String = "",
    var dietaryTypes: List<String> = emptyList(),
    var allergens: List<String> = emptyList(),
    var otherAllergen: String = "",

    // Preferences for Ranking
    var preferredTastes: List<String> = emptyList(), // e.g., ["Spicy", "Savory"]
    var prefersShortPrep: Boolean = false, // true if user selects < 30 mins

    // Nutrition & Budget
    var budgetRange: List<Float> = listOf(0f, 1000f),
    var proteinRange: List<Float> = listOf(0f, 100f),
    var maxCarbs: Int = 100,
    var maxSugar: Int = 100,
    var maxCalories: Int = 2000
) : Serializable

/**
 * Model for items in a Business Inventory.
 */
@IgnoreExtraProperties
data class InventoryItem(
    var id: String = "",
    var ingredient: String = "",
    var ingredientTag: String = "",
    var itemGrade: String = "Budget",
    var name: String = "",
    var itemName: String = "",
    var stock: Int = 0,
    var size: String = "",
    var price: Double = 0.0,
    var img: String = "",
    var imageUrl: String = "",
    var timestamp: Long = 0
) : Serializable {
    @Exclude
    fun getDisplayName(): String {
        return name.ifEmpty { itemName.ifEmpty { ingredient } }
    }

    @Exclude
    fun getDisplayImg(): String {
        return img.ifEmpty { imageUrl }
    }
}

/**
 * Model for items added to the User's Pantry or Shopping List.
 */
@IgnoreExtraProperties
data class PantryIngredient(
    var id: String = "",
    var name: String = "",
    var amount: String = "",
    var price: Double = 0.0,
    var recipeTitle: String = "",
    var isChecked: Boolean = true,
    var brandName: String = "",
    var size: String = "",
    var businessName: String = "",
    var imageUrl: String = "",
    var count: Int = 1,
    var itemGrade: String = "Budget",
    var ingredientTag: String = ""
) : Serializable

/**
 * Model for tracking orders sent to businesses.
 */
@IgnoreExtraProperties
data class Order(
    var id: String = "",
    var userId: String = "",
    var businessUid: String = "",
    var timestamp: Long = 0,
    var items: List<PantryIngredient> = emptyList(),
    var totalAmount: Double = 0.0,
    var status: String = "Pending",
    var businessName: String = "",
    var pickupAddress: String = "",
    var pickupTime: String = ""
) : Serializable

/**
 * Model for UI display in RecipesActivity.
 */
data class DisplayIngredient(
    val name: String,
    val amount: String,
    var isChecked: Boolean = false
)

object PriceCalculator {
    fun extractNumericValue(input: String): Double {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        return numberRegex.find(input)?.value?.toDoubleOrNull() ?: 0.0
    }

    fun calculateOrderCount(requiredPerServing: String, itemSize: String, multiplier: Int): Int {
        val reqValue = extractNumericValue(requiredPerServing)
        val sizeValue = extractNumericValue(itemSize)
        if (sizeValue <= 0 || reqValue <= 0) return multiplier
        val totalNeeded = reqValue * multiplier
        return ceil(totalNeeded / sizeValue).toInt().coerceAtLeast(1)
    }

    fun findCheapestMatch(ingredientName: String, inventory: List<InventoryItem>): InventoryItem? {
        val queryClean = ingredientName.lowercase().trim().replace(Regex("[^a-z0-9 ]"), " ")
        val queryWords = queryClean.split(" ").map { it.removeSuffix("s") }.filter { it.isNotBlank() }

        if (queryWords.isEmpty()) return null

        val scoredItems = inventory.mapNotNull { item ->
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

    fun calculateRecipePrice(recipe: Recipe, inventory: List<InventoryItem>, multiplier: Int): Double {
        var totalPrice = 0.0
        recipe.ingredients?.forEach { (name, amount) ->
            val cheapestItem = findCheapestMatch(name, inventory)
            if (cheapestItem != null) {
                val orderCount = calculateOrderCount(amount.toString(), cheapestItem.size, multiplier)
                totalPrice += cheapestItem.price * orderCount
            }
        }
        return totalPrice
    }

    fun scaleAmount(amount: String, multiplier: Int): String {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        val match = numberRegex.find(amount)
        return if (match != null) {
            val scaledValue = match.value.toDouble() * multiplier
            val formattedValue = if (scaledValue % 1 == 0.0) scaledValue.toInt().toString() else "%.1f".format(scaledValue)
            amount.replaceFirst(match.value, formattedValue)
        } else amount
    }
}
