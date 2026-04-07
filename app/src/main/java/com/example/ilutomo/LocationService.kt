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

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(lr: LocationResult) {
                val location = lr.lastLocation ?: return
                updateFirestore(location.latitude, location.longitude)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()

        val notification = NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("iLutoMo Tracking Active")
            .setContentText("Updating your location for active orders...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Required for Android 14 (API 34) and above: specify service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }

        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        // High accuracy every 10 seconds, faster if another app is already requesting it
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission denied: ${e.message}")
            stopSelf()
        }
    }

    private fun updateFirestore(lat: Double, lng: Double) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val updates = mapOf(
                "latitude" to lat,
                "longitude" to lng,
                "lastLocationUpdate" to com.google.firebase.Timestamp.now()
            )
            db.collection("users").document(userId).update(updates)
                .addOnFailureListener { e ->
                    Log.e("LocationService", "Firestore update failed: ${e.message}")
                }
        } else {
            // If user logged out but service is still running, stop it
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                "location_channel",
                "Order Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        // Essential to stop tracking when service is destroyed to save battery
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}