package com.gr.kakarwairider.model

import com.google.firebase.Timestamp

data class DriverModel(
    val driverId: String = "",
    val name: String = "",
    val phone: String = "",
    val pin: String = "",
    val areaId: String = "",
    val adminId: String = "",
    val isActive: Boolean = true,
    val isAvailable: Boolean = false,
    val vehicleType: String = "",
    val vehicleModel: String = "",
    val vehicleNumber: String = "",
    val walletBalance: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val totalRides: Int = 0,
    val rating: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)