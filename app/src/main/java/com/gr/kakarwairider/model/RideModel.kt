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

    // ✅ Distance & Duration
    val distance: Double = 0.0,
    val duration: Int = 0,

    // ============================================================
    // ✅ FARE BREAKDOWN - UPDATED
    // ============================================================

    // ✅ Base Price – From areas collection
    val basePrice: Double = 0.0,

    // ✅ Per Km Rate – From areas collection
    val perKmRate: Double = 0.0,

    // ✅ Distance based fare = Total Distance × Per Km Rate
    val distanceFare: Double = 0.0,

    // ✅ Final Total Fare = Base Price + Distance Fare
    val totalFare: Double = 0.0,

    // ============================================================
    // ✅ NEW FARE FIELDS – Distance Breakdown
    // ============================================================

    // ✅ Driver Current Location → Pickup (km)
    val pickupDistance: Double = 0.0,

    // ✅ Pickup → Destination (km)
    val tripDistance: Double = 0.0,

    // ✅ Total Distance = Pickup Distance + Trip Distance (km)
    val totalDistance: Double = 0.0,

    // ✅ Flag to check if fare is calculated
    val fareCalculated: Boolean = false,

    // ✅ Status Flow
    val status: String = "PENDING",  // PENDING, SEARCHING, DRIVER_ASSIGNED, ACCEPTED, ARRIVED_PICKUP, ON_THE_WAY, DESTINATION_REACHED, COMPLETED, CANCELLED

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
    // ✅ PIN Based Verification
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