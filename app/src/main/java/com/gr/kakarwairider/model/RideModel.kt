package com.gr.kakarwairider.model

import com.google.firebase.Timestamp

data class RideModel(
    val rideId: String = "",
    val userId: String = "",
    val userPhone: String = "",
    val userName: String = "",

    val pickup: LocationData? = null,
    val destination: LocationData? = null,

    // ✅ Vehicle Selection Fields
    val vehicleType: String = "car",
    val vehicleIcon: String = "🚗",
    val vehicleName: String = "Car",

    val distance: Double = 0.0,
    val duration: Int = 0,

    // ✅ Fare Breakdown
    val basePrice: Double = 0.0,
    val perKmRate: Double = 0.0,
    val distanceFare: Double = 0.0,
    val totalFare: Double = 0.0,

    // ✅ Status Flow
    val status: String = "PENDING",  // PENDING, SEARCHING, DRIVER_ASSIGNED, STARTED, COMPLETED, CANCELLED

    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverVehicle: String? = null,
    val driverVehicleNumber: String? = null,

    val areaId: String = "",
    val adminId: String = "",

    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",
    val cancelReason: String? = null,  // ✅ Add this
    val cancelledBy: String? = null,   // ✅ Add this (admin/user)

    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
)

data class LocationData(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)