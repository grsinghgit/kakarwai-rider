package com.gr.kakarwairider.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class DriverModel(
    @DocumentId
    val id: String = "",  // 🔥 IMPORTANT: Firestore document ID automatically map hota hai
    val name: String = "",
    val phone: String = "",
    val pin: String = "",
    val areaId: String = "",
    val adminId: String = "",
    val isActive: Boolean = true,  // ✅ DEFAULT TRUE - NULL HATAYA
    val isAvailable: Boolean = false,
    val vehicleType: String = "",
    val vehicleModel: String = "",
    val vehicleNumber: String = "",
    val walletBalance: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val totalRides: Int = 0,
    val rating: Double = 0.0,
    val isSpecial: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)