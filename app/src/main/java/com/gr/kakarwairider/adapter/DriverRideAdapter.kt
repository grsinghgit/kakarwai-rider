package com.gr.kakarwairider.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel

class DriverRideAdapter(
    private val rides: List<RideModel>,
    private val onAccept: (RideModel) -> Unit,
    private val onReject: (RideModel) -> Unit,
    private val onCancel: (RideModel) -> Unit  // ✅ Add this
) : RecyclerView.Adapter<DriverRideAdapter.RideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_driver_ride, parent, false)
        return RideViewHolder(view)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(rides[position])
    }

    override fun getItemCount(): Int = rides.size

    inner class RideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRideId: TextView = itemView.findViewById(R.id.tvRideId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        private val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)

        fun bind(ride: RideModel) {
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvStatus.text = ride.status
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"

            // ✅ Hide buttons if already accepted/rejected
            if (ride.status != "DRIVER_ASSIGNED") {
                btnAccept.visibility = View.GONE
                btnReject.visibility = View.GONE
            } else {
                btnAccept.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE
                btnAccept.setOnClickListener { onAccept(ride) }
                btnReject.setOnClickListener { onReject(ride) }
            }
        }
    }
}