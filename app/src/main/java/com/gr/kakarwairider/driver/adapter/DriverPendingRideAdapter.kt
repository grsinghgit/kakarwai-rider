package com.gr.kakarwairider.driver.adapter

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel
import java.text.SimpleDateFormat
import java.util.*

class DriverPendingRideAdapter(
    private var rides: List<RideModel>,
    private val onAccept: (RideModel) -> Unit,
    private val onReject: (RideModel) -> Unit
) : RecyclerView.Adapter<DriverPendingRideAdapter.PendingRideViewHolder>() {

    private val dateFormat = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingRideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_driver_pending_ride, parent, false)
        return PendingRideViewHolder(view)
    }

    override fun onBindViewHolder(holder: PendingRideViewHolder, position: Int) {
        holder.bind(rides[position])
    }

    override fun getItemCount(): Int = rides.size

    fun updateDrivers(newRides: List<RideModel>) {
        Log.d("PendingRideAdapter", "🔄 updateDrivers: ${newRides.size} rides")
        this.rides = newRides
        notifyDataSetChanged()
    }

    inner class PendingRideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRideId: TextView = itemView.findViewById(R.id.tvRideId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvUserPhone: TextView = itemView.findViewById(R.id.tvUserPhone)
        private val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnRoute: MaterialButton = itemView.findViewById(R.id.btnRoute)

        fun bind(ride: RideModel) {
            val context = itemView.context

            Log.d("PendingRideAdapter", "📌 Binding: ${ride.rideId}, status: ${ride.status}")

            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"
            tvUserPhone.text = "📞 ${ride.userPhone}"

            ride.createdAt?.let {
                tvTime.text = dateFormat.format(it.toDate())
            }

            // ✅ STATUS - DRIVER_ASSIGNED + ACCEPTED + STARTED
            when (ride.status) {
                "DRIVER_ASSIGNED" -> {
                    tvStatus.text = "🔄 New Request"
                    tvStatus.setTextColor(context.getColor(R.color.orange))
                    btnAccept.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                    btnCall.visibility = View.VISIBLE
                    btnRoute.visibility = View.VISIBLE
                }
                "ACCEPTED" -> {
                    tvStatus.text = "✅ Accepted"
                    tvStatus.setTextColor(context.getColor(R.color.green))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                    btnCall.visibility = View.VISIBLE
                    btnRoute.visibility = View.VISIBLE
                }
                "STARTED" -> {
                    tvStatus.text = "🚗 Started"
                    tvStatus.setTextColor(context.getColor(R.color.blue))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                    btnCall.visibility = View.VISIBLE
                    btnRoute.visibility = View.VISIBLE
                }
                else -> {
                    tvStatus.text = ride.status
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                    btnCall.visibility = View.VISIBLE
                    btnRoute.visibility = View.VISIBLE
                }
            }

            // ✅ CALL BUTTON
            btnCall.setOnClickListener {
                val phone = ride.userPhone
                if (phone.isEmpty()) {
                    Toast.makeText(context, "❌ Phone number not available", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Log.d("PendingRideAdapter", "📞 Calling: $phone")
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                context.startActivity(intent)
            }

            // ✅ ROUTE BUTTON
            btnRoute.setOnClickListener {
                val pickupLat = ride.pickup?.lat
                val pickupLng = ride.pickup?.lng
                val destLat = ride.destination?.lat
                val destLng = ride.destination?.lng

                if (pickupLat == null || pickupLng == null || destLat == null || destLng == null) {
                    Toast.makeText(context, "❌ Location data not available", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Log.d("PendingRideAdapter", "🗺️ Route: ($pickupLat, $pickupLng) → ($destLat, $destLng)")
                val uri = Uri.parse("https://www.google.com/maps/dir/$pickupLat,$pickupLng/$destLat,$destLng")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            }

            // ✅ ACCEPT
            btnAccept.setOnClickListener {
                Log.d("PendingRideAdapter", "✅ Accept: ${ride.rideId}")
                onAccept(ride)
            }

            // ✅ REJECT
            btnReject.setOnClickListener {
                Log.d("PendingRideAdapter", "❌ Reject: ${ride.rideId}")
                onReject(ride)
            }
        }
    }
}