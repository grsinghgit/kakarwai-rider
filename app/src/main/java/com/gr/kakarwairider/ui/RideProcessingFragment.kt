package com.gr.kakarwairider.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
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
import com.gr.kakarwairider.MainActivity2
import com.gr.kakarwairider.R
import com.gr.kakarwairider.ui.viewmodel.RideProcessingViewModel

class RideProcessingFragment : Fragment() {

    private val viewModel: RideProcessingViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    // ✅ Views
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
    private var isFragmentAttached = true
    private var isNavigating = false
    private var isRideFinished = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ride_processing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isRideFinished) return

        initViews(view)
        setupCallbacks()
        setupObservers()

        rideId = arguments?.getString("rideId")

        if (rideId != null) {
            viewModel.loadRideDetails(rideId!!)
            viewModel.listenForRideUpdates(rideId!!)
            startTimer()
        } else {
            Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
            goToHome()
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
        // ✅ Ride Data Observer
        viewModel.rideData.observe(viewLifecycleOwner, Observer { data ->
            data?.let { updateUI(it) }
        })

        // ✅ Status Observer
        viewModel.status.observe(viewLifecycleOwner, Observer { status ->
            handleStatusUpdate(status)
        })

        // ✅ Driver Details Observer
        viewModel.driverDetails.observe(viewLifecycleOwner, Observer { driver ->
            driver?.let {
                cardDriverDetails.visibility = View.VISIBLE
                tvDriverName.text = "🚗 ${it.name}"
                tvDriverPhone.text = "📱 ${it.phone}"
                tvDriverVehicle.text = "🚙 ${it.vehicle} | ${it.vehicleNumber}"
            }
        })

        // ✅ Loading Observer
        viewModel.isLoading.observe(viewLifecycleOwner, Observer { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        })

        // ✅ Error Observer
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
    }

    private fun handleStatusUpdate(status: String) {
        if (!isFragmentAttached || isRideFinished) return

        when (status) {
            "PENDING", "SEARCHING" -> showSearchingState()
            "DRIVER_ASSIGNED" -> showDriverAssignedState()
            "ACCEPTED" -> onDriverAccepted()
            "STARTED" -> navigateToTracking()
            "COMPLETED" -> finishRide("✅ Ride Completed!", R.color.green)
            "REJECTED" -> showRideRejected()
            "CANCELLED" -> showCancelledState()
            "EXPIRED" -> showExpiredState()
            else -> showSearchingState()
        }
    }

    private fun showSearchingState() {
        tvStatus.text = "⏳ Searching for a driver..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        progressBar.visibility = View.VISIBLE
        //btnCancel.visibility = View.VISIBLE
        btnStartRide.visibility = View.GONE
        cardDriverDetails.visibility = View.GONE
        tvTimer.visibility = View.VISIBLE
    }

    private fun showDriverAssignedState() {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "⏳ Waiting for Driver Approval..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        btnCancel.visibility = View.VISIBLE
        btnStartRide.visibility = View.GONE
        tvTimer.visibility = View.VISIBLE
        showToast("⏳ Waiting for driver to accept")
    }

    private fun onDriverAccepted() {
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnStartRide.visibility = View.GONE
        tvTimer.visibility = View.GONE

        tvStatus.text = "✅ Driver Accepted! Ride Starting..."
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))

        showToast("🚗 Driver accepted! Ride is starting...")

        rideId?.let { id ->
            viewModel.updateRideStatus("STARTED") { success ->
                if (success) {
                    handler.postDelayed({
                        if (isFragmentAttached && isAdded && !isRideFinished) {
                            navigateToTracking()
                        }
                    }, 800)
                }
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
                findNavController().navigate(R.id.rideTrackingFragment, bundle)
            } catch (e2: Exception) {
                try {
                    val fragment = RideTrackingFragment()
                    fragment.arguments = bundle
                    requireActivity().supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                } catch (e3: Exception) {
                    Toast.makeText(requireContext(), "Error opening tracking", Toast.LENGTH_SHORT).show()
                    goToHome()
                }
            }
        } finally {
            handler.postDelayed({ isNavigating = false }, 500)
        }
    }

    private fun showRideRejected() {
        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        tvStatus.text = "❌ Driver Rejected the Ride"
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
        btnCancel.visibility = View.GONE
        btnStartRide.visibility = View.GONE
        tvTimer.visibility = View.GONE

        showToast("Driver rejected the ride. Please try again.")
        handler.postDelayed({ goToHome() }, 2000)
    }

    private fun showCancelledState() {
        if (isRideFinished) return
        isRideFinished = true

        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnStartRide.visibility = View.GONE
        tvTimer.visibility = View.GONE

        tvStatus.text = "❌ Ride Cancelled"
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

        (requireActivity() as? MainActivity2)?.hideBottomNav(false)
        (requireActivity() as? MainActivity2)?.onRideStatusChanged("CANCELLED")

        showToast("Ride Cancelled")
        goToHome()
    }

    private fun showExpiredState() {
        if (isRideFinished) return
        isRideFinished = true

        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnStartRide.visibility = View.GONE
        tvTimer.visibility = View.GONE

        tvStatus.text = "⏰ Time Expired!"
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

        (requireActivity() as? MainActivity2)?.hideBottomNav(false)
        (requireActivity() as? MainActivity2)?.onRideStatusChanged("EXPIRED")

        showToast("Time expired! Please book a new ride.")
        goToHome()
    }

    private fun finishRide(message: String, colorRes: Int) {
        if (isRideFinished) return

        countDownTimer?.cancel()
        progressBar.visibility = View.GONE
        btnCancel.isEnabled = false
        btnCancel.visibility = View.GONE
        btnStartRide.visibility = View.GONE
        tvTimer.visibility = View.GONE

        tvStatus.text = message
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

        (requireActivity() as? MainActivity2)?.onRideStatusChanged("COMPLETED")

        handler.postDelayed({
            (requireActivity() as? MainActivity2)?.hideBottomNav(false)
            goToHistory()
        }, 1500)
    }

    private fun showToast(message: String) {
        if (isAdded && isFragmentAttached) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToHome() {
        if (isNavigating || !isFragmentAttached || !isAdded) return
        isNavigating = true
        try {
            (requireActivity() as? MainActivity2)?.hideBottomNav(false)
            (requireActivity() as? MainActivity2)?.onRideStatusChanged("CANCELLED")
            findNavController().navigate(R.id.homeFragment)
        } catch (e: Exception) {
            Log.e("RideProcessing", "Navigate error: ${e.message}")
        } finally {
            handler.postDelayed({ isNavigating = false }, 500)
        }
    }

    private fun goToHistory() {
        if (isNavigating || !isFragmentAttached || !isAdded) return
        isNavigating = true
        try {
            (requireActivity() as? MainActivity2)?.hideBottomNav(false)
            (requireActivity() as? MainActivity2)?.onRideStatusChanged("COMPLETED")
            findNavController().navigate(R.id.historyFragment)
        } catch (e: Exception) {
            Log.e("RideProcessing", "Navigate error: ${e.message}")
        } finally {
            handler.postDelayed({ isNavigating = false }, 500)
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
                if (!isFragmentAttached || isRideFinished) return
                viewModel.updateRideStatus("EXPIRED") { success ->
                    if (success) showExpiredState()
                }
            }
        }.start()
    }

    // ============================================================
    // ✅ BUTTON LISTENERS
    // ============================================================

    private fun setupListeners() {
        btnRefresh.setOnClickListener {
            rideId?.let { viewModel.loadRideDetails(it) }
            showToast("Status refreshed")
        }

       // btnCancel.setOnClickListener {
         //   cancelRide()
       // }
    }

    private fun cancelRide() {
        if (isRideFinished || !isAdded) return

        viewModel.cancelRide { success ->
            if (success) {
                countDownTimer?.cancel()
                showCancelledState()
            }
        }
    }

    // ============================================================
    // ✅ LIFECYCLE
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentAttached = false
        countDownTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}