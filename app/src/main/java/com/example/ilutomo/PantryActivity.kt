package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*
import java.util.*

class PantryActivity : AppCompatActivity() {

    private lateinit var llPantryList: LinearLayout
    private lateinit var llOrderSummaryItems: LinearLayout
    private lateinit var tvTotalPrice: TextView
    private val database = FirebaseDatabase.getInstance().reference
    private val pantryIngredients = mutableListOf<PantryIngredient>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry)

        llPantryList = findViewById(R.id.llPantryList)
        llOrderSummaryItems = findViewById(R.id.llOrderSummaryItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
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

        loadPantryIngredients()
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
                        // By default, they are checked when loaded
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
            return
        }

        for ((recipeTitle, ingredients) in groupedMap) {
            // Recipe Header
            val tvRecipe = TextView(this)
            tvRecipe.text = recipeTitle
            tvRecipe.textSize = 18f
            tvRecipe.setTypeface(null, Typeface.BOLD)
            tvRecipe.setTextColor(Color.parseColor("#1B3022"))
            tvRecipe.setPadding(0, 20, 0, 8)
            llPantryList.addView(tvRecipe)

            for (ing in ingredients) {
                // Pantry List Row with Checkbox
                val rowPantry = LinearLayout(this)
                rowPantry.orientation = LinearLayout.HORIZONTAL
                rowPantry.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
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
                tvName.textSize = 13f

                val tvPrice = TextView(this)
                tvPrice.text = String.format(Locale.US, "₱%.2f", ing.price)
                tvPrice.setTextColor(Color.BLACK)
                tvPrice.gravity = Gravity.END
                tvPrice.textSize = 13f

                rowPantry.addView(checkBox)
                rowPantry.addView(tvName)
                rowPantry.addView(tvPrice)
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
            tvName.text = "- ${ing.name} (${ing.amount})"
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

        val orderRef = database.child("Orders").push()
        val totalAmount = selectedItems.sumOf { it.price }
        
        val order = Order(
            id = orderRef.key ?: "",
            timestamp = System.currentTimeMillis(),
            items = selectedItems.toList(),
            totalAmount = totalAmount,
            status = "Pending"
        )

        orderRef.setValue(order).addOnSuccessListener {
            showOrderSummary(order)
            // Remove only ordered items from Pantry
            selectedItems.forEach { 
                database.child("Pantry").child(it.id).removeValue()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to place order: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOrderSummary(order: Order) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Order Successful!")
        
        val summary = StringBuilder()
        summary.append("Order ID: ${order.id}\n")
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        summary.append("Date: ${sdf.format(Date(order.timestamp))}\n\n")
        summary.append("Items:\n")
        order.items.forEach { 
            summary.append("- ${it.name}: ₱${"%.2f".format(it.price)}\n")
        }
        summary.append("\nTotal: ₱${"%.2f".format(order.totalAmount)}")
        
        builder.setMessage(summary.toString())
        builder.setPositiveButton("View My Orders") { _, _ ->
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
        }
        builder.setNegativeButton("Close", null)
        builder.show()
    }
}