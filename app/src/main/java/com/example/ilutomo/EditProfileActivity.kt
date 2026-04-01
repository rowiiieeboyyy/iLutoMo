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

        // --- 1. SETUP TOOLBAR & BACK BUTTON ---
        // This links the Toolbar from your XML to the Activity
        setSupportActionBar(binding.toolbar)

        // This forces the back arrow to appear
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Account Settings" // Sets the title in the bar
        }

        val userId = auth.currentUser?.uid

        // 2. Load current name from Firestore
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        binding.etProfileName.setText(document.getString("name"))
                    }
                }
        }

        // 3. Save Changes Logic
        binding.btnUpdateProfile.setOnClickListener {
            val newName = binding.etProfileName.text.toString().trim()
            if (newName.isNotEmpty() && userId != null) {
                db.collection("users").document(userId).update("name", newName)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Update failed.", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // 4. Logout Logic
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // --- 5. HANDLE BACK ARROW CLICK ---
    // This makes the arrow actually go back when clicked
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}