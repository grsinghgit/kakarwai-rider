package com.gr.kakarwairider.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.model.LocationData
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.model.VehicleOption
import com.gr.kakarwairider.utils.DistanceUtils
import java.util.*

class BookRideViewModel : ViewModel() {

    private val TAG = "BookRideViewModel"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _rideCreated = MutableLiveData<RideModel?>(null)
    val rideCreated: LiveData<RideModel?> = _rideCreated

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _areaVehicles = MutableLiveData<List<VehicleOption>>(emptyList())
    val areaVehicles: LiveData<List<VehicleOption>> = _areaVehicles

    private var currentAreaId: String? = null
    private var currentAreaAdminId: String? = null

    fun fetchVehiclesForArea(pickupLat: Double, pickupLng: Double) {
        _isLoading.value = true

        findNearestArea(pickupLat, pickupLng) { areaId, adminId, vehicleRates ->
            if (areaId == null || vehicleRates == null) {
                _errorMessage.value = "No vehicles available in your area"
                _isLoading.value = false
                return@findNearestArea
            }

            currentAreaId = areaId
            currentAreaAdminId = adminId

            val vehicles = mutableListOf<VehicleOption>()
            vehicleRates.forEach { (key, value) ->
                val data = value as? Map<*, *>
                if (data != null) {
                    val icon = data["icon"] as? String ?: "🚗"
                    val name = data["name"] as? String ?: key
                    val basePrice = (data["basePrice"] as? Number)?.toDouble() ?: 0.0
                    val perKmRate = (data["perKmRate"] as? Number)?.toDouble() ?: 0.0
                    val perMinRate = (data["perMinRate"] as? Number)?.toDouble() ?: 1.0

                    vehicles.add(
                        VehicleOption(
                            type = key,
                            icon = icon,
                            name = name,
                            basePrice = basePrice,
                            perKmRate = perKmRate,
                            perMinRate = perMinRate
                        )
                    )
                }
            }

            _areaVehicles.value = vehicles
            _isLoading.value = false
            Log.d(TAG, "✅ Loaded ${vehicles.size} vehicles from area")
        }
    }

    private fun findNearestArea(
        userLat: Double,
        userLng: Double,
        callback: (areaId: String?, adminId: String?, vehicleRates: Map<String, Any>?) -> Unit
    ) {
        db.collection("areas")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    Log.d(TAG, "No active areas found")
                    callback(null, null, null)
                    return@addOnSuccessListener
                }

                var nearestAreaId: String? = null
                var nearestAdminId: String? = null
                var nearestVehicleRates: Map<String, Any>? = null
                var minDistance = Double.MAX_VALUE

                for (document in documents) {
                    val center = document.getGeoPoint("center")
                    val radiusKm = document.getDouble("radiusKm") ?: 50.0
                    val areaId = document.id
                    val adminId = document.getString("adminId")
                    val vehicleRates = document.get("vehicleRates") as? Map<String, Any>

                    if (center != null) {
                        val distance = DistanceUtils.calculateDistance(
                            userLat, userLng,
                            center.latitude, center.longitude
                        )

                        if (distance <= radiusKm && distance < minDistance) {
                            minDistance = distance
                            nearestAreaId = areaId
                            nearestAdminId = adminId
                            nearestVehicleRates = vehicleRates
                        }
                    }
                }

                if (nearestAreaId != null) {
                    callback(nearestAreaId, nearestAdminId, nearestVehicleRates)
                } else {
                    callback(null, null, null)
                }
            }
            .addOnFailureListener {
                callback(null, null, null)
            }
    }

    fun bookRide(
        pickupAddress: String,
        pickupLat: Double,
        pickupLng: Double,
        destinationAddress: String,
        destinationLat: Double,
        destinationLng: Double,
        vehicleType: String,
        vehicleIcon: String,
        vehicleName: String,
        distance: Double,
        duration: Int,
        basePrice: Double,
        perKmRate: Double,
        totalFare: Double,
        serviceType: String = "GOODS",
        destinationPhone: String = "",
        goodsType: String = "",
        goodsConsent: Boolean = false
    ) {
        Log.d(TAG, "========== BOOK RIDE STARTED ==========")
        Log.d(TAG, "Service: $serviceType, Vehicle: $vehicleName")

        _isLoading.value = true

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "User not logged in"
            _isLoading.value = false
            return
        }

        val areaId = currentAreaId
        val adminId = currentAreaAdminId

        if (areaId == null) {
            _errorMessage.value = "No area found for your location"
            _isLoading.value = false
            return
        }

        createRide(
            rideId = db.collection("rides").document().id,
            userId = currentUser.uid,
            userPhone = currentUser.phoneNumber ?: "",
            pickup = LocationData(pickupAddress, pickupLat, pickupLng),
            destination = LocationData(destinationAddress, destinationLat, destinationLng),
            vehicleType = vehicleType,
            vehicleIcon = vehicleIcon,
            vehicleName = vehicleName,
            distance = distance,
            duration = duration,
            basePrice = basePrice,
            perKmRate = perKmRate,
            totalFare = totalFare,
            areaId = areaId,
            adminId = adminId,
            serviceType = serviceType,
            destinationPhone = destinationPhone,
            goodsType = goodsType,
            goodsConsent = goodsConsent
        )
    }

    private fun createRide(
        rideId: String,
        userId: String,
        userPhone: String,
        pickup: LocationData,
        destination: LocationData,
        vehicleType: String,
        vehicleIcon: String,
        vehicleName: String,
        distance: Double,
        duration: Int,
        basePrice: Double,
        perKmRate: Double,
        totalFare: Double,
        areaId: String,
        adminId: String?,
        serviceType: String = "GOODS",
        destinationPhone: String = "",
        goodsType: String = "",
        goodsConsent: Boolean = false
    ) {
        Log.d(TAG, "Creating ride...")

        val currentTime = Timestamp.now()
        val expireTime = Timestamp(Date(System.currentTimeMillis() + (5 * 60 * 1000)))

        val rideData = hashMapOf(
            "rideId" to rideId,
            "userId" to userId,
            "userPhone" to userPhone,
            "userName" to "",
            "serviceType" to serviceType,
            "pickup" to hashMapOf(
                "address" to pickup.address,
                "lat" to pickup.lat,
                "lng" to pickup.lng
            ),
            "destination" to hashMapOf(
                "address" to destination.address,
                "lat" to destination.lat,
                "lng" to destination.lng
            ),
            "destinationPhone" to destinationPhone,
            "goodsType" to goodsType,
            "goodsConsent" to goodsConsent,
            "vehicleType" to vehicleType,
            "vehicleIcon" to vehicleIcon,
            "vehicleName" to vehicleName,
            "distance" to distance,
            "duration" to duration,
            "basePrice" to basePrice,
            "perKmRate" to perKmRate,
            "distanceFare" to (distance * perKmRate),
            "totalFare" to totalFare,
            "areaId" to areaId,
            "adminId" to (adminId ?: ""),
            "status" to "PENDING",
            "driverId" to null,
            "driverName" to null,
            "driverPhone" to null,
            "driverVehicle" to null,
            "driverVehicleNumber" to null,
            "paymentMethod" to "CASH",
            "paymentStatus" to "PENDING",
            "createdAt" to currentTime,
            "updatedAt" to currentTime,
            "expiresAt" to expireTime
        )

        db.collection("rides")
            .document(rideId)
            .set(rideData)
            .addOnSuccessListener {
                Log.d(TAG, "Ride created successfully!")
                _isLoading.value = false

                val ride = RideModel(
                    rideId = rideId,
                    userId = userId,
                    userPhone = userPhone,
                    userName = "",
                    serviceType = serviceType,
                    pickup = pickup,
                    destination = destination,
                    destinationPhone = destinationPhone,
                    goodsType = goodsType,
                    goodsConsent = goodsConsent,
                    vehicleType = vehicleType,
                    vehicleIcon = vehicleIcon,
                    vehicleName = vehicleName,
                    distance = distance,
                    duration = duration,
                    basePrice = basePrice,
                    perKmRate = perKmRate,
                    distanceFare = distance * perKmRate,
                    totalFare = totalFare,
                    status = "PENDING",
                    driverId = null,
                    driverName = null,
                    driverPhone = null,
                    driverVehicle = null,
                    driverVehicleNumber = null,
                    areaId = areaId,
                    adminId = adminId ?: "",
                    paymentMethod = "CASH",
                    paymentStatus = "PENDING",
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    expiresAt = expireTime
                )
                _rideCreated.value = ride
                _errorMessage.value = null
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed: ${e.message}")
                _isLoading.value = false
                _errorMessage.value = "Failed to book ride: ${e.message}"
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}