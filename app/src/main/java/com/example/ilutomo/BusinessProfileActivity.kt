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

class BusinessProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBusinessProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val realtimeDb = FirebaseDatabase.getInstance().reference
    
    private var isEditing = false
    private var currentBusinessName: String? = null

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
        
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val bizName = document.getString("businessName")
                    currentBusinessName = bizName
                    
                    if (!bizName.isNullOrEmpty()) {
                        realtimeDb.child("Businesses").child(bizName).child("details")
                            .get().addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    binding.etBusinessName.setText(bizName)
                                    binding.etBusinessAddress.setText(snapshot.child("address").value?.toString() ?: "")
                                    binding.etBusinessPhone.setText(snapshot.child("phone").value?.toString() ?: "")
                                    binding.etBusinessLat.setText(snapshot.child("latitude").value?.toString() ?: "")
                                    binding.etBusinessLng.setText(snapshot.child("longitude").value?.toString() ?: "")
                                } else {
                                    binding.etBusinessName.setText(bizName)
                                }
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

        val details = mapOf(
            "address" to address,
            "phone" to phone,
            "latitude" to lat,
            "longitude" to lng
        )

        firestore.collection("users").document(uid).update("businessName", newName)
            .addOnSuccessListener {
                realtimeDb.child("Businesses").child(newName).child("details").setValue(details)
                    .addOnSuccessListener {
                        currentBusinessName = newName
                        toggleEditMode(false)
                        Toast.makeText(this, "Profile Saved", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "RTDB Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                firestore.collection("users").document(uid).set(mapOf("businessName" to newName), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        realtimeDb.child("Businesses").child(newName).child("details").setValue(details)
                            .addOnSuccessListener {
                                currentBusinessName = newName
                                toggleEditMode(false)
                                Toast.makeText(this, "Profile Saved", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
    }

    private fun setupBottomNavigation() {
        binding.businessBottomNav.selectedItemId = R.id.nav_business_profile
        binding.businessBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_business_dashboard -> {
                    startActivity(Intent(this, BusinessDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_inventory -> {
                    startActivity(Intent(this, BusinessInventoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_orders -> {
                    startActivity(Intent(this, BusinessOrdersActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_manage -> {
                    startActivity(Intent(this, BusinessManageActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_business_profile -> true
                else -> false
            }
        }
    }
}