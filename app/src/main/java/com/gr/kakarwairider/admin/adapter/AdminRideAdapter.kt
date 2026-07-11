package com.gr.kakarwairider.admin.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AdminRideAdapter(
    private val rides: List<RideModel>,
    private val onAssignClick: (RideModel) -> Unit,
    private val onReassignClick: (RideModel) -> Unit,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<AdminRideAdapter.RideViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private var areaCenterLat = 0.0
    private var areaCenterLng = 0.0

    init {
        db.collection("areas").get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val center = doc.getGeoPoint("center")
                    if (center != null) {
                        areaCenterLat = center.latitude
                        areaCenterLng = center.longitude
                        break
                    }
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_ride, parent, false)
        return RideViewHolder(view)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(rides[position])
    }

    override fun getItemCount(): Int = rides.size

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    inner class RideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRideId: TextView = itemView.findViewById(R.id.tvRideId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvUserPhone: TextView = itemView.findViewById(R.id.tvUserPhone)
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        private val tvVehicle: TextView = itemView.findViewById(R.id.tvVehicle)
        private val tvDistanceCenterToPickup: TextView = itemView.findViewById(R.id.tvDistanceCenterToPickup)
        private val tvDistancePickupToDest: TextView = itemView.findViewById(R.id.tvDistancePickupToDest)
        private val btnAssign: MaterialButton = itemView.findViewById(R.id.btnAssignDriver)
        private val btnCancel: MaterialButton = itemView.findViewById(R.id.btnCancelRide)
        private val btnComplete: MaterialButton = itemView.findViewById(R.id.btnCompleteRide)
        private val btnReassign: MaterialButton = itemView.findViewById(R.id.btnReassignDriver)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnRoute: MaterialButton = itemView.findViewById(R.id.btnRoute)

        fun bind(ride: RideModel) {
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvStatus.text = ride.status
            tvUserPhone.text = "📱 ${ride.userPhone}"
            tvDriverName.text = "🚗 ${ride.driverName ?: "Not Assigned"}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"
            tvVehicle.text = "${ride.vehicleIcon} ${ride.vehicleName}"

            val statusColor = when (ride.status) {
                "PENDING" -> itemView.context.getColor(R.color.orange)
                "DRIVER_ASSIGNED", "ACCEPTED" -> itemView.context.getColor(R.color.blue)
                "STARTED" -> itemView.context.getColor(R.color.green)
                "COMPLETED" -> itemView.context.getColor(R.color.green)
                "CANCELLED" -> itemView.context.getColor(R.color.red)
                else -> itemView.context.getColor(R.color.grey)
            }
            tvStatus.setTextColor(statusColor)

            // Distances
            val pickupLat = ride.pickup?.lat ?: 0.0
            val pickupLng = ride.pickup?.lng ?: 0.0
            val destLat = ride.destination?.lat ?: 0.0
            val destLng = ride.destination?.lng ?: 0.0

            val distCenterToPickup = calculateDistance(areaCenterLat, areaCenterLng, pickupLat, pickupLng)
            val distPickupToDest = calculateDistance(pickupLat, pickupLng, destLat, destLng)

            tvDistanceCenterToPickup.text = "📏 Center→Pickup: %.2f km".format(distCenterToPickup)
            tvDistancePickupToDest.text = "📏 Pickup→Dest: %.2f km".format(distPickupToDest)

            // ✅ Call Button
            btnCall.setOnClickListener {
                val phoneNumber = ride.userPhone
                if (phoneNumber.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                    }
                    itemView.context.startActivity(intent)
                } else {
                    Toast.makeText(itemView.context, "No phone number available", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ Route Button
            btnRoute.setOnClickListener {
                val centerLat = areaCenterLat
                val centerLng = areaCenterLng
                val url = "https://www.google.com/maps/dir/$centerLat,$centerLng/${ride.pickup?.lat},${ride.pickup?.lng}/${ride.destination?.lat},${ride.destination?.lng}"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.setPackage("com.google.android.apps.maps")
                    if (intent.resolveActivity(itemView.context.packageManager) != null) {
                        itemView.context.startActivity(intent)
                    } else {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        itemView.context.startActivity(browserIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(itemView.context, "Error opening maps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ Status-based button visibility
            when (ride.status) {
                "PENDING" -> {
                    btnAssign.visibility = View.VISIBLE
                    btnCancel.visibility = View.VISIBLE
                    btnComplete.visibility = View.GONE
                    btnReassign.visibility = View.GONE
                    btnAssign.setOnClickListener { onAssignClick(ride) }
                    btnCancel.setOnClickListener { showCancelDialog(ride) }
                }
                "DRIVER_ASSIGNED", "ACCEPTED" -> {
                    btnAssign.visibility = View.GONE
                    btnCancel.visibility = View.VISIBLE
                    btnComplete.visibility = View.GONE
                    btnReassign.visibility = View.VISIBLE
                    btnCancel.setOnClickListener { showCancelDialog(ride) }
                    btnReassign.setOnClickListener { onReassignClick(ride) }
                }
                "STARTED" -> {
                    btnAssign.visibility = View.GONE
                    btnCancel.visibility = View.VISIBLE
                    btnComplete.visibility = View.VISIBLE
                    btnReassign.visibility = View.GONE
                    btnCancel.setOnClickListener { showCancelDialog(ride) }
                    btnComplete.setOnClickListener { completeRide(ride) }
                }
                else -> {
                    btnAssign.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnReassign.visibility = View.GONE
                }
            }
        }

        private fun showCancelDialog(ride: RideModel) {
            val reasons = arrayOf(
                "Driver not available",
                "User not reachable",
                "Vehicle issue",
                "Weather conditions",
                "Technical issue",
                "Other"
            )

            AlertDialog.Builder(itemView.context)
                .setTitle("Cancel Ride")
                .setMessage("Select reason for cancelling Ride #${ride.rideId.takeLast(8)}")
                .setItems(reasons) { _, which ->
                    val reason = reasons[which]
                    cancelRide(ride, reason)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun cancelRide(ride: RideModel, reason: String) {
            db.collection("rides").document(ride.rideId)
                .update(
                    mapOf(
                        "status" to "CANCELLED",
                        "cancelReason" to reason,
                        "cancelledBy" to "admin",
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(itemView.context, "✅ Ride cancelled! Reason: $reason", Toast.LENGTH_LONG).show()
                    onRefresh()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(itemView.context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        private fun completeRide(ride: RideModel) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Complete Ride")
                .setMessage("Are you sure you want to complete Ride #${ride.rideId.takeLast(8)}?")
                .setPositiveButton("Yes") { _, _ ->
                    db.collection("rides").document(ride.rideId)
                        .update(
                            mapOf(
                                "status" to "COMPLETED",
                                "completedAt" to com.google.firebase.Timestamp.now(),
                                "updatedAt" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(itemView.context, "✅ Ride Completed!", Toast.LENGTH_LONG).show()
                            onRefresh()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(itemView.context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}