package com.gr.kakarwairider.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.gr.kakarwairider.R

class RideTrackingFragment : Fragment() {

    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverPhone: TextView
    private lateinit var tvVehicle: TextView
    private lateinit var tvVehicleNumber: TextView
    private lateinit var tvRideId: TextView
    private lateinit var tvFare: TextView
    private lateinit var cardDriverDetails: MaterialCardView
    private lateinit var btnStartRide: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var rideId: String? = null
    private var driverName: String? = null
    private var driverPhone: String? = null
    private var driverVehicle: String? = null
    private var driverVehicleNumber: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ride_tracking, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        // Get arguments
        arguments?.let {
            rideId = it.getString("rideId")
            driverName = it.getString("driverName")
            driverPhone = it.getString("driverPhone")
            driverVehicle = it.getString("driverVehicle")
            driverVehicleNumber = it.getString("driverVehicleNumber")
        }

        displayDriverDetails()

        btnStartRide.setOnClickListener {
            // Payment Method Selection
            Toast.makeText(requireContext(), "🚗 Ride Started! Payment: CASH", Toast.LENGTH_LONG).show()
            // Navigate to Payment Screen (Future)
        }

        btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun initViews(view: View) {
        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvDriverPhone = view.findViewById(R.id.tvDriverPhone)
        tvVehicle = view.findViewById(R.id.tvVehicle)
        tvVehicleNumber = view.findViewById(R.id.tvVehicleNumber)
        tvRideId = view.findViewById(R.id.tvRideId)
        tvFare = view.findViewById(R.id.tvFare)
        cardDriverDetails = view.findViewById(R.id.cardDriverDetails)
        btnStartRide = view.findViewById(R.id.btnStartRide)
        btnCancel = view.findViewById(R.id.btnCancel)
    }

    private fun displayDriverDetails() {
        tvDriverName.text = "🚗 $driverName"
        tvDriverPhone.text = "📱 $driverPhone"
        tvVehicle.text = "🚙 $driverVehicle"
        tvVehicleNumber.text = "🔢 $driverVehicleNumber"
        tvRideId.text = "Ride ID: ${rideId?.takeLast(8)}"

        // Show fare from ride data (we'll pass it later)
        tvFare.text = "💰 ₹236 (CASH Payment)"

        cardDriverDetails.visibility = View.VISIBLE
    }
}