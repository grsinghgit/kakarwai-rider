package com.gr.kakarwairider.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ServiceAreaChecker(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // ✅ Default Service Center (Delhi - Change karein)
    private val SERVICE_CENTER = LatLng(25.6163, 79.1859)
    private val SERVICE_RADIUS = 50000.0 // 50km in meters

    // ✅ Interface
    interface ServiceAreaCallback {
        fun onResult(isInServiceArea: Boolean, distance: Double, userLocation: LatLng?)
        fun onError(message: String)
    }

    fun checkUserLocation(callback: ServiceAreaCallback) {
        // ✅ Permission Check
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback.onError("Location permission not granted")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                val distance = calculateDistance(SERVICE_CENTER, userLatLng)

                if (distance <= SERVICE_RADIUS) {
                    callback.onResult(true, distance, userLatLng)
                } else {
                    callback.onResult(false, distance, userLatLng)
                }
            } else {
                callback.onError("Unable to get current location")
            }
        }.addOnFailureListener {
            callback.onError("Failed to get location: ${it.message}")
        }
    }

    // ✅ Distance Calculation - Haversine Formula
    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val R = 6371.0 // Earth's radius in km

        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dlat = lat2 - lat1
        val dlon = lon2 - lon1

        val a = sin(dlat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) *
                sin(dlon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c * 1000 // Returns distance in meters
    }
}