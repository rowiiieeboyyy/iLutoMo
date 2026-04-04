package com.example.ilutomo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ilutomo.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Account Settings"
        }

        val userId = auth.currentUser?.uid

        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        binding.etProfileName.setText(document.getString("name"))
                        binding.etLat.setText(document.get("latitude")?.toString() ?: "")
                        binding.etLng.setText(document.get("longitude")?.toString() ?: "")
                    }
                }
        }

        binding.btnUpdateProfile.setOnClickListener {
            val newName = binding.etProfileName.text.toString().trim()
            val lat = binding.etLat.text.toString().toDoubleOrNull() ?: 0.0
            val lng = binding.etLng.text.toString().toDoubleOrNull() ?: 0.0

            if (newName.isNotEmpty() && userId != null) {
                val updates = mapOf(
                    "name" to newName,
                    "latitude" to lat,
                    "longitude" to lng
                )
                db.collection("users").document(userId).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Update failed.", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}