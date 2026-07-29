package com.gr.kakarwairider.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.gr.kakarwairider.BookRideFragment
import com.gr.kakarwairider.MainActivity2
import com.gr.kakarwairider.R
import com.gr.kakarwairider.ui.viewmodel.RideProcessingViewModel
import com.gr.kakarwairider.utils.DistanceUtils

class RideProcessingFragment : Fragment() {

    companion object {
        private const val TAG = "RideProcessing"
    }

    private val viewModel: RideProcessingViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    // Views
    private lateinit var tvTimer: TextView
    private lateinit var tvRideId: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvPickup: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvEstimatedFare: TextView
    private lateinit var tvTotalFare: TextView
    private lateinit var tvDistanceBreakdown: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvVehicle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardRideDetails: MaterialCardView
    private lateinit var cardDriverDetails: MaterialCardView
    private lateinit var cardPayment: MaterialCardView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnConfirmRide: MaterialButton
    private lateinit var btnCallDriver: MaterialButton
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverPhone: TextView
    private lateinit var tvDriverVehicle: TextView
    private lateinit var rbCash: RadioButton
    private lateinit var rbOnline: RadioButton

    private var rideId: String? = null
    private var countDownTimer: CountDownTimer? = null
    private var isFragmentAttached = true
    private var isNavigating = false
    private var isRideFinished = false
    private var driverPhone: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ride_processing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "🔵 onViewCreated")

        if (isRideFinished) return

        initViews(view)
        setupCallbacks()
        setupObservers()
        setupListeners()

        rideId = arguments?.getString("rideId")
        Log.d(TAG, "📌 rideId: $rideId")

        if (rideId != null) {
            viewModel.loadRideDetails(rideId!!)
            viewModel.listenForRideUpdates(rideId!!)
        } else {
            Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
            navigateToMainActivityHome()
        }
    }

    private fun initViews(view: View) {
        tvTimer = view.findViewById(R.id.tvTimer)
        tvRideId = view.findViewById(R.id.tvRideId)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvPickup = view.findViewById(R.id.tvPickup)
        tvDestination = view.findViewById(R.id.tvDestination)
        tvEstimatedFare = view.findViewById(R.id.tvEstimatedFare)
        tvTotalFare = view.findViewById(R.id.tvTotalFare)
        tvDistanceBreakdown = view.findViewById(R.id.tvDistanceBreakdown)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvVehicle = view.findViewById(R.id.tvVehicle)
        progressBar = view.findViewById(R.id.progressBar)
        cardRideDetails = view.findViewById(R.id.cardRideDetails)
        cardDriverDetails = view.findViewById(R.id.cardDriverDetails)
        cardPayment = view.findViewById(R.id.cardPayment)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnConfirmRide = view.findViewById(R.id.btnConfirmRide)
        btnCallDriver = view.findViewById(R.id.btnCallDriver)
        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvDriverPhone = view.findViewById(R.id.tvDriverPhone)
        tvDriverVehicle = view.findViewById(R.id.tvDriverVehicle)
        rbCash = view.findViewById(R.id.rbCash)
        rbOnline = view.findViewById(R.id.rbOnline)

        btnCancel.visibility = View.GONE
        btnCallDriver.visibility = View.GONE
        Log.d(TAG, "✅ Views initialized")
    }

    private fun setupCallbacks() {
        (requireActivity() as? MainActivity2)?.hideBottomNav(true)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(requireContext(), "⏳ Ride is being processed!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun setupObservers() {
        viewModel.rideData.observe(viewLifecycleOwner, Observer { data ->
            data?.let { updateUI(it) }
        })

        viewModel.status.observe(viewLifecycleOwner, Observer { status ->
            Log.d(TAG, "📊 Status update: $status")
            handleStatusUpdate(status)
        })

        viewModel.driverDetails.observe(viewLifecycleOwner, Observer { driver ->
            driver?.let {
                cardDriverDetails.visibility = View.VISIBLE
                tvDriverName.text = "🚗 ${it.name}"
                driverPhone = it.phone
                tvDriverPhone.text = "📱 ${it.phone}"

                btnCallDriver.visibility = View.VISIBLE
                btnCallDriver.setOnClickListener {
                    val phone = driverPhone
                    if (phone != null && phone.isNotEmpty() && phone != "N/A") {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phone")
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(requireContext(), "Driver phone not available", Toast.LENGTH_SHORT).show()
                    }
                }

                tvDriverVehicle.text = "🚙 ${it.vehicle} | ${it.vehicleNumber}"
            }
        })

        viewModel.isLoading.observe(viewLifecycleOwner, Observer { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })
    }

    private fun updateUI(data: Map<String, Any>) {
        val pickup = data["pickup"] as? Map<*, *>
        val destination = data["destination"] as? Map<*, *>
        val vehicleIcon = data["vehicleIcon"] as? String ?: "🚗"
        val vehicleName = data["vehicleName"] as? String ?: "Car"
        val totalFare = data["totalFare"] as? Double ?: 0.0
        val duration = data["duration"] as? Long ?: 0

        val pickupDistance = (data["pickupDistance"] as? Number)?.toDouble() ?: 0.0
        val tripDistance = (data["tripDistance"] as? Number)?.toDouble() ?: 0.0
        val totalDistance = (data["totalDistance"] as? Number)?.toDouble() ?: 0.0

        tvRideId.text = "Ride ID: ${rideId?.takeLast(8)}"
        tvPickup.text = "📍 ${pickup?.get("address") ?: "N/A"}"
        tvDestination.text = "🏁 ${destination?.get("address") ?: "N/A"}"
        tvVehicle.text = "$vehicleIcon $vehicleName"
        tvDuration.text = "⏱️ ${duration} min"

        tvEstimatedFare.text = "💰 Estimated Fare: ₹${DistanceUtils.formatFareInt(totalFare)}"
        tvEstimatedFare.visibility = View.VISIBLE
        tvTotalFare.visibility = View.GONE

        if (pickupDistance > 0 || tripDistance > 0 || totalDistance > 0) {
            val pickupDist = DistanceUtils.formatDistance(pickupDistance)
            val tripDist = DistanceUtils.formatDistance(tripDistance)
            val totalDist = DistanceUtils.formatDistance(totalDistance)
            tvDistanceBreakdown.text = "📍 ${pickupDist}km + ${tripDist}km = ${totalDist}km"
            tvDistanceBreakdown.visibility = View.VISIBLE
        } else {
            tvDistanceBreakdown.visibility = View.GONE
        }

        cardRideDetails.visibility = View.VISIBLE
    }

    private fun handleStatusUpdate(status: String) {
        if (!isFragmentAttached || isRideFinished) {
            Log.d(TAG, "⏳ Ignoring status")
            return
        }

        Log.d(TAG, "🔄 Processing status: $status")

        when (status) {
            "PENDING", "SEARCHING" -> showSearchingState()
            "DRIVER_ASSIGNED" -> showDriverAssignedState()
            "ACCEPTED" -> showAcceptedState()
            "STARTED" -> navigateToTracking()
            "COMPLETED", "REJECTED", "CANCELLED", "EXPIRED" -> {
                // ✅ All these statuses navigate to MainActivity2 Home
                showFinalState(status)
            }
            else -> showSearchingState()
        }
    }

    private fun showFinalState(status: String) {
        isRideFinished = true
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnConfirmRide.visibility = View.GONE
        cardPayment.visibility = View.GONE
        tvTimer.visibility = View.GONE
        btnCallDriver.visibility = View.GONE

        when (status) {
            "COMPLETED" -> {
                tvStatus.text = "✅ Ride Completed!"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                Toast.makeText(requireContext(), "✅ Ride Completed!", Toast.LENGTH_SHORT).show()
            }
            "REJECTED" -> {
                tvStatus.text = "❌ Driver Rejected the Ride"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                Toast.makeText(requireContext(), "Driver rejected the ride. Please try again.", Toast.LENGTH_LONG).show()
            }
            "CANCELLED" -> {
                tvStatus.text = "❌ Ride Cancelled"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                Toast.makeText(requireContext(), "❌ Ride Cancelled", Toast.LENGTH_SHORT).show()
            }
            "EXPIRED" -> {
                tvStatus.text = "⏰ Time Expired!"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                Toast.makeText(requireContext(), "Time expired! Please book a new ride.", Toast.LENGTH_LONG).show()
            }
        }

        // ✅ Navigate to MainActivity2 Home after delay
        handler.postDelayed({
            (requireActivity() as? MainActivity2)?.hideBottomNav(false)
            (requireActivity() as? MainActivity2)?.onRideStatusChanged(status)
            navigateToMainActivityHome()
        }, 1500)
    }

    private fun showSearchingState() {
        tvStatus.text = "⏳ Searching for a driver..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        progressBar.visibility = View.VISIBLE
        tvTimer.visibility = View.GONE
        cardDriverDetails.visibility = View.GONE
        cardPayment.visibility = View.GONE
        btnConfirmRide.visibility = View.GONE
        btnRefresh.visibility = View.GONE
        btnConfirmRide.isEnabled = false
        btnCancel.visibility = View.GONE
        btnCallDriver.visibility = View.GONE

        tvTotalFare.visibility = View.GONE
        tvEstimatedFare.visibility = View.VISIBLE
    }

    private fun showDriverAssignedState() {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "⏳ Waiting for Driver Approval..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        tvTimer.visibility = View.GONE
        cardPayment.visibility = View.GONE
        btnConfirmRide.visibility = View.GONE
        btnRefresh.visibility = View.VISIBLE
        btnConfirmRide.isEnabled = false
        btnCancel.visibility = View.GONE
        btnCallDriver.visibility = View.GONE
        tvTotalFare.visibility = View.GONE
        tvEstimatedFare.visibility = View.VISIBLE

        Toast.makeText(requireContext(), "⏳ Waiting for driver to accept", Toast.LENGTH_SHORT).show()
    }

    private fun showAcceptedState() {
        progressBar.visibility = View.GONE
        tvStatus.text = "✅ Driver Accepted! Confirm payment to start ride."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))

        tvTimer.visibility = View.VISIBLE
        cardPayment.visibility = View.VISIBLE
        btnConfirmRide.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE
        btnRefresh.visibility = View.GONE

        tvTotalFare.visibility = View.VISIBLE
        tvEstimatedFare.visibility = View.GONE

        if (countDownTimer == null) {
            startTimer()
        }

        rbCash.isChecked = true
        btnConfirmRide.isEnabled = true
        btnConfirmRide.text = "✅ Confirm & Start Ride"

        Toast.makeText(requireContext(), "✅ Driver accepted! Please confirm payment.", Toast.LENGTH_LONG).show()
    }

    private fun startTimer() {
        val duration = 5 * 60 * 1000L
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvTimer.text = "⏳ ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                if (!isFragmentAttached || isRideFinished) return
                viewModel.updateRideStatus(rideId!!, "EXPIRED") { success ->
                    if (success) {
                        showFinalState("EXPIRED")
                    }
                }
            }
        }.start()
    }

    private fun setupListeners() {
        btnRefresh.setOnClickListener {
            rideId?.let { viewModel.loadRideDetails(it) }
            Toast.makeText(requireContext(), "Status refreshed", Toast.LENGTH_SHORT).show()
        }

        btnCancel.setOnClickListener {
            Log.d(TAG, "🚀 Cancel button clicked")
            cancelRide()
        }

        btnConfirmRide.setOnClickListener {
            if (rbCash.isChecked) {
                btnConfirmRide.isEnabled = false
                btnConfirmRide.text = "⏳ Confirming..."

                viewModel.updateRideWithPayment(rideId!!, "CASH") { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "✅ Payment confirmed! Starting ride...", Toast.LENGTH_SHORT).show()
                        viewModel.updateRideStatus(rideId!!, "STARTED") { updateSuccess ->
                            if (updateSuccess) {
                                countDownTimer?.cancel()
                                navigateToTracking()
                            } else {
                                btnConfirmRide.isEnabled = true
                                btnConfirmRide.text = "✅ Confirm & Start Ride"
                                Toast.makeText(requireContext(), "❌ Failed to start ride", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        btnConfirmRide.isEnabled = true
                        btnConfirmRide.text = "✅ Confirm & Start Ride"
                        Toast.makeText(requireContext(), "❌ Failed to confirm payment", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Please select Cash payment method", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancelRide() {
        if (isRideFinished || !isAdded) {
            Log.d(TAG, "⚠️ Cancel blocked")
            return
        }

        Log.d(TAG, "🔄 Starting cancel ride process")
        btnCancel.isEnabled = false

        viewModel.cancelRide { success ->
            Log.d(TAG, "📤 Cancel result: $success")
            if (success) {
                countDownTimer?.cancel()
                isRideFinished = true

                tvStatus.text = "❌ Ride Cancelled"
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                progressBar.visibility = View.GONE
                btnCancel.visibility = View.GONE
                btnConfirmRide.visibility = View.GONE
                cardPayment.visibility = View.GONE
                tvTimer.visibility = View.GONE
                btnCallDriver.visibility = View.GONE

                Toast.makeText(requireContext(), "❌ Ride Cancelled", Toast.LENGTH_SHORT).show()

                handler.postDelayed({
                    Log.d(TAG, "🏠 Navigating to MainActivity2...")
                    (requireActivity() as? MainActivity2)?.hideBottomNav(false)
                    (requireActivity() as? MainActivity2)?.onRideStatusChanged("CANCELLED")
                    navigateToMainActivityHome()
                }, 500)
            } else {
                btnCancel.isEnabled = true
                Toast.makeText(requireContext(), "❌ Failed to cancel ride", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToTracking() {
        if (!isFragmentAttached || !isAdded || isNavigating || isRideFinished) return

        isNavigating = true
        val bundle = Bundle().apply {
            putString("rideId", rideId)
        }

        try {
            findNavController().navigate(R.id.action_processing_to_tracking, bundle)
        } catch (e: Exception) {
            try {
                val fragment = RideTrackingFragment()
                fragment.arguments = bundle
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Error opening tracking", Toast.LENGTH_SHORT).show()
                navigateToMainActivityHome()
            }
        } finally {
            handler.postDelayed({ isNavigating = false }, 500)
        }
    }

    private fun navigateToMainActivityHome() {
        if (isNavigating || !isFragmentAttached || !isAdded) return
        isNavigating = true
        try {
            val intent = Intent(requireContext(), MainActivity2::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            requireActivity().finish()
            Log.d(TAG, "🏠 Navigated to MainActivity2")
        } catch (e: Exception) {
            Log.e(TAG, "❌ navigateToMainActivityHome error: ${e.message}")
            requireActivity().finish()
        } finally {
            handler.postDelayed({ isNavigating = false }, 500)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "🔴 onDestroyView")
        isFragmentAttached = false
        countDownTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        viewModel.stopListening()
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "🔴 onDetach")
        isFragmentAttached = false
        countDownTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        viewModel.stopListening()
    }
}