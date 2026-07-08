package com.gr.kakarwairider.model

data class VehicleOption(
    val type: String,      // bike, car, suv
    val icon: String,      // 🏍️, 🚗, 🚙
    val name: String,      // Bike, Car, SUV
    val basePrice: Double,
    val perKmRate: Double,
    val perMinRate: Double = 1.0
) {
    companion object {
        fun getDefaultVehicles(): List<VehicleOption> {
            return listOf(
                VehicleOption("bike", "🏍️", "Bike", 30.0, 8.0, 1.0),
                VehicleOption("car", "🚗", "Car", 50.0, 12.0, 2.0),
                VehicleOption("suv", "🚙", "SUV", 80.0, 18.0, 3.0)
            )
        }
    }
}