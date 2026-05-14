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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var accountType: String? = null

    companion object {
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener {
                accountType = it.getString("accountType")
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(lr: LocationResult) {
                val location = lr.lastLocation ?: return
                updateLocations(location.latitude, location.longitude)
                Log.d("LocationService", "New Location: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iLutoMo Tracking Active")
            .setContentText("Your live location is shared for order coordination.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // IMPORTANT: Must call startForeground immediately if started via startForegroundService
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
            stopSelf()
            return START_NOT_STICKY
        }

        if (auth.currentUser == null) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .setMinUpdateDistanceMeters(2f)
            .build()

        try {
            locationCallback?.let {
                fusedLocationClient.requestLocationUpdates(req, it, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission denied: ${e.message}")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateLocations(lat: Double, lng: Double) {
        val userId = auth.currentUser?.uid ?: return

        val updates = hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "lastLocationUpdate" to FieldValue.serverTimestamp(),
            "isTrackingActive" to true
        )
        db.collection("users").document(userId).update(updates as Map<String, Any>)

        if (accountType == "Business") {
            val rtdbUpdates = mapOf(
                "latitude" to lat,
                "longitude" to lng
            )
            FirebaseDatabase.getInstance().reference.child("Businesses").child(userId).child("details").updateChildren(rtdbUpdates)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "Live Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).update("isTrackingActive", false)
        }
        super.onDestroy()
    }
}
