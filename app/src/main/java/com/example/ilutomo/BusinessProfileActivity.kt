package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityBusinessProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class BusinessProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val realtimeDb = FirebaseDatabase.getInstance().reference

    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        loadProfile()
        setupBottomNavigation()

        binding.btnEditProfile.setOnClickListener {
            toggleEditMode(true)
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun toggleEditMode(editing: Boolean) {
        isEditing = editing
        binding.etBusinessName.isEnabled = editing
        binding.etBusinessAddress.isEnabled = editing
        binding.etBusinessPhone.isEnabled = editing
        binding.etBusinessLat.isEnabled = editing
        binding.etBusinessLng.isEnabled = editing

        binding.btnEditProfile.visibility = if (editing) View.GONE else View.VISIBLE
        binding.btnSaveProfile.visibility = if (editing) View.VISIBLE else View.GONE
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return

        // We load from the permanent UID node now
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val bizName = document.getString("businessName") ?: ""
                    binding.etBusinessName.setText(bizName)

                    // Fetch details from RTDB using UID
                    realtimeDb.child("Businesses").child(uid).child("details")
                        .get().addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                binding.etBusinessAddress.setText(snapshot.child("address").value?.toString() ?: "")
                                binding.etBusinessPhone.setText(snapshot.child("phone").value?.toString() ?: "")
                                binding.etBusinessLat.setText(snapshot.child("latitude").value?.toString() ?: "")
                                binding.etBusinessLng.setText(snapshot.child("longitude").value?.toString() ?: "")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val uid = auth.currentUser?.uid ?: return
        val newName = binding.etBusinessName.text.toString().trim()
        val address = binding.etBusinessAddress.text.toString().trim()
        val phone = binding.etBusinessPhone.text.toString().trim()
        val lat = binding.etBusinessLat.text.toString().toDoubleOrNull() ?: 0.0
        val lng = binding.etBusinessLng.text.toString().toDoubleOrNull() ?: 0.0

        if (newName.isEmpty() || address.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        // We include the name inside the details object
        val details = mapOf(
            "businessName" to newName,
            "address" to address,
            "phone" to phone,
            "latitude" to lat,
            "longitude" to lng
        )

        // 1. Update Firestore
        firestore.collection("users").document(uid).set(mapOf("businessName" to newName), SetOptions.merge())
            .addOnSuccessListener {
                // 2. Update RTDB using the UID as the folder key.
                // This is what prevents duplicate business folders!
                realtimeDb.child("Businesses").child(uid).child("details").setValue(details)
                    .addOnSuccessListener {
                        toggleEditMode(false)
                        Toast.makeText(this, "Profile Updated Successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "RTDB Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_profile
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_business_profile) return@setOnItemSelectedListener true

            val intent = when (item.itemId) {
                R.id.nav_business_dashboard -> Intent(this, BusinessDashboardActivity::class.java)
                R.id.nav_business_inventory -> Intent(this, BusinessInventoryActivity::class.java)
                R.id.nav_business_orders -> Intent(this, BusinessOrdersActivity::class.java)
                else -> null
            }
            intent?.let {
                startActivity(it)
                finish()
            }
            true
        }
    }
}