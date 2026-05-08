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

    private var currentIngredients = mutableListOf<DisplayIngredient>()
    private var currentRecipe: Recipe? = null
    private val ingredientLibrary = mutableMapOf<String, Map<String, Double>>()
    private val allStoreItems = mutableListOf<InventoryItem>()

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        tvNeeded = findViewById(R.id.tvNeededList)
        rvAvailable = findViewById(R.id.rvAvailableIngredients)
        tvServings = findViewById(R.id.tvRecipeServings)
        tvDetailPrice = findViewById(R.id.tvRecipeDetailPrice)

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
            else showStepsDialog(currentRecipe!!)
        }
        btnAddToPantry.setOnClickListener { addToPantry() }
        btnLogToDiary.setOnClickListener { logRecipeToDiary() }
    }

    private fun loadData() {
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
        var estimatedTotalPrice = 0.0

        currentIngredients.forEach { ing ->
            val scaledAmount = PriceCalculator.scaleAmount(ing.amount, multiplier)
            
            val cheapestItem = PriceCalculator.findCheapestMatch(ing.name, allStoreItems)
            val priceText: String
            
            if (cheapestItem != null) {
                val orderCount = PriceCalculator.calculateOrderCount(ing.amount, cheapestItem.size, multiplier)
                val cost = cheapestItem.price * orderCount
                val sizeText = if (cheapestItem.size.isNotEmpty()) " [${cheapestItem.size}]" else ""
                priceText = " - ₱${String.format("%.2f", cost)} (${cheapestItem.name}$sizeText)"
                if (ing.isChecked) estimatedTotalPrice += cost
            } else {
                priceText = " - Not Available"
            }
            
            fullText.append("${ing.name} ($scaledAmount)$priceText\n\n")
        }

        tvDetailPrice.text = "Total Price: ₱${String.format("%.2f", estimatedTotalPrice)}"

        val spannable = SpannableString(fullText.toString())
        var currentPos = 0
        currentIngredients.forEach { ing ->
            val scaledAmount = PriceCalculator.scaleAmount(ing.amount, multiplier)
            val cheapestItem = PriceCalculator.findCheapestMatch(ing.name, allStoreItems)
            
            val segmentPricePart: String
            if (cheapestItem != null) {
                val orderCount = PriceCalculator.calculateOrderCount(ing.amount, cheapestItem.size, multiplier)
                val cost = cheapestItem.price * orderCount
                val sizeText = if (cheapestItem.size.isNotEmpty()) " [${cheapestItem.size}]" else ""
                segmentPricePart = " - ₱${String.format("%.2f", cost)} (${cheapestItem.name}$sizeText)"
            } else {
                segmentPricePart = " - Not Available"
            }
            
            val segment = "${ing.name} ($scaledAmount)$segmentPricePart\n\n"
            
            if (!ing.isChecked) {
                spannable.setSpan(StrikethroughSpan(), currentPos, currentPos + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.GRAY), currentPos, currentPos + ing.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (cheapestItem == null) {
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
        var totalCals = 0.0; var totalCarbs = 0.0; var totalProt = 0.0; var totalPrice = 0.0

        recipe.ingredients?.forEach { (fullName, amount) ->
            val cleanName = fullName.split("(")[0].trim()
            val qtyNumeric = PriceCalculator.extractNumericValue(amount.toString())
            val scaledQty = qtyNumeric * multiplier
            val entry = ingredientLibrary.entries.find { it.key.equals(cleanName, true) }?.value
            val cheapestItem = PriceCalculator.findCheapestMatch(fullName, allStoreItems)

            if (entry != null) {
                val factor = if (cleanName.contains("Egg", true)) scaledQty else (scaledQty / 50.0)
                val kcal = factor * (entry["cal"] ?: 0.0)
                val carbs = factor * (entry["carb"] ?: 0.0)
                val prot = factor * (entry["pro"] ?: 0.0)
                totalCals += kcal; totalCarbs += carbs; totalProt += prot
                
                contentBuilder.append("• $fullName (${PriceCalculator.scaleAmount(amount.toString(), multiplier)})\n")
                if (cheapestItem != null) {
                    val orderCount = PriceCalculator.calculateOrderCount(amount.toString(), cheapestItem.size, multiplier)
                    val cost = cheapestItem.price * orderCount
                    totalPrice += cost
                    val sizeText = if (cheapestItem.size.isNotEmpty()) " [${cheapestItem.size}]" else ""
                    contentBuilder.append("   ₱${String.format("%.2f", cost)} (${cheapestItem.name}$sizeText) | ${kcal.toInt()} kcal | P: ${prot.toInt()}g\n\n")
                } else {
                    contentBuilder.append("   Not Available | ${kcal.toInt()} kcal | P: ${prot.toInt()}g\n\n")
                }
            }
        }
        tvContent.text = contentBuilder.toString().trim()
        tvTotal.text = "Total: ${totalCals.toInt()} kcal | ₱${String.format("%.2f", totalPrice)}\nP: ${totalProt.toInt()}g | C: ${totalCarbs.toInt()}g"
        btnCloseX.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showStepsDialog(recipe: Recipe) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_info, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val tvContent = dialogView.findViewById<TextView>(R.id.dialogContent)
        val llTotal = dialogView.findViewById<LinearLayout>(R.id.llTotalSection)
        val btnCloseX = dialogView.findViewById<ImageButton>(R.id.btnCloseDialog)
        tvTitle.text = "${recipe.title} - Steps"
        llTotal.visibility = View.GONE
        val steps = recipe.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n\n") ?: "No steps available."
        tvContent.text = steps
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

        val pantryRef = database.child("Users").child(uid).child("Pantry")
        selected.forEach { ing ->
            val cheapestItem = PriceCalculator.findCheapestMatch(ing.name, allStoreItems)
            val key = pantryRef.push().key ?: return@forEach

            val orderCount = if (cheapestItem != null) PriceCalculator.calculateOrderCount(ing.amount, cheapestItem.size, multiplier) else multiplier
            val linePrice = if (cheapestItem != null) cheapestItem.price * orderCount else 0.0

            val item = mapOf(
                "id" to key,
                "name" to (cheapestItem?.name ?: ing.name),
                "amount" to PriceCalculator.scaleAmount(ing.amount, multiplier),
                "recipeTitle" to recipe.title,
                "isChecked" to true,
                "count" to orderCount,
                "price" to linePrice,
                "imageUrl" to (cheapestItem?.img ?: ""),
                "ingredientTag" to (cheapestItem?.ingredient ?: ing.name),
                "size" to (cheapestItem?.size ?: "")
            )
            pantryRef.child(key).setValue(item)
        }
        Toast.makeText(this, "Added to Pantry!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, PantryActivity::class.java))
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