package com.example.ilutomo

class IngredientsLogic {

    // This function filters the list to show only what the user has in their pantry
    fun getAvailableIngredients(allIngredients: List<Ingredients>): List<Ingredients> {
        return allIngredients.filter { ingredient ->
            // ERROR WAS HERE: Ensure you are using '==' or just the boolean variable
            // If you write 'ingredient.isAvailable = true', it returns Unit (Error)
            // You must write 'ingredient.isAvailable == true' or just 'ingredient.isAvailable'
            ingredient.isAvailable
        }
    }

    // Example of another logic piece for your thesis: matching recipes
    fun canMakeRecipe(required: List<Ingredients>, available: List<Ingredients>): Boolean {
        return available.containsAll(required)
    }
}