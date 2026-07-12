package com.gr.kakarwairider.admin.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.DriverModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminDriverViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _drivers = MutableLiveData<List<DriverModel>>(emptyList())
    val drivers: LiveData<List<DriverModel>> = _drivers

    private val _selectedDriver = MutableLiveData<DriverModel?>(null)
    val selectedDriver: LiveData<DriverModel?> = _selectedDriver

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>(null)
    val successMessage: LiveData<String?> = _successMessage

    private var listener: com.google.firebase.firestore.ListenerRegistration? = null
    private var isListenerAttached = false

    // ✅ ONE TIME - Snapshot Listener
    fun attachSnapshotListener() {
        if (isListenerAttached) {
            Log.d("AdminDriverVM", "⚠️ Listener already attached")
            return
        }

        Log.d("AdminDriverVM", "✅ Attaching Snapshot Listener...")
        _isLoading.value = true

        listener?.remove()
        listener = db.collection("drivers")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val drivers = snapshots?.documents?.mapNotNull { document ->
                    val driver = document.toObject<DriverModel>()
                    driver?.copy(id = document.id)
                } ?: emptyList()

                Log.d("FirestoreDebug", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("FirestoreDebug", "📋 Snapshot update: ${drivers.size} drivers")
                drivers.forEach {
                    Log.d("FirestoreDebug", "   - ${it.name}: isActive=${it.isActive}, wallet=${it.walletBalance}")
                }
                Log.d("FirestoreDebug", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                _drivers.value = drivers.sortedByDescending { it.createdAt?.toDate() }
                _isLoading.value = false
                isListenerAttached = true
            }
    }

    // ✅ Get driver by ID
    fun getDriverById(driverId: String) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "❌ Invalid Driver ID"
            return
        }

        viewModelScope.launch {
            try {
                val doc = db.collection("drivers").document(driverId).get().await()
                if (doc.exists()) {
                    val driver = doc.toObject<DriverModel>()
                    _selectedDriver.value = driver?.copy(id = doc.id)
                } else {
                    _errorMessage.value = "❌ Driver not found"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load driver: ${e.message}"
            }
        }
    }

    // ✅ Add driver - Snapshot listener auto updates
    fun addDriver(driver: DriverModel, callback: (Boolean) -> Unit) {
        _isLoading.value = true

        val adminId = "admin_uid_123"
        Log.d("AdminDriverVM", "👤 Adding driver: ${driver.name}")

        val driverData = hashMapOf(
            "name" to driver.name,
            "phone" to driver.phone,
            "pin" to driver.pin,
            "areaId" to driver.areaId,
            "adminId" to adminId,
            "isActive" to true,
            "isAvailable" to false,
            "vehicleType" to driver.vehicleType,
            "vehicleModel" to driver.vehicleModel,
            "vehicleNumber" to driver.vehicleNumber,
            "walletBalance" to 0.0,
            "totalEarnings" to 0.0,
            "totalRides" to 0,
            "rating" to 0.0,
            "isSpecial" to driver.isSpecial,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        db.collection("drivers")
            .add(driverData)
            .addOnSuccessListener { documentRef ->
                val locationData = hashMapOf(
                    "driverId" to documentRef.id,
                    "driverName" to driver.name,
                    "driverPhone" to driver.phone,
                    "status" to "OFFLINE",
                    "isAvailable" to false,
                    "updatedAt" to Timestamp.now()
                )
                db.collection("driver_locations")
                    .document(documentRef.id)
                    .set(locationData)

                _successMessage.value = "✅ Driver added successfully!"
                _isLoading.value = false
                callback(true)
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to add driver: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
    }

    // ✅ Update driver - NO loadDrivers()
    fun updateDriver(driverId: String, updates: Map<String, Any>, callback: (Boolean) -> Unit) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "❌ Cannot update: Driver ID is empty!"
            callback(false)
            return
        }

        _isLoading.value = true
        Log.d("AdminDriverVM", "🔄 Updating driver: $driverId")

        val updateMap = updates.toMutableMap()
        updateMap["updatedAt"] = Timestamp.now()

        db.collection("drivers").document(driverId)
            .update(updateMap)
            .addOnSuccessListener {
                Log.d("AdminDriverVM", "✅ Driver updated: $driverId")
                _successMessage.value = "✅ Driver updated successfully!"
                _isLoading.value = false
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("AdminDriverVM", "❌ Update failed: ${e.message}")
                _errorMessage.value = "Failed to update: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
    }

    // ✅ Toggle driver status - NO loadDrivers()
    fun toggleDriverStatus(driverId: String, isActive: Boolean, callback: (Boolean) -> Unit) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "❌ Cannot toggle: Driver ID is empty!"
            callback(false)
            return
        }
        Log.d("AdminDriverVM", "🔄 Toggle status: $driverId → $isActive")
        updateDriver(driverId, mapOf("isActive" to isActive), callback)
    }

    // ✅ Toggle Special status - NO loadDrivers()
    fun toggleSpecialStatus(driverId: String, isSpecial: Boolean, callback: (Boolean) -> Unit) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "❌ Cannot toggle special: Driver ID is empty!"
            callback(false)
            return
        }
        updateDriver(driverId, mapOf("isSpecial" to isSpecial), callback)
    }

    // ✅ Recharge Wallet - NO loadDrivers()
    fun rechargeWallet(driverId: String, amount: Double, callback: (Boolean) -> Unit) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "❌ Cannot recharge: Driver ID is empty!"
            callback(false)
            return
        }

        _isLoading.value = true
        Log.d("AdminDriverVM", "💰 Recharging: $driverId, ₹$amount")

        viewModelScope.launch {
            try {
                val doc = db.collection("drivers").document(driverId).get().await()
                val currentBalance = doc.getDouble("walletBalance") ?: 0.0
                val newBalance = currentBalance + amount

                db.collection("drivers").document(driverId)
                    .update("walletBalance", newBalance)
                    .addOnSuccessListener {
                        val transaction = hashMapOf(
                            "driverId" to driverId,
                            "type" to "RECHARGE",
                            "amount" to amount,
                            "description" to "Wallet recharge by admin",
                            "status" to "SUCCESS",
                            "createdAt" to Timestamp.now()
                        )
                        db.collection("driver_transactions").add(transaction)

                        Log.d("AdminDriverVM", "✅ Recharge success: ₹$amount")
                        _successMessage.value = "✅ ₹$amount recharged!"
                        _isLoading.value = false
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("AdminDriverVM", "❌ Recharge failed: ${e.message}")
                        _errorMessage.value = "Failed to recharge: ${e.message}"
                        _isLoading.value = false
                        callback(false)
                    }
            } catch (e: Exception) {
                Log.e("AdminDriverVM", "❌ Recharge error: ${e.message}")
                _errorMessage.value = "Failed to recharge: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
        }
    }

    // ✅ Delete Driver - NO loadDrivers()
    fun deleteDriver(driverId: String, callback: (Boolean) -> Unit) {
        if (driverId.isEmpty()) {
            _errorMessage.value = "❌ Cannot delete: Driver ID is empty!"
            callback(false)
            return
        }

        _isLoading.value = true
        Log.d("AdminDriverVM", "🗑️ Deleting: $driverId")

        db.collection("drivers").document(driverId)
            .delete()
            .addOnSuccessListener {
                db.collection("driver_locations").document(driverId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d("AdminDriverVM", "✅ Deleted: $driverId")
                        _successMessage.value = "🗑️ Driver deleted!"
                        _isLoading.value = false
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("AdminDriverVM", "⚠️ Location delete failed: ${e.message}")
                        _isLoading.value = false
                        callback(true)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AdminDriverVM", "❌ Delete failed: ${e.message}")
                _errorMessage.value = "Failed to delete: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
    }

    fun clearError() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        isListenerAttached = false
    }
}