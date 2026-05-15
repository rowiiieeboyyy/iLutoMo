package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityRecipeDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class RecipeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailsBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val allStoreItems = mutableListOf<InventoryItem>()
    private val currentPantryItems = mutableListOf<PantryIngredient>()
    private var currentRecipe: Recipe? = null
    private var userMaxBudget: Double = 0.0
    private var isBudgetEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentRecipe = intent.getSerializableExtra("RECIPE") as? Recipe

        val recipe = currentRecipe
        if (recipe == null) {
            Toast.makeText(this, "Error: Recipe data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.detailToolbar.setNavigationOnClickListener { finish() }

        binding.btnDetailPlus.setOnClickListener {
            recipe.servings++
            updateServingUI()
        }

        binding.btnDetailMinus.setOnClickListener {
            if (recipe.servings > 1) {
                recipe.servings--
                updateServingUI()
            }
        }

        binding.fabAddToPantry.setOnClickListener { addToPantry(recipe) }
        binding.btnSaveRecipe.setOnClickListener { saveRecipeToMyList(recipe) }
        binding.btnLogToDiary.setOnClickListener { logRecipeToDiary(recipe) }
        
        binding.btnErrorBack.setOnClickListener { finish() }

        fetchUserPreferences(recipe)
    }
    
    private fun fetchUserPreferences(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                userMaxBudget = snapshot.child("budget_max").value?.toString()?.toDoubleOrNull() ?: 1000.0
                isBudgetEnabled = snapshot.child("is_budget_enabled").value as? Boolean ?: false
            }
            loadPantryAndBusinessData(recipe)
        }.addOnFailureListener {
            loadPantryAndBusinessData(recipe)
        }
    }

    private fun loadPantryAndBusinessData(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        // Fetch Pantry first to enable unit awareness in optimization
        database.child("Users").child(uid).child("Pantry").get().addOnSuccessListener { pantrySnap ->
            currentPantryItems.clear()
            pantrySnap.children.forEach { child ->
                child.getValue(PantryIngredient::class.java)?.let { 
                    it.id = child.key ?: ""
                    currentPantryItems.add(it) 
                }
            }
            loadBusinessData(recipe)
        }.addOnFailureListener {
            loadBusinessData(recipe)
        }
    }

    private fun updateServingUI() {
        val recipe = currentRecipe ?: return
        binding.tvDetailServings.text = recipe.servings.toString()
        setupUI(recipe)
    }

    private fun loadBusinessData(recipe: Recipe) {
        database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allStoreItems.clear()
                for (bizSnapshot in snapshot.children) {
                    val inventoryNode = bizSnapshot.child("inventory")
                    if (inventoryNode.exists()) {
                        for (itemSnap in inventoryNode.children) {
                            try {
                                val itm = InventoryItem().apply {
                                    id = itemSnap.key ?: ""
                                    name = itemSnap.child("name").value?.toString()
                                        ?: itemSnap.child("itemName").value?.toString() ?: ""
                                    ingredient = itemSnap.child("ingredient").value?.toString() ?: ""
                                    ingredientTag = itemSnap.child("ingredientTag").value?.toString() ?: ""
                                    itemGrade = itemSnap.child("itemGrade").value?.toString() ?: ""
                                    size = itemSnap.child("size").value?.toString() ?: ""
                                    img = itemSnap.child("img").value?.toString()
                                        ?: itemSnap.child("imageUrl").value?.toString() ?: ""
                                    price = itemSnap.child("price").value?.toString()?.toDoubleOrNull() ?: 0.0
                                    stock = itemSnap.child("stock").value?.toString()?.toIntOrNull() ?: 0
                                }
                                if (itm.name.isNotBlank()) allStoreItems.add(itm)
                            } catch (e: Exception) {
                                Log.e("DATA_PARSE", "Error parsing ${itemSnap.key}: ${e.message}")
                            }
                        }
                    }
                }
                setupUI(recipe)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("DB_ERROR", error.message)
            }
        })
    }

    private fun setupUI(recipe: Recipe) {
        val multiplier = recipe.servings
        binding.tvDetailTitle.text = recipe.title
        binding.tvDetailDescription.text = recipe.description
        binding.tvDetailServings.text = multiplier.toString()

        binding.tvDetailPrepTime.text = "🕒 ${recipe.totalTime} mins"

        val imageResId = resources.getIdentifier(recipe.imageResourceName, "drawable", packageName)
        binding.ivRecipeDetailImage.setImageResource(if (imageResId != 0) imageResId else R.drawable.placeholder_food)

        val macros = recipe.calculatedMacros
        binding.tvDetailCalories.text = ((macros["Calories"] ?: 0) * multiplier).toString()
        binding.tvDetailProtein.text = "${(macros["Protein"] ?: 0) * multiplier}g"
        binding.tvDetailCarbs.text = "${(macros["Carbs"] ?: 0) * multiplier}g"
        binding.tvDetailSugar.text = "${(macros["Sugar"] ?: 0) * multiplier}g"
        binding.tvDetailSodium.text = "${(macros["Sodium"] ?: 0) * multiplier}mg"

        binding.llIngredientsList.removeAllViews()
        
        val pool = PriceCalculator.buildAvailablePool(currentPantryItems)

        // 1. BUDGET ERROR CHECK: Always uses Greedy Optimization if budget is enabled.
        val effectiveBudget = if (isBudgetEnabled) userMaxBudget else 0.0
        val (greedyCost, fitsBudget, _) = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, effectiveBudget, pool)

        if (isBudgetEnabled && !fitsBudget) {
            binding.layoutBudgetError.visibility = View.VISIBLE
            binding.tvBudgetMessage.text = "Your budget of ₱${String.format("%.2f", userMaxBudget)} is too low. Estimated incremental cost is ₱${String.format("%.2f", greedyCost)} even with optimized alternatives."
            binding.recipeContentScroll.visibility = View.GONE
            binding.detailActionButtons.visibility = View.GONE
            binding.appBar.setExpanded(false, false)
            return
        } else {
            binding.layoutBudgetError.visibility = View.GONE
            binding.recipeContentScroll.visibility = View.VISIBLE
            binding.detailActionButtons.visibility = View.VISIBLE
        }

        // 2. UI DISPLAY: Always uses Standard Prices (Greedy OFF for Display)
        // Pass 0.0 as budget to performGreedyOptimization to force it to use Standard choices.
        val (standardIncrementalCost, _, standardSelections) = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, 0.0, pool)

        recipe.ingredients?.forEach { (name, rawAmount) ->
            val amountStr = rawAmount.toString()
            val scaledAmount = scaleAmount(amountStr, multiplier)

            val selectedItem = standardSelections[name]

            val priceText: String
            if (selectedItem != null) {
                val tag = selectedItem.ingredientTag.lowercase().trim()
                val needed = PriceCalculator.extractNumericValue(amountStr) * multiplier
                val availInPool = pool[tag] ?: 0.0
                
                if (availInPool >= needed) {
                    priceText = " - FREE (Using leftovers)"
                } else {
                    val gap = needed - availInPool
                    val orderCount = ceil(gap / PriceCalculator.extractNumericValue(selectedItem.size)).toInt().coerceAtLeast(1)
                    val cost = selectedItem.price * orderCount
                    val sizeText = if (selectedItem.size.isNotEmpty()) " [${selectedItem.size}]" else ""
                    val gradeText = if (selectedItem.getInferredGrade() != "Standard") " <${selectedItem.getInferredGrade()}>" else ""
                    priceText = " - ₱${String.format("%.2f", cost)} (${selectedItem.name}$sizeText)$gradeText"
                }
            } else {
                priceText = " - Not Available"
            }

            val tvName = TextView(this).apply {
                text = "• $name ($scaledAmount)$priceText"
                setTextColor(if (selectedItem != null) Color.BLACK else Color.RED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, 8, 0, 8)
            }
            binding.llIngredientsList.addView(tvName)
        }

        // The displayed incremental total is now based on Standard choices (with leftover awareness)
        binding.tvDetailPrice.text = "₱${String.format("%.2f", standardIncrementalCost)}"
        
        binding.tvStepsList.text = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No cooking steps provided."
    }

    private fun scaleAmount(amount: String, multiplier: Int): String {
        val numberRegex = "([0-9]*\\.?[0-9]+)".toRegex()
        val match = numberRegex.find(amount)
        return if (match != null) {
            val scaledValue = match.value.toDouble() * multiplier
            val formattedValue = if (scaledValue % 1 == 0.0) scaledValue.toInt().toString() else "%.1f".format(scaledValue)
            amount.replaceFirst(match.value, formattedValue)
        } else amount
    }

    private fun logRecipeToDiary(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val multiplier = recipe.servings
        val macros = recipe.calculatedMacros
        
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val entry = DiaryEntry(
            foodName = recipe.title,
            calories = (macros["Calories"] ?: 0) * multiplier,
            protein = (macros["Protein"] ?: 0) * multiplier,
            carbs = (macros["Carbs"] ?: 0) * multiplier,
            fats = (macros["Fats"] ?: 0) * multiplier,
            servings = multiplier
        )
        
        database.child("Users").child(uid).child("DailyDiary").child(dateStr).push().setValue(entry)
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe logged to Diary!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DiaryActivity::class.java))
            }
    }

    private fun addToPantry(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val multiplier = recipe.servings
        
        // ADD TO PANTRY: Always uses Greedy Optimization if budget is enabled.
        val effectiveBudget = if (isBudgetEnabled) userMaxBudget else 0.0
        val pool = PriceCalculator.buildAvailablePool(currentPantryItems)
        val optimization = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, effectiveBudget, pool)
        val incrementalCost = optimization.first
        val fitsBudget = optimization.second
        val selections = optimization.third
        
        if (isBudgetEnabled && !fitsBudget) {
            showBudgetLowError(incrementalCost)
            return
        }

        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val updates = mutableMapOf<String, Any?>()
        
        val runningPool = pool.toMutableMap()

        recipe.ingredients?.forEach { (name, amount) ->
            val selectedItem = selections[name] ?: return@forEach
            val amountStr = amount.toString()
            val volumeNeededNow = PriceCalculator.extractNumericValue(amountStr) * multiplier
            val standardTag = selectedItem.ingredientTag.lowercase().trim()
            
            val unitSize = PriceCalculator.extractNumericValue(selectedItem.size)
            if (unitSize <= 0) return@forEach

            val availableNow = runningPool[standardTag] ?: 0.0
            
            val extraPacksNeeded: Int
            val costForThisRecipe: Double
            
            if (availableNow >= volumeNeededNow) {
                extraPacksNeeded = 0
                costForThisRecipe = 0.0
                runningPool[standardTag] = availableNow - volumeNeededNow
            } else {
                val gap = volumeNeededNow - availableNow
                extraPacksNeeded = ceil(gap / unitSize).toInt().coerceAtLeast(1)
                costForThisRecipe = selectedItem.price * extraPacksNeeded
                runningPool[standardTag] = (availableNow + (extraPacksNeeded * unitSize)) - volumeNeededNow
            }

            if (extraPacksNeeded > 0) {
                val key = pantryRef.push().key ?: return@forEach
                val pantryItem = mapOf(
                    "id" to key,
                    "name" to selectedItem.name,
                    "amount" to scaleAmount(amountStr, multiplier),
                    "recipeTitle" to recipe.title,
                    "isChecked" to true,
                    "count" to extraPacksNeeded,
                    "price" to costForThisRecipe,
                    "size" to selectedItem.size,
                    "imageUrl" to selectedItem.getDisplayImg(),
                    "ingredientTag" to selectedItem.ingredientTag,
                    "itemGrade" to selectedItem.getInferredGrade()
                )
                updates[key] = pantryItem
            }
        }

        if (updates.isNotEmpty()) {
            pantryRef.updateChildren(updates).addOnSuccessListener {
                Toast.makeText(this, "Recipe added to Pantry!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, PantryActivity::class.java))
            }
        } else {
            Toast.makeText(this, "All ingredients are already in your pantry!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showBudgetLowError(optimizedCost: Double) {
        AlertDialog.Builder(this)
            .setTitle("Budget Too Low")
            .setMessage("Even with budget-friendly alternatives and shared ingredients (₱${String.format("%.2f", optimizedCost)}), this recipe exceeds your budget of ₱$userMaxBudget.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun extractUnit(input: String): String {
        return input.replace(Regex("[0-9]*\\.?[0-9]+"), "").trim()
    }

    private fun saveRecipeToMyList(recipe: Recipe) {
        val uid = auth.currentUser?.uid ?: return
        val savedRecipesRef = database.child("Users").child(uid).child("AddedRecipes")
        val key = savedRecipesRef.push().key ?: return
        val saveMap = mapOf(
            "id" to key,
            "title" to recipe.title,
            "description" to recipe.description,
            "category" to recipe.category,
            "imageResourceName" to recipe.imageResourceName,
            "ingredients" to recipe.ingredients,
            "steps" to recipe.steps,
            "totalTime" to recipe.totalTime,
            "tasteProfile" to recipe.tasteProfile,
            "servings" to 1
        )
        savedRecipesRef.child(key).setValue(saveMap)
            .addOnSuccessListener { Toast.makeText(this, "Saved to My Recipes!", Toast.LENGTH_SHORT).show() }
    }
}
