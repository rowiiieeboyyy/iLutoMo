package com.example.ilutomo

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

/**
 * Model for Recipe data stored in Firebase.
 */
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

    // NEW: Fields for Taste Ranking and Prep Time
    var totalTime: Int = 0,
    var tasteProfile: Map<String, Boolean> = mutableMapOf(),

    // Excluded from Firebase: Local portion control
    @get:Exclude
    var servings: Int = 1,

    // Excluded from Firebase: UI/Calculation results
    @get:Exclude
    var calculatedPrice: Double = 0.0,

    // For the "Detailed Nutrition" breakdown
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

    // NEW: Excluded field to store the match score during ranking
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