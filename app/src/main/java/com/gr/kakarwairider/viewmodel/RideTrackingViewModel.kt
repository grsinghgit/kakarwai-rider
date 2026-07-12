package com.gr.kakarwairider.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.utils.DistanceUtils

class RideTrackingViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // ✅ LiveData
    private val _rideData = MutableLiveData<RideModel?>()
    val rideData: LiveData<RideModel?> = _rideData

    private val _status = MutableLiveData<String>("PENDING")
    val status: LiveData<String> = _status

    private val _driverLocation = MutableLiveData<GeoPoint?>()
    val driverLocation: LiveData<GeoPoint?> = _driverLocation

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var rideListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var locationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentRideId: String? = null
    private var currentDriverId: String? = null

    // ✅ Driver Details Data Class
    data class DriverDetails(
        val name: String,
        val phone: String,
        val vehicle: String,
        val vehicleNumber: String
    )

    private val _driverDetails = MutableLiveData<DriverDetails?>()
    val driverDetails: LiveData<DriverDetails?> = _driverDetails

    // ✅ Fare + Distance Data
    data class FareDetails(
        val totalFare: Double,
        val pickupDistance: Double,
        val tripDistance: Double,
        val totalDistance: Double,
        val perKmRate: Double,
        val basePrice: Double,
        val pickupPin: String?,
        val completePin: String?
    )

    private val _fareDetails = MutableLiveData<FareDetails?>()
    val fareDetails: LiveData<FareDetails?> = _fareDetails

    // ✅ Load Ride Details
    fun loadRideDetails(rideId: String) {
        currentRideId = rideId

        rideListener?.remove()
        rideListener = db.collection("rides").document(rideId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val ride = snapshot.toObject<RideModel>()
                ride?.let {
                    _rideData.value = it.copy(rideId = snapshot.id)
                    _status.value = it.status

                    // ✅ Extract Driver Details
                    it.driverName?.let { name ->
                        _driverDetails.value = DriverDetails(
                            name = name,
                            phone = it.driverPhone ?: "N/A",
                            vehicle = it.driverVehicle ?: "Car",
                            vehicleNumber = it.driverVehicleNumber ?: "N/A"
                        )
                    }

                    // ✅ Extract Fare + Distance Details
                    if (it.fareCalculated) {
                        _fareDetails.value = FareDetails(
                            totalFare = it.totalFare,
                            pickupDistance = it.pickupDistance,
                            tripDistance = it.tripDistance,
                            totalDistance = it.totalDistance,
                            perKmRate = it.perKmRate,
                            basePrice = it.basePrice,
                            pickupPin = it.pickupPin,
                            completePin = it.completePin
                        )
                    }

                    // ✅ Start Driver Location Tracking if driver assigned
                    it.driverId?.let { driverId ->
                        if (currentDriverId != driverId) {
                            currentDriverId = driverId
                            listenForDriverLocation(driverId)
                        }
                    }
                }
            }
    }

    // ✅ Listen for Driver Location
    private fun listenForDriverLocation(driverId: String) {
        locationListener?.remove()
        locationListener = db.collection("driver_locations").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val location = snapshot.getGeoPoint("currentLocation")
                if (location != null) {
                    _driverLocation.value = location
                }
            }
    }

    // ✅ Cancel Ride
    fun cancelRide(rideId: String, cancelledBy: String, reason: String, callback: (Boolean) -> Unit) {
        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "status" to "CANCELLED",
                    "cancelledBy" to cancelledBy,
                    "cancelReason" to reason,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d("RideTrackingVM", "✅ Ride Cancelled")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("RideTrackingVM", "❌ Cancel failed: ${e.message}")
                _errorMessage.value = "Failed to cancel: ${e.message}"
                callback(false)
            }
    }

    // ✅ Get Ride Status
    fun getRideStatus(rideId: String, callback: (String) -> Unit) {
        db.collection("rides").document(rideId)
            .get()
            .addOnSuccessListener { document ->
                val status = document.getString("status") ?: "PENDING"
                callback(status)
            }
            .addOnFailureListener {
                callback("PENDING")
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        rideListener?.remove()
        locationListener?.remove()
    }
}