package com.gr.kakarwairider.admin.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.admin.model.RideStatsModel
import com.gr.kakarwairider.model.RideModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class AdminRideRepository {

    private val db = FirebaseFirestore.getInstance()

    // ✅ Get rides with real-time updates
    fun getRidesByArea(areaId: String): Flow<List<RideModel>> = callbackFlow {
        val listener = db.collection("rides")
            .whereEqualTo("areaId", areaId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val rides = snapshots?.documents?.mapNotNull {
                    val ride = it.toObject<RideModel>()
                    ride?.copy(rideId = it.id)
                } ?: emptyList()
                trySend(rides)
            }
        awaitClose { listener.remove() }
    }

    // ✅ Get stats
    fun getStats(rides: List<RideModel>): RideStatsModel {
        val today = Date()
        val todayStart = Timestamp(Date(today.time - 24 * 60 * 60 * 1000))

        val todayRides = rides.filter { it.createdAt?.toDate()?.after(todayStart.toDate()) == true }

        return RideStatsModel(
            totalRides = rides.size,
            pendingRides = rides.count { it.status == "PENDING" || it.status == "SEARCHING" },
            activeRides = rides.count { it.status in listOf("DRIVER_ASSIGNED", "ACCEPTED", "STARTED", "ON_THE_WAY", "ARRIVED_PICKUP", "DESTINATION_REACHED") },
            completedRides = rides.count { it.status == "COMPLETED" },
            cancelledRides = rides.count { it.status == "CANCELLED" },
            todayEarnings = todayRides.filter { it.status == "COMPLETED" }.sumOf { it.totalFare },
            totalEarnings = rides.filter { it.status == "COMPLETED" }.sumOf { it.totalFare }
        )
    }

    // ✅ Get available drivers (exclude busy)
    fun getAvailableDrivers(): Flow<List<DriverInfo>> = callbackFlow {
        val listener = db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val drivers = snapshots?.documents?.mapNotNull { doc ->
                    DriverInfo(
                        driverId = doc.id,
                        name = doc.getString("driverName") ?: "Unknown",
                        phone = doc.getString("driverPhone") ?: "N/A",
                        location = doc.getGeoPoint("currentLocation"),
                        isAvailable = doc.getBoolean("isAvailable") ?: false
                    )
                } ?: emptyList()
                trySend(drivers)
            }
        awaitClose { listener.remove() }
    }

    // ✅ FIXED: assignDriver - with proper await and error handling
    suspend fun assignDriver(rideId: String, driverId: String, driverName: String): Boolean {
        return try {
            db.collection("rides").document(rideId)
                .update(
                    mapOf(
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "status" to "DRIVER_ASSIGNED",
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            Log.d("AdminRideRepo", "✅ Driver assigned: $driverName to $rideId")
            true
        } catch (e: Exception) {
            Log.e("AdminRideRepo", "❌ Assign failed: ${e.message}")
            false
        }
    }

    // ✅ FIXED: reassignDriver
    suspend fun reassignDriver(rideId: String, driverId: String, driverName: String): Boolean {
        return assignDriver(rideId, driverId, driverName)
    }

    // ✅ FIXED: cancelRide
    suspend fun cancelRide(rideId: String, reason: String, cancelledBy: String): Boolean {
        return try {
            db.collection("rides").document(rideId)
                .update(
                    mapOf(
                        "status" to "CANCELLED",
                        "cancelReason" to reason,
                        "cancelledBy" to cancelledBy,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            Log.d("AdminRideRepo", "✅ Ride cancelled: $rideId")
            true
        } catch (e: Exception) {
            Log.e("AdminRideRepo", "❌ Cancel failed: ${e.message}")
            false
        }
    }

    // ✅ FIXED: completeRide
    suspend fun completeRide(rideId: String): Boolean {
        return try {
            db.collection("rides").document(rideId)
                .update(
                    mapOf(
                        "status" to "COMPLETED",
                        "completedAt" to Timestamp.now(),
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            Log.d("AdminRideRepo", "✅ Ride completed: $rideId")
            true
        } catch (e: Exception) {
            Log.e("AdminRideRepo", "❌ Complete failed: ${e.message}")
            false
        }
    }
}

data class DriverInfo(
    val driverId: String,
    val name: String,
    val phone: String,
    val location: com.google.firebase.firestore.GeoPoint?,
    val isAvailable: Boolean
)