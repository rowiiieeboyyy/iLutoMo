package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class OrdersActivity : AppCompatActivity() {

    private lateinit var rvOrders: RecyclerView
    private lateinit var tvNoOrders: TextView
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance() // Added Firestore
    private val orderList = mutableListOf<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        rvOrders = findViewById(R.id.rvOrders)
        tvNoOrders = findViewById(R.id.tvNoOrders)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        rvOrders.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            val targetActivity = when (item.itemId) {
                R.id.nav_home -> HomeActivity::class.java
                R.id.nav_recipes -> RecipesActivity::class.java
                R.id.nav_pantry -> PantryActivity::class.java
                R.id.nav_profile -> ProfileActivity::class.java
                else -> null
            }
            if (targetActivity != null && this::class.java != targetActivity) {
                val intent = Intent(this, targetActivity)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
                true
            } else { false }
        }

        loadOrders()
    }

    private fun loadOrders() {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("MyOrders")
            .addValueEventListener(object : ValueEventListener {
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
                    tvNoOrders.visibility = if (orderList.isEmpty()) View.VISIBLE else View.GONE

                    // Pass firestore to the adapter
                    rvOrders.adapter = OrdersAdapter(orderList, database, firestore) { order ->
                        showCancelConfirmation(order)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showCancelConfirmation(order: Order) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Cancel Order")
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { _, _ ->
                val updates = HashMap<String, Any?>()
                updates["Users/$uid/MyOrders/${order.id}"] = null
                updates["BusinessOrders/${order.businessUid}/${order.id}"] = null
                database.updateChildren(updates)
            }
            .setNegativeButton("No", null).show()
    }

    class OrdersAdapter(
        private val orders: List<Order>,
        private val database: DatabaseReference,
        private val firestore: FirebaseFirestore, // Added for location lookup
        private val onCancelClick: (Order) -> Unit
    ) : RecyclerView.Adapter<OrdersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvId: TextView = view.findViewById(R.id.tvOrderId)
            val tvStatus: TextView = view.findViewById(R.id.tvOrderStatus)
            val tvDate: TextView = view.findViewById(R.id.tvOrderDate)
            val tvDetails: TextView = view.findViewById(R.id.tvOrderDetails)
            val tvTotal: TextView = view.findViewById(R.id.tvOrderTotal)
            val tvPickup: TextView = view.findViewById(R.id.tvOrderPickup)
            val tvPickupTime: TextView = view.findViewById(R.id.tvOrderPickupTime)
            val btnCancel: Button = view.findViewById(R.id.btnCancelOrder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = orders[position]
            holder.tvId.text = "Order #${order.id.takeLast(6).uppercase()}"
            holder.tvStatus.text = "Status: ${order.status}"

            // 1. LIVE BUSINESS INFO LOOKUP
            if (order.businessUid.isNotEmpty()) {
                database.child("Businesses").child(order.businessUid).child("details")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val name = snapshot.child("businessName").value?.toString() ?: order.businessName
                            val addr = snapshot.child("address").value?.toString() ?: order.pickupAddress
                            holder.tvPickup.text = "Pickup at: $name\n$addr"
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }

            // 2. LIVE LOCATION LOOKUP (Read-Only)
            // This listens to the Customer's own location or the Business location
            // depending on who is viewing the screen.
            firestore.collection("users").document(order.userId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val lat = snapshot.getDouble("latitude") ?: 0.0
                        val lng = snapshot.getDouble("longitude") ?: 0.0
                        // You can display this in a small sub-text or a specific location field
                        if (lat != 0.0) {
                            holder.tvDate.text = "Placed: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(order.timestamp))}\nYour Loc: $lat, $lng"
                        }
                    }
                }

            val itemsSummary = order.items.joinToString(", ") { it.brandName.ifEmpty { it.name } }
            holder.tvDetails.text = "Items: $itemsSummary"
            holder.tvTotal.text = String.format(Locale.US, "Total: ₱%.2f", order.totalAmount)

            if (order.status == "Pending") {
                holder.btnCancel.visibility = View.VISIBLE
                holder.btnCancel.setOnClickListener { onCancelClick(order) }
            } else {
                holder.btnCancel.visibility = View.GONE
            }
        }

        override fun getItemCount() = orders.size
    }
}