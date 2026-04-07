package com.example.ilutomo

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class OrderConfirmationActivity : AppCompatActivity() {
    private lateinit var llOrderItems: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvPickupTime: TextView
    private lateinit var tvExpectedReadyTime: TextView

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var orderId: String? = null
    private var currentBusiness: String = ""
    private var orderListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_confirmation)

        orderId = intent.getStringExtra("ORDER_ID")
        llOrderItems = findViewById(R.id.llOrderItemsContainer)
        tvStatus = findViewById(R.id.tvOrderStatus)
        tvAddress = findViewById(R.id.tvPickupAddress)
        tvPickupTime = findViewById(R.id.tvPickupTime)
        tvExpectedReadyTime = findViewById(R.id.tvExpectedReadyTime)

        // Set up click listener for the pickup time
        tvPickupTime.setOnClickListener { showTimePicker() }

        findViewById<ImageView>(R.id.order_back_arrow)?.setOnClickListener { returnToPantry() }
        findViewById<Button>(R.id.order_btn_cancel)?.setOnClickListener { cancelOrder() }

        // Logic for the "Place Order" button (previously History)
        findViewById<Button>(R.id.order_btn_place_order)?.setOnClickListener {
            // Redirect to History/View Orders screen
            val intent = Intent(this, OrdersActivity::class.java)
            startActivity(intent)
            finish()
        }

        if (orderId != null) {
            loadOrderDetails()
        } else {
            finish()
        }
    }

    private fun showTimePicker() {
        val c = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(this, { _, h, m ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
            updatePickupTimeInFirebase(timeStr)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)

        timePickerDialog.show()
    }

    private fun updatePickupTimeInFirebase(newTime: String) {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return

        val updates = HashMap<String, Any?>()
        updates["Users/$uid/MyOrders/$id/pickupTime"] = newTime
        if (currentBusiness.isNotEmpty()) {
            updates["BusinessOrders/$currentBusiness/$id/pickupTime"] = newTime
        }

        database.updateChildren(updates).addOnSuccessListener {
            tvPickupTime.text = "Pick up at\n$newTime"
            Toast.makeText(this, "Pickup time updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOrderDetails() {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return

        orderListener = database.child("Users").child(uid).child("MyOrders").child(id)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val order = snapshot.getValue(Order::class.java)
                    if (order != null) {
                        currentBusiness = order.businessName
                        updateUI(order)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUI(order: Order) {
        tvStatus.text = "Order Status : ${order.status}"
        tvAddress.text = "${order.businessName}\n${order.pickupAddress}"
        tvPickupTime.text = if (order.pickupTime.isEmpty()) "Set Pickup Time" else "Pick up at\n${order.pickupTime}"
        tvExpectedReadyTime.text = "Approximately 30 minutes"

        llOrderItems.removeAllViews()
        val items = order.items
        for (i in items.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            row.addView(createItemCard(items[i]))
            if (i + 1 < items.size) {
                row.addView(createItemCard(items[i + 1]))
            } else {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            llOrderItems.addView(row)
        }

        findViewById<Button>(R.id.order_btn_cancel).visibility =
            if (order.status == "Pending") View.VISIBLE else View.GONE
    }

    private fun createItemCard(item: PantryIngredient): CardView {
        val card = CardView(this).apply {
            val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(8, 8, 8, 8) }
            layoutParams = lp
            radius = 16f
            cardElevation = 4f
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 180).apply { topMargin = 8 }
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        })
        inner.addView(TextView(this).apply {
            text = item.brandName.ifEmpty { item.name }
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        inner.addView(TextView(this).apply {
            text = "₱${"%.2f".format(item.price)}"
            textSize = 16f
            setTextColor(Color.parseColor("#2E7D32"))
        })
        card.addView(inner)
        return card
    }

    private fun cancelOrder() {
        val id = orderId ?: return
        val uid = auth.currentUser?.uid ?: return

        val updates = HashMap<String, Any?>()
        updates["Users/$uid/MyOrders/$id"] = null
        if (currentBusiness.isNotEmpty()) {
            updates["BusinessOrders/$currentBusiness/$id"] = null
        }

        database.updateChildren(updates).addOnSuccessListener {
            returnToPantry()
        }
    }

    private fun returnToPantry() {
        val intent = Intent(this, PantryActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        val uid = auth.currentUser?.uid
        val id = orderId
        if (uid != null && id != null && orderListener != null) {
            database.child("Users").child(uid).child("MyOrders").child(id).removeEventListener(orderListener!!)
        }
    }
}