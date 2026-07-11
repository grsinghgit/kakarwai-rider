package com.gr.kakarwairider.admin.model

data class RideFilterModel(
    val dateRange: String = "TODAY",  // TODAY, YESTERDAY, THIS_WEEK, CUSTOM
    val status: String = "ALL",        // ALL, PENDING, ASSIGNED, STARTED, COMPLETED, CANCELLED
    val vehicleType: String = "ALL",   // ALL, BIKE, CAR, SUV
    val paymentType: String = "ALL"    // ALL, CASH, ONLINE
)

sealed class DateRange {
    object TODAY : DateRange()
    object YESTERDAY : DateRange()
    object THIS_WEEK : DateRange()
    data class CUSTOM(val start: Long, val end: Long) : DateRange()
}

sealed class RideStatus {
    object ALL : RideStatus()
    object PENDING : RideStatus()
    object ASSIGNED : RideStatus()
    object STARTED : RideStatus()
    object COMPLETED : RideStatus()
    object CANCELLED : RideStatus()
}