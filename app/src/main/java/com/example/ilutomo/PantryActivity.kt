package com.example.ilutomo

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PantryActivity : AppCompatActivity() {

    private lateinit var llPantryList: LinearLayout
    private lateinit var llOrderSummaryItems: LinearLayout
    private lateinit var tvTotalPrice: TextView
    private lateinit var btnClearPantry: Button
    private lateinit var btnGetIngredient: Button

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var pantryIngredients = mutableListOf<PantryIngredient>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry)

        llPantryList = findViewById(R.id.llPantryList)
        llOrderSummaryItems = findViewById(R.id.llOrderSummaryItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        btnClearPantry = findViewById(R.id.btnClearPantry)
        btnGetIngredient = findViewById(R.id.btnGetIngredient)

        setupNavigation()

        btnGetIngredient.setOnClickListener { processCheckout() }
        btnClearPantry.setOnClickListener { showClearPantryConfirmation() }

        loadPantryIngredients()
    }

    private fun processCheckout() {
        val uid = auth.currentUser?.uid ?: return
        val selectedItems = pantryIngredients.filter { it.isChecked }

        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Select items first!", Toast.LENGTH_SHORT).show()
            return
        }

        // FIX: Extract the business name from the selected items
        val businessName = selectedItems[0].businessName.ifEmpty { "SM Supermarket Aura" }

        // Generate a unique ID for the order
        val orderRef = database.child("BusinessOrders").child(businessName).push()
        val orderId = orderRef.key ?: return

        val orderData = Order(
            id = orderId,
            userId = uid,
            timestamp = System.currentTimeMillis(),
            items = selectedItems,
            totalAmount = selectedItems.sumOf { it.price },
            status = "Pending",
            businessName = businessName,
            pickupAddress = "Teresa, Rizal"
        )

        // DOUBLE-SAVE: Send to both the User folder and the Business Inbox
        val updates = HashMap<String, Any?>()
        updates["BusinessOrders/$businessName/$orderId"] = orderData
        updates["Users/$uid/MyOrders/$orderId"] = orderData

        database.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, OrderConfirmationActivity::class.java)
            intent.putExtra("ORDER_ID", orderId)
            startActivity(intent)
        }.addOnFailureListener {
            Toast.makeText(this, "Checkout failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPantryIngredients() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Pantry").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pantryIngredients.clear()
                val groupedMap = mutableMapOf<String, MutableList<PantryIngredient>>()
                for (child in snapshot.children) {
                    val ing = child.getValue(PantryIngredient::class.java) ?: continue
                    if (ing.id.isEmpty()) ing.id = child.key ?: ""
                    pantryIngredients.add(ing)
                    groupedMap.getOrPut(ing.recipeTitle) { mutableListOf() }.add(ing)
                }
                updateUI(groupedMap)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUI(groupedMap: Map<String, List<PantryIngredient>>) {
        llPantryList.removeAllViews()
        for ((title, items) in groupedMap) {
            val tv = TextView(this).apply { text = title; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, 20, 0, 10) }
            llPantryList.addView(tv)
            for (ing in items) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 10) }
                val cb = CheckBox(this).apply {
                    isChecked = ing.isChecked
                    setOnCheckedChangeListener { _, isChecked -> ing.isChecked = isChecked; updateOrderSummary() }
                }
                row.addView(cb)
                row.addView(TextView(this).apply { text = "${ing.name} (${ing.amount})"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                llPantryList.addView(row)
            }
        }
        updateOrderSummary()
    }

    private fun updateOrderSummary() {
        llOrderSummaryItems.removeAllViews()
        var total = 0.0
        pantryIngredients.filter { it.isChecked }.forEach {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = "• ${it.name}"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            row.addView(TextView(this).apply { text = "₱${"%.2f".format(it.price)}" })
            llOrderSummaryItems.addView(row)
            total += it.price
        }
        tvTotalPrice.text = "Total Price: ₱${"%.2f".format(total)}"
    }

    private fun setupNavigation() {
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
    }

    private fun showClearPantryConfirmation() {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this).setTitle("Clear Pantry?").setPositiveButton("Yes") { _, _ ->
            database.child("Users").child(uid).child("Pantry").removeValue()
        }.setNegativeButton("No", null).show()
    }
}