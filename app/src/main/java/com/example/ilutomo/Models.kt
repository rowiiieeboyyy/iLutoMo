package com.example.ilutomo

data class Recipe(
    val title: String = "",
    val category: String = "",
    val imageResourceName: String = "", // Matches the filename in res/drawable
    val allergens: List<String> = emptyList(),
    val ingredients: Map<String, Double> = emptyMap()
)

data class Ingredient(
    val name: String,
    val price: Double,
    val isOwned: Boolean = false
)