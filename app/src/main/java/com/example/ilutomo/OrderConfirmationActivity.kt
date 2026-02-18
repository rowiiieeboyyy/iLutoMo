package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class OrderConfirmationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_confirmation)

        // Top Left Back Arrow
        val btnBack = findViewById<ImageView>(R.id.order_back_arrow)
        btnBack?.setOnClickListener {
            returnToPantry()
        }

        // Cancel Order Button
        val btnCancel = findViewById<Button>(R.id.order_btn_cancel)
        btnCancel?.setOnClickListener {
            returnToPantry()
        }

        // View More Orders Button
        val btnViewMore = findViewById<Button>(R.id.order_btn_view_more)
        btnViewMore?.setOnClickListener {
            returnToPantry()
        }
    }

    private fun returnToPantry() {
        val intent = Intent(this, PantryActivity::class.java)
        // Clear the stack so this screen is removed
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}