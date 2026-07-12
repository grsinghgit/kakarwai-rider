package com.gr.kakarwairider.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.MainActivity2
import com.gr.kakarwairider.ui.viewmodel.RideTrackingViewModel
import com.gr.kakarwairider.utils.DistanceUtils

class RideTrackingFragment : Fragment(), OnMapReadyCallback {

    private val viewModel: RideTrackingViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    // ✅ Map
    private lateinit var mMap: GoogleMap
    private var isMapReady = false
    private var isFirstLocation = true
    private var isFragmentAttached = true
    private var isNavigating = false
    private var driverMarker: Marker? = null
    private var pickupLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null

    // ✅ Views - All in One Card
    private lateinit var tvStatus: TextView
    private lateinit var btnCancelRide: MaterialButton

    // ✅ Card Views
    private lateinit var cardRideDetails: MaterialCardView
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverPhone: TextView
    private lateinit var tvVehicleInfo: TextView
    private lateinit var btnCallDriver: MaterialButton
    private lateinit var tvPickup: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvPickupPin: TextView
    private lateinit var tvCompletePin: TextView
    private lateinit var tvFare: TextView
    private lateinit var tvDistanceDetail: TextView

    private var rideId: String? = null

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

        rideId = arguments?.getString("rideId")

        if (rideId == null) {
            Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
            safePopBack()
            return
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        setupObservers()
        viewModel.loadRideDetails(rideId!!)

        btnCancelRide.setOnClickListener {
            showCancelDialog()
        }

        view.findViewById<View>(R.id.ivBack)?.setOnClickListener {
            safePopBack()
        }
    }

    private fun initViews(view: View) {
        tvStatus = view.findViewById(R.id.tvStatus)
        btnCancelRide = view.findViewById(R.id.btnCancelRide)

        // ✅ Card Views
        cardRideDetails = view.findViewById(R.id.cardRideDetails)
        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvDriverPhone = view.findViewById(R.id.tvDriverPhone)
        tvVehicleInfo = view.findViewById(R.id.tvVehicleInfo)
        btnCallDriver = view.findViewById(R.id.btnCallDriver)
        tvPickup = view.findViewById(R.id.tvPickup)
        tvDestination = view.findViewById(R.id.tvDestination)
        tvPickupPin = view.findViewById(R.id.tvPickupPin)
        tvCompletePin = view.findViewById(R.id.tvCompletePin)
        tvFare = view.findViewById(R.id.tvFare)
        tvDistanceDetail = view.findViewById(R.id.tvDistanceDetail)
    }

    private fun setupObservers() {
        // ✅ Ride Data Observer - Update Card
        viewModel.rideData.observe(viewLifecycleOwner, Observer { ride ->
            ride?.let {
                // Update map locations
                it.pickup?.let { pickup ->
                    pickupLatLng = LatLng(pickup.lat, pickup.lng)
                }
                it.destination?.let { dest ->
                    destinationLatLng = LatLng(dest.lat, dest.lng)
                }

                // ✅ Update Full Card
                updateCard(it)

                if (isMapReady) {
                    updateMapWithLocations()
                }
            }
        })

        // ✅ Status Observer
        viewModel.status.observe(viewLifecycleOwner, Observer { status ->
            handleStatusUpdate(status)
        })

        // ✅ Driver Location Observer
        viewModel.driverLocation.observe(viewLifecycleOwner, Observer { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                updateDriverMarker(latLng)
                if (isFirstLocation && isMapReady) {
                    centerMapOnAllPoints()
                    isFirstLocation = false
                }
            }
        })

        // ✅ Error Observer
        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })
    }

    // ✅ Update Full Card
    private fun updateCard(ride: com.gr.kakarwairider.model.RideModel) {
        cardRideDetails.visibility = View.VISIBLE

        // ✅ Driver Section
        ride.driverName?.let {
            tvDriverName.text = "🚗 $it"
        } ?: run {
            tvDriverName.text = "🚗 Driver"
        }

        tvDriverPhone.text = "📱 ${ride.driverPhone ?: "N/A"}"

        val vehicle = ride.driverVehicle ?: "Car"
        val vehicleNumber = ride.driverVehicleNumber ?: "N/A"
        tvVehicleInfo.text = "🚙 $vehicle | $vehicleNumber"

        // ✅ Call Driver Button
        btnCallDriver.setOnClickListener {
            val phone = ride.driverPhone
            if (!phone.isNullOrEmpty() && phone != "N/A") {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Driver phone not available", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Ride Section
        tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
        tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"

        // ✅ PINs
        if (!ride.pickupPin.isNullOrEmpty()) {
            tvPickupPin.text = "🔑 Pickup PIN: ${ride.pickupPin}"
            tvPickupPin.visibility = View.VISIBLE
        } else {
            tvPickupPin.visibility = View.GONE
        }

        if (!ride.completePin.isNullOrEmpty()) {
            tvCompletePin.text = "🔑 Complete PIN: ${ride.completePin}"
            tvCompletePin.visibility = View.VISIBLE
        } else {
            tvCompletePin.visibility = View.GONE
        }

        // ✅ Fare + Distance
        if (ride.fareCalculated && ride.totalFare > 0) {
            tvFare.text = "💰 ₹${DistanceUtils.formatFareInt(ride.totalFare)}"
            tvFare.visibility = View.VISIBLE

            val pickupDist = DistanceUtils.formatDistance(ride.pickupDistance)
            val tripDist = DistanceUtils.formatDistance(ride.tripDistance)
            val totalDist = DistanceUtils.formatDistance(ride.totalDistance)
            tvDistanceDetail.text = "📍 ${pickupDist}km + ${tripDist}km = ${totalDist}km"
            tvDistanceDetail.visibility = View.VISIBLE
        } else {
            tvFare.visibility = View.GONE
            tvDistanceDetail.visibility = View.GONE
        }
    }

    private fun handleStatusUpdate(status: String) {
        if (!isAdded || !isFragmentAttached) return

        when (status) {
            "DRIVER_ASSIGNED" -> updateStatusUI("⏳ Waiting for Driver...", R.color.orange, true)
            "ACCEPTED" -> updateStatusUI("🚗 Driver Accepted!", R.color.green, true)
            "STARTED", "ON_THE_WAY" -> {
                updateStatusUI(
                    if (status == "STARTED") "🚗 Ride Started!" else "🚗 On The Way",
                    R.color.blue,
                    false
                )
            }
            "ARRIVED_PICKUP" -> updateStatusUI("📍 Driver Arrived at Pickup", R.color.blue, false)
            "DESTINATION_REACHED" -> updateStatusUI("📍 Destination Reached", R.color.orange, false)
            "COMPLETED" -> {
                driverMarker?.remove()
                if (isAdded) {
                    tvStatus.text = "✅ Ride Completed"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                    btnCancelRide.visibility = View.GONE
                    (requireActivity() as? MainActivity2)?.onRideStatusChanged("COMPLETED")
                    Toast.makeText(requireContext(), "Ride Completed! Thank you.", Toast.LENGTH_LONG).show()
                    handler.postDelayed({
                        if (isAdded && isFragmentAttached) {
                            try {
                                findNavController().navigate(R.id.historyFragment)
                            } catch (e: Exception) {
                                safePopBack()
                            }
                        }
                    }, 2000)
                }
            }
            "CANCELLED" -> {
                driverMarker?.remove()
                if (isAdded) {
                    tvStatus.text = "❌ Ride Cancelled"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    btnCancelRide.visibility = View.GONE
                    (requireActivity() as? MainActivity2)?.onRideStatusChanged("CANCELLED")
                    safePopBack()
                }
            }
            "REJECTED" -> {
                if (isAdded) {
                    tvStatus.text = "❌ Driver Rejected"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    btnCancelRide.visibility = View.GONE
                    Toast.makeText(requireContext(), "Driver rejected the ride", Toast.LENGTH_LONG).show()
                    handler.postDelayed({ safePopBack() }, 1500)
                }
            }
            else -> {
                if (isAdded) {
                    tvStatus.text = "⏳ Searching for driver..."
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                    btnCancelRide.visibility = View.GONE
                }
            }
        }
    }

    private fun updateStatusUI(message: String, colorRes: Int, showCancel: Boolean) {
        if (!isAdded) return
        tvStatus.text = message
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        btnCancelRide.visibility = if (showCancel) View.VISIBLE else View.GONE
    }

    // ============================================================
    // ✅ MAP FUNCTIONS
    // ============================================================

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }

        updateMapWithLocations()
    }

    private fun updateMapWithLocations() {
        if (!isMapReady || !::mMap.isInitialized || !isAdded) return

        try {
            mMap.clear()
            driverMarker = null

            pickupLatLng?.let { latLng ->
                mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("📍 Pickup")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

            destinationLatLng?.let { latLng ->
                mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("🏁 Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }

            drawRoute()
            centerMapOnAllPoints()
        } catch (e: Exception) {
            android.util.Log.e("RideTracking", "Map update error: ${e.message}")
        }
    }

    private fun drawRoute() {
        if (pickupLatLng == null || destinationLatLng == null) return

        val polylineOptions = PolylineOptions()
            .add(pickupLatLng!!, destinationLatLng!!)
            .width(6f)
            .color(ContextCompat.getColor(requireContext(), R.color.blue))
            .geodesic(true)
        mMap.addPolyline(polylineOptions)
    }

    private fun centerMapOnAllPoints() {
        try {
            val builder = LatLngBounds.Builder()
            pickupLatLng?.let { builder.include(it) }
            destinationLatLng?.let { builder.include(it) }
            driverMarker?.position?.let { builder.include(it) }

            val bounds = builder.build()
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: Exception) {
            pickupLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f))
            }
        }
    }

    private fun updateDriverMarker(latLng: LatLng) {
        if (!isMapReady || !::mMap.isInitialized || !isAdded) return

        if (driverMarker == null) {
            driverMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("🚗 Driver")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .anchor(0.5f, 0.5f)
            )
        } else {
            driverMarker?.position = latLng
        }
    }

    // ============================================================
    // ✅ CANCEL RIDE
    // ============================================================

    private fun showCancelDialog() {
        if (!isAdded) return
        rideId?.let { id ->
            viewModel.getRideStatus(id) { status ->
                if (status == "STARTED" || status == "ON_THE_WAY" || status == "COMPLETED") {
                    Toast.makeText(requireContext(), "Cannot cancel ride now. Contact support.", Toast.LENGTH_LONG).show()
                    return@getRideStatus
                }
                if (isAdded) {
                    val dialog = CancelRideDialog(id) { reason ->
                        viewModel.cancelRide(id, "user", reason) { success ->
                            if (success) {
                                (requireActivity() as? MainActivity2)?.onRideStatusChanged("CANCELLED")
                                Toast.makeText(requireContext(), "✅ Ride Cancelled: $reason", Toast.LENGTH_LONG).show()
                                safePopBack()
                            }
                        }
                    }
                    dialog.show(childFragmentManager, "CancelRideDialog")
                }
            }
        }
    }

    private fun safePopBack() {
        if (isNavigating) return
        isNavigating = true
        try {
            if (isAdded && isFragmentAttached) {
                findNavController().popBackStack()
            }
        } catch (e: Exception) {
            android.util.Log.e("RideTracking", "PopBack error: ${e.message}")
        } finally {
            handler.postDelayed({ isNavigating = false }, 500)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentAttached = false
        handler.removeCallbacksAndMessages(null)
        driverMarker?.remove()
    }

    override fun onDetach() {
        super.onDetach()
        isFragmentAttached = false
        handler.removeCallbacksAndMessages(null)
        driverMarker?.remove()
    }
}