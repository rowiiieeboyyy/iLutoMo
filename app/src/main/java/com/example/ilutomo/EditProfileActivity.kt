package com.example.ilutomo

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ilutomo.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.HashMap

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance().reference
    private var snapshotListener: ListenerRegistration? = null
    private lateinit var progressDialog: ProgressDialog

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (fineGranted && notificationGranted) {
            updateTrackingState(true)
        } else {
            binding.switchLocationTracking.isChecked = false
            Toast.makeText(this, "Permissions required for live tracking.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Progress Dialog
        progressDialog = ProgressDialog(this).apply {
            setMessage("Updating professional profile...")
            setCancelable(false)
        }

        // FIXED: Toolbar Back Navigation
        // Instead of looking for a separate btnBack, we use the Toolbar's built-in navigation
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadUserData()

        // Toggle Tracking logic
        binding.switchLocationTracking.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndStart()
            } else {
                updateTrackingState(false)
            }
        }

        // Save Profile logic - Matches the ID in your XML (btnUpdateProfile)
        binding.btnUpdateProfile.setOnClickListener {
            saveProfileChanges()
        }

        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        // 1. Load Business Details from Realtime Database
        realtimeDb.child("Businesses").child(userId).child("details")
            .get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // etProfileName in XML is used for the Store Name
                    binding.etProfileName.setText(snapshot.child("businessName").value?.toString())
                    binding.etPhone.setText(snapshot.child("phone").value?.toString())
                }
            }

        // 2. Load Location Info from Firestore (Live Listener)
        snapshotListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // Update read-only coordinates
                val lat = snapshot.getDouble("latitude") ?: 0.0
                val lng = snapshot.getDouble("longitude") ?: 0.0
                binding.etLat.setText(String.format("%.6f", lat))
                binding.etLng.setText(String.format("%.6f", lng))

                // Sync tracking switch state
                val isEnabled = snapshot.getBoolean("isTrackingEnabled") ?: false
                if (binding.switchLocationTracking.isChecked != isEnabled) {
                    binding.switchLocationTracking.isChecked = isEnabled
                }
            }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            updateTrackingState(true)
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun updateTrackingState(shouldStart: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val serviceIntent = Intent(this, LocationService::class.java)

        firestore.collection("users").document(userId).update("isTrackingEnabled", shouldStart)

        if (shouldStart) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
    }

    private fun saveProfileChanges() {
        val userId = auth.currentUser?.uid ?: return
        val newName = binding.etProfileName.text.toString().trim()
        val newPhone = binding.etPhone.text.toString().trim()

        if (newName.isEmpty()) {
            binding.etProfileName.error = "Store name is required"
            return
        }

        progressDialog.show()

        // Update Realtime Database
        val rtUpdates = HashMap<String, Any?>()
        rtUpdates["Businesses/$userId/details/businessName"] = newName
        rtUpdates["Businesses/$userId/details/phone"] = newPhone

        // Update Firestore for general user consistency
        val fsUpdates = hashMapOf<String, Any>("name" to newName)

        realtimeDb.updateChildren(rtUpdates).addOnSuccessListener {
            firestore.collection("users").document(userId).update(fsUpdates)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }.addOnFailureListener {
            progressDialog.dismiss()
            Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performLogout() {
        updateTrackingState(false)
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }
}