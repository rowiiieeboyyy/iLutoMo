package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class RecipesActivity : AppCompatActivity() {

    private lateinit var tvNeeded: TextView
    private lateinit var rvAvailable: RecyclerView
    private lateinit var tvServings: TextView
    private lateinit var tvDetailPrice: TextView
    
    // UI for Full Page Error
    private lateinit var layoutBudgetError: View
    private lateinit var tvBudgetMessage: TextView
    private lateinit var btnErrorClose: Button
    private lateinit var contentLayout: View
    private lateinit var actionButtonsLayout: View

    private var currentIngredients = mutableListOf<DisplayIngredient>()
    private var currentRecipe: Recipe? = null
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()
    private val allStoreItems = mutableListOf<InventoryItem>()
    private val currentPantryItems = mutableListOf<PantryIngredient>()

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    
    private var userMaxBudget = 1000.0
    private var isBudgetEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        tvServings = findViewById(R.id.tvRecipeServings)
        tvDetailPrice = findViewById(R.id.tvRecipeDetailPrice)
        
        layoutBudgetError = findViewById(R.id.layoutRecipesBudgetError)
        tvBudgetMessage = findViewById(R.id.tvRecipesBudgetMessage)
        btnErrorClose = findViewById(R.id.btnRecipesErrorClose)
        contentLayout = findViewById(R.id.llLists)
        actionButtonsLayout = findViewById(R.id.llActionButtons)

        val btnChoose = findViewById<Button>(R.id.btnChooseRecipe)
        val btnMacros = findViewById<Button>(R.id.btnViewMacros)
        val btnSteps = findViewById<Button>(R.id.btnViewSteps)
        val btnAddToPantry = findViewById<Button>(R.id.btnAddToPantry)
        val btnLogToDiary = findViewById<Button>(R.id.btnLogToDiary)
        val btnPlus = findViewById<ImageButton>(R.id.btnRecipePlus)
        val btnMinus = findViewById<ImageButton>(R.id.btnRecipeMinus)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvAvailable.layoutManager = LinearLayoutManager(this)
        
        loadData()
        fetchPreferences()

        btnPlus.setOnClickListener {
            currentRecipe?.let {
                it.servings++
                tvServings.text = it.servings.toString()
                updateUI()
            }
        }

        btnMinus.setOnClickListener {
            currentRecipe?.let {
                if (it.servings > 1) {
                    it.servings--
                    tvServings.text = it.servings.toString()
                    updateUI()
                }
            }
        }
        
        btnErrorClose.setOnClickListener {
            layoutBudgetError.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
            actionButtonsLayout.visibility = View.VISIBLE
        }

        bottomNav.selectedItemId = R.id.nav_recipes
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> true
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }

        btnChoose.setOnClickListener { showAllRecipesDiscovery(btnChoose) }
        btnMacros.setOnClickListener {
            if (currentRecipe == null) Toast.makeText(this, "Select a recipe first!", Toast.LENGTH_SHORT).show()
            else showAccurateMacrosDialog(currentRecipe!!)
        }
        btnSteps.setOnClickListener {
            if (currentRecipe == null) Toast.makeText(this, "Select a recipe first!", Toast.LENGTH_SHORT).show()
            else {
                val intent = Intent(this, CookingStepsActivity::class.java)
                intent.putExtra("RECIPE", currentRecipe)
                startActivity(intent)
            }
        }
        btnAddToPantry.setOnClickListener { addToPantry() }
        btnLogToDiary.setOnClickListener { logRecipeToDiary() }
    }

    private fun fetchPreferences() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences").get().addOnSuccessListener { s ->
            userMaxBudget = s.child("budget_max").value?.toString()?.toDoubleOrNull() ?: 1000.0
            isBudgetEnabled = s.child("is_budget_enabled").value as? Boolean ?: false
            if (currentRecipe != null) updateUI()
        }
    }

    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return
        
        // Load Pantry first for unit awareness
        database.child("Users").child(uid).child("Pantry").get().addOnSuccessListener { pantrySnap ->
            currentPantryItems.clear()
            pantrySnap.children.forEach { child ->
                child.getValue(PantryIngredient::class.java)?.let {
                    it.id = child.key ?: ""
                    currentPantryItems.add(it)
                }
            }
            
            // Load Store Inventory
            database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allStoreItems.clear()
                    for (bizSnapshot in snapshot.children) {
                        bizSnapshot.child("inventory").children.forEach { itemSnap ->
                            try {
                                val itm = InventoryItem().apply {
                                    id = itemSnap.key ?: ""
                                    name = itemSnap.child("name").value?.toString() ?: itemSnap.child("itemName").value?.toString() ?: ""
                                    ingredient = itemSnap.child("ingredient").value?.toString() ?: ""
                                    ingredientTag = itemSnap.child("ingredientTag").value?.toString() ?: ""
                                    price = itemSnap.child("price").value?.toString()?.toDoubleOrNull() ?: 0.0
                                    stock = itemSnap.child("stock").value?.toString()?.toIntOrNull() ?: 0
                                    size = itemSnap.child("size").value?.toString() ?: ""
                                    itemGrade = itemSnap.child("itemGrade").value?.toString() ?: ""
                                    img = itemSnap.child("img").value?.toString() ?: itemSnap.child("imageUrl").value?.toString() ?: ""
                                }
                                allStoreItems.add(itm)
                            } catch (e: Exception) {}
                        }
                    }
                    loadIngredientLibrary()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun loadIngredientLibrary() {
        database.child("ingredient_library").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ingredientLibrary.clear()
                for (data in snapshot.children) {
                    val stats = data.children.associate { it.key!! to (it.value?.toString()?.toDoubleOrNull() ?: 0.0) }
                    ingredientLibrary[data.key!!] = stats
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAllRecipesDiscovery(btnChoose: Button) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("AddedRecipes").get().addOnSuccessListener { recipeSnap ->
            val rankedRecipes = mutableListOf<Recipe>()
            for (snap in recipeSnap.children) {
                val recipe = snap.getValue(Recipe::class.java) ?: continue
                recipe.id = snap.key ?: ""
                rankedRecipes.add(recipe)
            }
            if (rankedRecipes.isEmpty()) {
                Toast.makeText(this, "No recipes in your list!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val titles = rankedRecipes.map { it.title }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Recipe")
                .setItems(titles) { _, which ->
                    val selected = rankedRecipes[which]
                    currentRecipe = selected
                    btnChoose.text = selected.title
                    tvServings.text = selected.servings.toString()
                    loadRecipeDataIntoUI(selected)
                }
                .setNeutralButton("Delete") { _, _ -> showDeleteRecipeDialog(rankedRecipes, btnChoose) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showDeleteRecipeDialog(recipes: List<Recipe>, btnChoose: Button) {
        val uid = auth.currentUser?.uid ?: return
        val titles = recipes.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Remove Recipe")
            .setItems(titles) { _, which ->
                val selected = recipes[which]
                database.child("Users").child(uid).child("AddedRecipes")
                    .child(selected.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Removed ${selected.title}", Toast.LENGTH_SHORT).show()
                        if (currentRecipe?.id == selected.id) {
                            currentRecipe = null
                            btnChoose.text = "Select from your added recipes"
                            tvServings.text = "1"
                            currentIngredients.clear()
                            updateUI()
                        }
                    }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun loadRecipeDataIntoUI(recipe: Recipe) {
        currentIngredients.clear()
        recipe.ingredients?.forEach { (name, amount) ->
            currentIngredients.add(DisplayIngredient(name, amount.toString(), isChecked = true))
        }
        updateUI()
    }

    private fun updateUI() {
        val multiplier = currentRecipe?.servings ?: 1
        rvAvailable.adapter = IngredientCheckAdapter(currentIngredients, multiplier) {
            renderNeededList()
        }
        renderNeededList()
    }

    private fun renderNeededList() {
        val recipe = currentRecipe ?: return
        val multiplier = recipe.servings
        val fullText = StringBuilder()
        
        val pool = PriceCalculator.buildAvailablePool(currentPantryItems)

        // 1. BUDGET ERROR CHECK: Always uses Greedy Optimization if budget is enabled.
        val effectiveBudget = if (isBudgetEnabled) userMaxBudget else 0.0
        val (greedyCost, fitsBudget, _) = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, effectiveBudget, pool)

        if (isBudgetEnabled && !fitsBudget) {
            layoutBudgetError.visibility = View.VISIBLE
            tvBudgetMessage.text = "Your budget of ₱${String.format("%.2f", userMaxBudget)} is too low. Estimated incremental cost is ₱${String.format("%.2f", greedyCost)} even with optimized alternatives."
            contentLayout.visibility = View.GONE
            actionButtonsLayout.visibility = View.GONE
            return
        } else {
            layoutBudgetError.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
            actionButtonsLayout.visibility = View.VISIBLE
        }

        // 2. UI DISPLAY: Always uses Standard Prices (Greedy OFF for Display)
        val (standardIncrementalCost, _, standardSelections) = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, 0.0, pool)

        currentIngredients.forEach { ing ->
            val scaledAmount = PriceCalculator.scaleAmount(ing.amount, multiplier)
            val selectedItem = standardSelections[ing.name]
            
            val priceText: String
            if (selectedItem != null) {
                val tag = selectedItem.ingredientTag.lowercase().trim()
                val needed = PriceCalculator.extractNumericValue(ing.amount) * multiplier
                val avail = pool[tag] ?: 0.0
                
                if (avail >= needed) {
                    priceText = " - FREE (Using leftovers)"
                } else {
                    val gap = needed - avail
                    val unitSize = PriceCalculator.extractNumericValue(selectedItem.size)
                    val packs = if (unitSize > 0) ceil(gap / unitSize).toInt().coerceAtLeast(1) else 1
                    val cost = selectedItem.price * packs
                    val sizeText = if (selectedItem.size.isNotEmpty()) " [${selectedItem.size}]" else ""
                    val gradeText = if (selectedItem.getInferredGrade() != "Standard") " <${selectedItem.getInferredGrade()}>" else ""
                    priceText = " - ₱${String.format("%.2f", cost)} (${selectedItem.name}$sizeText)$gradeText"
                }
            } else {
                priceText = " - Not Available"
            }
            
            fullText.append("${ing.name} ($scaledAmount)$priceText\n\n")
        }

        tvDetailPrice.text = "Incremental Total: ₱${String.format("%.2f", standardIncrementalCost)}"
        // tvDetailPrice color doesn't need to be red here because the full page error handles budget breach.
        tvDetailPrice.setTextColor(Color.parseColor("#2D5A27"))

        val spannable = SpannableString(fullText.toString())
        var currentPos = 0
        currentIngredients.forEach { ing ->
            val scaledAmount = PriceCalculator.scaleAmount(ing.amount, multiplier)
            val selectedItem = standardSelections[ing.name]
            
            val segmentPricePart: String
            if (selectedItem != null) {
                val tag = selectedItem.ingredientTag.lowercase().trim()
                val needed = PriceCalculator.extractNumericValue(ing.amount) * multiplier
                val avail = pool[tag] ?: 0.0
                
                if (avail >= needed) {
                    segmentPricePart = " - FREE (Using leftovers)"
                } else {
                    val gap = needed - avail
                    val unitSize = PriceCalculator.extractNumericValue(selectedItem.size)
                    val packs = if (unitSize > 0) ceil(gap / unitSize).toInt().coerceAtLeast(1) else 1
                    val cost = selectedItem.price * packs
                    val sizeText = if (selectedItem.size.isNotEmpty()) " [${selectedItem.size}]" else ""
                    val gradeText = if (selectedItem.getInferredGrade() != "Standard") " <${selectedItem.getInferredGrade()}>" else ""
                    segmentPricePart = " - ₱${String.format("%.2f", cost)} (${selectedItem.name}$sizeText)$gradeText"
                }
            } else {
                segmentPricePart = " - Not Available"
            }
            
            val segment = "${ing.name} ($scaledAmount)$segmentPricePart\n\n"
            
            if (!ing.isChecked) {
                spannable.setSpan(StrikethroughSpan(), currentPos, currentPos + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.GRAY), currentPos, currentPos + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (selectedItem == null) {
                spannable.setSpan(ForegroundColorSpan(Color.RED), currentPos, currentPos + segment.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            currentPos += segment.length
        }
        tvNeeded.text = spannable
    }

    private fun showAccurateMacrosDialog(recipe: Recipe) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_info, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val tvContent = dialogView.findViewById<TextView>(R.id.dialogContent)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotalValue)
        val llTotal = dialogView.findViewById<LinearLayout>(R.id.llTotalSection)
        val btnCloseX = dialogView.findViewById<ImageButton>(R.id.btnCloseDialog)
        tvTitle.text = "Detailed Nutrition"
        llTotal.visibility = View.VISIBLE
        val contentBuilder = StringBuilder()
        val multiplier = recipe.servings
        var totalCals = 0.0; var totalCarbs = 0.0; var totalProt = 0.0; var incrementalPrice = 0.0

        val pool = PriceCalculator.buildAvailablePool(currentPantryItems)
        // Dialog price also follows Dashboard rule (Standard prices)
        val (standardCost, _, standardSelections) = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, 0.0, pool)

        recipe.ingredients?.forEach { (fullName, amount) ->
            val cleanName = fullName.split("(")[0].trim()
            val qtyNumeric = PriceCalculator.extractNumericValue(amount.toString())
            val scaledQty = qtyNumeric * multiplier
            val entry = ingredientLibrary.entries.find { it.key.equals(cleanName, true) }?.value
            val selectedItem = standardSelections[fullName]

            if (entry != null) {
                val factor = if (cleanName.contains("Egg", true)) scaledQty else (scaledQty / 50.0)
                val kcal = factor * (entry["cal"] ?: 0.0)
                val carbs = factor * (entry["carb"] ?: 0.0)
                val prot = factor * (entry["pro"] ?: 0.0)
                totalCals += kcal; totalCarbs += carbs; totalProt += prot
                
                contentBuilder.append("• $fullName (${PriceCalculator.scaleAmount(amount.toString(), multiplier)})\n")
                if (selectedItem != null) {
                    val tag = selectedItem.ingredientTag.lowercase().trim()
                    val needed = qtyNumeric * multiplier
                    val avail = pool[tag] ?: 0.0
                    
                    if (avail >= needed) {
                        contentBuilder.append("   FREE (Leftovers) | ${kcal.toInt()} kcal | P: ${prot.toInt()}g\n\n")
                    } else {
                        val gap = needed - avail
                        val unitSize = PriceCalculator.extractNumericValue(selectedItem.size)
                        val packs = if (unitSize > 0) ceil(gap / unitSize).toInt().coerceAtLeast(1) else 1
                        val cost = selectedItem.price * packs
                        incrementalPrice += cost
                        val sizeText = if (selectedItem.size.isNotEmpty()) " [${selectedItem.size}]" else ""
                        val gradeText = if (selectedItem.getInferredGrade() != "Standard") " <${selectedItem.getInferredGrade()}>" else ""
                        contentBuilder.append("   ₱${String.format("%.2f", cost)} (${selectedItem.name}$sizeText)$gradeText" + " | ${kcal.toInt()} kcal | P: ${prot.toInt()}g\n\n")
                    }
                } else {
                    contentBuilder.append("   Not Available | ${kcal.toInt()} kcal | P: ${prot.toInt()}g\n\n")
                }
            }
        }
        tvContent.text = contentBuilder.toString().trim()
        tvTotal.text = "Nutrition: ${totalCals.toInt()} kcal | Incremental Price: ₱${String.format("%.2f", incrementalPrice)}\nP: ${totalProt.toInt()}g | C: ${totalCarbs.toInt()}g"
        btnCloseX.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun addToPantry() {
        val uid = auth.currentUser?.uid ?: return
        val recipe = currentRecipe ?: return
        val multiplier = recipe.servings
        val selected = currentIngredients.filter { it.isChecked }

        if (selected.isEmpty()) {
            Toast.makeText(this, "Check ingredients first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val pool = PriceCalculator.buildAvailablePool(currentPantryItems)
        // Add to Pantry uses Greedy if budget is enabled.
        val effectiveBudget = if (isBudgetEnabled) userMaxBudget else 0.0
        val optimization = PriceCalculator.performGreedyOptimization(recipe, allStoreItems, multiplier, effectiveBudget, pool)
        val incrementalCost = optimization.first
        val fitsBudget = optimization.second
        val selections = optimization.third

        if (isBudgetEnabled && !fitsBudget) {
            AlertDialog.Builder(this)
                .setTitle("Budget Too Low")
                .setMessage("Even with budget-friendly alternatives and leftovers (₱${String.format("%.2f", incrementalCost)}), this recipe exceeds your budget of ₱$userMaxBudget.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val pantryRef = database.child("Users").child(uid).child("Pantry")
        val updates = mutableMapOf<String, Any?>()
        
        val runningPool = pool.toMutableMap()

        selected.forEach { ing ->
            val selectedItem = selections[ing.name]
            val amountStr = ing.amount
            val volumeNeededNow = PriceCalculator.extractNumericValue(amountStr) * multiplier
            
            var extraPacksNeeded = 0
            var costForThisRecipe = 0.0
            
            if (selectedItem != null) {
                val tag = selectedItem.ingredientTag.lowercase().trim()
                val unitSize = PriceCalculator.extractNumericValue(selectedItem.size)
                val availableNow = runningPool[tag] ?: 0.0
                
                if (availableNow >= volumeNeededNow) {
                    extraPacksNeeded = 0
                    costForThisRecipe = 0.0
                    runningPool[tag] = availableNow - volumeNeededNow
                } else {
                    val gap = volumeNeededNow - availableNow
                    extraPacksNeeded = if (unitSize > 0) ceil(gap / unitSize).toInt().coerceAtLeast(1) else 1
                    costForThisRecipe = selectedItem.price * extraPacksNeeded
                    runningPool[tag] = (availableNow + (extraPacksNeeded * (if (unitSize > 0) unitSize else gap))) - volumeNeededNow
                }
            } else {
                extraPacksNeeded = 0 
            }

            if (extraPacksNeeded > 0) {
                val key = pantryRef.push().key ?: return@forEach
                val pantryItem = mapOf(
                    "id" to key,
                    "name" to (selectedItem?.name ?: ing.name),
                    "amount" to PriceCalculator.scaleAmount(ing.amount, multiplier),
                    "recipeTitle" to recipe.title,
                    "isChecked" to true,
                    "count" to extraPacksNeeded,
                    "price" to costForThisRecipe,
                    "imageUrl" to (selectedItem?.getDisplayImg() ?: ""),
                    "ingredientTag" to (selectedItem?.ingredientTag ?: ing.name),
                    "size" to (selectedItem?.size ?: ""),
                    "itemGrade" to (selectedItem?.getInferredGrade() ?: "Standard")
                )
                updates[key] = pantryItem
            }
        }
        
        if (updates.isNotEmpty()) {
            pantryRef.updateChildren(updates).addOnSuccessListener {
                Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, PantryActivity::class.java))
            }
        } else {
            Toast.makeText(this, "All selected ingredients are already in your pantry!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logRecipeToDiary() {
        val recipe = currentRecipe ?: return
        val uid = auth.currentUser?.uid ?: return
        val multiplier = recipe.servings
        
        var totalCals = 0.0; var totalCarbs = 0.0; var totalProt = 0.0; var totalFats = 0.0

        recipe.ingredients?.forEach { (fullName, amount) ->
            val cleanName = fullName.split("(")[0].trim()
            val qtyNumeric = PriceCalculator.extractNumericValue(amount.toString())
            val scaledQty = qtyNumeric * multiplier
            val entry = ingredientLibrary[cleanName]

            if (entry != null) {
                val factor = if (cleanName.contains("Egg", true)) scaledQty else (scaledQty / 50.0)
                totalCals += factor * (entry["cal"] ?: 0.0)
                totalCarbs += factor * (entry["carb"] ?: 0.0)
                totalProt += factor * (entry["pro"] ?: 0.0)
                totalFats += factor * (entry["fat"] ?: 0.0)
            }
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val entry = DiaryEntry(
            foodName = recipe.title,
            calories = totalCals.toInt(),
            protein = totalProt.toInt(),
            carbs = totalCarbs.toInt(),
            fats = totalFats.toInt(),
            servings = multiplier
        )
        
        database.child("Users").child(uid).child("DailyDiary").child(dateStr).push().setValue(entry)
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe logged to Diary!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DiaryActivity::class.java))
            }
    }
}
