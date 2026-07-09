package com.gr.kakarwairider.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R

class DriverLocationService : Service() {

    private val TAG = "DriverLocationService"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val db = FirebaseFirestore.getInstance()
    private var driverId: String? = null
    private var isLocationUpdatesStarted = false
    private var isFirstLocationUpdate = true

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "driver_location_channel"
        private const val UPDATE_INTERVAL = 10000L
        private const val FASTEST_INTERVAL = 5000L
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "🔵 onCreate: Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("🔄 Initializing..."))
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        android.util.Log.d(TAG, "🔵 onCreate: FusedLocationClient initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "🟢 onStartCommand: Service started")

        val driverIdLocal = intent?.getStringExtra("driverId")
        android.util.Log.d(TAG, "📌 onStartCommand: driverId from intent = $driverIdLocal")

        if (driverIdLocal == null || driverIdLocal.isEmpty()) {
            android.util.Log.e(TAG, "❌ onStartCommand: driverId is null or empty! Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        driverId = driverIdLocal
        android.util.Log.d(TAG, "✅ onStartCommand: driverId set to $driverId")

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .build()
        android.util.Log.d(TAG, "📌 onStartCommand: LocationRequest built")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    android.util.Log.d(TAG, "📍 Location: ${location.latitude}, ${location.longitude}")
                    updateDriverLocation(location)
                } else {
                    android.util.Log.w(TAG, "⚠️ Location is null")
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                android.util.Log.d(TAG, "📡 Location availability: ${availability.isLocationAvailable}")
            }
        }

        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        android.util.Log.d(TAG, "🚀 startLocationUpdates: Starting...")

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e(TAG, "❌ Location permission NOT granted!")
            stopSelf()
            return
        }
        android.util.Log.d(TAG, "✅ Location permission granted")

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        android.util.Log.d(TAG, "📡 GPS enabled = $isGpsEnabled")

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isLocationUpdatesStarted = true
        android.util.Log.d(TAG, "✅ Location updates requested")
        updateNotification("🟢 Online - Tracking location...")
    }

    private fun updateDriverLocation(location: Location) {
        val id = driverId
        if (id == null) {
            android.util.Log.e(TAG, "❌ driverId is null! Aborting.")
            return
        }

        val lat = location.latitude
        val lng = location.longitude
        android.util.Log.d(TAG, "📍 Updating: $lat, $lng")

        val data = hashMapOf(
            "currentLocation" to com.google.firebase.firestore.GeoPoint(lat, lng),
            "updatedAt" to Timestamp.now(),
            "status" to "ONLINE",
            "isAvailable" to true
        )

        db.collection("driver_locations")
            .document(id)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if (isFirstLocationUpdate) {
                    android.util.Log.d(TAG, "✅✅✅ FIRST LOCATION SAVED! ✅✅✅")
                    isFirstLocationUpdate = false
                } else {
                    android.util.Log.d(TAG, "✅ Location updated")
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "❌ Failed: ${e.message}")
            }
    }

    private fun stopLocationUpdates() {
        android.util.Log.d(TAG, "🛑 Stopping location updates")
        try {
            if (::locationCallback.isInitialized && isLocationUpdatesStarted) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error: ${e.message}")
        }
        isLocationUpdatesStarted = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "This service keeps your location updated for rides"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Driver Online")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification(message))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Notification error: ${e.message}")
        }
    }

    override fun onDestroy() {
        android.util.Log.d(TAG, "🔴 onDestroy: Service destroying")
        stopLocationUpdates()

        val id = driverId
        if (id != null) {
            android.util.Log.d(TAG, "📤 Updating status to OFFLINE")
            db.collection("driver_locations")
                .document(id)
                .update(
                    mapOf(
                        "status" to "OFFLINE",
                        "isAvailable" to false,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    android.util.Log.d(TAG, "✅ Status updated to OFFLINE")
                }
        }
        super.onDestroy()
        android.util.Log.d(TAG, "🔴 onDestroy: Service destroyed")
    }
}