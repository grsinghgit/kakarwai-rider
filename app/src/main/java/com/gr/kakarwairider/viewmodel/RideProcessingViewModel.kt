package com.gr.kakarwairider.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel

class RideProcessingViewModel : ViewModel() {

    private val TAG = "RideProcessingVM"
    private val db = FirebaseFirestore.getInstance()

    private val _rideData = MutableLiveData<Map<String, Any>?>(null)
    val rideData: LiveData<Map<String, Any>?> = _rideData

    private val _status = MutableLiveData<String>("PENDING")
    val status: LiveData<String> = _status

    private val _driverDetails = MutableLiveData<DriverDetail?>(null)
    val driverDetails: LiveData<DriverDetail?> = _driverDetails

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null
    private var driverListener: com.google.firebase.firestore.ListenerRegistration? = null

    data class DriverDetail(
        val name: String,
        val phone: String,
        val vehicle: String,
        val vehicleNumber: String
    )

    fun loadRideDetails(rideId: String) {
        _isLoading.value = true

        db.collection("rides").document(rideId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    _rideData.value = document.data
                    val status = document.getString("status") ?: "PENDING"
                    _status.value = status
                    _isLoading.value = false

                    // ✅ Load driver details if assigned
                    val driverId = document.getString("driverId")
                    if (!driverId.isNullOrEmpty()) {
                        loadDriverDetails(driverId)
                    }
                } else {
                    _errorMessage.value = "Ride not found"
                    _isLoading.value = false
                }
            }
            .addOnFailureListener {
                _errorMessage.value = it.message
                _isLoading.value = false
            }
    }

    private fun loadDriverDetails(driverId: String) {
        driverListener?.remove()
        driverListener = db.collection("drivers").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading driver: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: "Driver"
                    val phone = snapshot.getString("phone") ?: "N/A"
                    val vehicleType = snapshot.getString("vehicleType") ?: "Car"
                    val vehicleModel = snapshot.getString("vehicleModel") ?: ""
                    val vehicleNumber = snapshot.getString("vehicleNumber") ?: "N/A"

                    val vehicle = if (vehicleModel.isNotEmpty()) {
                        "$vehicleType $vehicleModel"
                    } else {
                        vehicleType
                    }

                    _driverDetails.value = DriverDetail(name, phone, vehicle, vehicleNumber)
                }
            }
    }

    fun listenForRideUpdates(rideId: String) {
        listener?.remove()
        listener = db.collection("rides").document(rideId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val newStatus = snapshot.getString("status") ?: "PENDING"
                    _status.value = newStatus
                    _rideData.value = snapshot.data

                    // ✅ Load driver details if driver assigned
                    val driverId = snapshot.getString("driverId")
                    if (!driverId.isNullOrEmpty()) {
                        loadDriverDetails(driverId)
                    }
                }
            }
    }

    fun updateRideStatus(rideId: String, status: String, callback: (Boolean) -> Unit) {
        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "✅ Ride updated to $status")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Update failed: ${e.message}")
                _errorMessage.value = e.message
                callback(false)
            }
    }

    // ✅ NEW: Update payment method
    fun updateRideWithPayment(rideId: String, paymentMethod: String, callback: (Boolean) -> Unit) {
        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "paymentMethod" to paymentMethod,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "✅ Payment method updated: $paymentMethod")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Payment update failed: ${e.message}")
                callback(false)
            }
    }

    fun cancelRide(callback: (Boolean) -> Unit) {
        val rideId = _rideData.value?.get("rideId") as? String ?: run {
            callback(false)
            return
        }

        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "status" to "CANCELLED",
                    "cancelledBy" to "user",
                    "cancelReason" to "User cancelled",
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun stopListening() {
        listener?.remove()
        driverListener?.remove()
        listener = null
        driverListener = null
        Log.d(TAG, "🔇 Listeners stopped")
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        driverListener?.remove()
    }
}