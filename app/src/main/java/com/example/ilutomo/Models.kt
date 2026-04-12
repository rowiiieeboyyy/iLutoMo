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

    // Excluded from Firebase: Local portion control
    @get:Exclude
    var servings: Int = 1,

    // Excluded from Firebase: UI/Calculation results
    @get:Exclude
    var calculatedPrice: Double = 0.0,

    // FIX: Added to store individual costs for the "Detailed Nutrition" breakdown
    @get:Exclude
    var ingredientPrices: MutableMap<String, Double> = mutableMapOf(),

    @get:Exclude
    var calculatedMacros: MutableMap<String, Int> = mutableMapOf(
        "Protein" to 0,
        "Carbs" to 0,
        "Sugar" to 0,
        "Calories" to 0,
        "Sodium" to 0
    )
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
    var stock: Int = 0,
    var size: String = "",
    var price: Double = 0.0,
    var img: String = "",
    var timestamp: Long = 0
) : Serializable

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