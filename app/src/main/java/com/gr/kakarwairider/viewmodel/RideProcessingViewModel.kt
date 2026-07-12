package com.gr.kakarwairider.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RideProcessingViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // ✅ LiveData
    private val _rideData = MutableLiveData<Map<String, Any>?>()
    val rideData: LiveData<Map<String, Any>?> = _rideData

    private val _rideModel = MutableLiveData<RideModel?>()
    val rideModel: LiveData<RideModel?> = _rideModel

    private val _status = MutableLiveData<String>("PENDING")
    val status: LiveData<String> = _status

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _driverDetails = MutableLiveData<DriverDetails?>()
    val driverDetails: LiveData<DriverDetails?> = _driverDetails

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentRideId: String? = null

    // ✅ Driver Details Data Class
    data class DriverDetails(
        val name: String,
        val phone: String,
        val vehicle: String,
        val vehicleNumber: String
    )

    // ✅ Load Ride Details
    fun loadRideDetails(rideId: String) {
        currentRideId = rideId
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val document = db.collection("rides").document(rideId).get().await()
                if (document.exists()) {
                    val data = document.data ?: emptyMap()
                    _rideData.value = data

                    // ✅ Convert to RideModel
                    val ride = document.toObject<RideModel>()
                    _rideModel.value = ride?.copy(rideId = document.id)

                    extractDriverDetails(data)
                    updateStatus(data["status"] as? String ?: "PENDING")
                } else {
                    _errorMessage.value = "Ride not found"
                }
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load ride: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // ✅ Listen for Real-time Updates
    fun listenForRideUpdates(rideId: String) {
        currentRideId = rideId
        listener?.remove()

        listener = db.collection("rides").document(rideId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener
                _rideData.value = data

                val ride = snapshot.toObject<RideModel>()
                _rideModel.value = ride?.copy(rideId = snapshot.id)

                extractDriverDetails(data)
                updateStatus(data["status"] as? String ?: "PENDING")
            }
    }

    // ✅ Extract Driver Details
    private fun extractDriverDetails(data: Map<String, Any>) {
        val driverName = data["driverName"] as? String
        val driverPhone = data["driverPhone"] as? String
        val driverVehicle = data["driverVehicle"] as? String
        val driverVehicleNumber = data["driverVehicleNumber"] as? String

        if (driverName != null) {
            _driverDetails.value = DriverDetails(
                name = driverName,
                phone = driverPhone ?: "N/A",
                vehicle = driverVehicle ?: "Car",
                vehicleNumber = driverVehicleNumber ?: "N/A"
            )
        }
    }

    // ✅ Update Status
    private fun updateStatus(newStatus: String) {
        _status.value = newStatus
    }

    // ✅ Update Ride Status
    fun updateRideStatus(status: String, callback: (Boolean) -> Unit) {
        currentRideId?.let { rideId ->
            db.collection("rides").document(rideId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d("RideProcessingVM", "✅ Status updated: $status")
                    callback(true)
                }
                .addOnFailureListener { e ->
                    Log.e("RideProcessingVM", "❌ Update failed: ${e.message}")
                    _errorMessage.value = "Failed to update status: ${e.message}"
                    callback(false)
                }
        } ?: callback(false)
    }

    // ✅ Cancel Ride
    fun cancelRide(callback: (Boolean) -> Unit) {
        currentRideId?.let { rideId ->
            db.collection("rides").document(rideId)
                .update(
                    mapOf(
                        "status" to "CANCELLED",
                        "updatedAt" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d("RideProcessingVM", "✅ Ride Cancelled")
                    callback(true)
                }
                .addOnFailureListener { e ->
                    Log.e("RideProcessingVM", "❌ Cancel failed: ${e.message}")
                    _errorMessage.value = "Failed to cancel: ${e.message}"
                    callback(false)
                }
        } ?: callback(false)
    }

    // ✅ Clear Error
    fun clearError() {
        _errorMessage.value = null
    }

    // ✅ Cleanup
    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}