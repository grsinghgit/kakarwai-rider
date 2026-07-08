package com.gr.kakarwairider.model

import com.google.firebase.Timestamp

data class RideModel(
    val rideId: String = "",
    val userId: String = "",
    val userPhone: String = "",
    val userName: String = "",

    val pickup: LocationData? = null,
    val destination: LocationData? = null,

    val rideType: String = "",
    val distance: Double = 0.0,
    val duration: Int = 0,
    val fare: Double = 0.0,

    val status: String = "PENDING",
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverVehicle: String? = null,
    val driverVehicleNumber: String? = null,

    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",

    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
)

data class LocationData(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)