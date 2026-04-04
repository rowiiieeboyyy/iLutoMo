package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import kotlin.math.*

class PantryActivity : AppCompatActivity() {

    private lateinit var llPantryList: LinearLayout
    private lateinit var llOrderSummaryItems: LinearLayout
    private lateinit var tvTotalPrice: TextView
    private lateinit var btnClearPantry: Button
    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val pantryIngredients = mutableListOf<PantryIngredient>()
    private val inventoryMap = mutableMapOf<String, MutableList<InventoryItem>>()
    private val businessDetailsMap = mutableMapOf<String, BusinessLocation>()
    
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0
    private var budgetMax: Double = 10000.0

    data class BusinessLocation(val address: String, val lat: Double, val lng: Double, var distance: Double = 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry)

        llPantryList = findViewById(R.id.llPantryList)
        llOrderSummaryItems = findViewById(R.id.llOrderSummaryItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        btnClearPantry = findViewById(R.id.btnClearPantry)
        val btnGetIngredient = findViewById<Button>(R.id.btnGetIngredient)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_pantry

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); finish(); true }
                R.id.nav_pantry -> true
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }

        btnGetIngredient.setOnClickListener {
            placeOrder()
        }

        btnClearPantry.setOnClickListener {
            showClearPantryConfirmation()
        }

        fetchUserLocationAndData()
    }

    private fun showClearPantryConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Pantry")
            .setMessage("Are you sure you want to remove all items from your pantry?")
            .setPositiveButton("Clear All") { _, _ ->
                database.child("Pantry").removeValue().addOnSuccessListener {
                    Toast.makeText(this, "Pantry cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeSpecificIngredient(ingredientId: String) {
        database.child("Pantry").child(ingredientId).removeValue().addOnSuccessListener {
            Toast.makeText(this, "Item removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchUserLocationAndData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                userLat = document.getDouble("latitude") ?: 0.0
                userLng = document.getDouble("longitude") ?: 0.0
                loadUserPreferences()
            }
            .addOnFailureListener {
                loadUserPreferences()
            }
    }

    private fun loadUserPreferences() {
        database.child("UserPreferences").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                budgetMax = snapshot.child("budget_max").value?.toString()?.toDouble() ?: 10000.0
                loadBusinessDataAndPantry()
            }
            override fun onCancelled(error: DatabaseError) {
                loadBusinessDataAndPantry()
            }
        })
    }

    private fun loadBusinessDataAndPantry() {
        database.child("Businesses").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inventoryMap.clear()
                businessDetailsMap.clear()
                for (businessSnapshot in snapshot.children) {
                    val businessName = businessSnapshot.key ?: continue
                    
                    val details = businessSnapshot.child("details")
                    val address = details.child("address").value?.toString() ?: "No Address"
                    val lat = details.child("latitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = details.child("longitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    
                    val distance = calculateDistance(userLat, userLng, lat, lng)
                    businessDetailsMap[businessName] = BusinessLocation(address, lat, lng, distance)

                    val inventorySnapshot = businessSnapshot.child("inventory")
                    val items = mutableListOf<InventoryItem>()
                    for (itemSnapshot in inventorySnapshot.children) {
                        val item = itemSnapshot.getValue(InventoryItem::class.java)
                        if (item != null) {
                            items.add(item)
                        }
                    }
                    inventoryMap[businessName] = items
                }
                loadPantryIngredients()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun loadPantryIngredients() {
        database.child("Pantry").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llPantryList.removeAllViews()
                pantryIngredients.clear()
                
                val groupedMap = mutableMapOf<String, MutableList<PantryIngredient>>()

                for (child in snapshot.children) {
                    val ingredient = child.getValue(PantryIngredient::class.java)
                    if (ingredient != null) {
                        ingredient.id = child.key ?: ""
                        pantryIngredients.add(ingredient)
                        
                        val list = groupedMap.getOrPut(ingredient.recipeTitle) { mutableListOf() }
                        list.add(ingredient)
                    }
                }
                
                updatePantryUI(groupedMap)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PantryActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updatePantryUI(groupedMap: Map<String, List<PantryIngredient>>) {
        if (groupedMap.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = "Your pantry is empty. Add ingredients from the Recipes page."
            tvEmpty.setPadding(0, 20, 0, 0)
            llPantryList.addView(tvEmpty)
            llOrderSummaryItems.removeAllViews()
            tvTotalPrice.text = "Total: ₱0.00"
            btnClearPantry.visibility = View.GONE
            return
        }

        btnClearPantry.visibility = View.VISIBLE
        val sortedBusinesses = businessDetailsMap.entries.sortedBy { it.value.distance }

        for ((recipeTitle, ingredients) in groupedMap) {
            val tvRecipe = TextView(this)
            tvRecipe.text = recipeTitle
            tvRecipe.textSize = 18f
            tvRecipe.setTypeface(null, Typeface.BOLD)
            tvRecipe.setTextColor(Color.parseColor("#1B3022"))
            tvRecipe.setPadding(0, 20, 0, 8)
            llPantryList.addView(tvRecipe)

            for (ing in ingredients) {
                if (ing.businessName.isEmpty() && sortedBusinesses.isNotEmpty()) {
                    // Find the nearest business that has this ingredient
                    for (bizEntry in sortedBusinesses) {
                        val bizName = bizEntry.key
                        val matches = inventoryMap[bizName]?.filter { it.ingredient.equals(ing.name, ignoreCase = true) }
                        if (!matches.isNullOrEmpty()) {
                            // Pick the lowest price option within THIS specific nearest business to fit budget strategy
                            val cheapestMatch = matches.minByOrNull { it.price }!!
                            ing.price = cheapestMatch.price
                            ing.brandName = cheapestMatch.itemName
                            ing.size = cheapestMatch.size
                            ing.businessName = bizName
                            break
                        }
                    }
                }

                val rowPantry = LinearLayout(this)
                rowPantry.orientation = LinearLayout.VERTICAL
                rowPantry.setPadding(0, 8, 0, 8)

                val mainRow = LinearLayout(this)
                mainRow.orientation = LinearLayout.HORIZONTAL
                mainRow.gravity = Gravity.CENTER_VERTICAL
                
                val checkBox = CheckBox(this)
                checkBox.isChecked = ing.isChecked
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    ing.isChecked = isChecked
                    updateOrderSummary()
                }
                
                val tvName = TextView(this)
                tvName.text = "${ing.name} (${ing.amount})"
                tvName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                tvName.setTextColor(Color.BLACK)
                tvName.textSize = 14f
                tvName.setTypeface(null, Typeface.BOLD)

                val tvPrice = TextView(this)
                tvPrice.text = String.format(Locale.US, "₱%.2f", ing.price)
                tvPrice.setTextColor(Color.BLACK)
                tvPrice.gravity = Gravity.END
                tvPrice.textSize = 14f
                tvPrice.setPadding(0, 0, 16, 0)

                val btnDelete = ImageView(this)
                btnDelete.setImageResource(android.R.drawable.ic_menu_delete)
                btnDelete.setColorFilter(Color.parseColor("#F44336"))
                btnDelete.setOnClickListener {
                    removeSpecificIngredient(ing.id)
                }

                mainRow.addView(checkBox)
                mainRow.addView(tvName)
                mainRow.addView(tvPrice)
                mainRow.addView(btnDelete)
                rowPantry.addView(mainRow)

                val optionsLayout = LinearLayout(this)
                optionsLayout.orientation = LinearLayout.VERTICAL
                optionsLayout.setPadding(80, 0, 0, 0)

                sortedBusinesses.forEach { (bizName, bizLoc) ->
                    val items = inventoryMap[bizName] ?: emptyList<InventoryItem>()
                    items.filter { it.ingredient.equals(ing.name, ignoreCase = true) }.forEach { invItem ->
                        val optionRow = LinearLayout(this)
                        optionRow.orientation = LinearLayout.HORIZONTAL
                        
                        val rbOption = CheckBox(this)
                        val distStr = if (bizLoc.distance < 1.0) 
                            String.format("(%.0fm away)", bizLoc.distance * 1000) 
                            else String.format("(%.1fkm away)", bizLoc.distance)
                            
                        rbOption.text = "${invItem.itemName} - ₱${invItem.price} $distStr"
                        rbOption.textSize = 12f
                        
                        // Set checked if this matches the currently selected brand/business for this pantry item
                        if (ing.brandName == invItem.itemName && ing.businessName == bizName) {
                            rbOption.isChecked = true
                        }

                        rbOption.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                // Uncheck other options for this specific ingredient
                                for (i in 0 until optionsLayout.childCount) {
                                    val child = optionsLayout.getChildAt(i) as? LinearLayout
                                    val cb = child?.getChildAt(0) as? CheckBox
                                    if (cb != rbOption) cb?.isChecked = false
                                }
                                ing.price = invItem.price
                                ing.brandName = invItem.itemName
                                ing.size = invItem.size
                                ing.businessName = bizName
                                tvPrice.text = String.format(Locale.US, "₱%.2f", ing.price)
                                updateOrderSummary()
                            }
                        }
                        
                        optionRow.addView(rbOption)
                        optionsLayout.addView(optionRow)
                    }
                }
                rowPantry.addView(optionsLayout)
                llPantryList.addView(rowPantry)
            }
        }
        updateOrderSummary()
    }

    private fun updateOrderSummary() {
        llOrderSummaryItems.removeAllViews()
        var total = 0.0

        pantryIngredients.filter { it.isChecked }.forEach { ing ->
            val rowSummary = LinearLayout(this)
            rowSummary.orientation = LinearLayout.HORIZONTAL
            rowSummary.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowSummary.setPadding(16, 4, 0, 4)

            val tvName = TextView(this)
            val displayText = if (ing.brandName.isNotEmpty()) "- ${ing.brandName} (${ing.size})" else "- ${ing.name} (${ing.amount})"
            tvName.text = displayText
            tvName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tvName.setTextColor(Color.BLACK)
            tvName.textSize = 13f

            val tvPrice = TextView(this)
            tvPrice.text = String.format(Locale.US, "₱%.2f", ing.price)
            tvPrice.setTextColor(Color.BLACK)
            tvPrice.gravity = Gravity.END
            tvPrice.textSize = 13f

            rowSummary.addView(tvName)
            rowSummary.addView(tvPrice)
            llOrderSummaryItems.addView(rowSummary)
            
            total += ing.price
        }

        tvTotalPrice.text = String.format(Locale.US, "Total: ₱%.2f", total)
    }

    private fun placeOrder() {
        val selectedItems = pantryIngredients.filter { it.isChecked }
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No ingredients selected to order!", Toast.LENGTH_SHORT).show()
            return
        }

        val ordersByBusiness = selectedItems.groupBy { it.businessName }
        
        ordersByBusiness.forEach { (bizName, items) ->
            val orderRef = database.child("Orders").push()
            val orderId = orderRef.key ?: ""
            val totalAmount = items.sumOf { it.price }
            val address = businessDetailsMap[bizName]?.address ?: "Default Address"
            
            val order = Order(
                id = orderId,
                timestamp = System.currentTimeMillis(),
                items = items,
                totalAmount = totalAmount,
                status = "Pending",
                businessName = if (bizName.isNullOrEmpty()) "General" else bizName,
                pickupAddress = address
            )

            orderRef.setValue(order).addOnSuccessListener {
                items.forEach { 
                    database.child("Pantry").child(it.id).removeValue()
                }
                if (bizName == ordersByBusiness.keys.last()) {
                    showOrderSummary(order)
                }
            }
        }
    }

    private fun showOrderSummary(order: Order) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Order Successful!")
        
        val summary = StringBuilder()
        summary.append("Order ID: ${order.id}\n")
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        summary.append("Date: ${sdf.format(Date(order.timestamp))}\n")
        summary.append("Business: ${order.businessName}\n")
        summary.append("Pickup: ${order.pickupAddress}\n\n")
        summary.append("Items:\n")
        order.items.forEach { 
            val name = if (it.brandName.isNotEmpty()) it.brandName else it.name
            summary.append("- $name: ₱${"%.2f".format(it.price)}\n")
        }
        summary.append("\nTotal: ₱${"%.2f".format(order.totalAmount)}")
        
        builder.setMessage(summary.toString())
        builder.setPositiveButton("View Order Details") { _, _ ->
            val intent = Intent(this, OrderConfirmationActivity::class.java)
            intent.putExtra("ORDER_ID", order.id)
            startActivity(intent)
            finish()
        }
        builder.setNegativeButton("Close") { _, _ ->
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
        }
        builder.show()
    }
}