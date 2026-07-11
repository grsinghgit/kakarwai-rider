package com.gr.kakarwairider.driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.DriverModel
import kotlinx.coroutines.tasks.await

class DriverHomeViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _driverData = MutableLiveData<DriverModel?>(null)
    val driverData: LiveData<DriverModel?> = _driverData

    private val _walletBalance = MutableLiveData(0.0)
    val walletBalance: LiveData<Double> = _walletBalance

    private val _totalRides = MutableLiveData(0)
    val totalRides: LiveData<Int> = _totalRides

    private val _totalEarnings = MutableLiveData(0.0)
    val totalEarnings: LiveData<Double> = _totalEarnings

    private val _rating = MutableLiveData(0.0)
    val rating: LiveData<Double> = _rating

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadDriverData(driverId: String) {
        _isLoading.value = true

        db.collection("drivers").document(driverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val driver = document.toObject<DriverModel>()
                    _driverData.value = driver

                    driver?.let {
                        _walletBalance.value = it.walletBalance ?: 0.0
                        _totalRides.value = it.totalRides ?: 0
                        _totalEarnings.value = it.totalEarnings ?: 0.0
                        _rating.value = it.rating ?: 0.0
                    }
                }
                _isLoading.value = false
            }
            .addOnFailureListener {
                _errorMessage.value = it.message
                _isLoading.value = false
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}