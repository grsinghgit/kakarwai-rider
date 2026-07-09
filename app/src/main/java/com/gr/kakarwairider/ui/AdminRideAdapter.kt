package com.gr.kakarwairider.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
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
    private val onAssignClick: (RideModel) -> Unit
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
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        private val tvVehicle: TextView = itemView.findViewById(R.id.tvVehicle)
        private val tvDistanceCenterToPickup: TextView = itemView.findViewById(R.id.tvDistanceCenterToPickup)
        private val tvDistancePickupToDest: TextView = itemView.findViewById(R.id.tvDistancePickupToDest)
        private val btnAssign: MaterialButton = itemView.findViewById(R.id.btnAssignDriver)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnRoute: MaterialButton = itemView.findViewById(R.id.btnRoute)
        private val btnCancelRide: MaterialButton = itemView.findViewById(R.id.btnCancelRide)

        fun bind(ride: RideModel) {
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvStatus.text = ride.status
            tvUserPhone.text = "📱 ${ride.userPhone}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"
            tvVehicle.text = "${ride.vehicleIcon} ${ride.vehicleName}"

            val statusColor = when (ride.status) {
                "PENDING" -> itemView.context.getColor(R.color.orange)
                "DRIVER_ASSIGNED" -> itemView.context.getColor(R.color.green)
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

            // Call Button
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

            // Route Button
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

            // Cancel Ride Button
            if (ride.status == "PENDING") {
                btnCancelRide.visibility = View.VISIBLE
                btnCancelRide.setOnClickListener {
                    showCancelReasonDialog(ride)
                }
            } else {
                btnCancelRide.visibility = View.GONE
            }

            // Assign Driver Button
            if (ride.status != "PENDING") {
                btnAssign.visibility = View.GONE
            } else {
                btnAssign.visibility = View.VISIBLE
                btnAssign.setOnClickListener {
                    onAssignClick(ride)
                }
            }
        }

        // ✅ Cancel with Reason Dialog
        private fun showCancelReasonDialog(ride: RideModel) {
            val builder = AlertDialog.Builder(itemView.context)
            builder.setTitle("❌ Cancel Ride")
            builder.setMessage("Enter reason for cancelling Ride #${ride.rideId.takeLast(8)}")

            val input = EditText(itemView.context)
            input.hint = "Enter cancel reason..."
            input.setSingleLine(false)
            input.setLines(3)
            builder.setView(input)

            builder.setPositiveButton("✅ Submit") { dialog, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(itemView.context, "Please enter a reason", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cancelRide(ride, reason)
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            builder.show()
        }

        // ✅ Cancel Ride - Fixed Navigation
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

                    // ✅ SAFE NAVIGATION - Refresh AdminFragment
                    try {
                        val activity = itemView.context as? FragmentActivity
                        activity?.supportFragmentManager?.beginTransaction()
                            ?.replace(R.id.nav_host_fragment, AdminFragment())
                            ?.commit()
                    } catch (e: Exception) {
                        // Fallback: Just refresh the adapter data
                        Toast.makeText(itemView.context, "Refresh the page to see updates", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(itemView.context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}