package com.example.ilutomo

import android.content.Intent
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class OrdersActivity : AppCompatActivity() {

    private lateinit var rvOrders: RecyclerView
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val orderList = mutableListOf<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        rvOrders = findViewById(R.id.rvOrders)
        rvOrders.layoutManager = LinearLayoutManager(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_recipes -> { startActivity(Intent(this, RecipesActivity::class.java)); finish(); true }
                R.id.nav_pantry -> { startActivity(Intent(this, PantryActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        }

        loadOrders()
    }

    private fun loadOrders() {
        val uid = auth.currentUser?.uid ?: return

        // ANTI-BLEED: Pointing to private user node
        database.child("Users").child(uid).child("MyOrders")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    orderList.clear()
                    for (child in snapshot.children) {
                        val order = child.getValue(Order::class.java)
                        if (order != null) {
                            // Sync the ID from the Firebase key if empty
                            if (order.id.isEmpty()) order.id = child.key ?: ""
                            orderList.add(order)
                        }
                    }
                    orderList.sortByDescending { it.timestamp }
                    rvOrders.adapter = OrdersAdapter(orderList) { order ->
                        showCancelConfirmation(order)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@OrdersActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showCancelConfirmation(order: Order) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order?")
            .setPositiveButton("Yes") { _, _ ->
                database.child("Users").child(uid).child("MyOrders").child(order.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Order cancelled successfully", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
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
            val tvPickup: TextView? = view.findViewById(R.id.tvOrderPickup)
            val btnCancel: Button = view.findViewById(R.id.btnCancelOrder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = orders[position]
            val context = holder.itemView.context

            holder.tvId.text = "Order #${order.id.takeLast(6).uppercase()}"
            holder.tvStatus.text = "Status: ${order.status}"

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            holder.tvDate.text = "Date: ${sdf.format(Date(order.timestamp))}"

            // FIXED SYNTAX: This handles empty brand names safely
            val itemsSummary = order.items.joinToString(", ") { item ->
                if (item.brandName.isNotEmpty()) item.brandName else item.name
            }
            holder.tvDetails.text = "Items: $itemsSummary"

            // Check your Order model for totalAmount or price calculation
            holder.tvTotal.text = String.format(Locale.US, "Total: ₱%.2f", order.totalAmount)

            holder.tvPickup?.text = "Pickup at: ${order.businessName}\n${order.pickupAddress}"
            holder.tvPickup?.visibility = if (order.pickupAddress.isNotEmpty()) View.VISIBLE else View.GONE

            if (order.status == "Pending") {
                holder.btnCancel.visibility = View.VISIBLE
                holder.btnCancel.setOnClickListener { onCancelClick(order) }
            } else {
                holder.btnCancel.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(context, OrderConfirmationActivity::class.java)
                intent.putExtra("ORDER_ID", order.id)
                context.startActivity(intent)
            }
        }

        override fun getItemCount() = orders.size
    }
}