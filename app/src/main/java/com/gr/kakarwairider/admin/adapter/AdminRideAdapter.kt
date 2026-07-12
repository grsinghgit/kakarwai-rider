package com.gr.kakarwairider.admin.adapter

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.repository.DriverInfo
import com.gr.kakarwairider.model.RideModel
import java.text.SimpleDateFormat
import java.util.*

class AdminRideAdapter(
    private val rides: List<RideModel>,
    private val availableDrivers: List<DriverInfo>,
    private val onAssign: (RideModel, String, String) -> Unit,
    private val onReassign: (RideModel, String, String) -> Unit,
    private val onCancel: (RideModel, String) -> Unit,
    private val onComplete: (RideModel) -> Unit
) : RecyclerView.Adapter<AdminRideAdapter.RideViewHolder>() {

    private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_admin_card, parent, false)
        return RideViewHolder(view)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(rides[position])
    }

    override fun getItemCount(): Int = rides.size

    inner class RideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRideId: TextView = itemView.findViewById(R.id.tvRideId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvUserPhone: TextView = itemView.findViewById(R.id.tvUserPhone)
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        private val tvVehicle: TextView = itemView.findViewById(R.id.tvVehicle)
        private val tvCancelReason: TextView = itemView.findViewById(R.id.tvCancelReason)
        private val tvTimeline: TextView = itemView.findViewById(R.id.tvTimeline)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnRoute: MaterialButton = itemView.findViewById(R.id.btnRoute)
        private val btnAssign: MaterialButton = itemView.findViewById(R.id.btnAssign)
        private val btnReassign: MaterialButton = itemView.findViewById(R.id.btnReassign)
        private val btnComplete: MaterialButton = itemView.findViewById(R.id.btnComplete)
        private val btnCancel: MaterialButton = itemView.findViewById(R.id.btnCancel)

        fun bind(ride: RideModel) {
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvStatus.text = ride.status
            tvUserPhone.text = "📱 ${ride.userPhone}"
            tvDriverName.text = "🚗 ${ride.driverName ?: "Not Assigned"}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"
            tvVehicle.text = "${ride.vehicleIcon} ${ride.vehicleName}"

            // Status color
            val statusColor = when (ride.status) {
                "PENDING", "SEARCHING" -> itemView.context.getColor(R.color.orange)
                "DRIVER_ASSIGNED", "ACCEPTED" -> itemView.context.getColor(R.color.blue)
                "STARTED", "ON_THE_WAY", "ARRIVED_PICKUP", "DESTINATION_REACHED" -> itemView.context.getColor(R.color.green)
                "COMPLETED" -> itemView.context.getColor(R.color.green)
                "CANCELLED" -> itemView.context.getColor(R.color.red)
                else -> itemView.context.getColor(R.color.grey)
            }
            tvStatus.setTextColor(statusColor)

            // Status background
            val statusBg = when (ride.status) {
                "PENDING", "SEARCHING" -> R.drawable.bg_status_pending
                "DRIVER_ASSIGNED", "ACCEPTED" -> R.drawable.bg_status_assigned
                "STARTED", "ON_THE_WAY", "ARRIVED_PICKUP", "DESTINATION_REACHED" -> R.drawable.bg_status_started
                "COMPLETED" -> R.drawable.bg_status_completed
                "CANCELLED" -> R.drawable.bg_status_cancelled
                else -> 0
            }
            if (statusBg != 0) {
                tvStatus.setBackgroundResource(statusBg)
            }

            // Cancel reason
            if (ride.status == "CANCELLED" && !ride.cancelReason.isNullOrEmpty()) {
                tvCancelReason.text = "❌ Reason: ${ride.cancelReason}"
                tvCancelReason.visibility = View.VISIBLE
            } else {
                tvCancelReason.visibility = View.GONE
            }

            // Timeline
            val timeline = StringBuilder()
            ride.createdAt?.let {
                timeline.append("📅 Booking: ${dateFormat.format(it.toDate())}")
            }
            ride.driverId?.let {
                ride.updatedAt?.let { updated ->
                    timeline.append(" → Assigned: ${dateFormat.format(updated.toDate())}")
                }
            }
            ride.completedAt?.let {
                timeline.append(" → Complete: ${dateFormat.format(it.toDate())}")
            }
            tvTimeline.text = timeline.toString()

            // ✅ BUTTONS VISIBILITY - ALL STATUSES
            when (ride.status) {
                "PENDING", "SEARCHING" -> {
                    btnAssign.visibility = View.VISIBLE
                    btnReassign.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnCancel.visibility = View.VISIBLE
                    btnAssign.setOnClickListener { showAssignDialog(ride, false) }
                    btnCancel.setOnClickListener { showCancelDialog(ride) }
                }
                "DRIVER_ASSIGNED", "ACCEPTED" -> {
                    btnAssign.visibility = View.GONE
                    btnReassign.visibility = View.VISIBLE
                    btnComplete.visibility = View.GONE
                    btnCancel.visibility = View.VISIBLE
                    btnReassign.setOnClickListener { showAssignDialog(ride, true) }
                    btnCancel.setOnClickListener { showCancelDialog(ride) }
                }
                "STARTED", "ON_THE_WAY", "ARRIVED_PICKUP", "DESTINATION_REACHED" -> {
                    btnAssign.visibility = View.GONE
                    btnReassign.visibility = View.GONE
                    btnComplete.visibility = View.VISIBLE  // ✅ COMPLETE BUTTON SHOW
                    btnCancel.visibility = View.VISIBLE    // ✅ CANCEL BUTTON SHOW
                    btnComplete.setOnClickListener {
                        AlertDialog.Builder(itemView.context)
                            .setTitle("✅ Complete Ride")
                            .setMessage("Are you sure you want to complete this ride?")
                            .setPositiveButton("Complete") { _, _ -> onComplete(ride) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    btnCancel.setOnClickListener { showCancelDialog(ride) }
                }
                "COMPLETED" -> {
                    btnAssign.visibility = View.GONE
                    btnReassign.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                }
                "CANCELLED" -> {
                    btnAssign.visibility = View.GONE
                    btnReassign.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                }
                else -> {
                    btnAssign.visibility = View.GONE
                    btnReassign.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                }
            }

            // Call
            btnCall.setOnClickListener {
                val phone = ride.userPhone
                if (phone.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    itemView.context.startActivity(intent)
                } else {
                    Toast.makeText(itemView.context, "No phone number", Toast.LENGTH_SHORT).show()
                }
            }

            // Route - Google Maps
            btnRoute.setOnClickListener {
                val pickupLat = ride.pickup?.lat ?: 0.0
                val pickupLng = ride.pickup?.lng ?: 0.0
                val destLat = ride.destination?.lat ?: 0.0
                val destLng = ride.destination?.lng ?: 0.0
                val url = "https://www.google.com/maps/dir/$pickupLat,$pickupLng/$destLat,$destLng"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.setPackage("com.google.android.apps.maps")
                try {
                    itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    itemView.context.startActivity(browserIntent)
                }
            }
        }

        private fun showAssignDialog(ride: RideModel, isReassign: Boolean = false) {
            val available = availableDrivers.filter { it.isAvailable }
            if (available.isEmpty()) {
                Toast.makeText(itemView.context, "No drivers available", Toast.LENGTH_SHORT).show()
                return
            }

            val availableList = if (isReassign) {
                available.filter { it.driverId != ride.driverId }
            } else {
                available
            }

            if (availableList.isEmpty()) {
                Toast.makeText(itemView.context, "No other drivers available", Toast.LENGTH_SHORT).show()
                return
            }

            val driverNames = availableList.map { "${it.name} (${it.phone})" }.toTypedArray()

            AlertDialog.Builder(itemView.context)
                .setTitle(if (isReassign) "🔄 Reassign Driver" else "✅ Assign Driver")
                .setItems(driverNames) { _, which ->
                    val driver = availableList[which]
                    Log.d("AdminRideAdapter", "📌 Selected: ${driver.name} (${driver.driverId})")

                    if (isReassign) {
                        onReassign(ride, driver.driverId, driver.name)
                    } else {
                        onAssign(ride, driver.driverId, driver.name)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
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
                .setTitle("❌ Cancel Ride")
                .setItems(reasons) { _, which ->
                    onCancel(ride, reasons[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}