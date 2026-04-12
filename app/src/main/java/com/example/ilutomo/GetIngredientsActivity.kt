package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore

class GetIngredientsActivity : AppCompatActivity() {
    private lateinit var rvIngredients: RecyclerView
    private lateinit var tvTotalCost: TextView
    private lateinit var tvBudgetStatus: TextView

    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var userBudget: Double = 0.0
    private var selectedBusiness: String = "" // This should be passed via Intent
    private val recipeIngredients = mutableListOf<String>() // Tags like "pork_belly"
    private val optimizedItems = mutableListOf<InventoryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_ingredients)

        // Initialize Views
        rvIngredients = findViewById(R.id.rvIngredientsList) // Ensure this ID exists in your XML
        tvTotalCost = findViewById(R.id.tvTotalCost)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnGet = findViewById<Button>(R.id.btnGetIngredientAction)

        // Get Data from Intent (Passed from RecipeDetails or Store Selection)
        selectedBusiness = intent.getStringExtra("BUSINESS_NAME") ?: ""
        val recipeTags = intent.getStringArrayListExtra("INGREDIENT_TAGS") ?: arrayListOf()
        recipeIngredients.addAll(recipeTags)

        setupRecyclerView()
        fetchUserBudgetAndOptimize()

        btnBack?.setOnClickListener {
            startActivity(Intent(this, PantryActivity::class.java))
            finish()
        }

        btnGet?.setOnClickListener {
            // Save the optimized list to the "Orders" or "Cart" node
            val intent = Intent(this, OrderConfirmationActivity::class.java)
            startActivity(intent)
        }

        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        rvIngredients.layoutManager = LinearLayoutManager(this)
        // You'll need a simple adapter to show the selected optimizedItems
    }

    private fun fetchUserBudgetAndOptimize() {
        val uid = auth.currentUser?.uid ?: return

        // 1. Get User's Budget from Firestore
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                userBudget = document.getDouble("budget_max") ?: 0.0

                // 2. Load Business Inventory and Optimize
                loadAndOptimizeInventory()
            }
    }

    private fun loadAndOptimizeInventory() {
        if (selectedBusiness.isEmpty()) return

        database.child("Businesses").child(selectedBusiness).child("inventory")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val storeInventory = mutableListOf<InventoryItem>()
                    for (child in snapshot.children) {
                        child.getValue(InventoryItem::class.java)?.let { storeInventory.add(it) }
                    }

                    performOptimization(storeInventory)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun performOptimization(inventory: List<InventoryItem>) {
        optimizedItems.clear()
        var currentTotal = 0.0

        // Step A: Initially pick PREMIUM for everything
        for (tag in recipeIngredients) {
            val premiumItem = inventory.find { it.ingredientTag == tag && it.itemGrade == "Premium" }
            val budgetItem = inventory.find { it.ingredientTag == tag && it.itemGrade == "Budget" }

            val selected = premiumItem ?: budgetItem
            selected?.let {
                optimizedItems.add(it)
                currentTotal += it.price
            }
        }

        // Step B: If over budget, swap Premium for Budget one by one
        if (currentTotal > userBudget && userBudget > 0) {
            for (i in optimizedItems.indices) {
                if (optimizedItems[i].itemGrade == "Premium") {
                    val tag = optimizedItems[i].ingredientTag
                    val budgetAlternative = inventory.find { it.ingredientTag == tag && it.itemGrade == "Budget" }

                    if (budgetAlternative != null) {
                        currentTotal -= optimizedItems[i].price
                        currentTotal += budgetAlternative.price
                        optimizedItems[i] = budgetAlternative

                        // Stop swapping if we are now under budget
                        if (currentTotal <= userBudget) break
                    }
                }
            }
        }

        updateUI(currentTotal)
    }

    private fun updateUI(total: Double) {
        tvTotalCost.text = "Total: ₱${String.format("%.2f", total)}"
        if (total > userBudget && userBudget > 0) {
            tvBudgetStatus.text = "Over Budget by ₱${String.format("%.2f", total - userBudget)}"
            tvBudgetStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
        } else {
            tvBudgetStatus.text = "Within Budget"
            tvBudgetStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        }
        // Notify your adapter here: adapter.notifyDataSetChanged()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav?.selectedItemId = R.id.nav_pantry
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}