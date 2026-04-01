package com.example.ilutomo

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Recipe(
    var id: String = "",
    var title: String = "",
    var category: String = "",
    var imageResourceName: String = "",
    var ingredients: Map<String, Any>? = emptyMap(),
    var allergens: List<String>? = emptyList(),
    // Macros stored as "Protein" -> "25g", "Kcal" -> "300", etc.
    var macros: Map<String, String>? = emptyMap(),
    var steps: List<String>? = emptyList(),
    var calculatedPrice: Double = 0.0
)

data class DisplayIngredient(
    val name: String,
    val amount: String,
    var isChecked: Boolean = false
)

@IgnoreExtraProperties
data class PantryIngredient(
    var id: String = "",
    var name: String = "",
    var amount: String = "",
    var price: Double = 0.0,
    var recipeTitle: String = "",
    var isChecked: Boolean = true
)

@IgnoreExtraProperties
data class Order(
    var id: String = "",
    var timestamp: Long = 0,
    var items: List<PantryIngredient> = emptyList(),
    var totalAmount: Double = 0.0,
    var status: String = "Pending"
)