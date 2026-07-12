package com.gr.kakarwairider.driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.utils.DistanceUtils

class DriverPendingRidesViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _rides = MutableLiveData<List<RideModel>>(emptyList())
    val rides: LiveData<List<RideModel>> = _rides

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null

    // ✅ Load rides
    fun loadPendingRides(driverId: String) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "Driver ID is empty"
            return
        }

        _isLoading.value = true
        Log.d("PendingRidesVM", "🔄 Loading rides for: $driverId")

        listener?.remove()
        listener = db.collection("rides")
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf(
                "DRIVER_ASSIGNED",
                "ACCEPTED",
                "ARRIVED_PICKUP",
                "ON_THE_WAY",
                "DESTINATION_REACHED",
                "STARTED"
            ))
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val rides = snapshots?.documents?.mapNotNull { document ->
                    val ride = document.toObject<RideModel>()
                    ride?.copy(rideId = document.id)
                } ?: emptyList()

                Log.d("PendingRidesVM", "📋 Rides: ${rides.size}")
                rides.forEach {
                    Log.d("PendingRidesVM", "   - ${it.rideId}: ${it.status}, Fare: ₹${it.totalFare}")
                }

                _rides.value = rides.sortedByDescending { it.createdAt?.toDate() }
                _isLoading.value = false
            }
    }

    // ✅ Update ride status
    fun updateRideStatus(rideId: String, status: String, callback: (Boolean) -> Unit) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        Log.d("PendingRidesVM", "🔄 Updating ride: $rideId → $status")

        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d("PendingRidesVM", "✅ Ride updated: $rideId → $status")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("PendingRidesVM", "❌ Update failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                callback(false)
            }
    }

    // ✅ Update ride with Pickup PIN
    fun updateRideWithPin(
        rideId: String,
        status: String,
        pickupPin: String,
        pickupTime: Timestamp,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        Log.d("PendingRidesVM", "📍 Arrived: $rideId, PIN: $pickupPin")

        val updates = mapOf(
            "status" to status,
            "pickupPin" to pickupPin,
            "pickupTime" to pickupTime,
            "updatedAt" to Timestamp.now()
        )

        db.collection("rides").document(rideId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("PendingRidesVM", "✅ Arrived updated: $rideId")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("PendingRidesVM", "❌ Failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                callback(false)
            }
    }

    // ✅ Update ride with Complete PIN (Destination Reached)
    fun updateRideWithCompletePin(
        rideId: String,
        status: String,
        completePin: String,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        Log.d("PendingRidesVM", "📍 Destination Reached: $rideId, Complete PIN: $completePin")

        val updates = mapOf(
            "status" to status,
            "completePin" to completePin,
            "updatedAt" to Timestamp.now()
        )

        db.collection("rides").document(rideId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("PendingRidesVM", "✅ Destination Reached updated: $rideId")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("PendingRidesVM", "❌ Failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                callback(false)
            }
    }

    // ✅ Complete Ride with PIN verification
    fun completeRideWithPin(
        rideId: String,
        enteredPin: String,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Ride ID is empty"
            callback(false)
            return
        }

        Log.d("PendingRidesVM", "🔑 Completing ride: $rideId")

        db.collection("rides").document(rideId)
            .get()
            .addOnSuccessListener { document ->
                val savedPin = document.getString("completePin")

                if (savedPin == enteredPin) {
                    val updates = mapOf(
                        "status" to "COMPLETED",
                        "completedAt" to Timestamp.now(),
                        "completeTime" to Timestamp.now(),
                        "updatedAt" to Timestamp.now()
                    )

                    db.collection("rides").document(rideId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d("PendingRidesVM", "✅ Ride Completed: $rideId")
                            callback(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e("PendingRidesVM", "❌ Complete failed: ${e.message}")
                            _errorMessage.value = "Failed to complete: ${e.message}"
                            callback(false)
                        }
                } else {
                    Log.d("PendingRidesVM", "❌ Invalid PIN")
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PendingRidesVM", "❌ Fetch failed: ${e.message}")
                _errorMessage.value = "Failed to fetch ride: ${e.message}"
                callback(false)
            }
    }

    // ✅ CALCULATE FARE - Main Function
    fun calculateFareForRide(
        rideId: String,
        driverId: String,
        areaId: String,
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double,
        callback: (Boolean) -> Unit
    ) {
        if (rideId.isEmpty() || driverId.isEmpty() || areaId.isEmpty()) {
            _errorMessage.value = "Missing required data"
            callback(false)
            return
        }

        Log.d("PendingRidesVM", "💰 Calculating fare for ride: $rideId")

        // ✅ Step 1: Fetch Driver Current Location
        db.collection("driver_locations").document(driverId)
            .get()
            .addOnSuccessListener { driverDoc ->
                if (!driverDoc.exists()) {
                    _errorMessage.value = "Driver location not found"
                    callback(false)
                    return@addOnSuccessListener
                }

                val currentLocation = driverDoc.getGeoPoint("currentLocation")
                if (currentLocation == null) {
                    _errorMessage.value = "Driver current location not available"
                    callback(false)
                    return@addOnSuccessListener
                }

                val driverLat = currentLocation.latitude
                val driverLng = currentLocation.longitude

                Log.d("PendingRidesVM", "   Driver Location: ($driverLat, $driverLng)")

                // ✅ Step 2: Calculate Distances
                val pickupDistance = DistanceUtils.calculateDistance(
                    driverLat, driverLng,
                    pickupLat, pickupLng
                )

                val tripDistance = DistanceUtils.calculateDistance(
                    pickupLat, pickupLng,
                    destLat, destLng
                )

                val totalDistance = pickupDistance + tripDistance

                Log.d("PendingRidesVM", "   Pickup Distance: ${DistanceUtils.formatDistance(pickupDistance)} km")
                Log.d("PendingRidesVM", "   Trip Distance: ${DistanceUtils.formatDistance(tripDistance)} km")
                Log.d("PendingRidesVM", "   Total Distance: ${DistanceUtils.formatDistance(totalDistance)} km")

                // ✅ Step 3: Fetch Area Details
                db.collection("areas").document(areaId)
                    .get()
                    .addOnSuccessListener { areaDoc ->
                        if (!areaDoc.exists()) {
                            _errorMessage.value = "Area not found"
                            callback(false)
                            return@addOnSuccessListener
                        }

                        val perKmRate = areaDoc.getDouble("perKmRate") ?: 10.0
                        val basePrice = areaDoc.getDouble("basePrice") ?: 30.0

                        val distanceFare = totalDistance * perKmRate
                        val totalFare = basePrice + distanceFare

                        Log.d("PendingRidesVM", "   Per Km Rate: ₹$perKmRate")
                        Log.d("PendingRidesVM", "   Base Price: ₹$basePrice")
                        Log.d("PendingRidesVM", "   Distance Fare: ₹${DistanceUtils.formatFare(distanceFare)}")
                        Log.d("PendingRidesVM", "   Total Fare: ₹${DistanceUtils.formatFare(totalFare)}")

                        // ✅ Step 4: Save in Ride Document
                        val updates = mapOf(
                            "pickupDistance" to pickupDistance,
                            "tripDistance" to tripDistance,
                            "totalDistance" to totalDistance,
                            "perKmRate" to perKmRate,
                            "basePrice" to basePrice,
                            "distanceFare" to distanceFare,
                            "totalFare" to totalFare,
                            "fareCalculated" to true,
                            "updatedAt" to Timestamp.now()
                        )

                        db.collection("rides").document(rideId)
                            .update(updates)
                            .addOnSuccessListener {
                                Log.d("PendingRidesVM", "✅ Fare calculated: ₹${DistanceUtils.formatFare(totalFare)}")
                                callback(true)
                            }
                            .addOnFailureListener { e ->
                                Log.e("PendingRidesVM", "❌ Save failed: ${e.message}")
                                _errorMessage.value = "Failed to save fare: ${e.message}"
                                callback(false)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("PendingRidesVM", "❌ Area fetch failed: ${e.message}")
                        _errorMessage.value = "Failed to fetch area: ${e.message}"
                        callback(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("PendingRidesVM", "❌ Driver location fetch failed: ${e.message}")
                _errorMessage.value = "Failed to fetch driver location: ${e.message}"
                callback(false)
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}