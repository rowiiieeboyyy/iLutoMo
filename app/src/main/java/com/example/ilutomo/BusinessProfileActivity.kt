package com.example.ilutomo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.ilutomo.databinding.ActivityBusinessProfileBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class BusinessProfileActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityBusinessProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val realtimeDb = FirebaseDatabase.getInstance().reference

    private var googleMap: GoogleMap? = null
    private var pinnedMarker: Marker? = null
    private var isEditing = false
    private var selectedLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

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
        
        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        googleMap?.setOnMapClickListener { latLng ->
            if (isEditing) {
                updatePinnedLocation(latLng)
            }
        }

        // Try to center on current location if possible
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    private fun updatePinnedLocation(latLng: LatLng) {
        selectedLatLng = latLng
        pinnedMarker?.remove()
        pinnedMarker = googleMap?.addMarker(MarkerOptions().position(latLng).title("Store Location"))
        googleMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        
        updateAddressFromLatLng(latLng)
    }

    private fun updateAddressFromLatLng(latLng: LatLng) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0].getAddressLine(0)
                binding.tvPinnedAddress.text = "Address: $address"
            }
        } catch (e: Exception) {
            binding.tvPinnedAddress.text = "Address: Lat: ${latLng.latitude}, Lng: ${latLng.longitude}"
        }
    }

    private fun toggleEditMode(editing: Boolean) {
        isEditing = editing
        binding.etBusinessName.isEnabled = editing
        binding.etBusinessPhone.isEnabled = editing
        binding.ivMapOverlay.visibility = if (editing) View.GONE else View.VISIBLE

        binding.btnEditProfile.visibility = if (editing) View.GONE else View.VISIBLE
        binding.btnSaveProfile.visibility = if (editing) View.VISIBLE else View.GONE
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val bizName = document.getString("businessName") ?: ""
                    binding.etBusinessName.setText(bizName)

                    realtimeDb.child("Businesses").child(uid).child("details")
                        .get().addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                binding.etBusinessPhone.setText(snapshot.child("phone").value?.toString() ?: "")
                                
                                val lat = snapshot.child("latitude").value?.toString()?.toDoubleOrNull()
                                val lng = snapshot.child("longitude").value?.toString()?.toDoubleOrNull()
                                val address = snapshot.child("address").value?.toString() ?: "Not pinned yet"
                                
                                binding.tvPinnedAddress.text = "Address: $address"
                                
                                if (lat != null && lng != null) {
                                    val savedPos = LatLng(lat, lng)
                                    selectedLatLng = savedPos
                                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPos, 15f))
                                    pinnedMarker?.remove()
                                    pinnedMarker = googleMap?.addMarker(MarkerOptions().position(savedPos).title("Store Location"))
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
        val phone = binding.etBusinessPhone.text.toString().trim()
        val address = binding.tvPinnedAddress.text.toString().removePrefix("Address: ").trim()

        if (newName.isEmpty() || phone.isEmpty() || selectedLatLng == null) {
            Toast.makeText(this, "All fields and map location are required", Toast.LENGTH_SHORT).show()
            return
        }

        val details = mapOf(
            "businessName" to newName,
            "address" to address,
            "phone" to phone,
            "latitude" to selectedLatLng!!.latitude,
            "longitude" to selectedLatLng!!.longitude
        )

        firestore.collection("users").document(uid).set(mapOf("businessName" to newName), SetOptions.merge())
            .addOnSuccessListener {
                realtimeDb.child("Businesses").child(uid).child("details").updateChildren(details)
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