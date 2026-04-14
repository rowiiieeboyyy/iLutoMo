package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class PantryActivity : AppCompatActivity() {

    private lateinit var rvPantryList: RecyclerView
    private lateinit var llOrderSummaryItems: LinearLayout
    private lateinit var tvTotalPrice: TextView
    private lateinit var btnClearPantry: Button
    private lateinit var btnGetIngredient: Button

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var pantryIngredients = mutableListOf<PantryIngredient>()
    private lateinit var pantryAdapter: PantryAdapter

    sealed class PantryListItem {
        data class Header(val title: String) : PantryListItem()
        data class Ingredient(val item: PantryIngredient) : PantryListItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry)

        rvPantryList = findViewById(R.id.rvPantryList)
        llOrderSummaryItems = findViewById(R.id.llOrderSummaryItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        btnClearPantry = findViewById(R.id.btnClearPantry)
        btnGetIngredient = findViewById(R.id.btnGetIngredient)

        rvPantryList.layoutManager = LinearLayoutManager(this)
        pantryAdapter = PantryAdapter(mutableListOf(), 
            onCheckChanged = { item, isChecked -> toggleIngredientCheck(item, isChecked) },
            onUpdateCount = { item, change -> updateFirebaseItemCount(item, change) }
        )
        rvPantryList.adapter = pantryAdapter

        setupNavigation()

        btnGetIngredient.setOnClickListener { fetchStoresAndShowDialog() }
        btnClearPantry.setOnClickListener { showClearPantryConfirmation() }

        loadPantryIngredients()
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
                
                val listItems = mutableListOf<PantryListItem>()
                for ((title, items) in groupedMap) {
                    listItems.add(PantryListItem.Header(title))
                    items.forEach { listItems.add(PantryListItem.Ingredient(it)) }
                }
                
                pantryAdapter.updateData(listItems)
                updateOrderSummary()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun toggleIngredientCheck(ing: PantryIngredient, isChecked: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        // Update local object immediately to avoid UI lag/flicker
        ing.isChecked = isChecked
        updateOrderSummary()
        
        // Update Firebase
        database.child("Users").child(uid).child("Pantry").child(ing.id).child("isChecked").setValue(isChecked)
    }

    private fun updateFirebaseItemCount(ing: PantryIngredient, change: Int) {
        val uid = auth.currentUser?.uid ?: return
        val currentC = if (ing.count <= 0) 1 else ing.count
        val newC = currentC + change
        if (newC <= 0) return

        val singleItemPrice = if (currentC > 0) ing.price / currentC else 0.0
        val totalNewPrice = newC * singleItemPrice

        val updates = mapOf(
            "count" to newC,
            "price" to totalNewPrice
        )
        database.child("Users").child(uid).child("Pantry").child(ing.id).updateChildren(updates)
    }

    private fun updateOrderSummary() {
        llOrderSummaryItems.removeAllViews()
        var total = 0.0
        pantryIngredients.filter { it.isChecked }.forEach {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 5, 0, 5) }
            row.addView(TextView(this).apply {
                text = "• ${it.name} x${it.count}"
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
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

    private fun fetchStoresAndShowDialog() {
        val selectedItems = pantryIngredients.filter { it.isChecked }
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Select items first!", Toast.LENGTH_SHORT).show()
            return
        }
        database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val storeNames = mutableListOf<String>()
                val storeUids = mutableListOf<String>()
                for (bizSnapshot in snapshot.children) {
                    val name = bizSnapshot.child("details").child("businessName").value?.toString()
                    val uid = bizSnapshot.key
                    if (name != null && uid != null) {
                        storeNames.add(name)
                        storeUids.add(uid)
                    }
                }
                if (storeNames.isEmpty()) {
                    Toast.makeText(this@PantryActivity, "No stores available", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this@PantryActivity)
                        .setTitle("Select Store to Pick Up")
                        .setItems(storeNames.toTypedArray()) { _, which ->
                            navigateToConfirmation(storeNames[which], storeUids[which], selectedItems)
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun navigateToConfirmation(businessName: String, businessUid: String, selectedItems: List<PantryIngredient>) {
        val intent = Intent(this, OrderConfirmationActivity::class.java)
        intent.putExtra("STORE_NAME", businessName)
        intent.putExtra("STORE_UID", businessUid)
        intent.putExtra("SELECTED_ITEMS", ArrayList(selectedItems))
        startActivity(intent)
    }

    private fun showClearPantryConfirmation() {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Clear Pantry?")
            .setMessage("Do you want to remove all items?")
            .setPositiveButton("Yes") { _, _ -> database.child("Users").child(uid).child("Pantry").removeValue() }
            .setNegativeButton("No", null).show()
    }

    class PantryAdapter(
        private var items: List<PantryListItem>,
        private val onCheckChanged: (PantryIngredient, Boolean) -> Unit,
        private val onUpdateCount: (PantryIngredient, Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_ITEM = 1
        }

        fun updateData(newItems: List<PantryListItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is PantryListItem.Header -> TYPE_HEADER
                is PantryListItem.Ingredient -> TYPE_ITEM
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pantry_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pantry_ingredient, parent, false)
                ItemViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder && item is PantryListItem.Header) {
                holder.tvHeader.text = item.title
            } else if (holder is ItemViewHolder && item is PantryListItem.Ingredient) {
                val ing = item.item
                holder.tvName.text = ing.name
                holder.tvCount.text = ing.count.toString()
                
                // CRITICAL FIX: Use setOnClickListener instead of setOnCheckedChangeListener
                // to prevent programmatic state changes from triggering Firebase updates
                // and causing the "recheck all" or jumping UI behavior.
                holder.checkBox.setOnCheckedChangeListener(null)
                holder.checkBox.isChecked = ing.isChecked
                holder.checkBox.setOnClickListener {
                    val isChecked = (it as CheckBox).isChecked
                    onCheckChanged(ing, isChecked)
                }

                holder.btnPlus.setOnClickListener { onUpdateCount(ing, 1) }
                holder.btnMinus.setOnClickListener { onUpdateCount(ing, -1) }
            }
        }

        override fun getItemCount() = items.size

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeader: TextView = view.findViewById(R.id.tvPantryHeader)
        }

        class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.cbPantryIngredient)
            val tvName: TextView = view.findViewById(R.id.tvPantryIngredientName)
            val tvCount: TextView = view.findViewById(R.id.tvPantryIngredientCount)
            val btnPlus: ImageButton = view.findViewById(R.id.btnPantryPlus)
            val btnMinus: ImageButton = view.findViewById(R.id.btnPantryMinus)
        }
    }
}