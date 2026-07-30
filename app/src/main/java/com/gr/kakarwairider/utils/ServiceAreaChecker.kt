package com.gr.kakarwairider.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ServiceAreaChecker(private val context: Context) {

    companion object {
        private const val TAG = "ServiceAreaChecker"
        private const val LOCATION_TIMEOUT = 8000L // ✅ 8 seconds (reduced from 15s)
        private const val RETRY_DELAY = 1000L // ✅ 1 second (reduced from 2s)
        private const val MAX_RETRIES = 2 // ✅ Reduced from 3 to 2
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val db = FirebaseFirestore.getInstance()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var serviceCenter: LatLng? = null
    private var serviceRadiusMeters: Double = 50000.0
    private var areaName: String = "Kakrawi"
    private var isLocationFetching = false
    private var locationCallback: LocationCallback? = null
    private var retryCount = 0

    interface ServiceAreaCallback {
        fun onResult(isInServiceArea: Boolean, distance: Double, userLocation: LatLng?)
        fun onError(message: String)
    }

    fun checkUserLocation(callback: ServiceAreaCallback) {
        fetchServiceArea { success ->
            if (!success) {
                Log.d(TAG, "⚠️ Using default service area")
                serviceCenter = LatLng(25.6163, 79.1859)
                serviceRadiusMeters = 50000.0
                areaName = "Kakrawi (Default)"
            }
            retryCount = 0
            getCurrentLocation(callback)
        }
    }

    private fun fetchServiceArea(callback: (Boolean) -> Unit) {
        Log.d(TAG, "📡 Fetching service area...")

        db.collection("areas")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    callback(false)
                    return@addOnSuccessListener
                }

                val doc = documents.first()
                val center = doc.getGeoPoint("center")
                val radiusKm = doc.getDouble("radiusKm") ?: 50.0

                if (center == null) {
                    callback(false)
                    return@addOnSuccessListener
                }

                serviceCenter = LatLng(center.latitude, center.longitude)
                serviceRadiusMeters = radiusKm * 1000
                areaName = doc.getString("name") ?: "Service Area"

                Log.d(TAG, "✅ Service area loaded: $areaName")
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun getCurrentLocation(callback: ServiceAreaCallback) {
        if (isLocationFetching) {
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback.onError("Location permission not granted")
            return
        }

        val center = serviceCenter ?: run {
            callback.onError("Service center not available")
            return
        }

        isLocationFetching = true

        // ✅ Try last location first (fast)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    isLocationFetching = false
                    processLocation(location, center, callback)
                } else {
                    requestNewLocation(center, callback)
                }
            }
            .addOnFailureListener {
                requestNewLocation(center, callback)
            }
    }

    private fun requestNewLocation(center: LatLng, callback: ServiceAreaCallback) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            isLocationFetching = false
            callback.onError("Location permission not granted")
            return
        }

        // ✅ Faster location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L // ✅ 3 seconds (reduced from 5s)
        )
            .setMinUpdateIntervalMillis(1000L) // ✅ 1 second
            .setMaxUpdateDelayMillis(5000L) // ✅ 5 seconds
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    isLocationFetching = false
                    locationCallback?.let {
                        fusedLocationClient.removeLocationUpdates(it)
                    }
                    locationCallback = null
                    retryCount = 0
                    processLocation(location, center, callback)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )

        // ✅ Timeout - faster
        handler.postDelayed({
            if (isLocationFetching) {
                isLocationFetching = false
                locationCallback?.let {
                    fusedLocationClient.removeLocationUpdates(it)
                }
                locationCallback = null

                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    handler.postDelayed({
                        getCurrentLocation(callback)
                    }, RETRY_DELAY)
                } else {
                    callback.onError("Unable to get current location. Please try again.")
                }
            }
        }, LOCATION_TIMEOUT)
    }

    private fun processLocation(location: Location, center: LatLng, callback: ServiceAreaCallback) {
        val userLatLng = LatLng(location.latitude, location.longitude)
        val distance = calculateDistance(center, userLatLng)

        Log.d(TAG, "📍 Location: ${location.latitude}, ${location.longitude}")

        if (distance <= serviceRadiusMeters) {
            callback.onResult(true, distance, userLatLng)
        } else {
            callback.onResult(false, distance, userLatLng)
        }
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val R = 6371.0
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lon2 = Math.toRadians(point2.longitude)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c * 1000
    }

    fun getServiceCenter(): LatLng? = serviceCenter
    fun getServiceRadius(): Double = serviceRadiusMeters
    fun getAreaName(): String = areaName
}