package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ilutomo.databinding.ActivityBusinessDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class BusinessDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessDashboardBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var businessName: String? = null
    
    private val activeOrders = mutableListOf<Order>()
    private lateinit var ordersAdapter: PendingOrdersAdapter
    
    private val lowStockItems = mutableListOf<InventoryItem>()
    private lateinit var lowStockAdapter: LowStockBriefAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        fetchBusinessInfoAndLoadData()
        setupBottomNavigation()
        setupClickListeners()
    }

    private fun setupRecyclerViews() {
        // Active Orders
        ordersAdapter = PendingOrdersAdapter(activeOrders) {
            startActivity(Intent(this, BusinessOrdersActivity::class.java))
        }
        binding.rvPendingOrders.layoutManager = LinearLayoutManager(this)
        binding.rvPendingOrders.adapter = ordersAdapter
        
        // Low Stock
        lowStockAdapter = LowStockBriefAdapter(lowStockItems) {
            startActivity(Intent(this, BusinessInventoryActivity::class.java).apply {
                putExtra("FILTER_LOW_STOCK", true)
            })
        }
        binding.rvLowStockItems.layoutManager = LinearLayoutManager(this)
        binding.rvLowStockItems.adapter = lowStockAdapter
    }

    private fun fetchBusinessInfoAndLoadData() {
        val uid = auth.currentUser?.uid ?: return
        
        loadInventoryStats()
        loadActiveOrders()

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                businessName = document.getString("businessName")?.trim()
                if (businessName.isNullOrEmpty()) {
                    Toast.makeText(this, "Set Business Name in Profile to enable all features", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Log.e("Dashboard", "Failed to load business info")
            }
    }

    private fun loadInventoryStats() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Businesses").child(uid).child("inventory")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalCount = 0
                    val allLowStock = mutableListOf<InventoryItem>()
                    
                    for (child in snapshot.children) {
                        val item = child.getValue(InventoryItem::class.java)
                        if (item != null) {
                            totalCount++
                            if (item.stock < 50) {
                                allLowStock.add(item)
                            }
                        }
                    }
                    
                    binding.tvTotalItems.text = totalCount.toString()
                    binding.tvLowStock.text = allLowStock.size.toString()
                    binding.tvLowStockAlertTitle.text = "Low Stock Alerts (${allLowStock.size})"
                    
                    binding.cvLowStock.visibility = if (allLowStock.isEmpty()) View.GONE else View.VISIBLE
                    
                    lowStockItems.clear()
                    allLowStock.sortBy { it.stock }
                    lowStockItems.addAll(allLowStock.take(10))
                    lowStockAdapter.notifyDataSetChanged()
                    
                    binding.tvSeeMoreLowStock.visibility = if (allLowStock.size > 10) View.VISIBLE else View.GONE
                    binding.rlLowStockListHeader.visibility = if (allLowStock.isEmpty()) View.GONE else View.VISIBLE
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Dashboard", "Inventory Error: ${error.message}")
                }
            })
    }

    private fun loadActiveOrders() {
        val uid = auth.currentUser?.uid ?: return
        database.child("BusinessOrders").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activeOrders.clear()
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null && order.status != "Completed" && order.status != "Declined") {
                        if (order.id.isEmpty()) order.id = child.key ?: ""
                        activeOrders.add(order)
                    }
                }
                activeOrders.sortByDescending { it.timestamp }
                ordersAdapter.notifyDataSetChanged()
                
                binding.tvNoPendingOrders.visibility = if (activeOrders.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Dashboard", "Orders Error: ${error.message}")
            }
        })
    }

    private fun setupClickListeners() {
        binding.cvTotalItems.setOnClickListener {
            startActivity(Intent(this, BusinessInventoryActivity::class.java))
        }
        binding.cvLowStock.setOnClickListener {
            startActivity(Intent(this, BusinessInventoryActivity::class.java).apply {
                putExtra("FILTER_LOW_STOCK", true)
            })
        }
        binding.tvSeeMoreLowStock.setOnClickListener {
            startActivity(Intent(this, BusinessInventoryActivity::class.java).apply {
                putExtra("FILTER_LOW_STOCK", true)
            })
        }
        binding.tvViewAllOrders.setOnClickListener {
            startActivity(Intent(this, BusinessOrdersActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_dashboard
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_business_dashboard -> true
                R.id.nav_business_inventory -> {
                    startActivity(Intent(this, BusinessInventoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_orders -> {
                    startActivity(Intent(this, BusinessOrdersActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_profile -> {
                    startActivity(Intent(this, BusinessProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    class LowStockBriefAdapter(
        private val items: List<InventoryItem>,
        private val onItemClick: () -> Unit
    ) : RecyclerView.Adapter<LowStockBriefAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvItemName: TextView = view.findViewById(R.id.tvItemName)
            val tvStockCount: TextView = view.findViewById(R.id.tvStockCount)
            val tvItemDetails: TextView = view.findViewById(R.id.tvItemDetails)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_low_stock_brief, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvItemName.text = item.getDisplayName()
            holder.tvStockCount.text = "${item.stock} left"
            holder.tvItemDetails.text = "${item.size} • ₱${String.format(Locale.getDefault(), "%.2f", item.price)}"
            holder.itemView.setOnClickListener { onItemClick() }
        }

        override fun getItemCount() = items.size
    }

    class PendingOrdersAdapter(
        private val orders: List<Order>,
        private val onItemClick: () -> Unit
    ) : RecyclerView.Adapter<PendingOrdersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvOrderId: TextView = view.findViewById(R.id.tvOrderId)
            val tvOrderDate: TextView = view.findViewById(R.id.tvOrderDate)
            val tvOrderDetails: TextView = view.findViewById(R.id.tvOrderDetails)
            val tvCustomerName: TextView = view.findViewById(R.id.tvCustomerName)
            val tvOrderStatusBrief: TextView = view.findViewById(R.id.tvOrderStatusBrief)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_order_brief, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = orders[position]
            holder.tvOrderId.text = "Order #${order.id.takeLast(6).uppercase()}"
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            holder.tvOrderDate.text = sdf.format(Date(order.timestamp))
            
            val itemsText = order.items.joinToString(", ") { it.brandName.ifEmpty { it.name } }
            holder.tvOrderDetails.text = itemsText

            holder.tvOrderStatusBrief.text = order.status
            
            holder.tvCustomerName.text = "Loading..."
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(order.userId).get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("fullName") ?: "Unknown User"
                    holder.tvCustomerName.text = fullName
                }
                .addOnFailureListener {
                    holder.tvCustomerName.text = "User #${order.userId.takeLast(4)}"
                }

            holder.itemView.setOnClickListener { onItemClick() }
        }

        override fun getItemCount() = if (orders.size > 5) 5 else orders.size
    }
}
