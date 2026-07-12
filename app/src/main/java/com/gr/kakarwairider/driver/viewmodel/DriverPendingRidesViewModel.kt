package com.gr.kakarwairider.driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel

class DriverPendingRidesViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _rides = MutableLiveData<List<RideModel>>(emptyList())
    val rides: LiveData<List<RideModel>> = _rides

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null

    // ✅ Load DRIVER_ASSIGNED + ACCEPTED + ARRIVED_PICKUP + ON_THE_WAY + STARTED rides
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
                "DESTINATION_REACHED",  // ✅ NEW
                "ON_THE_WAY",
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
                    Log.d("PendingRidesVM", "   - ${it.rideId}: ${it.status}")
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

    // ✅ Update ride with PIN
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

    fun clearError() {
        _errorMessage.value = null
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

        Log.d("PendingRidesVM", "🔑 Completing ride: $rideId, PIN: $enteredPin")

        // ✅ Pehle ride fetch karo completePin check karne ke liye
        db.collection("rides").document(rideId)
            .get()
            .addOnSuccessListener { document ->
                val savedPin = document.getString("completePin")

                if (savedPin == enteredPin) {
                    // ✅ PIN Match - Complete Ride
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
                    // ❌ PIN Mismatch
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

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}