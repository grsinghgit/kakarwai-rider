package com.gr.kakarwairider.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R

class RideProcessingFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvTimer: TextView
    private lateinit var tvRideId: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPickup: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvFare: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvVehicle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardRideDetails: MaterialCardView
    private lateinit var cardDriverDetails: MaterialCardView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnStartRide: MaterialButton
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverPhone: TextView
    private lateinit var tvDriverVehicle: TextView

    private var rideId: String? = null
    private var countDownTimer: CountDownTimer? = null
    private var isDriverAssigned = false

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

        rideId = arguments?.getString("rideId")

        if (rideId != null) {
            displayRideDetails()
            listenForRideUpdates()
            startTimer()
        } else {
            Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        setupListeners()
    }

    private fun initViews(view: View) {
        tvTimer = view.findViewById(R.id.tvTimer)
        tvRideId = view.findViewById(R.id.tvRideId)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvPickup = view.findViewById(R.id.tvPickup)
        tvDestination = view.findViewById(R.id.tvDestination)
        tvFare = view.findViewById(R.id.tvFare)
        tvDistance = view.findViewById(R.id.tvDistance)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvVehicle = view.findViewById(R.id.tvVehicle)
        progressBar = view.findViewById(R.id.progressBar)
        cardRideDetails = view.findViewById(R.id.cardRideDetails)
        cardDriverDetails = view.findViewById(R.id.cardDriverDetails)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnStartRide = view.findViewById(R.id.btnStartRide)
        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvDriverPhone = view.findViewById(R.id.tvDriverPhone)
        tvDriverVehicle = view.findViewById(R.id.tvDriverVehicle)
    }

    private fun displayRideDetails() {
        rideId?.let { id ->
            db.collection("rides").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val data = document.data ?: return@addOnSuccessListener

                        val pickup = data["pickup"] as? Map<*, *>
                        val destination = data["destination"] as? Map<*, *>
                        val vehicleIcon = data["vehicleIcon"] as? String ?: "🚗"
                        val vehicleName = data["vehicleName"] as? String ?: "Car"
                        val distance = data["distance"] as? Double ?: 0.0
                        val duration = data["duration"] as? Long ?: 0
                        val totalFare = data["totalFare"] as? Double ?: 0.0

                        tvRideId.text = "Ride ID: ${id.takeLast(8)}"
                        tvPickup.text = "📍 ${pickup?.get("address") ?: "N/A"}"
                        tvDestination.text = "🏁 ${destination?.get("address") ?: "N/A"}"
                        tvDistance.text = "📍 %.1f km".format(distance)
                        tvDuration.text = "⏱️ %d min".format(duration)
                        tvVehicle.text = "$vehicleIcon $vehicleName"
                        tvFare.text = "💰 ₹${totalFare.toInt()}"

                        cardRideDetails.visibility = View.VISIBLE

                        val status = data["status"] as? String ?: "PENDING"
                        handleStatusUpdate(status, data)
                    }
                }
        }
    }

    private fun listenForRideUpdates() {
        rideId?.let { id ->
            db.collection("rides").document(id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener

                    val data = snapshot?.data ?: return@addSnapshotListener
                    val status = data["status"] as? String ?: "PENDING"
                    handleStatusUpdate(status, data)
                }
        }
    }

    // ============================================================
    // ✅ HANDLE STATUS UPDATE
    // ============================================================

    private fun handleStatusUpdate(status: String, data: Map<String, Any>) {
        when (status) {
            "PENDING", "SEARCHING" -> showSearchingState(data)
            "DRIVER_ASSIGNED", "ACCEPTED" -> showDriverAssignedState(data)
            "STARTED" -> navigateToTracking(data)
            "COMPLETED", "CANCELLED", "EXPIRED" -> finishRide(status, data)
        }
    }

    private fun showSearchingState(data: Map<String, Any>) {
        tvStatus.text = "⏳ Searching for a driver..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        progressBar.visibility = View.VISIBLE

        btnCancel.visibility = View.VISIBLE
        btnStartRide.visibility = View.GONE
        cardDriverDetails.visibility = View.GONE

        if (countDownTimer == null) {
            startTimer()
        }
    }

    // ✅ UPDATED: Driver Assigned State with Navigation to Tracking
    private fun showDriverAssignedState(data: Map<String, Any>) {
        isDriverAssigned = true
        progressBar.visibility = View.GONE
        countDownTimer?.cancel()

        tvStatus.text = "✅ Driver Assigned!"
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))

        // ✅ Show driver details
        val driverName = data["driverName"] as? String ?: "Unknown"
        val driverPhone = data["driverPhone"] as? String ?: "N/A"
        val driverVehicle = data["driverVehicle"] as? String ?: "Car"
        val driverVehicleNumber = data["driverVehicleNumber"] as? String ?: "N/A"

        tvDriverName.text = "🚗 $driverName"
        tvDriverPhone.text = "📱 $driverPhone"
        tvDriverVehicle.text = "🚙 $driverVehicle | $driverVehicleNumber"

        // ✅ Show fare
        val totalFare = data["totalFare"] as? Double ?: 0.0
        tvFare.text = "💰 Total Fare: ₹${totalFare.toInt()}"

        // ✅ Show Start Ride button, hide Cancel
        btnCancel.visibility = View.GONE
        btnStartRide.visibility = View.VISIBLE
        btnStartRide.isEnabled = true
        cardDriverDetails.visibility = View.VISIBLE

        tvTimer.text = "🚗 Driver is on the way!"

        Toast.makeText(requireContext(), "🚗 Driver $driverName assigned!", Toast.LENGTH_LONG).show()

        // ✅ Navigate to Tracking after 2 seconds
        tvTimer.postDelayed({
            navigateToTracking(data)
        }, 2000)
    }

    private fun navigateToTracking(data: Map<String, Any>) {
        val bundle = Bundle().apply {
            putString("rideId", data["rideId"] as? String)
        }
        findNavController().navigate(R.id.action_processing_to_tracking, bundle)
    }

    private fun finishRide(status: String, data: Map<String, Any>) {
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE

        when (status) {
            "COMPLETED" -> {
                tvStatus.text = "✅ Ride Completed!"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            }
            "CANCELLED" -> {
                tvStatus.text = "❌ Ride Cancelled"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            }
            "EXPIRED" -> {
                tvStatus.text = "⏰ Time Expired!"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            }
        }

        btnCancel.isEnabled = false
        btnStartRide.visibility = View.GONE
    }

    // ============================================================
    // ✅ TIMER
    // ============================================================

    private fun startTimer() {
        val expireTime = System.currentTimeMillis() + 300000
        val timeLeft = expireTime - System.currentTimeMillis()

        if (timeLeft <= 0) {
            tvTimer.text = "⏰ Time Expired!"
            return
        }

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvTimer.text = "⏳ ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                tvTimer.text = "⏰ Time Expired!"
                rideId?.let { id ->
                    db.collection("rides").document(id)
                        .update("status", "EXPIRED")
                }
                Toast.makeText(requireContext(), "Time expired! Please try again.", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // ============================================================
    // ✅ BUTTON LISTENERS
    // ============================================================

    private fun setupListeners() {
        btnRefresh.setOnClickListener {
            rideId?.let { id ->
                db.collection("rides").document(id)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val data = document.data ?: return@addOnSuccessListener
                            val status = data["status"] as? String ?: "PENDING"
                            handleStatusUpdate(status, data)
                            Toast.makeText(requireContext(), "Status refreshed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to refresh", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnCancel.setOnClickListener {
            cancelRide()
        }

        btnStartRide.setOnClickListener {
            rideId?.let { id ->
                db.collection("rides").document(id)
                    .update("status", "STARTED")
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "🚗 Ride Started!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to start ride", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // ============================================================
    // ✅ CANCEL RIDE
    // ============================================================

    private fun cancelRide() {
        rideId?.let { id ->
            db.collection("rides").document(id)
                .update("status", "CANCELLED")
                .addOnSuccessListener {
                    countDownTimer?.cancel()
                    Toast.makeText(requireContext(), "Ride Cancelled", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
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