package com.gr.kakarwairider.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.VehicleOption

class VehicleSelectionAdapter(
    private val vehicles: List<VehicleOption>,
    private val onVehicleSelected: (VehicleOption) -> Unit
) : RecyclerView.Adapter<VehicleSelectionAdapter.VehicleViewHolder>() {

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle_select, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = vehicles[position]
        val isSelected = position == selectedPosition
        holder.bind(vehicle, isSelected)

        holder.itemView.setOnClickListener {
            // ✅ Use getAdapterPosition() instead of position directly
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val previousSelected = selectedPosition
                selectedPosition = currentPosition
                notifyItemChanged(previousSelected)
                notifyItemChanged(currentPosition)
                onVehicleSelected(vehicles[currentPosition])
            }
        }
    }

    override fun getItemCount(): Int = vehicles.size

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    class VehicleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon: TextView = itemView.findViewById(R.id.tvVehicleIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvVehicleName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvVehiclePrice)
        private val tvPerKm: TextView = itemView.findViewById(R.id.tvVehiclePerKm)
        private val cardView: CardView = itemView.findViewById(R.id.cardVehicle)
        private val viewSelected: View = itemView.findViewById(R.id.viewSelected)

        fun bind(vehicle: VehicleOption, isSelected: Boolean) {
            tvIcon.text = vehicle.icon
            tvName.text = vehicle.name
            tvPrice.text = "₹${vehicle.basePrice.toInt()} + ₹${vehicle.perKmRate.toInt()}/km"
            tvPerKm.text = "₹${vehicle.perKmRate.toInt()}/km"

            if (isSelected) {
                viewSelected.visibility = View.VISIBLE
                cardView.setCardBackgroundColor(0x1A6200EE.toInt())
            } else {
                viewSelected.visibility = View.GONE
                cardView.setCardBackgroundColor(0xFFFFFFFF.toInt())
            }
        }
    }
}