package com.gr.kakarwairider.admin.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import com.gr.kakarwairider.admin.model.DriverLocationModel
import com.gr.kakarwairider.admin.model.DriverWithDistance
import com.gr.kakarwairider.admin.repository.AdminMapRepository
import kotlinx.coroutines.launch

class AdminMapViewModel : ViewModel() {

    private val repository = AdminMapRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _drivers = MutableLiveData<List<DriverWithDistance>>(emptyList())
    val drivers: LiveData<List<DriverWithDistance>> = _drivers

    private val _rides = MutableLiveData<List<Map<String, Any>>>(emptyList())
    val rides: LiveData<List<Map<String, Any>>> = _rides

    private val _selectedRide = MutableLiveData<Map<String, Any>?>(null)
    val selectedRide: LiveData<Map<String, Any>?> = _selectedRide

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val centerLat = 28.6139
    private val centerLng = 77.2090
    private var areaId: String? = null

    init {
        loadAdminArea()
    }

    private fun loadAdminArea() {
        // 🔥 TODO: Production mein Firebase Auth UID use karein
        // val adminId = auth.currentUser?.uid
        // android.util.Log.d("AdminMapVM", "👤 Admin ID from Firebase Auth: $adminId")

        // ✅ Abhi ke liye manual admin ID use karein (test ke liye)
        val adminId = "admin_uid_123"
        android.util.Log.d("AdminMapVM", "👤 Using manual Admin ID: $adminId")

        if (adminId.isBlank()) {
            android.util.Log.e("AdminMapVM", "❌ No admin ID configured!")
            _errorMessage.value = "Admin ID not configured"
            return
        }

        repository.getAdminArea(adminId) { area ->
            areaId = area
            android.util.Log.d("AdminMapVM", "📍 Area ID: $areaId")
            if (area != null) {
                loadRides(area)
            } else {
                android.util.Log.e("AdminMapVM", "❌ No area found for admin: $adminId")
                _errorMessage.value = "No area assigned to this admin"
            }
        }
    }

    fun loadOnlineDrivers() {
        _isLoading.value = true

        repository.getOnlineDrivers(
            onSuccess = { driverList ->
                _isLoading.value = false
                val driversWithDistance = driverList.mapNotNull { driver ->
                    val location = driver.currentLocation
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        val distance = repository.calculateDistance(
                            centerLat, centerLng,
                            location.latitude, location.longitude
                        )
                        DriverWithDistance(driver, distance, latLng)
                    } else {
                        null
                    }
                }
                _drivers.value = driversWithDistance
            },
            onError = { error ->
                _isLoading.value = false
                _errorMessage.value = error
            }
        )
    }

    fun loadRides(areaId: String) {
        repository.getRidesByArea(
            areaId = areaId,
            onSuccess = { rideList ->
                _rides.value = rideList
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    fun loadActiveRide(rideId: String?) {
        if (rideId == null) {
            _selectedRide.value = null
            return
        }

        repository.getActiveRide(rideId) { data ->
            _selectedRide.value = data
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getAreaId(): String? = areaId
}