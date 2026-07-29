package com.gr.kakarwairider.model

data class VehicleOption(
    val type: String,      // bike, car, suv, auto
    val icon: String,      // 🏍️, 🚗, 🚙, 🛺
    val name: String,      // Bike, Car, SUV, Auto
    val basePrice: Double,
    val perKmRate: Double,
    val perMinRate: Double = 1.0
)