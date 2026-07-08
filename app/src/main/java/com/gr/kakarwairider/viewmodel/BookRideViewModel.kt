package com.gr.kakarwairider.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.model.LocationData
import com.gr.kakarwairider.model.RideModel
import java.util.*

class BookRideViewModel : ViewModel() {

    private val TAG = "BookRideViewModel"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _rideCreated = MutableLiveData<RideModel?>(null)
    val rideCreated: LiveData<RideModel?> = _rideCreated

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // ============================================================
    // ✅ BOOK RIDE – Complete Function
    // ============================================================

    fun bookRide(
        pickupAddress: String,
        pickupLat: Double,
        pickupLng: Double,
        destinationAddress: String,
        destinationLat: Double,
        destinationLng: Double,
        vehicleType: String,
        vehicleIcon: String,
        vehicleName: String,
        distance: Double,
        duration: Int,
        basePrice: Double,
        perKmRate: Double,
        totalFare: Double
    ) {
        Log.d(TAG, "========== BOOK RIDE STARTED ==========")
        Log.d(TAG, "Vehicle: $vehicleName ($vehicleType)")

        _isLoading.value = true

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "User not logged in"
            _isLoading.value = false
            return
        }

        Log.d(TAG, "User logged in: ${currentUser.uid}")

        // Find area based on user location
        findNearestArea(pickupLat, pickupLng) { areaId, adminId ->
            if (areaId == null) {
                Log.e(TAG, "No area found")
                _errorMessage.value = "Service not available in your area"
                _isLoading.value = false
                return@findNearestArea
            }

            Log.d(TAG, "Area found: $areaId")

            createRide(
                rideId = db.collection("rides").document().id,
                userId = currentUser.uid,
                userPhone = currentUser.phoneNumber ?: "",
                pickupAddress = pickupAddress,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                destinationAddress = destinationAddress,
                destinationLat = destinationLat,
                destinationLng = destinationLng,
                vehicleType = vehicleType,
                vehicleIcon = vehicleIcon,
                vehicleName = vehicleName,
                distance = distance,
                duration = duration,
                basePrice = basePrice,
                perKmRate = perKmRate,
                totalFare = totalFare,
                areaId = areaId,
                adminId = adminId
            )
        }
    }

    // ============================================================
    // ✅ FIND NEAREST AREA
    // ============================================================

    private fun findNearestArea(
        userLat: Double,
        userLng: Double,
        callback: (areaId: String?, adminId: String?) -> Unit
    ) {
        db.collection("areas")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    Log.d(TAG, "No active areas found")
                    callback(null, null)
                    return@addOnSuccessListener
                }

                var nearestAreaId: String? = null
                var nearestAdminId: String? = null
                var minDistance = Double.MAX_VALUE

                for (document in documents) {
                    val center = document.getGeoPoint("center")
                    val radiusKm = document.getDouble("radiusKm") ?: 50.0
                    val areaId = document.id
                    val adminId = document.getString("adminId")

                    if (center != null) {
                        val distance = calculateDistance(
                            userLat, userLng,
                            center.latitude, center.longitude
                        )

                        if (distance <= radiusKm && distance < minDistance) {
                            minDistance = distance
                            nearestAreaId = areaId
                            nearestAdminId = adminId
                        }
                    }
                }

                if (nearestAreaId != null) {
                    callback(nearestAreaId, nearestAdminId)
                } else {
                    callback(null, null)
                }
            }
            .addOnFailureListener {
                callback(null, null)
            }
    }

    // ============================================================
    // ✅ CREATE RIDE IN FIRESTORE
    // ============================================================

    private fun createRide(
        rideId: String,
        userId: String,
        userPhone: String,
        pickupAddress: String,
        pickupLat: Double,
        pickupLng: Double,
        destinationAddress: String,
        destinationLat: Double,
        destinationLng: Double,
        vehicleType: String,
        vehicleIcon: String,
        vehicleName: String,
        distance: Double,
        duration: Int,
        basePrice: Double,
        perKmRate: Double,
        totalFare: Double,
        areaId: String,
        adminId: String?
    ) {
        Log.d(TAG, "Creating ride...")

        val currentTime = Timestamp.now()
        val expireTime = Timestamp(Date(System.currentTimeMillis() + (5 * 60 * 1000)))

        val rideData = hashMapOf(
            "rideId" to rideId,
            "userId" to userId,
            "userPhone" to userPhone,
            "userName" to "",
            "pickup" to hashMapOf(
                "address" to pickupAddress,
                "lat" to pickupLat,
                "lng" to pickupLng
            ),
            "destination" to hashMapOf(
                "address" to destinationAddress,
                "lat" to destinationLat,
                "lng" to destinationLng
            ),
            "vehicleType" to vehicleType,
            "vehicleIcon" to vehicleIcon,
            "vehicleName" to vehicleName,
            "distance" to distance,
            "duration" to duration,
            "basePrice" to basePrice,
            "perKmRate" to perKmRate,
            "distanceFare" to (distance * perKmRate),
            "totalFare" to totalFare,
            "areaId" to areaId,
            "adminId" to (adminId ?: ""),
            "status" to "PENDING",
            "driverId" to null,
            "driverName" to null,
            "driverPhone" to null,
            "driverVehicle" to null,
            "driverVehicleNumber" to null,
            "paymentMethod" to "CASH",
            "paymentStatus" to "PENDING",
            "createdAt" to currentTime,
            "updatedAt" to currentTime,
            "expiresAt" to expireTime
        )

        db.collection("rides")
            .document(rideId)
            .set(rideData)
            .addOnSuccessListener {
                Log.d(TAG, "Ride created successfully!")
                _isLoading.value = false

                val ride = RideModel(
                    rideId = rideId,
                    userId = userId,
                    userPhone = userPhone,
                    userName = "",
                    pickup = LocationData(pickupAddress, pickupLat, pickupLng),
                    destination = LocationData(destinationAddress, destinationLat, destinationLng),
                    vehicleType = vehicleType,
                    vehicleIcon = vehicleIcon,
                    vehicleName = vehicleName,
                    distance = distance,
                    duration = duration,
                    basePrice = basePrice,
                    perKmRate = perKmRate,
                    distanceFare = distance * perKmRate,
                    totalFare = totalFare,
                    status = "PENDING",
                    driverId = null,
                    driverName = null,
                    driverPhone = null,
                    driverVehicle = null,
                    driverVehicleNumber = null,
                    areaId = areaId,
                    adminId = adminId ?: "",
                    paymentMethod = "CASH",
                    paymentStatus = "PENDING",
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    expiresAt = expireTime
                )
                _rideCreated.value = ride
                _errorMessage.value = null
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed: ${e.message}")
                _isLoading.value = false
                _errorMessage.value = "Failed to book ride: ${e.message}"
            }
    }

    // ============================================================
    // ✅ DISTANCE CALCULATION
    // ============================================================

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    fun clearError() {
        _errorMessage.value = null
    }
}