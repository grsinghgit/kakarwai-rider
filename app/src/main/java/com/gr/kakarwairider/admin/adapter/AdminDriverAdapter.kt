package com.gr.kakarwairider.admin.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.DriverModel

class AdminDriverAdapter(
    private val onRechargeClick: (DriverModel) -> Unit,
    private val onToggleSpecial: (DriverModel) -> Unit,
    private val onToggleStatus: (DriverModel, Boolean) -> Unit,
    private val onDelete: (DriverModel) -> Unit
) : RecyclerView.Adapter<AdminDriverAdapter.DriverViewHolder>() {

    private var drivers: List<DriverModel> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_driver, parent, false)
        return DriverViewHolder(view)
    }

    override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
        holder.bind(drivers[position])
    }

    override fun getItemCount(): Int = drivers.size

    // ✅ Update drivers - NO adapter recreation
    fun updateDrivers(newDrivers: List<DriverModel>) {
        Log.d("AdapterDebug", "🔄 updateDrivers: ${newDrivers.size} drivers")
        this.drivers = newDrivers
        notifyDataSetChanged()
    }

    inner class DriverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        private val tvVehicle: TextView = itemView.findViewById(R.id.tvVehicle)
        private val tvRides: TextView = itemView.findViewById(R.id.tvRides)
        private val tvEarnings: TextView = itemView.findViewById(R.id.tvEarnings)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val tvWallet: TextView = itemView.findViewById(R.id.tvWallet)
        private val switchStatus: SwitchCompat = itemView.findViewById(R.id.switchStatus)
        private val btnRecharge: MaterialButton = itemView.findViewById(R.id.btnRecharge)
        private val btnToggleSpecial: MaterialButton = itemView.findViewById(R.id.btnToggleSpecial)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)

        fun bind(driver: DriverModel) {
            Log.d("AdapterDebug", "📌 Binding: ${driver.name}, isActive: ${driver.isActive}")

            val driverId = driver.id
            if (driverId.isNullOrEmpty()) {
                Log.e("AdapterDebug", "❌ ID null: ${driver.name}")
                return
            }

            // Set info
            tvDriverName.text = driver.name
            tvPhone.text = "📱 ${driver.phone}"
            tvVehicle.text = "🚗 ${driver.vehicleModel} | ${driver.vehicleNumber}"
            tvRides.text = "📊 ${driver.totalRides} rides"
            tvEarnings.text = "💰 ₹${driver.totalEarnings.toInt()}"
            tvRating.text = "⭐ ${driver.rating}"
            tvWallet.text = "💳 ₹${driver.walletBalance.toInt()}"

            // ✅ SWITCH - Correct sequence
            switchStatus.setOnCheckedChangeListener(null)
            switchStatus.isChecked = driver.isActive
            updateSwitchColors(driver.isActive)
            switchStatus.setOnCheckedChangeListener { _, isChecked ->
                if (driver.isActive == isChecked) {
                    Log.d("AdapterDebug", "⚠️ State same: $isChecked, ignoring")
                    return@setOnCheckedChangeListener
                }
                Log.d("AdapterDebug", "🔄 Switch: ${driver.name} → $isChecked")
                onToggleStatus(driver, isChecked)
            }

            // Special
            if (driver.isSpecial) {
                btnToggleSpecial.text = "⭐ Special"
                btnToggleSpecial.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange))
                btnToggleSpecial.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                btnToggleSpecial.text = "⭐ Make Special"
                btnToggleSpecial.setBackgroundColor(0x00000000)
                btnToggleSpecial.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary))
            }

            // Enable buttons
            btnRecharge.isEnabled = true
            btnToggleSpecial.isEnabled = true
            btnDelete.isEnabled = true

            // Click listeners
            btnRecharge.setOnClickListener { onRechargeClick(driver) }
            btnToggleSpecial.setOnClickListener { onToggleSpecial(driver) }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(itemView.context)
                    .setTitle("🗑️ Delete Driver")
                    .setMessage("Delete ${driver.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        onDelete(driver)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        private fun updateSwitchColors(isActive: Boolean) {
            val context = itemView.context
            if (isActive) {
                switchStatus.thumbDrawable?.setTint(ContextCompat.getColor(context, R.color.switch_thumb_active))
                switchStatus.trackDrawable?.setTint(ContextCompat.getColor(context, R.color.switch_track_active))
            } else {
                switchStatus.thumbDrawable?.setTint(ContextCompat.getColor(context, R.color.switch_thumb_inactive))
                switchStatus.trackDrawable?.setTint(ContextCompat.getColor(context, R.color.switch_track_inactive))
            }
        }
    }
}