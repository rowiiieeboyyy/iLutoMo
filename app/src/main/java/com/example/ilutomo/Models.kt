package com.example.ilutomo

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

/**
 * Model for Recipe data stored in Firebase.
 * Updated with @Exclude to prevent crashes from missing local calculation fields.
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

    // Kept for backward compatibility
    var macros: Map<String, String>? = mutableMapOf(),

    // LOCAL-ONLY FIELDS: We EXCLUDE these from Firebase to prevent crashes.
    @get:Exclude
    var calculatedPrice: Double = 0.0,

    @get:Exclude
    var calculatedMacros: MutableMap<String, Int> = mutableMapOf()
) : Serializable

/**
 * Model for UI display in RecipesActivity.
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