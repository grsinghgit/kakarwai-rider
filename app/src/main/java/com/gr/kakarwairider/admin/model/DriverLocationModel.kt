package com.gr.kakarwairider.admin.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.android.gms.maps.model.LatLng

data class DriverLocationModel(
    val driverId: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val currentLocation: GeoPoint? = null,
    val status: String = "OFFLINE",
    val isAvailable: Boolean = false,
    val updatedAt: Timestamp? = null,
    val rideId: String? = null,
    val distanceFromCenter: Double = 0.0
)

data class DriverWithDistance(
    val driver: DriverLocationModel,
    val distance: Double,
    val latLng: LatLng
)