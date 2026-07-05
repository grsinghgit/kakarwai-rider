package com.gr.kakarwairider.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class BookRideViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _fareEstimate = MutableLiveData<String?>(null)
    val fareEstimate: LiveData<String?> = _fareEstimate

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _rideBookingResult = MutableLiveData<String?>(null)
    val rideBookingResult: LiveData<String?> = _rideBookingResult

    fun calculateFare(pickup: String, drop: String) {
        // TODO: यहाँ Real Fare Calculation API Call करें
        val fakeFare = (100..500).random().toString()
        _fareEstimate.value = fakeFare
    }

    fun bookRide(pickup: String, drop: String, rideType: String) {
        _isLoading.value = true

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _isLoading.value = false
            _rideBookingResult.value = "ERROR"
            return
        }

        val fareValue = _fareEstimate.value?.toIntOrNull() ?: 0

        val rideData = mapOf(
            "passengerId" to userId,
            "pickup" to pickup,
            "drop" to drop,
            "rideType" to rideType,
            "status" to "SEARCHING",
            "timestamp" to System.currentTimeMillis(),
            "fare" to fareValue
        )

        db.collection("rides")
            .add(rideData)
            .addOnSuccessListener {
                _isLoading.value = false
                _rideBookingResult.value = "SUCCESS"
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _rideBookingResult.value = "ERROR"
                e.printStackTrace()
            }
    }
}