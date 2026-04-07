package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ilutomo.databinding.ActivityBusinessOrdersBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class BusinessOrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessOrdersBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var businessName: String? = null
    private val orderList = mutableListOf<Order>()
    private lateinit var adapter: BusinessOrdersAdapter

    // To prevent memory leaks and redundant listeners
    private var locationListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        fetchBusinessNameAndLoadOrders()
        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        adapter = BusinessOrdersAdapter(orderList,
            onStatusUpdate = { order, newStatus -> updateOrderStatus(order, newStatus) },
            onTrackClick = { order -> startTrackingCustomer(order.userId) }
        )
        binding.rvBusinessOrders.layoutManager = LinearLayoutManager(this)
        binding.rvBusinessOrders.adapter = adapter
    }

    // LISTENS TO CUSTOMER GPS IN REAL-TIME
    private fun startTrackingCustomer(customerUid: String) {
        if (customerUid.isEmpty()) {
            Toast.makeText(this, "Cannot track: Customer ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        // Remove previous listener before starting a new one
        locationListener?.remove()

        Toast.makeText(this, "Fetching live location...", Toast.LENGTH_SHORT).show()

        locationListener = firestore.collection("users").document(customerUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Tracking", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val lat = snapshot.getDouble("latitude") ?: 0.0
                    val lng = snapshot.getDouble("longitude") ?: 0.0
                    val isTracking = snapshot.getBoolean("isTrackingEnabled") ?: false

                    if (isTracking) {
                        // Displaying the location in the 'No Orders' field as a status bar
                        binding.tvNoOrders.visibility = View.VISIBLE
                        binding.tvNoOrders.text = "Tracking Customer: $lat, $lng"
                        binding.tvNoOrders.setBackgroundColor(getColor(android.R.color.holo_blue_light))
                    } else {
                        binding.tvNoOrders.text = "Customer has disabled live tracking."
                        binding.tvNoOrders.setBackgroundColor(getColor(android.R.color.transparent))
                        locationListener?.remove()
                    }
                }
            }
    }

    private fun fetchBusinessNameAndLoadOrders() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                businessName = document.getString("businessName")
                if (!businessName.isNullOrEmpty()) {
                    loadOrders()
                } else {
                    binding.tvNoOrders.text = "Please set Business Name in Profile"
                    binding.tvNoOrders.visibility = View.VISIBLE
                }
            }
    }

    private fun loadOrders() {
        val biz = businessName ?: return
        database.child("BusinessOrders").child(biz).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                orderList.clear()
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        if (order.id.isEmpty()) order.id = child.key ?: ""
                        orderList.add(order)
                    }
                }
                orderList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()

                // Only show "No Orders" text if we aren't currently tracking someone
                if (orderList.isEmpty()) {
                    binding.tvNoOrders.text = "No active orders for $biz"
                    binding.tvNoOrders.visibility = View.VISIBLE
                } else if (locationListener == null) {
                    binding.tvNoOrders.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateOrderStatus(order: Order, status: String) {
        val biz = businessName ?: return
        val updates = HashMap<String, Any?>()
        updates["BusinessOrders/$biz/${order.id}/status"] = status
        updates["Users/${order.userId}/MyOrders/${order.id}/status"] = status

        database.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Order marked as $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_orders
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_business_dashboard -> { startActivity(Intent(this, BusinessDashboardActivity::class.java)); finish(); true }
                R.id.nav_business_inventory -> { startActivity(Intent(this, BusinessInventoryActivity::class.java)); finish(); true }
                R.id.nav_business_orders -> true
                R.id.nav_business_manage -> { startActivity(Intent(this, BusinessManageActivity::class.java)); finish(); true }
                R.id.nav_business_profile -> { startActivity(Intent(this, BusinessProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.remove()
    }

    // --- ADAPTER ---
    class BusinessOrdersAdapter(
        private val orders: List<Order>,
        private val onStatusUpdate: (Order, String) -> Unit,
        private val onTrackClick: (Order) -> Unit
    ) : RecyclerView.Adapter<BusinessOrdersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvId: TextView = view.findViewById(R.id.tvOrderId)
            val tvStatus: TextView = view.findViewById(R.id.tvOrderStatus)
            val tvDate: TextView = view.findViewById(R.id.tvOrderDate)
            val tvDetails: TextView = view.findViewById(R.id.tvOrderDetails)
            val btnAccept: Button = view.findViewById(R.id.btnAcceptOrder)
            val btnComplete: Button = view.findViewById(R.id.btnCompleteOrder)
            val btnTrack: Button = view.findViewById(R.id.btnTrackCustomer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_business_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = orders[position]
            holder.tvId.text = "Order #${order.id.takeLast(6).uppercase()}"
            holder.tvStatus.text = "Status: ${order.status}"
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(order.timestamp))

            holder.tvDetails.text = order.items.joinToString("\n") {
                "• ${it.brandName.ifEmpty { it.name }} (${it.size.ifEmpty { it.amount }})"
            }

            holder.btnTrack.setOnClickListener { onTrackClick(order) }

            when (order.status) {
                "Pending" -> {
                    holder.btnAccept.visibility = View.VISIBLE
                    holder.btnComplete.visibility = View.GONE
                    holder.btnAccept.setOnClickListener { onStatusUpdate(order, "Preparing Ingredients") }
                }
                "Preparing Ingredients" -> {
                    holder.btnAccept.visibility = View.GONE
                    holder.btnComplete.visibility = View.VISIBLE
                    holder.btnComplete.text = "Ready for Pickup"
                    holder.btnComplete.setOnClickListener { onStatusUpdate(order, "Ready for Pickup") }
                }
                "Ready for Pickup" -> {
                    holder.btnAccept.visibility = View.GONE
                    holder.btnComplete.visibility = View.VISIBLE
                    holder.btnComplete.text = "Completed"
                    holder.btnComplete.setOnClickListener { onStatusUpdate(order, "Completed") }
                }
                else -> {
                    holder.btnAccept.visibility = View.GONE
                    holder.btnComplete.visibility = View.GONE
                }
            }
        }

        override fun getItemCount() = orders.size
    }
}