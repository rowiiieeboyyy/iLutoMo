package com.example.ilutomo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.database.*
import java.util.*

class OrderConfirmationActivity : AppCompatActivity() {
    private lateinit var llOrderItems: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private val database = FirebaseDatabase.getInstance().reference
    private var orderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_confirmation)

        orderId = intent.getStringExtra("ORDER_ID")

        llOrderItems = findViewById(R.id.llOrderItemsContainer)
        tvStatus = findViewById(R.id.tvOrderStatus)
        tvAddress = findViewById(R.id.tvPickupAddress)

        val btnBack = findViewById<ImageView>(R.id.order_back_arrow)
        btnBack?.setOnClickListener { returnToPantry() }

        val btnCancel = findViewById<Button>(R.id.order_btn_cancel)
        btnCancel?.setOnClickListener { cancelOrder() }

        val btnViewMore = findViewById<Button>(R.id.order_btn_view_more)
        btnViewMore?.setOnClickListener { 
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
        }

        val btnRefresh = findViewById<Button>(R.id.btnRefreshStatus)
        btnRefresh?.setOnClickListener { loadOrderDetails() }

        if (orderId != null) {
            loadOrderDetails()
        } else {
            Toast.makeText(this, "Order ID not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadOrderDetails() {
        val id = orderId ?: return
        database.child("Orders").child(id).get().addOnSuccessListener { snapshot ->
            val order = snapshot.getValue(Order::class.java)
            if (order != null) {
                updateUI(order)
            }
        }
    }

    private fun updateUI(order: Order) {
        tvStatus.text = "Pickup Instructions\nOrder Status : ${order.status}"
        tvAddress.text = "${order.businessName}\n${order.pickupAddress}"
        
        llOrderItems.removeAllViews()
        
        val items = order.items
        for (i in items.indices step 2) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.weightSum = 2f
            
            row.addView(createItemCard(items[i]))
            
            if (i + 1 < items.size) {
                row.addView(createItemCard(items[i + 1]))
            } else {
                val space = View(this)
                val spaceLp = LinearLayout.LayoutParams(0, 1, 1f)
                space.layoutParams = spaceLp
                row.addView(space)
            }
            llOrderItems.addView(row)
        }

        findViewById<Button>(R.id.order_btn_cancel).visibility = 
            if (order.status == "Pending") View.VISIBLE else View.GONE
    }

    private fun createItemCard(item: PantryIngredient): CardView {
        val card = CardView(this)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(8, 8, 8, 8)
        card.layoutParams = lp
        card.radius = 16f
        card.setCardBackgroundColor(Color.WHITE)
        card.cardElevation = 4f

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(16, 16, 16, 16)

        val tvType = TextView(this)
        tvType.text = "Ingredient"
        tvType.textSize = 10f
        tvType.setBackgroundColor(Color.parseColor("#E0E0E0"))
        tvType.setPadding(8, 4, 8, 4)
        inner.addView(tvType)

        val placeholder = View(this)
        val pLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200)
        pLp.topMargin = 8
        placeholder.layoutParams = pLp
        placeholder.setBackgroundColor(Color.parseColor("#F5F5F5"))
        inner.addView(placeholder)

        val tvName = TextView(this)
        tvName.text = if (item.brandName.isNotEmpty()) item.brandName else item.name
        tvName.textSize = 12f
        val nLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        nLp.topMargin = 8
        tvName.layoutParams = nLp
        inner.addView(tvName)

        val tvPrice = TextView(this)
        tvPrice.text = String.format(Locale.US, "₱%.2f", item.price)
        tvPrice.textSize = 16f
        tvPrice.setTypeface(null, android.graphics.Typeface.BOLD)
        inner.addView(tvPrice)

        card.addView(inner)
        return card
    }

    private fun cancelOrder() {
        val id = orderId ?: return
        database.child("Orders").child(id).removeValue().addOnSuccessListener {
            Toast.makeText(this, "Order Cancelled", Toast.LENGTH_SHORT).show()
            returnToPantry()
        }
    }

    private fun returnToPantry() {
        val intent = Intent(this, PantryActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}