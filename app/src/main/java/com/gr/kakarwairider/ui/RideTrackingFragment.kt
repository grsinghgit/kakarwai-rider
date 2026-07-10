package com.gr.kakarwairider.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R

class RideTrackingFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var tvStatus: TextView
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverPhone: TextView
    private lateinit var tvVehicleInfo: TextView
    private lateinit var tvFare: TextView
    private lateinit var cardDriverDetails: MaterialCardView
    private lateinit var btnCancelRide: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private var rideId: String? = null
    private var driverId: String? = null
    private var driverMarker: Marker? = null
    private var isMapReady = false
    private var isFirstLocation = true
    private var pickupLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null
    private var rideDataMap: Map<String, Any>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ride_tracking, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvDriverPhone = view.findViewById(R.id.tvDriverPhone)
        tvVehicleInfo = view.findViewById(R.id.tvVehicleInfo)
        tvFare = view.findViewById(R.id.tvFare)
        cardDriverDetails = view.findViewById(R.id.cardDriverDetails)
        btnCancelRide = view.findViewById(R.id.btnCancelRide)

        rideId = arguments?.getString("rideId")

        if (rideId == null) {
            Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        listenForRideUpdates()

        btnCancelRide.setOnClickListener {
            showCancelDialog()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }

        // ✅ If we already have ride data, update map
        rideDataMap?.let {
            updateMapWithLocations()
        }
    }

    private fun listenForRideUpdates() {
        rideId?.let { id ->
            db.collection("rides").document(id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val data = snapshot.data
                    if (data == null) return@addSnapshotListener

                    rideDataMap = data
                    val status = data["status"] as? String ?: "PENDING"

                    // ✅ Extract locations
                    val pickup = data["pickup"] as? Map<*, *>
                    val destination = data["destination"] as? Map<*, *>

                    pickup?.let {
                        val lat = (it["lat"] as? Number)?.toDouble() ?: 0.0
                        val lng = (it["lng"] as? Number)?.toDouble() ?: 0.0
                        pickupLatLng = LatLng(lat, lng)
                    }

                    destination?.let {
                        val lat = (it["lat"] as? Number)?.toDouble() ?: 0.0
                        val lng = (it["lng"] as? Number)?.toDouble() ?: 0.0
                        destinationLatLng = LatLng(lat, lng)
                    }

                    when (status) {
                        "DRIVER_ASSIGNED", "ACCEPTED", "STARTED" -> {
                            val driverId = data["driverId"] as? String
                            val driverName = data["driverName"] as? String
                            val driverPhone = data["driverPhone"] as? String
                            val driverVehicle = data["driverVehicle"] as? String
                            val driverVehicleNumber = data["driverVehicleNumber"] as? String
                            val totalFare = data["totalFare"] as? Double ?: 0.0

                            tvDriverName.text = "🚗 ${driverName ?: "Driver"}"
                            tvDriverPhone.text = "📱 ${driverPhone ?: "N/A"}"
                            tvVehicleInfo.text = "🚙 ${driverVehicle ?: "Car"} | ${driverVehicleNumber ?: "N/A"}"
                            tvFare.text = "💰 ₹${totalFare.toInt()}"
                            cardDriverDetails.visibility = View.VISIBLE

                            if (isMapReady) {
                                updateMapWithLocations()
                            }

                            if (driverId != null) {
                                this@RideTrackingFragment.driverId = driverId
                                listenForDriverLocation(driverId)
                            }

                            when (status) {
                                "DRIVER_ASSIGNED" -> {
                                    tvStatus.text = "⏳ Waiting for Driver..."
                                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                                    btnCancelRide.visibility = View.VISIBLE
                                }
                                "ACCEPTED" -> {
                                    tvStatus.text = "🚗 Driver Accepted!"
                                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                                    btnCancelRide.visibility = View.VISIBLE
                                }
                                "STARTED" -> {
                                    tvStatus.text = "🚗 Ride Started!"
                                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
                                    btnCancelRide.visibility = View.GONE
                                }
                            }
                        }
                        "COMPLETED" -> {
                            tvStatus.text = "✅ Ride Completed"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                            driverMarker?.remove()
                            btnCancelRide.visibility = View.GONE
                            Toast.makeText(requireContext(), "Ride Completed! Thank you.", Toast.LENGTH_LONG).show()
                        }
                        "CANCELLED" -> {
                            tvStatus.text = "❌ Ride Cancelled"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                            driverMarker?.remove()
                            btnCancelRide.visibility = View.GONE
                            if (isAdded) findNavController().popBackStack()
                        }
                        "REJECTED" -> {
                            tvStatus.text = "❌ Driver Rejected"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                            btnCancelRide.visibility = View.GONE
                            Toast.makeText(requireContext(), "Driver rejected the ride", Toast.LENGTH_LONG).show()
                            if (isAdded) findNavController().popBackStack()
                        }
                        else -> {
                            tvStatus.text = "⏳ Searching for driver..."
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                            btnCancelRide.visibility = View.GONE
                        }
                    }
                }
        }
    }

    // ✅ Update Map with Pickup, Destination, Route
    private fun updateMapWithLocations() {
        if (!isMapReady) return

        // Clear old markers
        mMap.clear()
        driverMarker = null

        // ✅ 1. Pickup Marker (Green)
        pickupLatLng?.let { latLng ->
            mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("📍 Pickup")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }

        // ✅ 2. Destination Marker (Red)
        destinationLatLng?.let { latLng ->
            mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("🏁 Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        // ✅ 3. Draw Route Line (Blue)
        drawRoute()

        // ✅ 4. Center map to show all points
        centerMapOnAllPoints()
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
        val builder = LatLngBounds.Builder()
        pickupLatLng?.let { builder.include(it) }
        destinationLatLng?.let { builder.include(it) }
        driverMarker?.position?.let { builder.include(it) }

        try {
            val bounds = builder.build()
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: Exception) {
            // Fallback to pickup location
            pickupLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f))
            }
        }
    }

    private fun listenForDriverLocation(driverId: String) {
        db.collection("driver_locations").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val location = snapshot.getGeoPoint("currentLocation")
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateDriverMarker(latLng)

                    if (isFirstLocation && isMapReady) {
                        centerMapOnAllPoints()
                        isFirstLocation = false
                    }
                }
            }
    }

    private fun updateDriverMarker(latLng: LatLng) {
        if (!isMapReady) return

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

    private fun showCancelDialog() {
        if (!isAdded) return
        rideId?.let { id ->
            db.collection("rides").document(id).get()
                .addOnSuccessListener { document ->
                    val status = document.getString("status") ?: "PENDING"
                    if (status == "STARTED" || status == "COMPLETED") {
                        Toast.makeText(requireContext(), "Cannot cancel ride now. Contact support.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    if (isAdded) {
                        val dialog = CancelRideDialog(id) { reason ->
                            cancelRide(id, "user", reason)
                        }
                        dialog.show(childFragmentManager, "CancelRideDialog")
                    }
                }
        }
    }

    private fun cancelRide(rideId: String, cancelledBy: String, reason: String) {
        if (!isAdded) return
        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "status" to "CANCELLED",
                    "cancelledBy" to cancelledBy,
                    "cancelReason" to reason,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "✅ Ride Cancelled: $reason", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDetach() {
        super.onDetach()
        driverMarker?.remove()
    }
}