package com.gr.kakarwairider.driver.adapter

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

class DriverRideAdapter(
    private val rides: List<RideModel>,
    private val onAccept: (RideModel) -> Unit,
    private val onReject: (RideModel) -> Unit
) : RecyclerView.Adapter<DriverRideAdapter.RideViewHolder>() {

    private val dateFormat = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())

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
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)

        fun bind(ride: RideModel) {
            tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
            tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
            tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
            tvFare.text = "💰 ₹${ride.totalFare.toInt()}"

            ride.createdAt?.let {
                tvTime.text = dateFormat.format(it.toDate())
            }

            // Status and buttons
            when (ride.status) {
                "DRIVER_ASSIGNED" -> {
                    tvStatus.text = "⏳ New Request"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.orange))
                    btnAccept.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                    btnAccept.setOnClickListener { onAccept(ride) }
                    btnReject.setOnClickListener { onReject(ride) }
                }
                "ACCEPTED" -> {
                    tvStatus.text = "✅ Accepted"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.green))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
                "STARTED" -> {
                    tvStatus.text = "🚗 Started"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.blue))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
                else -> {
                    tvStatus.text = ride.status
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
            }
        }
    }
}