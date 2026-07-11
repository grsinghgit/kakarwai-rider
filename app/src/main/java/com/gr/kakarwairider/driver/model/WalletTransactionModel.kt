package com.gr.kakarwairider.driver.model

import com.google.firebase.Timestamp

data class WalletTransactionModel(
    val transactionId: String = "",
    val driverId: String = "",
    val rideId: String? = null,
    val type: String = "",          // CREDIT, DEBIT, RECHARGE, FEE
    val amount: Double = 0.0,
    val description: String = "",
    val status: String = "SUCCESS", // SUCCESS, PENDING, FAILED
    val createdAt: Timestamp? = null
)