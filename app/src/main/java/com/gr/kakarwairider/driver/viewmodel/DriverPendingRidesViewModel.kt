package com.gr.kakarwairider.driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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

    // ✅ Load DRIVER_ASSIGNED + ACCEPTED + STARTED rides
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
            .whereIn("status", listOf("DRIVER_ASSIGNED", "ACCEPTED", "STARTED"))
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
                    "updatedAt" to com.google.firebase.Timestamp.now()
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

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}