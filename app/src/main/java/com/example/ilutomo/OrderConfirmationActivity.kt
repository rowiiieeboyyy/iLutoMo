package com.example.ilutomo

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var btnCancel: Button
    private lateinit var btnPlaceOrder: Button

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var storeName: String = ""
    private var storeUid: String = ""
    private var selectedItems = ArrayList<PantryIngredient>()
    private var finalPickupTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_confirmation)

        llOrderItems = findViewById(R.id.llOrderItemsContainer)
        tvStatus = findViewById(R.id.tvOrderStatus)
        tvAddress = findViewById(R.id.tvPickupAddress)
        tvPickupTime = findViewById(R.id.tvPickupTime)
        tvExpectedReadyTime = findViewById(R.id.tvExpectedReadyTime)
        btnCancel = findViewById(R.id.btnCancelOrder)
        btnPlaceOrder = findViewById(R.id.order_btn_place_order)

        storeName = intent.getStringExtra("STORE_NAME") ?: ""
        storeUid = intent.getStringExtra("STORE_UID") ?: ""
        selectedItems = intent.getSerializableExtra("SELECTED_ITEMS") as? ArrayList<PantryIngredient> ?: arrayListOf()

        tvAddress.text = "$storeName\nTeresa, Rizal"
        tvStatus.text = "Order Status : Pending"

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 30)
        finalPickupTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
        tvPickupTime.text = "Pick up at\n$finalPickupTime"

        displayDraftItems()

        tvPickupTime.setOnClickListener { showTimePicker() }
        findViewById<ImageView>(R.id.order_back_arrow)?.setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }
        btnPlaceOrder.setOnClickListener { executeFirebaseSave() }
    }

    private fun displayDraftItems() {
        llOrderItems.removeAllViews()
        for (i in selectedItems.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }
            row.addView(createItemCard(selectedItems[i]))
            if (i + 1 < selectedItems.size) {
                row.addView(createItemCard(selectedItems[i + 1]))
            } else {
                row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            llOrderItems.addView(row)
        }
    }

    private fun createItemCard(item: PantryIngredient): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(8, 8, 8, 8) }
            radius = 16f
            cardElevation = 4f
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 180)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        })

        inner.addView(TextView(this).apply {
            text = "${item.name} x${item.count}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        })

        inner.addView(TextView(this).apply {
            text = "₱${"%.2f".format(item.price)}"
            textSize = 16f
            setTextColor(Color.parseColor("#2E7D32"))
        })
        card.addView(inner)
        return card
    }

    private fun executeFirebaseSave() {
        val uid = auth.currentUser?.uid ?: return
        if (storeUid.isEmpty()) return

        btnPlaceOrder.isEnabled = false
        val orderRef = database.child("BusinessOrders").child(storeUid).push()
        val orderId = orderRef.key ?: return

        val orderData = Order(
            id = orderId,
            userId = uid,
            businessUid = storeUid,
            businessName = storeName,
            items = selectedItems,
            totalAmount = selectedItems.sumOf { it.price },
            timestamp = System.currentTimeMillis(),
            status = "Pending",
            pickupAddress = "Teresa, Rizal",
            pickupTime = finalPickupTime
        )

        val updates = HashMap<String, Any?>()
        updates["BusinessOrders/$storeUid/$orderId"] = orderData
        updates["Users/$uid/MyOrders/$orderId"] = orderData

        database.updateChildren(updates).addOnSuccessListener {
            // Deduct stock before clearing pantry
            deductInventoryStock(storeUid, selectedItems)
            
            val pantryRef = database.child("Users").child(uid).child("Pantry")
            selectedItems.forEach { item -> pantryRef.child(item.id).removeValue() }

            Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, OrdersActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }.addOnFailureListener {
            btnPlaceOrder.isEnabled = true
            Toast.makeText(this, "Failed to place order.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deductInventoryStock(businessUid: String, items: List<PantryIngredient>) {
        val inventoryRef = database.child("Businesses").child(businessUid).child("inventory")
        
        items.forEach { orderedItem ->
            // Use the brandName/name which is the key in the inventory database
            val itemKey = orderedItem.name 
            
            inventoryRef.child(itemKey).runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val inventoryItem = mutableData.getValue(InventoryItem::class.java)
                        ?: return Transaction.success(mutableData)
                    
                    val currentStock = inventoryItem.stock
                    val orderedCount = orderedItem.count
                    
                    // Deduct the stock
                    inventoryItem.stock = (currentStock - orderedCount).coerceAtLeast(0)
                    
                    mutableData.value = inventoryItem
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) {
                        android.util.Log.e("STOCK_UPDATE", "Failed to deduct stock for ${orderedItem.name}: ${error.message}")
                    }
                }
            })
        }
    }

    private fun showTimePicker() {
        val c = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
            finalPickupTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
            tvPickupTime.text = "Pick up at\n$finalPickupTime"
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }
}