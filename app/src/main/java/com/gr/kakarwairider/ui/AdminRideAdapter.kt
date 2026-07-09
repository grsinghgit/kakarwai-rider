package com.gr.kakarwairider.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel

class AdminRideAdapter(
    private val rides: List<RideModel>,
    private val onAssignClick: (RideModel) -> Unit
) : RecyclerView.Adapter<AdminRideAdapter.RideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_ride, parent, false)
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
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        private val tvVehicle: TextView = itemView.findViewById(R.id.tvVehicle)
        private val btnAssign: MaterialButton = itemView.findViewById(R.id.btnAssignDriver)

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

            // Hide Assign button if already assigned
            if (ride.status != "PENDING") {
                btnAssign.visibility = View.GONE
            } else {
                btnAssign.visibility = View.VISIBLE
                btnAssign.setOnClickListener {
                    onAssignClick(ride)
                }
            }
        }
    }
}