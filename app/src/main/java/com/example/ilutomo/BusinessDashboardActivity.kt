package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
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
        fetchBusinessNameAndLoadData()
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

    private fun fetchBusinessNameAndLoadData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val name = document.getString("businessName")?.trim()
                if (!name.isNullOrEmpty()) {
                    businessName = name
                    loadInventoryStats()
                    loadActiveOrders()
                } else {
                    Toast.makeText(this, "Set Business Name in Profile first", Toast.LENGTH_SHORT).show()
                    binding.tvTotalItems.text = "0"
                    binding.tvLowStock.text = "0"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load business info", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadInventoryStats() {
        val biz = businessName ?: return
        // Path should be Businesses/[BusinessName]/inventory
        database.child("Businesses").child(biz).child("inventory")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalCount = 0
                    val allLowStock = mutableListOf<InventoryItem>()
                    
                    for (child in snapshot.children) {
                        val item = child.getValue(InventoryItem::class.java)
                        if (item != null) {
                            totalCount++
                            // Threshold updated to 30
                            if (item.stock < 30) {
                                allLowStock.add(item)
                            }
                        }
                    }
                    
                    binding.tvTotalItems.text = totalCount.toString()
                    binding.tvLowStock.text = allLowStock.size.toString()
                    
                    // Show top 6 lowest stock items on dashboard
                    lowStockItems.clear()
                    allLowStock.sortBy { it.stock }
                    lowStockItems.addAll(allLowStock.take(6))
                    lowStockAdapter.notifyDataSetChanged()
                    
                    // Show "See More" if there are more than 6 low stock items
                    binding.tvSeeMoreLowStock.visibility = if (allLowStock.size > 6) View.VISIBLE else View.GONE
                    binding.rlLowStockListHeader.visibility = if (allLowStock.isEmpty()) View.GONE else View.VISIBLE
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BusinessDashboardActivity, "Inventory Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadActiveOrders() {
        val biz = businessName ?: return
        // Ensure path matches save logic: BusinessOrders/[BusinessName]
        database.child("BusinessOrders").child(biz).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activeOrders.clear()
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    // Show all latest orders that are not yet completed or declined
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
                Toast.makeText(this@BusinessDashboardActivity, "Orders Error: ${error.message}", Toast.LENGTH_SHORT).show()
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
                R.id.nav_business_manage -> {
                    startActivity(Intent(this, BusinessManageActivity::class.java))
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
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_low_stock_brief, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvItemName.text = item.name
            holder.tvStockCount.text = "${item.stock} left"
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

            holder.itemView.setOnClickListener { onItemClick() }
        }

        override fun getItemCount() = if (orders.size > 5) 5 else orders.size
    }
}