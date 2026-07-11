package com.gr.kakarwairider.admin.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.gr.kakarwairider.admin.model.DriverLocationModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AdminMapRepository {

    private val db = FirebaseFirestore.getInstance()

    // ✅ Get online drivers
    fun getOnlineDrivers(
        onSuccess: (List<DriverLocationModel>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load drivers")
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    onError("No data")
                    return@addSnapshotListener
                }

                val drivers = mutableListOf<DriverLocationModel>()
                for (doc in snapshots) {
                    val data = doc.data
                    val driver = DriverLocationModel(
                        driverId = doc.id,
                        driverName = data["driverName"] as? String ?: "Unknown Driver",
                        driverPhone = data["driverPhone"] as? String ?: "N/A",
                        currentLocation = data["currentLocation"] as? GeoPoint,
                        status = data["status"] as? String ?: "OFFLINE",
                        isAvailable = data["isAvailable"] as? Boolean ?: false,
                        updatedAt = data["updatedAt"] as? com.google.firebase.Timestamp,
                        rideId = data["rideId"] as? String
                    )
                    drivers.add(driver)
                }
                onSuccess(drivers)
            }
    }

    // ✅ Get rides by areaId (Pending + Running)
    // ✅ Get all rides except COMPLETED, CANCELLED, EXPIRED
    fun getRidesByArea(
        areaId: String,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        // ✅ Sabhi status jo show karni hain (COMPLETED, CANCELLED, EXPIRED ko chhodkar)
        val statusList = listOf(
            "PENDING",
            "SEARCHING",
            "DRIVER_ASSIGNED",
            "ACCEPTED",
            "STARTED"
        )

        db.collection("rides")
            .whereEqualTo("areaId", areaId)
            .whereIn("status", statusList)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load rides")
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    onError("No data")
                    return@addSnapshotListener
                }

                val rides = mutableListOf<Map<String, Any>>()
                for (doc in snapshots) {
                    val data = doc.data
                    data["rideId"] = doc.id
                    rides.add(data)
                }
                onSuccess(rides)
            }
    }

    fun getActiveRide(rideId: String, callback: (Map<String, Any>?) -> Unit) {
        db.collection("rides").document(rideId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    callback(null)
                    return@addSnapshotListener
                }
                callback(snapshot.data)
            }
    }

    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // ✅ Get admin's areaId
    fun getAdminArea(adminId: String, callback: (String?) -> Unit) {
        db.collection("admins")
            .document(adminId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val areaId = document.getString("areaId")
                    callback(areaId)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}