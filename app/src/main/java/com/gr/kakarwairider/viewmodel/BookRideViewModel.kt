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
    // ✅ BOOK RIDE – With Area Detection
    // ============================================================

    fun bookRide(
        pickupAddress: String,
        pickupLat: Double,
        pickupLng: Double,
        destinationAddress: String,
        destinationLat: Double,
        destinationLng: Double,
        rideType: String,
        distance: Double,
        duration: Int,
        fare: Double
    ) {
        Log.d(TAG, "========== BOOK RIDE STARTED ==========")
        Log.d(TAG, "Pickup: $pickupAddress ($pickupLat, $pickupLng)")
        Log.d(TAG, "Destination: $destinationAddress ($destinationLat, $destinationLng)")
        Log.d(TAG, "RideType: $rideType, Distance: $distance km, Duration: $duration min, Fare: $fare")

        _isLoading.value = true

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "❌ User not logged in")
            _errorMessage.value = "User not logged in"
            _isLoading.value = false
            return
        }

        Log.d(TAG, "✅ User logged in: ${currentUser.uid}, Phone: ${currentUser.phoneNumber}")

        // Find area based on user location
        findNearestArea(pickupLat, pickupLng) { areaId, adminId ->
            if (areaId == null) {
                Log.e(TAG, "❌ No area found for location: ($pickupLat, $pickupLng)")
                _errorMessage.value = "Service not available in your area"
                _isLoading.value = false
                return@findNearestArea
            }

            Log.d(TAG, "✅ Area found: $areaId, AdminId: $adminId")

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
                rideType = rideType,
                distance = distance,
                duration = duration,
                fare = fare,
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
        Log.d(TAG, "🔍 Finding nearest area for user location: ($userLat, $userLng)")

        db.collection("areas")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "📄 Areas found with filter (isActive=true): ${documents.size()}")

                if (documents.isEmpty()) {
                    // ✅ Check without filter to debug
                    db.collection("areas")
                        .get()
                        .addOnSuccessListener { allDocs ->
                            Log.d(TAG, "📄 Total areas (without filter): ${allDocs.size()}")
                            for (doc in allDocs) {
                                val isActive = doc.getBoolean("isActive")
                                Log.d(TAG, "   Document: ${doc.id}, isActive: $isActive")
                            }
                        }
                    Log.e(TAG, "❌ No active areas found")
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
                    val areaName = document.getString("areaName")

                    Log.d(TAG, "📍 Checking area: $areaName ($areaId)")
                    Log.d(TAG, "   Center: $center, Radius: $radiusKm km")

                    if (center != null) {
                        val distance = calculateDistance(
                            userLat, userLng,
                            center.latitude, center.longitude
                        )

                        Log.d(TAG, "   Distance from user: %.2f km".format(distance))

                        if (distance <= radiusKm && distance < minDistance) {
                            minDistance = distance
                            nearestAreaId = areaId
                            nearestAdminId = adminId
                            Log.d(TAG, "   ✅ Area matches! Distance: %.2f km".format(distance))
                        } else {
                            Log.d(TAG, "   ❌ Area not in range (Distance: %.2f km > Radius: %.2f km)".format(distance, radiusKm))
                        }
                    } else {
                        Log.e(TAG, "   ❌ Center is null for document: ${document.id}")
                    }
                }

                if (nearestAreaId != null) {
                    Log.d(TAG, "✅ Final area selected: $nearestAreaId (Distance: %.2f km)".format(minDistance))
                    callback(nearestAreaId, nearestAdminId)
                } else {
                    Log.e(TAG, "❌ No area found within radius for user location: ($userLat, $userLng)")
                    callback(null, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error fetching areas: ${e.message}", e)
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
        rideType: String,
        distance: Double,
        duration: Int,
        fare: Double,
        areaId: String,
        adminId: String?
    ) {
        Log.d(TAG, "📝 Creating ride...")
        Log.d(TAG, "   RideId: $rideId")
        Log.d(TAG, "   AreaId: $areaId")
        Log.d(TAG, "   AdminId: $adminId")

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
            "rideType" to rideType,
            "distance" to distance,
            "duration" to duration,
            "fare" to fare,
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

        Log.d(TAG, "📤 Saving ride to Firestore...")

        db.collection("rides")
            .document(rideId)
            .set(rideData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Ride created successfully! RideId: $rideId")
                _isLoading.value = false
                val ride = RideModel(
                    rideId = rideId,
                    userId = userId,
                    userPhone = userPhone,
                    userName = "",
                    pickup = LocationData(pickupAddress, pickupLat, pickupLng),
                    destination = LocationData(destinationAddress, destinationLat, destinationLng),
                    rideType = rideType,
                    distance = distance,
                    duration = duration,
                    fare = fare,
                    status = "PENDING",
                    driverId = null,
                    driverName = null,
                    driverPhone = null,
                    driverVehicle = null,
                    driverVehicleNumber = null,
                    paymentMethod = "CASH",
                    paymentStatus = "PENDING",
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    expiresAt = expireTime
                )
                _rideCreated.value = ride
                _errorMessage.value = null
                Log.d(TAG, "========== BOOK RIDE COMPLETED ==========")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create ride: ${e.message}", e)
                _isLoading.value = false
                _errorMessage.value = "Failed to book ride: ${e.message}"
            }
    }

    // ============================================================
    // ✅ DISTANCE CALCULATION (Haversine Formula)
    // ============================================================

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0 // Earth's radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }

    // ============================================================
    // ✅ CLEAR ERROR
    // ============================================================

    fun clearError() {
        _errorMessage.value = null
    }
}