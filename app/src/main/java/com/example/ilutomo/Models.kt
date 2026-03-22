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