package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private val orderList = mutableListOf<Order>()
    private lateinit var adapter: BusinessOrdersAdapter
    private var locationListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadOrders()
        setupBottomNavigation()

        binding.tvNoOrders.setOnClickListener {
            if (locationListener != null) {
                stopTrackingUI()
                Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = BusinessOrdersAdapter(orderList,
            onStatusUpdate = { order, newStatus -> updateOrderStatus(order, newStatus) },
            onTrackClick = { order -> startTrackingCustomer(order.userId) }
        )
        binding.rvBusinessOrders.layoutManager = LinearLayoutManager(this)
        binding.rvBusinessOrders.adapter = adapter
    }

    private fun startTrackingCustomer(customerUid: String?) {
        if (customerUid.isNullOrEmpty()) {
            Toast.makeText(this, "Cannot track: Customer ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        locationListener?.remove()
        Toast.makeText(this, "Connecting to customer GPS...", Toast.LENGTH_SHORT).show()

        locationListener = firestore.collection("users").document(customerUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Tracking Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val lat = snapshot.getDouble("latitude") ?: 0.0
                    val lng = snapshot.getDouble("longitude") ?: 0.0
                    val isActive = snapshot.getBoolean("isTrackingActive") ?: false

                    if (lat != 0.0 || lng != 0.0) {
                        binding.tvNoOrders.visibility = View.VISIBLE
                        val statusText = if (isActive) "LIVE" else "LAST SEEN"
                        binding.tvNoOrders.text = "[$statusText] LOC: $lat, $lng\n(Tap to close tracking)"
                        val bgColor = if (isActive) "#2196F3" else "#757575"
                        binding.tvNoOrders.setBackgroundColor(Color.parseColor(bgColor))
                        binding.tvNoOrders.setTextColor(Color.WHITE)
                    } else {
                        stopTrackingUI()
                    }
                }
            }
    }

    private fun stopTrackingUI() {
        locationListener?.remove()
        locationListener = null
        binding.tvNoOrders.setBackgroundColor(Color.TRANSPARENT)
        binding.tvNoOrders.setTextColor(Color.BLACK)

        if (orderList.isEmpty()) {
            binding.tvNoOrders.text = "No active orders"
            binding.tvNoOrders.visibility = View.VISIBLE
        } else {
            binding.tvNoOrders.visibility = View.GONE
        }
    }

    private fun loadOrders() {
        val uid = auth.currentUser?.uid ?: return

        database.child("BusinessOrders").child(uid).addValueEventListener(object : ValueEventListener {
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

                if (locationListener == null) {
                    if (orderList.isEmpty()) {
                        binding.tvNoOrders.text = "No active orders"
                        binding.tvNoOrders.visibility = View.VISIBLE
                    } else {
                        binding.tvNoOrders.visibility = View.GONE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateOrderStatus(order: Order, status: String) {
        val uid = auth.currentUser?.uid ?: return
        val updates = HashMap<String, Any?>()

        updates["BusinessOrders/$uid/${order.id}/status"] = status
        updates["Users/${order.userId}/MyOrders/${order.id}/status"] = status

        database.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Order marked as $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_orders
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_business_orders) return@setOnItemSelectedListener true

            val intent = when (item.itemId) {
                R.id.nav_business_dashboard -> Intent(this, BusinessDashboardActivity::class.java)
                R.id.nav_business_inventory -> Intent(this, BusinessInventoryActivity::class.java)
                R.id.nav_business_manage -> Intent(this, BusinessManageActivity::class.java)
                R.id.nav_business_profile -> Intent(this, BusinessProfileActivity::class.java)
                else -> null
            }

            intent?.let {
                it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(it)
                finish()
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.remove()
    }

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
            val btnDecline: Button = view.findViewById(R.id.btnDeclineOrder)
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
            val orderDate = sdf.format(Date(order.timestamp))
            val pickup = if (order.pickupTime.isNullOrEmpty()) "ASAP" else order.pickupTime
            holder.tvDate.text = "Placed: $orderDate\nPickup: $pickup"

            holder.tvDetails.text = order.items.joinToString("\n") {
                "• ${it.name} (${it.amount})"
            }

            // Reset visibilities first
            holder.btnAccept.visibility = View.GONE
            holder.btnDecline.visibility = View.GONE
            holder.btnComplete.visibility = View.GONE
            holder.btnTrack.visibility = View.GONE

            holder.btnTrack.setOnClickListener { onTrackClick(order) }

            when (order.status) {
                "Pending" -> {
                    holder.btnAccept.visibility = View.VISIBLE
                    holder.btnDecline.visibility = View.VISIBLE

                    holder.btnAccept.setOnClickListener { onStatusUpdate(order, "Preparing Ingredients") }
                    holder.btnDecline.setOnClickListener {
                        AlertDialog.Builder(holder.itemView.context)
                            .setTitle("Decline Order")
                            .setMessage("Are you sure you want to decline this order?")
                            .setPositiveButton("Yes") { _, _ -> onStatusUpdate(order, "Declined") }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
                "Preparing Ingredients" -> {
                    // Track button is now visible while preparing
                    holder.btnTrack.visibility = View.VISIBLE
                    holder.btnComplete.visibility = View.VISIBLE
                    holder.btnComplete.text = "Ready for Pickup"
                    holder.btnComplete.setOnClickListener { onStatusUpdate(order, "Ready for Pickup") }
                }
                "Ready for Pickup" -> {
                    // Track button remains visible when ready
                    holder.btnTrack.visibility = View.VISIBLE
                    holder.btnComplete.visibility = View.VISIBLE
                    holder.btnComplete.text = "Mark Completed"
                    holder.btnComplete.setOnClickListener { onStatusUpdate(order, "Completed") }
                }
                else -> {
                    // No buttons for Declined or Completed status
                }
            }
        }

        override fun getItemCount() = orders.size
    }
}