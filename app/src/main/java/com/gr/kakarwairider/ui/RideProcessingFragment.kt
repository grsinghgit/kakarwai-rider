package com.gr.kakarwairider.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.viewmodel.BookRideViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class RideProcessingFragment : Fragment() {

    private val viewModel: BookRideViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvTimer: TextView
    private lateinit var tvRideId: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPickup: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvFare: TextView
    private lateinit var cardRideDetails: MaterialCardView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var ride: RideModel? = null
    private var countDownTimer: CountDownTimer? = null
    private var timeLeft = 300000L // 5 minutes in milliseconds

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ride_processing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        // ✅ Get ride from arguments
        arguments?.let {
            ride = it.getParcelable("ride")
        }

        ride?.let { rideData ->
            displayRideDetails(rideData)
            startTimer(rideData)
            listenForRideUpdate(rideData.rideId)
        } ?: run {
            // No ride data - go back
            findNavController().popBackStack()
        }

        btnRefresh.setOnClickListener {
            ride?.rideId?.let { rideId ->
                checkRideStatus(rideId)
            }
        }

        btnCancel.setOnClickListener {
            cancelRide()
        }
    }

    private fun initViews(view: View) {
        tvTimer = view.findViewById(R.id.tvTimer)
        tvRideId = view.findViewById(R.id.tvRideId)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvPickup = view.findViewById(R.id.tvPickup)
        tvDestination = view.findViewById(R.id.tvDestination)
        tvFare = view.findViewById(R.id.tvFare)
        cardRideDetails = view.findViewById(R.id.cardRideDetails)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnCancel = view.findViewById(R.id.btnCancel)
    }

    private fun displayRideDetails(ride: RideModel) {
        tvRideId.text = "Ride ID: ${ride.rideId.takeLast(8)}"
        tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
        tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
        tvFare.text = "₹ ${ride.fare}"

        tvStatus.text = "⏳ Waiting for Driver..."
        tvStatus.setTextColor(resources.getColor(R.color.primary, null))
        cardRideDetails.visibility = View.VISIBLE
    }

    // ============================================================
    // ✅ 5 MINUTE TIMER
    // ============================================================

    private fun startTimer(ride: RideModel) {
        // Calculate remaining time
        val expiresAt = ride.expiresAt
        if (expiresAt != null) {
            val currentTime = System.currentTimeMillis()
            val expireTime = expiresAt.toDate().time
            timeLeft = expireTime - currentTime

            if (timeLeft <= 0) {
                tvTimer.text = "⏰ Time Expired!"
                btnCancel.isEnabled = false
                return
            }
        }

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvTimer.text = "⏳ ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                tvTimer.text = "⏰ Time Expired!"
                btnCancel.isEnabled = false
                // Cancel the ride
                ride.rideId?.let { rideId ->
                    db.collection("rides").document(rideId)
                        .update("status", "EXPIRED")
                }
                tvStatus.text = "❌ No driver available. Please try again."
                tvStatus.setTextColor(resources.getColor(R.color.red, null))
            }
        }.start()
    }

    // ============================================================
    // ✅ LISTEN FOR RIDE UPDATE
    // ============================================================

    private fun listenForRideUpdate(rideId: String) {
        db.collection("rides").document(rideId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val rideData = document?.data
                if (rideData != null) {
                    val status = rideData["status"] as? String ?: "PENDING"
                    handleStatusUpdate(status, rideData)
                }
            }
    }

    private fun handleStatusUpdate(status: String, rideData: Map<String, Any>) {
        when (status) {
            "ACCEPTED" -> {
                // ✅ Driver Assigned!
                countDownTimer?.cancel()
                tvStatus.text = "✅ Driver Assigned!"
                tvStatus.setTextColor(resources.getColor(R.color.green, null))
                tvTimer.text = "🚗 Driver is on the way!"

                // Show driver details
                val driverName = rideData["driverName"] as? String ?: "Unknown"
                val driverPhone = rideData["driverPhone"] as? String ?: "N/A"
                val driverVehicle = rideData["driverVehicle"] as? String ?: "Car"
                val driverVehicleNumber = rideData["driverVehicleNumber"] as? String ?: "N/A"

                Toast.makeText(requireContext(), "🚗 Driver $driverName assigned!", Toast.LENGTH_LONG).show()

                // Navigate to Ride Tracking Screen (Step 7)
                val bundle = Bundle().apply {
                    putString("rideId", rideData["rideId"] as? String)
                    putString("driverName", driverName)
                    putString("driverPhone", driverPhone)
                    putString("driverVehicle", driverVehicle)
                    putString("driverVehicleNumber", driverVehicleNumber)
                }
                findNavController().navigate(R.id.action_processing_to_tracking, bundle)
            }

            "REJECTED" -> {
                countDownTimer?.cancel()
                tvStatus.text = "❌ Ride Rejected"
                tvStatus.setTextColor(resources.getColor(R.color.red, null))
                btnCancel.isEnabled = false
                Toast.makeText(requireContext(), "Ride has been rejected.", Toast.LENGTH_LONG).show()
            }

            "EXPIRED" -> {
                countDownTimer?.cancel()
                tvStatus.text = "⏰ Time Expired!"
                tvStatus.setTextColor(resources.getColor(R.color.red, null))
                btnCancel.isEnabled = false
            }
        }
    }

    // ============================================================
    // ✅ CHECK RIDE STATUS MANUALLY
    // ============================================================

    private fun checkRideStatus(rideId: String) {
        db.collection("rides").document(rideId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status") ?: "PENDING"
                    handleStatusUpdate(status, document.data ?: emptyMap())
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to check status", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================================================
    // ✅ CANCEL RIDE
    // ============================================================

    private fun cancelRide() {
        ride?.rideId?.let { rideId ->
            db.collection("rides").document(rideId)
                .update("status", "CANCELLED")
                .addOnSuccessListener {
                    countDownTimer?.cancel()
                    tvStatus.text = "❌ Ride Cancelled"
                    tvStatus.setTextColor(resources.getColor(R.color.red, null))
                    btnCancel.isEnabled = false
                    Toast.makeText(requireContext(), "Ride cancelled successfully.", Toast.LENGTH_SHORT).show()
                    // Go back after 2 seconds
                    tvTimer.postDelayed({
                        findNavController().popBackStack()
                    }, 2000)
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to cancel ride", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }
}