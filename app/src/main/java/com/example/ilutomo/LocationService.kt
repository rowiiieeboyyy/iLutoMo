package com.example.ilutomo

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(lr: LocationResult) {
                val location = lr.lastLocation ?: return
                updateFirestore(location.latitude, location.longitude)
                Log.d("LocationService", "New Location: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure user is still logged in before starting
        if (auth.currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iLutoMo Tracking Active")
            .setContentText("Your live location is shared with the seller.")
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Handle Android 14+ Foreground Service types (MUST match Manifest)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Failed to start foreground service: ${e.message}")
        }

        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10 seconds
            .setMinUpdateIntervalMillis(5000) // 5 seconds
            .setMinUpdateDistanceMeters(2f)   // Reduced sensitivity to save battery (2 meters)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission denied: ${e.message}")
            stopSelf()
        }
    }

    private fun updateFirestore(lat: Double, lng: Double) {
        val userId = auth.currentUser?.uid ?: return

        val updates = hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "lastLocationUpdate" to FieldValue.serverTimestamp(),
            "isTrackingActive" to true
        )

        // Using "users" collection as per your logic
        db.collection("users").document(userId).update(updates as Map<String, Any>)
            .addOnFailureListener { e ->
                Log.e("LocationService", "Firestore update failed: ${e.message}")
            }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Live Order Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for showing live location tracking status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        // CLEANUP: Important to stop location requests to save battery
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Optional: Mark tracking as inactive in Firestore when service is stopped
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).update("isTrackingActive", false)
        }

        super.onDestroy()
        Log.d("LocationService", "Service Destroyed and Updates Stopped")
    }
}