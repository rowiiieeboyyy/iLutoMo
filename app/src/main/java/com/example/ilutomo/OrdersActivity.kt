package com.example.ilutomo

import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.*

class OrdersActivity : AppCompatActivity() {

    private lateinit var rvOrders: RecyclerView
    private lateinit var tvNoOrders: TextView
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val orderList = mutableListOf<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        rvOrders = findViewById(R.id.rvOrders)
        tvNoOrders = findViewById(R.id.tvNoOrders)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        rvOrders.layoutManager = LinearLayoutManager(this)
        btnBack.setOnClickListener { finish() }

        setupBottomNavigation(bottomNav)
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
                            order.id = child.key ?: ""
                            orderList.add(order)
                        }
                    }

                    orderList.sortByDescending { it.timestamp }
                    tvNoOrders.visibility = if (orderList.isEmpty()) View.VISIBLE else View.GONE
                    rvOrders.adapter = OrdersAdapter(orderList) { order -> showCancelConfirmation(order) }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@OrdersActivity, "Error loading orders", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showCancelConfirmation(order: Order) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order?")
            .setPositiveButton("Yes") { _, _ ->
                val updates = HashMap<String, Any?>()
                updates["Users/$uid/MyOrders/${order.id}"] = null
                updates["BusinessOrders/${order.businessUid}/${order.id}"] = null
                database.updateChildren(updates)
            }
            .setNegativeButton("No", null).show()
    }

    private fun setupBottomNavigation(bottomNav: BottomNavigationView) {
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            val targetActivity = when (item.itemId) {
                R.id.nav_home -> HomeActivity::class.java
                R.id.nav_recipes -> RecipesActivity::class.java
                R.id.nav_pantry -> PantryActivity::class.java
                R.id.nav_profile -> ProfileActivity::class.java
                else -> null
            }
            if (targetActivity != null) {
                startActivity(Intent(this, targetActivity))
                finish()
                true
            } else false
        }
    }

    class OrdersAdapter(
        private val orders: List<Order>,
        private val onCancelClick: (Order) -> Unit
    ) : RecyclerView.Adapter<OrdersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvId: TextView = view.findViewById(R.id.tvOrderId)
            val tvStatus: TextView = view.findViewById(R.id.tvOrderStatus)
            val tvDate: TextView = view.findViewById(R.id.tvOrderDate)
            val tvDetails: TextView = view.findViewById(R.id.tvOrderDetails)
            val tvTotal: TextView = view.findViewById(R.id.tvOrderTotal)
            val tvPickup: TextView = view.findViewById(R.id.tvOrderPickup)

            // Link to the separate time field from XML
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

            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(order.timestamp))
            holder.tvDate.text = "Date: $dateStr"

            holder.tvDetails.text = "Items: ${order.items.joinToString(", ") { it.name }}"
            holder.tvTotal.text = String.format("Total: ₱%.2f", order.totalAmount)

            // --- FIXED BINDING ---
            // Separate the Store and Time so they don't appear on the same line
            holder.tvPickup.text = "Store: ${order.businessName}"
            holder.tvPickupTime.text = "Pickup Time: ${order.pickupTime}"

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