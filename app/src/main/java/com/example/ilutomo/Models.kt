package com.example.ilutomo

import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

/**
 * Model for Recipe data stored in Firebase.
 * Used for filtering based on dietary type, allergens, and nutritional macros.
 */
@IgnoreExtraProperties
data class Recipe(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var category: String = "", // Used for Dietary Type filtering (e.g., "Keto", "Vegetarian")
    var imageResourceName: String = "",
    var ingredients: Map<String, Any>? = emptyMap(), // Map of Ingredient Name to Amount (String/Double)
    var allergens: List<String>? = emptyList(), // Standard allergen tags
    var macros: Map<String, String>? = emptyMap(), // Nutritional summary (Calories, Carbs, etc.)
    var steps: List<String>? = emptyList(),
    var calculatedPrice: Double = 0.0
) : Serializable

/**
 * Model for UI display in RecipesActivity.
 * Allows users to check/uncheck ingredients they already have.
 */
data class DisplayIngredient(
    val name: String,
    val amount: String,
    var isChecked: Boolean = false
)

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
    var imageUrl: String = ""
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