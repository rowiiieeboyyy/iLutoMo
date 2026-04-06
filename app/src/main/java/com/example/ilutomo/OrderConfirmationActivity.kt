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
        findViewById<Button>(R.id.order_btn_cancel)?.setOnClickListener { cancelOrder() }
        findViewById<Button>(R.id.order_btn_view_more)?.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnRefreshStatus)?.setOnClickListener { loadOrderDetails() }

        if (orderId != null) {
            loadOrderDetails()
        } else {
            Toast.makeText(this, "Order ID not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadOrderDetails() {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return

        // FIXED: Retrieve ONLY from the current user's node
        database.child("Users").child(uid).child("MyOrders").child(id).get().addOnSuccessListener { snapshot ->
            val order = snapshot.getValue(Order::class.java)
            if (order != null) {
                updateUI(order)
            } else {
                Toast.makeText(this, "Order details not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load order", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(order: Order) {
        tvStatus.text = "Pickup Instructions\nOrder Status : ${order.status}"
        tvAddress.text = "${order.businessName}\n${order.pickupAddress}"

        llOrderItems.removeAllViews()
        val items = order.items ?: emptyList()

        for (i in items.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            row.addView(createItemCard(items[i]))
            if (i + 1 < items.size) {
                row.addView(createItemCard(items[i + 1]))
            } else {
                row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            llOrderItems.addView(row)
        }

        findViewById<Button>(R.id.order_btn_cancel).visibility =
            if (order.status == "Pending") View.VISIBLE else View.GONE
    }

    private fun createItemCard(item: PantryIngredient): CardView {
        val card = CardView(this).apply {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(8, 8, 8, 8)
            layoutParams = lp
            radius = 16f
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 4f
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        inner.addView(TextView(this).apply {
            text = "Ingredient"; textSize = 10f; setBackgroundColor(Color.parseColor("#E0E0E0"))
            setPadding(8, 4, 8, 4)
        })

        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 180).apply { topMargin = 8 }
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        })

        inner.addView(TextView(this).apply {
            text = if (item.brandName.isNotEmpty()) item.brandName else item.name
            textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 8 }
        })

        inner.addView(TextView(this).apply {
            text = String.format(Locale.US, "₱%.2f", item.price)
            textSize = 15f; setTextColor(Color.parseColor("#2E7D32"))
        })

        card.addView(inner)
        return card
    }

    private fun cancelOrder() {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("MyOrders").child(id).removeValue().addOnSuccessListener {
            Toast.makeText(this, "Order Cancelled", Toast.LENGTH_SHORT).show()
            returnToPantry()
        }
    }

    private fun returnToPantry() {
        startActivity(Intent(this, PantryActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP })
        finish()
    }
}