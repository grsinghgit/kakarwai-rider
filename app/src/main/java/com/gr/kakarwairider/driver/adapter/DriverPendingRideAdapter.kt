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
import com.google.android.material.textfield.TextInputEditText
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.utils.DistanceUtils
import java.text.SimpleDateFormat
import java.util.*

class DriverPendingRideAdapter(
    private var rides: List<RideModel>,
    private val onAccept: (RideModel) -> Unit,
    private val onReject: (RideModel) -> Unit,
    private val onArrivedPickup: (RideModel) -> Unit,
    private val onSubmitPin: (RideModel, String) -> Unit,
    private val onArrivedDestination: (RideModel) -> Unit,
    private val onSubmitCompletePin: (RideModel, String) -> Unit
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
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)  // ✅ NEW
        private val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnRoute: MaterialButton = itemView.findViewById(R.id.btnRoute)
        private val btnArrivedPickup: MaterialButton = itemView.findViewById(R.id.btnArrivedPickup)
        private val llPinEntry: View = itemView.findViewById(R.id.llPinEntry)
        private val etPin: TextInputEditText = itemView.findViewById(R.id.etPin)
        private val btnSubmitPin: MaterialButton = itemView.findViewById(R.id.btnSubmitPin)
        private val btnArrivedDestination: MaterialButton = itemView.findViewById(R.id.btnArrivedDestination)
        private val llCompletePinEntry: View = itemView.findViewById(R.id.llCompletePinEntry)
        private val etCompletePin: TextInputEditText = itemView.findViewById(R.id.etCompletePin)
        private val btnSubmitCompletePin: MaterialButton = itemView.findViewById(R.id.btnSubmitCompletePin)

        fun bind(ride: RideModel) {
            val context = itemView.context

            Log.d("PendingRideAdapter", "📌 Binding: ${ride.rideId}, status: ${ride.status}")

            // ✅ Set basic info
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"

            // ✅ Show Fare with Distance Details
            if (ride.fareCalculated && ride.totalFare > 0) {
                tvFare.text = "💰 ₹${DistanceUtils.formatFareInt(ride.totalFare)}"

                // ✅ Distance in One Line: Driver → Pickup + Pickup → Destination = Total
                val pickupDist = DistanceUtils.formatDistance(ride.pickupDistance)
                val tripDist = DistanceUtils.formatDistance(ride.tripDistance)
                val totalDist = DistanceUtils.formatDistance(ride.totalDistance)
                tvDistance.text = "📍 ${pickupDist}km + ${tripDist}km = ${totalDist}km"
                tvDistance.visibility = View.VISIBLE
            } else {
                tvFare.text = "💰 Calculating..."
                tvDistance.visibility = View.GONE
            }

            tvUserPhone.text = "📞 ${ride.userPhone}"

            ride.createdAt?.let {
                tvTime.text = dateFormat.format(it.toDate())
            }

            // ✅ SAB BUTTONS PEHLE HIDE KARO
            btnAccept.visibility = View.GONE
            btnReject.visibility = View.GONE
            btnArrivedPickup.visibility = View.GONE
            llPinEntry.visibility = View.GONE
            btnArrivedDestination.visibility = View.GONE
            llCompletePinEntry.visibility = View.GONE
            btnCall.visibility = View.VISIBLE
            btnRoute.visibility = View.VISIBLE

            // ✅ STATUS KE HISAB SE BUTTONS SHOW KARO
            when (ride.status) {
                "DRIVER_ASSIGNED" -> {
                    tvStatus.text = "🔄 New Request"
                    tvStatus.setTextColor(context.getColor(R.color.orange))
                    btnAccept.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                    Log.d("PendingRideAdapter", "   ✅ Showing Accept/Reject")
                }
                "ACCEPTED" -> {
                    tvStatus.text = "✅ Accepted"
                    tvStatus.setTextColor(context.getColor(R.color.green))
                    btnArrivedPickup.visibility = View.VISIBLE
                    Log.d("PendingRideAdapter", "   ✅ Showing Arrived Pickup")
                }
                "ARRIVED_PICKUP" -> {
                    tvStatus.text = "📍 Arrived at Pickup"
                    tvStatus.setTextColor(context.getColor(R.color.blue))
                    llPinEntry.visibility = View.VISIBLE
                    Log.d("PendingRideAdapter", "   ✅ Showing PIN Entry")
                }
                "ON_THE_WAY" -> {
                    tvStatus.text = "🚗 On The Way"
                    tvStatus.setTextColor(context.getColor(R.color.blue))
                    btnArrivedDestination.visibility = View.VISIBLE
                    Log.d("PendingRideAdapter", "   ✅ Showing Arrived Destination")
                }
                "DESTINATION_REACHED" -> {
                    tvStatus.text = "📍 Destination Reached"
                    tvStatus.setTextColor(context.getColor(R.color.orange))
                    llCompletePinEntry.visibility = View.VISIBLE
                    Log.d("PendingRideAdapter", "   ✅ Showing Complete PIN Entry")
                }
                "COMPLETED" -> {
                    tvStatus.text = "✅ Completed"
                    tvStatus.setTextColor(context.getColor(R.color.green))
                    Log.d("PendingRideAdapter", "   ✅ Completed")
                }
                "STARTED" -> {
                    tvStatus.text = "🚗 Started"
                    tvStatus.setTextColor(context.getColor(R.color.blue))
                    Log.d("PendingRideAdapter", "   ✅ Started")
                }
                else -> {
                    tvStatus.text = ride.status
                    Log.d("PendingRideAdapter", "   ✅ Other status: ${ride.status}")
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

            // ✅ ARRIVED AT PICKUP
            btnArrivedPickup.setOnClickListener {
                Log.d("PendingRideAdapter", "📍 Arrived Pickup: ${ride.rideId}")
                onArrivedPickup(ride)
            }

            // ✅ SUBMIT PICKUP PIN
            btnSubmitPin.setOnClickListener {
                val enteredPin = etPin.text.toString().trim()
                if (enteredPin.length != 4) {
                    Toast.makeText(context, "❌ Enter 4 digit PIN", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Log.d("PendingRideAdapter", "🔑 Submit Pickup PIN: $enteredPin")
                onSubmitPin(ride, enteredPin)
                etPin.text?.clear()
            }

            // ✅ ARRIVED AT DESTINATION
            btnArrivedDestination.setOnClickListener {
                Log.d("PendingRideAdapter", "📍 Arrived Destination: ${ride.rideId}")
                onArrivedDestination(ride)
            }

            // ✅ SUBMIT COMPLETE PIN
            btnSubmitCompletePin.setOnClickListener {
                val enteredPin = etCompletePin.text.toString().trim()
                if (enteredPin.length != 4) {
                    Toast.makeText(context, "❌ Enter 4 digit PIN", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Log.d("PendingRideAdapter", "🔑 Submit Complete PIN: $enteredPin")
                onSubmitCompletePin(ride, enteredPin)
                etCompletePin.text?.clear()
            }
        }
    }
}