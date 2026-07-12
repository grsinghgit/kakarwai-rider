package com.gr.kakarwairider.admin.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.admin.model.RideFilterModel
import com.gr.kakarwairider.admin.model.RideStatsModel
import com.gr.kakarwairider.admin.repository.AdminRideRepository
import com.gr.kakarwairider.admin.repository.DriverInfo
import com.gr.kakarwairider.model.RideModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminRideViewModel : ViewModel() {

    private val repository = AdminRideRepository()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _rides = MutableLiveData<List<RideModel>>(emptyList())
    val rides: LiveData<List<RideModel>> = _rides

    private val _filteredRides = MutableLiveData<List<RideModel>>(emptyList())
    val filteredRides: LiveData<List<RideModel>> = _filteredRides

    private val _stats = MutableLiveData<RideStatsModel>(RideStatsModel())
    val stats: LiveData<RideStatsModel> = _stats

    private val _availableDrivers = MutableLiveData<List<DriverInfo>>(emptyList())
    val availableDrivers: LiveData<List<DriverInfo>> = _availableDrivers

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _assignmentSuccess = MutableLiveData(false)
    val assignmentSuccess: LiveData<Boolean> = _assignmentSuccess

    private var filter = RideFilterModel()
    private var areaId: String? = null
    private var isListenerAttached = false

    init {
        loadAdminArea()
    }

    private fun loadAdminArea() {
        val adminId = "admin_uid_123"
        Log.d("AdminRideVM", "👤 Using Admin ID: $adminId")

        if (adminId.isBlank()) {
            _errorMessage.value = "Admin ID not configured"
            return
        }

        viewModelScope.launch {
            try {
                val doc = db.collection("admins").document(adminId).get().await()

                if (!doc.exists()) {
                    _errorMessage.value = "Admin profile not found"
                    return@launch
                }

                val areaIdFromFirestore = doc.getString("areaId")
                if (!areaIdFromFirestore.isNullOrBlank()) {
                    areaId = areaIdFromFirestore
                    Log.d("AdminRideVM", "✅ Area ID: $areaId")
                    loadRides()
                } else {
                    _errorMessage.value = "No area assigned to this admin"
                }
            } catch (e: Exception) {
                Log.e("AdminRideVM", "❌ Error: ${e.message}")
                _errorMessage.value = "Failed to load admin area: ${e.message}"
            }
        }
    }

    private fun loadRides() {
        val area = areaId ?: return

        if (isListenerAttached) {
            Log.d("AdminRideVM", "⚠️ Listener already attached, skipping...")
            return
        }

        Log.d("AdminRideVM", "✅ Attaching Snapshot Listener for area: $area")
        _isLoading.value = true
        isListenerAttached = true

        viewModelScope.launch {
            repository.getRidesByArea(area).collectLatest { rideList ->
                Log.d("AdminRideVM", "📋 Received ${rideList.size} rides")
                _rides.value = rideList
                applyFilter()
                calculateStats(rideList)
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            repository.getAvailableDrivers().collectLatest { drivers ->
                _availableDrivers.value = drivers
                Log.d("AdminRideVM", "👤 Available drivers: ${drivers.size}")
            }
        }
    }

    fun applyFilter(
        dateRange: String? = null,
        status: String? = null,
        vehicleType: String? = null,
        paymentType: String? = null
    ) {
        dateRange?.let { filter = filter.copy(dateRange = it) }
        status?.let { filter = filter.copy(status = it) }
        vehicleType?.let { filter = filter.copy(vehicleType = it) }
        paymentType?.let { filter = filter.copy(paymentType = it) }

        val currentRides = _rides.value ?: emptyList()
        val filtered = currentRides.filter { ride ->
            var matches = true

            if (filter.status != "ALL") {
                val statusMatch = when (filter.status) {
                    "PENDING" -> ride.status in listOf("PENDING", "SEARCHING")
                    "ASSIGNED" -> ride.status in listOf("DRIVER_ASSIGNED", "ACCEPTED")
                    "STARTED" -> ride.status in listOf("STARTED", "ON_THE_WAY", "ARRIVED_PICKUP", "DESTINATION_REACHED")
                    "COMPLETED" -> ride.status == "COMPLETED"
                    "CANCELLED" -> ride.status == "CANCELLED"
                    else -> true
                }
                matches = matches && statusMatch
            }

            if (filter.vehicleType != "ALL") {
                matches = matches && ride.vehicleType.equals(filter.vehicleType, ignoreCase = true)
            }

            if (filter.paymentType != "ALL") {
                matches = matches && ride.paymentMethod.equals(filter.paymentType, ignoreCase = true)
            }

            matches
        }
        _filteredRides.value = filtered
        Log.d("AdminRideVM", "🔍 Filtered: ${filtered.size} rides")
    }

    private fun calculateStats(rides: List<RideModel>) {
        viewModelScope.launch {
            val stats = repository.getStats(rides)
            _stats.value = stats
        }
    }

    fun assignDriver(rideId: String, driverId: String, driverName: String) {
        if (rideId.isEmpty() || driverId.isEmpty()) {
            _errorMessage.value = "Invalid ride or driver ID"
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("AdminRideVM", "🔄 Assigning driver $driverName to ride $rideId")

                val success = repository.assignDriver(rideId, driverId, driverName)

                if (success) {
                    Log.d("AdminRideVM", "✅ Driver assigned successfully")
                    _errorMessage.value = null
                    _assignmentSuccess.value = true
                    _assignmentSuccess.value = false
                } else {
                    _errorMessage.value = "Failed to assign driver"
                }
            } catch (e: Exception) {
                Log.e("AdminRideVM", "❌ Assign failed: ${e.message}")
                _errorMessage.value = "Failed to assign driver: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reassignDriver(rideId: String, driverId: String, driverName: String) {
        if (rideId.isEmpty() || driverId.isEmpty()) {
            _errorMessage.value = "Invalid ride or driver ID"
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("AdminRideVM", "🔄 Reassigning driver $driverName to ride $rideId")

                val success = repository.reassignDriver(rideId, driverId, driverName)

                if (success) {
                    Log.d("AdminRideVM", "✅ Driver reassigned successfully")
                    _errorMessage.value = null
                    _assignmentSuccess.value = true
                    _assignmentSuccess.value = false
                } else {
                    _errorMessage.value = "Failed to reassign driver"
                }
            } catch (e: Exception) {
                Log.e("AdminRideVM", "❌ Reassign failed: ${e.message}")
                _errorMessage.value = "Failed to reassign driver: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelRide(rideId: String, reason: String, cancelledBy: String = "admin") {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Invalid ride ID"
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("AdminRideVM", "🔄 Cancelling ride $rideId, reason: $reason")

                val success = repository.cancelRide(rideId, reason, cancelledBy)

                if (success) {
                    Log.d("AdminRideVM", "✅ Ride cancelled successfully")
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Failed to cancel ride"
                }
            } catch (e: Exception) {
                Log.e("AdminRideVM", "❌ Cancel failed: ${e.message}")
                _errorMessage.value = "Failed to cancel ride: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeRide(rideId: String) {
        if (rideId.isEmpty()) {
            _errorMessage.value = "Invalid ride ID"
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("AdminRideVM", "🔄 Completing ride $rideId")

                val success = repository.completeRide(rideId)

                if (success) {
                    Log.d("AdminRideVM", "✅ Ride completed successfully")
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Failed to complete ride"
                }
            } catch (e: Exception) {
                Log.e("AdminRideVM", "❌ Complete failed: ${e.message}")
                _errorMessage.value = "Failed to complete ride: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getAvailableDriversList(): List<DriverInfo> = _availableDrivers.value ?: emptyList()

    override fun onCleared() {
        super.onCleared()
        isListenerAttached = false
        Log.d("AdminRideVM", "🧹 ViewModel cleared")
    }
}