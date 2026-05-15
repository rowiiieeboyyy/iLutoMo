package com.example.ilutomo

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable
import kotlin.math.ceil

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

    // These must match the keys in your Firebase exactly
    var totalTime: Int = 0,
    var tasteProfile: Map<String, Boolean> = mutableMapOf(),

    @get:Exclude
    var servings: Int = 1,

    @get:Exclude
    var calculatedPrice: Double = 0.0,
    
    @get:Exclude
    var optimizedPrice: Double = 0.0,

    @get:Exclude
    var isMissingIngredients: Boolean = false,

    @get:Exclude
    var ingredientPrices: MutableMap<String, Double> = mutableMapOf(),

    @get:Exclude
    var calculatedMacros: MutableMap<String, Int> = mutableMapOf(
        "Protein" to 0,
        "Carbs" to 0,
        "Sugar" to 0,
        "Calories" to 0,
        "Sodium" to 0,
        "Fats" to 0
    ),

    @get:Exclude
    var matchScore: Double = 0.0,
    
    @get:Exclude
    var isOutOfBudget: Boolean = false
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
    var itemGrade: String = "", // Budget, Standard, Premium
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
    
    @Exclude
    fun getInferredGrade(): String {
        val n = getDisplayName().lowercase()
        val g = itemGrade.lowercase()
        return when {
            g == "budget" || n.contains("budget") -> "Budget"
            g == "premium" || n.contains("premium") -> "Premium"
            else -> "Standard"
        }
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
    var itemGrade: String = "Standard",
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
 * Model for Nutrition Diary Entries.
 */
data class DiaryEntry(
    var id: String = "",
    var foodName: String = "",
    var calories: Int = 0,
    var protein: Int = 0,
    var carbs: Int = 0,
    var fats: Int = 0,
    var servings: Int = 1,
    var date: String = "", // Added to store the yyyy-MM-dd date
    var timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Model for UI display in RecipesActivity.
 */
data class DisplayIngredient(
    val name: String,
    val amount: String,
    var isChecked: Boolean = false
)

object PriceCalculator {
    private val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
    private val cleanRegex = Regex("[^a-z0-9 ]")

    fun extractNumericValue(input: String?): Double {
        if (input == null) return 0.0
        return numberRegex.find(input)?.value?.toDoubleOrNull() ?: 0.0
    }

    fun calculateOrderCount(requiredPerServing: String, itemSize: String, multiplier: Int): Int {
        val reqValue = extractNumericValue(requiredPerServing)
        val sizeValue = extractNumericValue(itemSize)
        if (sizeValue <= 0 || reqValue <= 0) return multiplier
        val totalNeeded = reqValue * multiplier
        return ceil(totalNeeded / sizeValue).toInt().coerceAtLeast(1)
    }

    fun stem(word: String): String {
        val w = word.lowercase().trim()
        return when {
            w.endsWith("ies") && w.length > 3 -> w.removeSuffix("ies") + "y"
            w.endsWith("es") && w.length > 3 -> w.removeSuffix("es")
            w.endsWith("ed") && w.length > 3 -> w.removeSuffix("ed")
            w.endsWith("s") && w.length > 2 && !w.endsWith("ss") -> w.removeSuffix("s")
            else -> w
        }
    }

    fun findStandardMatch(ingredientName: String, inventory: List<InventoryItem>): InventoryItem? {
        return findMatchByGrade(ingredientName, inventory, listOf("Standard", "Budget", "Premium"))
    }
    
    fun findCheapestMatch(ingredientName: String, inventory: List<InventoryItem>): InventoryItem? {
        return findMatchByGrade(ingredientName, inventory, listOf("Budget", "Standard", "Premium"))
    }

    private fun findMatchByGrade(ingredientName: String, inventory: List<InventoryItem>, preferredGrades: List<String>): InventoryItem? {
        val queryClean = ingredientName.lowercase().trim().replace(cleanRegex, " ")
        val rawQueryWords = queryClean.split(" ").filter { it.isNotBlank() }
        val queryWords = rawQueryWords.map { stem(it) }.toSet()

        if (queryWords.isEmpty()) return null

        val scoredItems = inventory.mapNotNull { item ->
            if (item.stock <= 0 || item.price <= 0) return@mapNotNull null

            val itemName = item.name.lowercase().replace(cleanRegex, " ")
            val itemIng = item.ingredient.lowercase().replace(cleanRegex, " ")
            val itemTag = item.ingredientTag.lowercase().replace(cleanRegex, " ")

            val combined = "$itemName $itemIng $itemTag"
            val itemWords = combined.split(" ").filter { it.isNotBlank() }.map { stem(it) }.toSet()

            // Strict intersection matching
            val matches = queryWords.intersect(itemWords).size
            if (matches == 0) return@mapNotNull null

            var score = matches * 3000
            
            // 1. Exact phrase match (Highest priority)
            if (itemName.trim() == queryClean || itemIng.trim() == queryClean) {
                score += 50000
            } else if (combined.contains(Regex("\\b${Regex.escape(queryClean)}\\b"))) {
                score += 20000
            }
            
            // 2. Bonus for matching all query words
            if (matches >= queryWords.size) {
                score += 10000
            }

            // --- CRITICAL FIXES FOR MISMAPPED INGREDIENTS ---

            // A. Chicken specificity: Chicken Strip vs generic Chicken meat vs Chicken Broth
            val isChickenQuery = "chicken" in queryWords
            val isStripQuery = "strip" in queryWords
            val isLiquidItem = itemWords.intersect(setOf("broth", "stock", "cube", "bouillon", "season", "powder")).isNotEmpty()
            
            if (isChickenQuery) {
                if (isStripQuery) {
                    if ("strip" !in itemWords) score -= 60000 // strongly penalize non-strip items
                    if (isLiquidItem) score -= 80000 // Disqualify broth
                } else {
                    // Generic chicken query should not favor broth or stock
                    if (isLiquidItem && "broth" !in queryWords && "stock" !in queryWords) {
                        score -= 40000
                    }
                }
            }

            // B. Rice Flour/Noodle vs Cooked Rice / Grain Rice
            if ("rice" in queryWords) {
                val isFlourQuery = "flour" in queryWords
                val isNoodleQuery = "noodle" in queryWords || "vermicelli" in queryWords || "bihon" in queryWords || "pancit" in queryWords
                
                val isNoodleItem = itemWords.intersect(setOf("noodle", "vermicelli", "bihon", "pancit")).isNotEmpty()
                val isFlourItem = "flour" in itemWords
                val isCookedRiceItem = "cook" in itemWords || combined.contains("cooked")
                
                if (isFlourQuery) {
                    if (!isFlourItem) score -= 60000 // "Rice" is not "Rice Flour"
                    if (isCookedRiceItem) score -= 45000 // "Cooked Rice" is not "Rice Flour"
                }
                
                if (isNoodleQuery) {
                    if (!isNoodleItem) score -= 60000 // "Rice" is not "Rice Noodle"
                    if (isCookedRiceItem) score -= 45000 // "Cooked Rice" is not "Rice Noodle"
                }
                
                // Grain rice check
                if (!isFlourQuery && !isNoodleQuery && !queryWords.contains("cook")) {
                    if (isFlourItem || isNoodleItem || isCookedRiceItem) {
                        score -= 30000
                    }
                }
            }

            // C. Eggs vs Eggplant
            if ("egg" in queryWords && "eggplant" !in queryWords) {
                if ("eggplant" in itemWords || combined.contains("eggplant")) {
                    score -= 100000 // Hard block eggplant
                }
            }
            
            // D. Egg Yolk/White redirection to generic Eggs if specific yolk product not found
            if ("egg" in queryWords && ("yolk" in queryWords || "white" in queryWords)) {
                if ("egg" in itemWords && "eggplant" !in itemWords) {
                    score += 15000 // Preference for generic Eggs fallback
                }
            }

            // E. Meat cut isolation
            val meatCutKeywords = setOf("breast", "thigh", "wing", "meat", "fillet", "ground", "mince", "drumstick", "leg", "steak", "rib", "loin", "chop", "strip")
            if (queryWords.intersect(meatCutKeywords).isNotEmpty() && isLiquidItem) {
                score -= 40000
            }

            if (score <= 0) return@mapNotNull null
            item to score
        }

        if (scoredItems.isEmpty()) return null

        // Sort best matches by score then price
        val maxScore = scoredItems.maxOf { it.second }
        val bestMatches = scoredItems.filter { it.second == maxScore }.map { it.first }

        for (grade in preferredGrades) {
            val gradeMatch = bestMatches.filter { it.getInferredGrade().equals(grade, true) }.minByOrNull { it.price }
            if (gradeMatch != null) return gradeMatch
        }

        return bestMatches.minByOrNull { it.price }
    }

    /**
     * Builds a map of available volume (leftovers) from current pantry items.
     */
    fun buildAvailablePool(pantryItems: List<PantryIngredient>): Map<String, Double> {
        val totalVolumeBought = mutableMapOf<String, Double>()
        val totalVolumeUsed = mutableMapOf<String, Double>()
        
        pantryItems.forEach { item ->
            val tag = item.ingredientTag.lowercase().trim()
            val unitSize = extractNumericValue(item.size)
            totalVolumeBought[tag] = (totalVolumeBought[tag] ?: 0.0) + (unitSize * item.count)
            totalVolumeUsed[tag] = (totalVolumeUsed[tag] ?: 0.0) + extractNumericValue(item.amount)
        }
        
        return totalVolumeBought.mapValues { (tag, total) ->
            (total - (totalVolumeUsed[tag] ?: 0.0)).coerceAtLeast(0.0)
        }
    }
    
    /**
     * Unit-Aware Greedy Algorithm: Tries to fit the recipe into the budget by swapping items to cheaper grades.
     * Accounts for existing leftovers in the pantry pool.
     */
    fun performGreedyOptimization(
        recipe: Recipe, 
        inventory: List<InventoryItem>, 
        multiplier: Int, 
        maxBudget: Double,
        pantryAvailablePool: Map<String, Double> = emptyMap()
    ): Triple<Double, Boolean, Map<String, InventoryItem>> {
        val selections = mutableMapOf<String, InventoryItem>()
        
        // Start with Standard grade for all ingredients
        recipe.ingredients?.forEach { (name, _) ->
            findStandardMatch(name, inventory)?.let { selections[name] = it }
        }
        
        fun calculateIncrementalCost(currentSelections: Map<String, InventoryItem>): Double {
            var incrementalTotal = 0.0
            val runningPool = pantryAvailablePool.toMutableMap()
            
            recipe.ingredients?.forEach { (name, amount) ->
                currentSelections[name]?.let { item ->
                    val tag = item.ingredientTag.lowercase().trim()
                    val needed = extractNumericValue(amount.toString()) * multiplier
                    val available = runningPool[tag] ?: 0.0
                    
                    if (available < needed) {
                        val gap = needed - available
                        val unitSize = extractNumericValue(item.size)
                        if (unitSize > 0) {
                            val extraPacks = ceil(gap / unitSize).toInt().coerceAtLeast(1)
                            incrementalTotal += item.price * extraPacks
                            runningPool[tag] = available + (extraPacks * unitSize)
                        } else {
                            incrementalTotal += item.price
                            runningPool[tag] = available + needed // Assume covered
                        }
                    }
                    runningPool[tag] = (runningPool[tag] ?: 0.0) - needed
                }
            }
            return incrementalTotal
        }
        
        var currentIncrementalTotal = calculateIncrementalCost(selections)
        
        // If the incremental cost exceeds the per-recipe budget, optimize to cheaper alternatives
        if (currentIncrementalTotal > maxBudget && maxBudget > 0) {
            val swapCandidates = recipe.ingredients?.keys?.mapNotNull { name ->
                val current = selections[name] ?: return@mapNotNull null
                if (current.getInferredGrade() == "Budget") return@mapNotNull null
                
                val budgetAlt = findMatchByGrade(name, inventory, listOf("Budget"))
                if (budgetAlt != null && budgetAlt.price < current.price) {
                    val savingsPerPack = current.price - budgetAlt.price
                    Triple(name, budgetAlt, savingsPerPack)
                } else null
            }?.sortedByDescending { it.third } // Greedy: Start with biggest potential savings
            
            swapCandidates?.forEach { (name, alt, _) ->
                if (currentIncrementalTotal <= maxBudget) return@forEach
                
                val original = selections[name]!!
                selections[name] = alt
                val newTotal = calculateIncrementalCost(selections)
                
                if (newTotal < currentIncrementalTotal) {
                    currentIncrementalTotal = newTotal
                } else {
                    selections[name] = original // Revert if swapping didn't improve total incremental cost
                }
            }
        }
        
        return Triple(currentIncrementalTotal, currentIncrementalTotal <= maxBudget || maxBudget <= 0, selections)
    }

    fun scaleAmount(amount: String, multiplier: Int): String {
        val match = numberRegex.find(amount)
        return if (match != null) {
            val scaledValue = match.value.toDouble() * multiplier
            val formattedValue = if (scaledValue % 1 == 0.0) scaledValue.toInt().toString() else "%.1f".format(scaledValue)
            amount.replaceFirst(match.value, formattedValue)
        } else amount
    }

    /**
     * Calculates the total price for a recipe based on available inventory items.
     * This follows a simplified unit-aware calculation without considering pantry leftovers.
     */
    fun calculateRecipePrice(recipe: Recipe, inventory: List<InventoryItem>, multiplier: Int): Double {
        val selections = mutableMapOf<String, InventoryItem>()
        recipe.ingredients?.forEach { (name, _) ->
            findStandardMatch(name, inventory)?.let { selections[name] = it }
        }
        
        var total = 0.0
        val runningPool = mutableMapOf<String, Double>()
        
        recipe.ingredients?.forEach { (name, amount) ->
            selections[name]?.let { item ->
                val tag = item.ingredientTag.lowercase().trim()
                val needed = extractNumericValue(amount.toString()) * multiplier
                val available = runningPool[tag] ?: 0.0
                
                if (available < needed) {
                    val gap = needed - available
                    val unitSize = extractNumericValue(item.size)
                    if (unitSize > 0) {
                        val extraPacks = ceil(gap / unitSize).toInt().coerceAtLeast(1)
                        total += item.price * extraPacks
                        runningPool[tag] = available + (extraPacks * unitSize)
                    } else {
                        total += item.price
                        runningPool[tag] = available + needed
                    }
                }
                runningPool[tag] = (runningPool[tag] ?: 0.0) - needed
            }
        }
        return total
    }

    fun isAvailableInPantry(ingredientName: String, amount: String, multiplier: Int, pantryItems: List<PantryIngredient>): Boolean {
        val pool = buildAvailablePool(pantryItems)
        val needed = extractNumericValue(amount) * multiplier
        val qClean = ingredientName.lowercase().trim().replace(cleanRegex, " ")
        val qWords = qClean.split(" ").filter { it.isNotBlank() }.map { stem(it) }.toSet()
        
        for ((tag, avail) in pool) {
            val pWords = tag.lowercase().split(" ").filter { it.isNotBlank() }.map { stem(it) }.toSet()
            val intersect = qWords.intersect(pWords)
            
            if (intersect.isNotEmpty()) {
                var possibleMatch = true
                
                // Chicken specificity
                if ("strip" in qWords && "strip" !in pWords && "chicken" in pWords) possibleMatch = false
                
                // Rice specificity
                if ("flour" in qWords && "rice" in qWords && "flour" !in pWords) possibleMatch = false
                if (("noodle" in qWords || "bihon" in qWords) && "rice" in qWords && ("noodle" !in pWords && "bihon" !in pWords)) possibleMatch = false
                
                // Egg vs Eggplant
                if ("egg" in qWords && "eggplant" !in qWords && "eggplant" in pWords) possibleMatch = false
                
                if (possibleMatch && avail >= (needed - 0.001)) return true
            }
        }
        return false
    }
}
