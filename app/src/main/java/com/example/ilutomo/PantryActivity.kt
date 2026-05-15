package com.example.ilutomo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
    private var outOfBudgetRecipes = mutableSetOf<String>()
    private lateinit var pantryAdapter: PantryAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var userMaxBudget = 1000.0
    private var isBudgetEnabled = false

    sealed class PantryListItem {
        data class Header(val title: String, val isOutOfBudget: Boolean = false) : PantryListItem()
        data class Ingredient(val item: PantryIngredient) : PantryListItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        rvPantryList = findViewById(R.id.rvPantryList)
        llOrderSummaryItems = findViewById(R.id.llOrderSummaryItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        btnClearPantry = findViewById(R.id.btnClearPantry)
        btnGetIngredient = findViewById(R.id.btnGetIngredient)

        rvPantryList.layoutManager = LinearLayoutManager(this)
        pantryAdapter = PantryAdapter(mutableListOf(), 
            onCheckChanged = { item, isChecked -> toggleIngredientCheck(item, isChecked) },
            onUpdateCount = { item, change -> updateFirebaseItemCount(item, change) },
            onSwapAlternative = { item -> showAlternativesDialog(item) }
        )
        rvPantryList.adapter = pantryAdapter

        setupNavigation()

        btnGetIngredient.setOnClickListener { fetchStoresAndShowDialog() }
        btnClearPantry.setOnClickListener { showClearPantryConfirmation() }

        fetchPreferences()
    }
    
    private fun fetchPreferences() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Preferences").get().addOnSuccessListener { s ->
            userMaxBudget = s.child("budget_max").value?.toString()?.toDoubleOrNull() ?: 1000.0
            isBudgetEnabled = s.child("is_budget_enabled").value as? Boolean ?: false
            loadPantryIngredients()
        }.addOnFailureListener { loadPantryIngredients() }
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
                
                calculateOutOfBudgetRecipes(groupedMap)
                
                val listItems = mutableListOf<PantryListItem>()
                for ((title, items) in groupedMap) {
                    // Filter out satisfied ingredients (count <= 0)
                    val displayItems = items.filter { it.count > 0 }
                    if (displayItems.isNotEmpty()) {
                        listItems.add(PantryListItem.Header(title, outOfBudgetRecipes.contains(title)))
                        displayItems.forEach { listItems.add(PantryListItem.Ingredient(it)) }
                    }
                }
                
                pantryAdapter.updateData(listItems)
                updateOrderSummary()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun calculateOutOfBudgetRecipes(groupedMap: Map<String, List<PantryIngredient>>) {
        outOfBudgetRecipes.clear()
        if (!isBudgetEnabled) return // Don't show budget errors if filter is off
        
        val tagToTotalVolumeNeeded = mutableMapOf<String, Double>()
        val tagToTotalCostPaid = mutableMapOf<String, Double>()
        
        pantryIngredients.forEach { ing ->
            val tag = ing.ingredientTag.lowercase().trim()
            val volume = PriceCalculator.extractNumericValue(ing.amount)
            tagToTotalVolumeNeeded[tag] = (tagToTotalVolumeNeeded[tag] ?: 0.0) + volume
            tagToTotalCostPaid[tag] = (tagToTotalCostPaid[tag] ?: 0.0) + ing.price
        }

        for ((title, ingredients) in groupedMap) {
            var recipeDistributedCost = 0.0
            for (ing in ingredients) {
                val tag = ing.ingredientTag.lowercase().trim()
                val totalVolumeNeededForTag = tagToTotalVolumeNeeded[tag] ?: 0.0
                val totalCostPaidForTag = tagToTotalCostPaid[tag] ?: 0.0
                
                if (totalVolumeNeededForTag > 0) {
                    val myVolume = PriceCalculator.extractNumericValue(ing.amount)
                    val myShare = (myVolume / totalVolumeNeededForTag) * totalCostPaidForTag
                    recipeDistributedCost += myShare
                }
            }
            if (recipeDistributedCost > userMaxBudget) {
                outOfBudgetRecipes.add(title)
            }
        }
    }

    private fun showAlternativesDialog(pantryItem: PantryIngredient) {
        val tag = if (pantryItem.ingredientTag.isNotEmpty()) pantryItem.ingredientTag else pantryItem.name
        database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alternatives = mutableListOf<InventoryItem>()
                for (bizSnapshot in snapshot.children) {
                    val invNode = bizSnapshot.child("inventory")
                    for (itemSnap in invNode.children) {
                        val invItem = itemSnap.getValue(InventoryItem::class.java) ?: continue
                        if (invItem.ingredient.equals(tag, true) || invItem.ingredientTag.equals(tag, true)) {
                            alternatives.add(invItem)
                        }
                    }
                }

                if (alternatives.isEmpty()) {
                    Toast.makeText(this@PantryActivity, "No alternatives found for $tag", Toast.LENGTH_SHORT).show()
                    return
                }

                val options = alternatives.map { 
                    val display = "${it.getDisplayName()} (${it.size}) - ₱${"%.2f".format(it.price)}"
                    if (it.getDisplayName() == pantryItem.name) "$display (Current)" else display
                }.toTypedArray()

                AlertDialog.Builder(this@PantryActivity)
                    .setTitle("Swap for $tag")
                    .setItems(options) { _, which ->
                        swapPantryItem(pantryItem, alternatives[which])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun swapPantryItem(oldItem: PantryIngredient, newItem: InventoryItem) {
        val uid = auth.currentUser?.uid ?: return
        val totalNewPrice = oldItem.count * newItem.price
        val updates = mapOf(
            "name" to newItem.getDisplayName(),
            "brandName" to (newItem.name),
            "price" to totalNewPrice,
            "size" to newItem.size,
            "imageUrl" to newItem.getDisplayImg(),
            "itemGrade" to newItem.getInferredGrade(),
            "ingredientTag" to (if (oldItem.ingredientTag.isEmpty()) oldItem.name else oldItem.ingredientTag)
        )
        database.child("Users").child(uid).child("Pantry").child(oldItem.id).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Swapped to ${newItem.getDisplayName()}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleIngredientCheck(ing: PantryIngredient, isChecked: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        ing.isChecked = isChecked
        updateOrderSummary()
        database.child("Users").child(uid).child("Pantry").child(ing.id).child("isChecked").setValue(isChecked)
    }

    private fun updateFirebaseItemCount(ing: PantryIngredient, change: Int) {
        val uid = auth.currentUser?.uid ?: return
        val currentC = if (ing.count <= 0) 1 else ing.count
        val newC = currentC + change
        if (newC <= 0) {
            database.child("Users").child(uid).child("Pantry").child(ing.id).removeValue()
            return
        }

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
        pantryIngredients.filter { it.isChecked && it.count > 0 }.forEach {
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
        tvTotalPrice.setTextColor(Color.BLACK)
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
        val selectedItems = pantryIngredients.filter { it.isChecked && it.count > 0 }
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Select items first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { userLocation ->
            database.child("Businesses").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val storeList = mutableListOf<Triple<String, String, Float>>() // Name, Uid, Distance
                    for (bizSnapshot in snapshot.children) {
                        val name = bizSnapshot.child("details").child("businessName").value?.toString()
                        val uid = bizSnapshot.key
                        val lat = bizSnapshot.child("details").child("latitude").value?.toString()?.toDoubleOrNull()
                        val lng = bizSnapshot.child("details").child("longitude").value?.toString()?.toDoubleOrNull()
                        
                        if (name != null && uid != null) {
                            var distance = Float.MAX_VALUE
                            if (userLocation != null && lat != null && lng != null) {
                                val results = FloatArray(1)
                                Location.distanceBetween(userLocation.latitude, userLocation.longitude, lat, lng, results)
                                distance = results[0]
                            }
                            storeList.add(Triple(name, uid, distance))
                        }
                    }

                    if (storeList.isEmpty()) {
                        Toast.makeText(this@PantryActivity, "No stores available", Toast.LENGTH_SHORT).show()
                        return
                    }

                    storeList.sortBy { it.third }

                    val storeNamesWithDistance = storeList.map { 
                        if (it.third == Float.MAX_VALUE) it.first 
                        else "${it.first} (${String.format("%.1f", it.third / 1000)} km)"
                    }.toTypedArray()

                    AlertDialog.Builder(this@PantryActivity)
                        .setTitle("Select Store (Closest first)")
                        .setItems(storeNamesWithDistance) { _, which ->
                            navigateToConfirmation(storeList[which].first, storeList[which].second, selectedItems)
                        }
                        .setNegativeButton("Cancel", null).show()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
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
        private val onUpdateCount: (PantryIngredient, Int) -> Unit,
        private val onSwapAlternative: (PantryIngredient) -> Unit
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
                if (item.isOutOfBudget) {
                    holder.ivError.visibility = View.VISIBLE
                    holder.tvError.visibility = View.VISIBLE
                } else {
                    holder.ivError.visibility = View.GONE
                    holder.tvError.visibility = View.GONE
                }
            } else if (holder is ItemViewHolder && item is PantryListItem.Ingredient) {
                val ing = item.item
                holder.tvName.text = ing.name
                holder.tvCount.text = ing.count.toString()
                holder.tvDetails.text = "${ing.size} • ₱${"%.2f".format(ing.price)}"
                
                if (!ing.imageUrl.isNullOrEmpty()) {
                    Glide.with(holder.imageView.context).load(ing.imageUrl).placeholder(R.drawable.placeholder_food).into(holder.imageView)
                } else {
                    holder.imageView.setImageResource(R.drawable.placeholder_food)
                }

                holder.checkBox.setOnCheckedChangeListener(null)
                holder.checkBox.isChecked = ing.isChecked
                holder.checkBox.setOnClickListener {
                    val isChecked = (it as CheckBox).isChecked
                    onCheckChanged(ing, isChecked)
                }

                holder.btnPlus.setOnClickListener { onUpdateCount(ing, 1) }
                holder.btnMinus.setOnClickListener { onUpdateCount(ing, -1) }
                holder.tvSwap.setOnClickListener { onSwapAlternative(ing) }
            }
        }

        override fun getItemCount() = items.size

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeader: TextView = view.findViewById(R.id.tvPantryHeader)
            val ivError: ImageView = view.findViewById(R.id.ivOutOfBudget)
            val tvError: TextView = view.findViewById(R.id.tvOutOfBudget)
        }

        class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.cbPantryIngredient)
            val imageView: ImageView = view.findViewById(R.id.ivPantryIngredientImage)
            val tvName: TextView = view.findViewById(R.id.tvPantryIngredientName)
            val tvDetails: TextView = view.findViewById(R.id.tvPantryIngredientDetails)
            val tvCount: TextView = view.findViewById(R.id.tvPantryIngredientCount)
            val btnPlus: ImageButton = view.findViewById(R.id.btnPantryPlus)
            val btnMinus: ImageButton = view.findViewById(R.id.btnPantryMinus)
            val tvSwap: TextView = view.findViewById(R.id.tvSwapAlternative)
        }
    }
}
