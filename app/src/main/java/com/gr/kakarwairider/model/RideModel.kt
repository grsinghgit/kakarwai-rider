package com.gr.kakarwairider.model

import com.google.firebase.Timestamp

data class RideModel(
    val rideId: String = "",
    val userId: String = "",
    val userPhone: String = "",
    val userName: String = "",

    // ✅ SERVICE TYPE
    val serviceType: String = "GOODS",  // "GOODS" or "RIDE"

    // ✅ Location Data (For both services)
    val pickup: LocationData? = null,
    val destination: LocationData? = null,

    // ✅ For GOODS only (Extra fields)
    val destinationPhone: String = "",
    val goodsType: String = "",
    val goodsConsent: Boolean = false,

    // ✅ Vehicle Selection
    val vehicleType: String = "bike",
    val vehicleIcon: String = "🏍️",
    val vehicleName: String = "Bike",

    // ✅ Distance & Fare
    val distance: Double = 0.0,
    val duration: Int = 0,
    val basePrice: Double = 0.0,
    val perKmRate: Double = 0.0,
    val distanceFare: Double = 0.0,
    val totalFare: Double = 0.0,
    val pickupDistance: Double = 0.0,
    val tripDistance: Double = 0.0,
    val totalDistance: Double = 0.0,
    val fareCalculated: Boolean = false,

    // ✅ Status
    val status: String = "PENDING",

    // ✅ Driver Details
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverVehicle: String? = null,
    val driverVehicleNumber: String? = null,

    // ✅ Area
    val areaId: String = "",
    val adminId: String = "",

    // ✅ Payment
    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",

    // ✅ Cancel
    val cancelReason: String? = null,
    val cancelledBy: String? = null,

    // ✅ Timestamps
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val completedAt: Timestamp? = null,

    // ✅ PIN Verification
    val pickupPin: String? = null,
    val pickupTime: Timestamp? = null,
    val completePin: String? = null,
    val completeTime: Timestamp? = null
)

data class LocationData(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)