package com.gr.kakarwairider.driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel
import kotlinx.coroutines.launch

class DriverRidesViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _rides = MutableLiveData<List<RideModel>>(emptyList())
    val rides: LiveData<List<RideModel>> = _rides

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null

    fun loadRides(driverId: String) {
        _isLoading.value = true

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

                val rides = snapshots?.documents?.mapNotNull { it.toObject<RideModel>() } ?: emptyList()
                _rides.value = rides
                _isLoading.value = false
            }
    }

    fun updateRideStatus(rideId: String, status: String, callback: (Boolean) -> Unit) {
        db.collection("rides").document(rideId)
            .update("status", status)
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

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}