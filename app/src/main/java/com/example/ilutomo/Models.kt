package com.example.ilutomo

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Recipe(
    var title: String = "",
    var category: String = "",
    var imageResourceName: String = "",
    var ingredients: Map<String, Any> = emptyMap(),
    var allergens: List<String> = emptyList(),
    var macros: Map<String, String> = emptyMap(),
    var steps: List<String> = emptyList()
)

data class DisplayIngredient(
    val name: String,
    val amount: String,
    var isChecked: Boolean = false
)