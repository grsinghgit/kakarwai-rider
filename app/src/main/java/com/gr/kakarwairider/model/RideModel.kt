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
    val status: String = "PENDING",  // PENDING, SEARCHING, DRIVER_ASSIGNED, ACCEPTED, ARRIVED_PICKUP, ON_THE_WAY, COMPLETED, CANCELLED

    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverVehicle: String? = null,
    val driverVehicleNumber: String? = null,

    val areaId: String = "",
    val adminId: String = "",

    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",
    val cancelReason: String? = null,
    val cancelledBy: String? = null,

    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,

    // ✅ NEW – Ride completion timestamp
    val completedAt: Timestamp? = null,

    // ============================================================
    // ✅ NEW FIELDS – PIN Based Verification
    // ============================================================

    // ✅ Pickup PIN – Driver side generate karega, user ko dikhega
    val pickupPin: String? = null,           // 4 digit random PIN

    // ✅ Pickup Time – Driver "ARRIVED_PICKUP" click karega tab save hoga
    val pickupTime: Timestamp? = null,

    // ✅ Complete PIN – Destination par generate hoga
    val completePin: String? = null,         // 4 digit random PIN

    // ✅ Complete Time – Ride complete hone par save hoga
    val completeTime: Timestamp? = null
)

data class LocationData(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)