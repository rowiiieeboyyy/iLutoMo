package com.example.ilutomo

import android.Manifest
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
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // 1. Permission Launcher handles both Location and Notifications for Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (fineGranted && notificationGranted) {
            startLiveTracking()
        } else {
            binding.switchLocationTracking.isChecked = false
            Toast.makeText(this, "Permissions (Location & Notifications) required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Listen for data changes to sync the UI fields
            db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    binding.etProfileName.setText(snapshot.getString("name"))

                    // Display coordinates (rounded for clean UI)
                    val lat = snapshot.get("latitude") ?: 0.0
                    val lng = snapshot.get("longitude") ?: 0.0
                    binding.etLat.setText(lat.toString())
                    binding.etLng.setText(lng.toString())

                    // Sync switch state from database
                    val isTracking = snapshot.getBoolean("isTrackingEnabled") ?: false
                    if (binding.switchLocationTracking.isChecked != isTracking) {
                        binding.switchLocationTracking.isChecked = isTracking
                    }
                }
            }
        }

        // 2. Toggle Tracking logic
        binding.switchLocationTracking.setOnClickListener {
            if (binding.switchLocationTracking.isChecked) {
                checkPermissionsAndStart()
            } else {
                stopLiveTracking()
            }
        }

        // 3. Save Profile and Tracking state
        binding.btnUpdateProfile.setOnClickListener {
            val updates = mutableMapOf<String, Any>(
                "name" to binding.etProfileName.text.toString(),
                "isTrackingEnabled" to binding.switchLocationTracking.isChecked
            )

            userId?.let { id ->
                db.collection("users").document(id).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        binding.btnLogout.setOnClickListener {
            stopLiveTracking() // Ensure GPS stops on logout
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        // Android 13 (Tiramisu) requires explicit notification permission for Foreground Services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startLiveTracking()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startLiveTracking() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLiveTracking() {
        val intent = Intent(this, LocationService::class.java)
        stopService(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}