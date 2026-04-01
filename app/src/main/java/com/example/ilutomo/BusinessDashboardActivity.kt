package com.example.ilutomo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// These are the imports that were missing:
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BusinessDashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_dashboard)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val tvBusinessEmail = findViewById<TextView>(R.id.tvBusinessEmail)
        val ivProfile = findViewById<ImageView>(R.id.ivBusinessProfile)

        // 1. Display the current user's email
        val currentUser = auth.currentUser
        if (currentUser != null) {
            tvBusinessEmail.text = currentUser.email
        } else {
            // If no user is logged in, send them back to Login
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 2. Logout logic (Triggered by clicking the profile image)
        ivProfile.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        // Sign out from Firebase
        auth.signOut()

        // Clear "Remember Me" status in SharedPreferences
        val prefs = getSharedPreferences("iLutoMoPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isRemembered", false).apply()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        // Redirect to Login (MainActivity) and clear the activity stack
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}