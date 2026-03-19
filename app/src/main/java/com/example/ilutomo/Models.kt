package com.example.ilutomo

data class Recipe(
    val title: String = "",
    val category: String = "",
    val imageResourceName: String = "",
    val ingredients: Map<String, Double> = emptyMap()
)

data class DisplayIngredient(
    val name: String,
    val amount: Double,
    var isChecked: Boolean = false
)