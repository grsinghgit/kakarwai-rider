package com.gr.kakarwairider.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val db = FirebaseFirestore.getInstance()

    // ✅ Default values (if Firebase fetch fails)
    private var serviceCenter: LatLng? = null
    private var serviceRadiusMeters: Double = 50000.0 // 50km
    private var areaName: String = "Kakrawi"

    // ✅ Interface
    interface ServiceAreaCallback {
        fun onResult(isInServiceArea: Boolean, distance: Double, userLocation: LatLng?)
        fun onError(message: String)
    }

    /**
     * ✅ Fetch service area from Firebase and check user location
     */
    fun checkUserLocation(callback: ServiceAreaCallback) {
        // ✅ First, fetch service area from Firebase
        fetchServiceArea { success ->
            if (!success) {
                // ✅ Use default values if Firebase fetch fails
                Log.d(TAG, "⚠️ Using default service area: Kakrawi, 50km")
                serviceCenter = LatLng(25.6163, 79.1859)
                serviceRadiusMeters = 50000.0
                areaName = "Kakrawi (Default)"
            }

            // ✅ Now check user location
            checkLocation(callback)
        }
    }

    /**
     * ✅ Fetch service area from Firebase `areas` collection
     */
    private fun fetchServiceArea(callback: (Boolean) -> Unit) {
        Log.d(TAG, "📡 Fetching service area from Firebase...")

        db.collection("areas")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    Log.e(TAG, "❌ No active areas found")
                    callback(false)
                    return@addOnSuccessListener
                }

                val doc = documents.first()
                val center = doc.getGeoPoint("center")
                val radiusKm = doc.getDouble("radiusKm") ?: 50.0

                if (center == null) {
                    Log.e(TAG, "❌ No center found in area document")
                    callback(false)
                    return@addOnSuccessListener
                }

                // ✅ Firebase se values set karein
                serviceCenter = LatLng(center.latitude, center.longitude)
                serviceRadiusMeters = radiusKm * 1000 // Convert km to meters
                areaName = doc.getString("name") ?: "Service Area"

                Log.d(TAG, "✅ Service area loaded: $areaName")
                Log.d(TAG, "📍 Center: (${center.latitude}, ${center.longitude})")
                Log.d(TAG, "📍 Radius: ${radiusKm}km")

                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to fetch service area: ${e.message}")
                callback(false)
            }
    }

    /**
     * ✅ Check user location against service area
     */
    private fun checkLocation(callback: ServiceAreaCallback) {
        val center = serviceCenter
        if (center == null) {
            callback.onError("Service center not available")
            return
        }

        // ✅ Permission Check
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback.onError("Location permission not granted")
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    val distance = calculateDistance(center, userLatLng)

                    Log.d(TAG, "📍 User distance: ${distance / 1000}km from center")
                    Log.d(TAG, "📍 Service radius: ${serviceRadiusMeters / 1000}km")

                    if (distance <= serviceRadiusMeters) {
                        callback.onResult(true, distance, userLatLng)
                    } else {
                        callback.onResult(false, distance, userLatLng)
                    }
                } else {
                    callback.onError("Unable to get current location")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get location: ${e.message}")
                callback.onError("Failed to get location: ${e.message}")
            }
    }

    /**
     * ✅ Distance Calculation - Haversine Formula
     * Returns distance in meters
     */
    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val R = 6371.0 // Earth's radius in km

        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c * 1000 // Returns distance in meters
    }

    /**
     * ✅ Get service center (for debugging)
     */
    fun getServiceCenter(): LatLng? = serviceCenter

    /**
     * ✅ Get service radius in meters (for debugging)
     */
    fun getServiceRadius(): Double = serviceRadiusMeters

    /**
     * ✅ Get area name (for debugging)
     */
    fun getAreaName(): String = areaName
}