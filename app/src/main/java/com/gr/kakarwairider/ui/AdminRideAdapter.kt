package com.gr.kakarwairider.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
        // ✅ Fetch area center
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

    // ✅ Distance Calculator
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

        fun bind(ride: RideModel) {
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvStatus.text = ride.status
            tvUserPhone.text = "📱 ${ride.userPhone}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"
            tvVehicle.text = "${ride.vehicleIcon} ${ride.vehicleName}"

            // Status Color
            val statusColor = when (ride.status) {
                "PENDING" -> itemView.context.getColor(R.color.orange)
                "DRIVER_ASSIGNED" -> itemView.context.getColor(R.color.green)
                else -> itemView.context.getColor(R.color.grey)
            }
            tvStatus.setTextColor(statusColor)

            // ✅ Calculate Distances
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

            // ✅ Google Maps Route Button
            btnRoute.setOnClickListener {
                openGoogleMapsRoute(ride)
            }

            // ✅ Assign Driver Button
            if (ride.status != "PENDING") {
                btnAssign.visibility = View.GONE
            } else {
                btnAssign.visibility = View.VISIBLE
                btnAssign.setOnClickListener {
                    onAssignClick(ride)
                }
            }
        }

        private fun openGoogleMapsRoute(ride: RideModel) {
            val centerLat = areaCenterLat
            val centerLng = areaCenterLng
            val pickupLat = ride.pickup?.lat ?: 0.0
            val pickupLng = ride.pickup?.lng ?: 0.0
            val destLat = ride.destination?.lat ?: 0.0
            val destLng = ride.destination?.lng ?: 0.0

            // ✅ Google Maps URL with multiple waypoints
            val url = "https://www.google.com/maps/dir/$centerLat,$centerLng/$pickupLat,$pickupLng/$destLat,$destLng"

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.setPackage("com.google.android.apps.maps")
                if (intent.resolveActivity(itemView.context.packageManager) != null) {
                    itemView.context.startActivity(intent)
                } else {
                    // Fallback to browser
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    itemView.context.startActivity(browserIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(itemView.context, "Error opening maps: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}