package com.gr.kakarwairider.admin.viewmodel

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

    private var filter = RideFilterModel()
    private var areaId: String? = null

    init {
        loadAdminArea()
    }

    private fun loadAdminArea() {
        val adminId = auth.currentUser?.uid
        android.util.Log.d("AdminRideVM", "👤 Admin ID: $adminId")

        if (adminId != null) {
            viewModelScope.launch {
                try {
                    val doc = FirebaseFirestore.getInstance()
                        .collection("admins").document(adminId).get().await()

                    // ✅ Get areaId with proper null/empty check
                    val areaIdFromFirestore = doc.getString("areaId")
                    android.util.Log.d("AdminRideVM", "📍 Raw areaId from Firestore: '$areaIdFromFirestore'")

                    // ✅ Check if areaId is not null and not empty
                    if (!areaIdFromFirestore.isNullOrBlank()) {
                        areaId = areaIdFromFirestore
                        android.util.Log.d("AdminRideVM", "✅ Area ID set: $areaId")
                    } else {
                        android.util.Log.w("AdminRideVM", "⚠️ areaId is null or empty!")

                        // ✅ Try to find area where adminId matches
                        val areaDoc = FirebaseFirestore.getInstance()
                            .collection("areas")
                            .whereEqualTo("adminId", adminId)
                            .get()
                            .await()

                        if (areaDoc.documents.isNotEmpty()) {
                            areaId = areaDoc.documents.first().id
                            android.util.Log.d("AdminRideVM", "📍 Found area from areas collection: $areaId")
                        } else {
                            // ✅ Final fallback - hardcoded
                            android.util.Log.w("AdminRideVM", "⚠️ No area found, using hardcoded test area")
                            areaId = "area_kakarwai"
                        }
                    }

                    if (areaId != null) {
                        loadRides()
                    } else {
                        android.util.Log.e("AdminRideVM", "❌ No areaId found anywhere!")
                        _errorMessage.value = "No area assigned to this admin"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminRideVM", "❌ Error: ${e.message}")
                    areaId = "a"
                    loadRides()
                }
            }
        }
    }

    private fun loadRides() {
        val area = areaId ?: return
        _isLoading.value = true

        viewModelScope.launch {
            repository.getRidesByArea(area).collectLatest { rideList ->
                _rides.value = rideList
                applyFilter()
                calculateStats(rideList)
                _isLoading.value = false
            }
        }

        // Load available drivers
        viewModelScope.launch {
            repository.getAvailableDrivers().collectLatest { drivers ->
                _availableDrivers.value = drivers
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

            // ✅ Status filter
            if (filter.status != "ALL") {
                val statusMatch = when (filter.status) {
                    "PENDING" -> ride.status in listOf("PENDING", "SEARCHING")
                    "ASSIGNED" -> ride.status in listOf("DRIVER_ASSIGNED", "ACCEPTED")
                    "STARTED" -> ride.status == "STARTED"
                    "COMPLETED" -> ride.status == "COMPLETED"
                    "CANCELLED" -> ride.status == "CANCELLED"
                    else -> true
                }
                matches = matches && statusMatch
            }

            // ✅ Vehicle filter
            if (filter.vehicleType != "ALL") {
                matches = matches && ride.vehicleType.equals(filter.vehicleType, ignoreCase = true)
            }

            // ✅ Payment filter
            if (filter.paymentType != "ALL") {
                matches = matches && ride.paymentMethod.equals(filter.paymentType, ignoreCase = true)
            }

            matches
        }
        _filteredRides.value = filtered
    }

    private fun calculateStats(rides: List<RideModel>) {
        _stats.value = repository.getStats(rides)
    }

    fun assignDriver(rideId: String, driverId: String, driverName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.assignDriver(rideId, driverId, driverName)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to assign driver: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reassignDriver(rideId: String, driverId: String, driverName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.reassignDriver(rideId, driverId, driverName)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reassign driver: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelRide(rideId: String, reason: String, cancelledBy: String = "admin") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.cancelRide(rideId, reason, cancelledBy)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cancel ride: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeRide(rideId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.completeRide(rideId)
                _errorMessage.value = null
            } catch (e: Exception) {
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
}