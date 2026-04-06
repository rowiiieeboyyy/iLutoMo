package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class OrderConfirmationActivity : AppCompatActivity() {
    private lateinit var llOrderItems: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var orderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_confirmation)

        orderId = intent.getStringExtra("ORDER_ID")
        llOrderItems = findViewById(R.id.llOrderItemsContainer)
        tvStatus = findViewById(R.id.tvOrderStatus)
        tvAddress = findViewById(R.id.tvPickupAddress)

        findViewById<ImageView>(R.id.order_back_arrow)?.setOnClickListener { returnToPantry() }
        findViewById<Button>(R.id.btnRefreshStatus)?.setOnClickListener { loadOrderDetails() }
        findViewById<Button>(R.id.order_btn_cancel)?.setOnClickListener { cancelOrder() }
        findViewById<Button>(R.id.order_btn_view_more)?.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
        }

        if (orderId != null) loadOrderDetails()
        else finish()
    }

    private fun loadOrderDetails() {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid).child("MyOrders").child(id).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val order = snapshot.getValue(Order::class.java)
                if (order != null) updateUI(order)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUI(order: Order) {
        tvStatus.text = "Pickup Instructions\nOrder Status : ${order.status}"
        tvAddress.text = "${order.businessName}\n${order.pickupAddress}"

        llOrderItems.removeAllViews()
        val items = order.items
        for (i in items.indices step 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
            row.addView(createItemCard(items[i]))
            if (i + 1 < items.size) row.addView(createItemCard(items[i + 1]))
            else row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            llOrderItems.addView(row)
        }
        findViewById<Button>(R.id.order_btn_cancel).visibility = if (order.status == "Pending") View.VISIBLE else View.GONE
    }

    private fun createItemCard(item: PantryIngredient): CardView {
        val card = CardView(this).apply {
            val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(8, 8, 8, 8) }
            layoutParams = lp; radius = 16f; cardElevation = 4f
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }

        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 180).apply { topMargin = 8 }
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        })
        inner.addView(TextView(this).apply {
            text = item.brandName.ifEmpty { item.name }
            textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
        })
        inner.addView(TextView(this).apply {
            text = "₱${"%.2f".format(item.price)}"
            textSize = 15f; setTextColor(Color.parseColor("#2E7D32"))
        })
        card.addView(inner)
        return card
    }

    private fun cancelOrder() {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("MyOrders").child(id).removeValue().addOnSuccessListener {
            returnToPantry()
        }
    }

    private fun returnToPantry() {
        startActivity(Intent(this, PantryActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP })
        finish()
    }
}