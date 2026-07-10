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
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverPhone: TextView
    private lateinit var tvDriverVehicle: TextView

    private var rideId: String? = null
    private var countDownTimer: CountDownTimer? = null
    private var isFragmentAttached = true

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
                        updateUI(data)
                    }
                }
        }
    }

    private fun updateUI(data: Map<String, Any>) {
        val pickup = data["pickup"] as? Map<*, *>
        val destination = data["destination"] as? Map<*, *>
        val vehicleIcon = data["vehicleIcon"] as? String ?: "🚗"
        val vehicleName = data["vehicleName"] as? String ?: "Car"
        val distance = data["distance"] as? Double ?: 0.0
        val duration = data["duration"] as? Long ?: 0
        val totalFare = data["totalFare"] as? Double ?: 0.0

        tvRideId.text = "Ride ID: ${rideId?.takeLast(8)}"
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

    private fun listenForRideUpdates() {
        rideId?.let { id ->
            db.collection("rides").document(id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    if (!isFragmentAttached) return@addSnapshotListener

                    val data = snapshot.data ?: return@addSnapshotListener
                    val status = data["status"] as? String ?: "PENDING"
                    handleStatusUpdate(status, data)
                }
        }
    }

    // ============================================================
    // ✅ STATUS UPDATE HANDLER
    // ============================================================

    private fun handleStatusUpdate(status: String, data: Map<String, Any>) {
        when (status) {
            "PENDING", "SEARCHING" -> showSearchingState()
            "DRIVER_ASSIGNED" -> showDriverAssignedState(data)
            "ACCEPTED" -> onDriverAccepted(data)  // ✅ Driver accept → auto start
            "STARTED" -> navigateToTracking(data)
            "COMPLETED" -> finishRide("✅ Ride Completed!", R.color.green)
            "REJECTED" -> showRideRejected()
            "CANCELLED" -> finishRide("❌ Ride Cancelled", R.color.red)
            "EXPIRED" -> finishRide("⏰ Time Expired!", R.color.red)
            else -> showSearchingState()
        }
    }

    private fun showSearchingState() {
        tvStatus.text = "⏳ Searching for a driver..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        progressBar.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE
        cardDriverDetails.visibility = View.GONE
        tvTimer.visibility = View.VISIBLE
    }

    // ✅ Driver Assigned - Waiting for Approval
    private fun showDriverAssignedState(data: Map<String, Any>) {
        progressBar.visibility = View.VISIBLE

        tvStatus.text = "⏳ Waiting for Driver Approval..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))

        val driverName = data["driverName"] as? String ?: "Unknown"
        val driverPhone = data["driverPhone"] as? String ?: "N/A"
        val driverVehicle = data["driverVehicle"] as? String ?: "Car"
        val driverVehicleNumber = data["driverVehicleNumber"] as? String ?: "N/A"
        val totalFare = data["totalFare"] as? Double ?: 0.0

        tvDriverName.text = "🚗 $driverName"
        tvDriverPhone.text = "📱 $driverPhone"
        tvDriverVehicle.text = "🚙 $driverVehicle | $driverVehicleNumber"
        tvFare.text = "💰 Total Fare: ₹${totalFare.toInt()}"
        cardDriverDetails.visibility = View.VISIBLE

        btnCancel.visibility = View.VISIBLE
        tvTimer.visibility = View.VISIBLE

        Toast.makeText(requireContext(), "⏳ Waiting for $driverName to accept", Toast.LENGTH_LONG).show()
    }

    // ✅ Driver Accepted → Auto Start Ride → Navigate to Tracking
    private fun onDriverAccepted(data: Map<String, Any>) {
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        btnCancel.visibility = View.GONE
        tvTimer.visibility = View.GONE

        tvStatus.text = "✅ Driver Accepted! Ride Starting..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))

        Toast.makeText(requireContext(), "🚗 Driver accepted! Ride is starting...", Toast.LENGTH_LONG).show()

        // ✅ Update ride status to STARTED
        rideId?.let { id ->
            db.collection("rides").document(id)
                .update("status", "STARTED")
                .addOnSuccessListener {
                    // ✅ Navigate to tracking after 1 second
                    tvStatus.postDelayed({
                        if (isFragmentAttached && isAdded) {
                            navigateToTracking(data)
                        }
                    }, 1500)
                }
        }
    }

    // ✅ Ride Rejected State
    private fun showRideRejected() {
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE

        tvStatus.text = "❌ Driver Rejected the Ride"
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
        btnCancel.visibility = View.GONE
        tvTimer.visibility = View.GONE

        Toast.makeText(requireContext(), "Driver rejected the ride. Please try again.", Toast.LENGTH_LONG).show()

        // ✅ Go back after 2 seconds
        if (isAdded) {
            tvStatus.postDelayed({
                if (isAdded) {
                    findNavController().popBackStack()
                }
            }, 2000)
        }
    }

    private fun navigateToTracking(data: Map<String, Any>) {
        if (!isFragmentAttached || !isAdded) return

        val bundle = Bundle().apply {
            putString("rideId", data["rideId"] as? String)
        }
        findNavController().navigate(R.id.action_processing_to_tracking, bundle)
    }

    private fun finishRide(message: String, colorRes: Int) {
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE

        tvStatus.text = message
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        btnCancel.isEnabled = false
        tvTimer.visibility = View.GONE

        if (isAdded) {
            tvStatus.postDelayed({
                if (isAdded) {
                    findNavController().popBackStack()
                }
            }, 3000)
        }
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
                if (!isFragmentAttached) {
                    cancel()
                    return
                }
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvTimer.text = "⏳ ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                if (!isFragmentAttached) return
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
            }
        }

        btnCancel.setOnClickListener {
            cancelRide()
        }
    }

    // ============================================================
    // ✅ CANCEL RIDE
    // ============================================================

    private fun cancelRide() {
        if (!isAdded) return

        rideId?.let { id ->
            db.collection("rides").document(id)
                .update("status", "CANCELLED")
                .addOnSuccessListener {
                    countDownTimer?.cancel()
                    Toast.makeText(requireContext(), "Ride Cancelled", Toast.LENGTH_SHORT).show()
                    if (isAdded) {
                        findNavController().popBackStack()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to cancel ride", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentAttached = false
        countDownTimer?.cancel()
    }
}