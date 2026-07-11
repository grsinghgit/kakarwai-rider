package com.gr.kakarwairider.admin.model

data class RideStatsModel(
    val totalRides: Int = 0,
    val pendingRides: Int = 0,
    val activeRides: Int = 0,
    val completedRides: Int = 0,
    val cancelledRides: Int = 0,
    val todayEarnings: Double = 0.0,
    val totalEarnings: Double = 0.0
)