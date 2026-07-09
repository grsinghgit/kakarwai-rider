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

    private val db = FirebaseFirestore.getInstance()
    private var rideId: String? = null
    private var driverId: String? = null
    private var driverMarker: Marker? = null
    private var rideStatus: String = "SEARCHING"
    private var isMapReady = false
    private var isFirstLocation = true

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

        rideId = arguments?.getString("rideId")

        if (rideId == null) {
            Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        listenForRideUpdates()
    }

    // ============================================================
    // ✅ MAP SETUP
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

        val defaultLocation = LatLng(28.6139, 77.2090)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
    }

    // ============================================================
    // ✅ LISTEN FOR RIDE UPDATES
    // ============================================================

    private fun listenForRideUpdates() {
        rideId?.let { id ->
            db.collection("rides").document(id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val data = snapshot.data
                    if (data == null) return@addSnapshotListener

                    val status = data["status"] as? String ?: "PENDING"
                    rideStatus = status

                    when (status) {
                        "DRIVER_ASSIGNED", "ACCEPTED" -> {
                            val driverId = data["driverId"] as? String
                            val driverName = data["driverName"] as? String
                            val driverPhone = data["driverPhone"] as? String
                            val driverVehicle = data["driverVehicle"] as? String
                            val driverVehicleNumber = data["driverVehicleNumber"] as? String
                            val totalFare = data["totalFare"] as? Double ?: 0.0

                            tvStatus.text = "🚗 Driver Assigned"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))

                            tvDriverName.text = "🚗 ${driverName ?: "Driver"}"
                            tvDriverPhone.text = "📱 ${driverPhone ?: "N/A"}"
                            tvVehicleInfo.text = "🚙 ${driverVehicle ?: "Car"} | ${driverVehicleNumber ?: "N/A"}"
                            tvFare.text = "💰 ₹${totalFare.toInt()}"
                            cardDriverDetails.visibility = View.VISIBLE

                            if (driverId != null) {
                                this@RideTrackingFragment.driverId = driverId
                                listenForDriverLocation(driverId)
                            }
                        }
                        "STARTED" -> {
                            tvStatus.text = "🚗 Ride Started"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
                        }
                        "COMPLETED" -> {
                            tvStatus.text = "✅ Ride Completed"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                            driverMarker?.remove()
                            Toast.makeText(requireContext(), "Ride Completed! Thank you.", Toast.LENGTH_LONG).show()
                        }
                        "CANCELLED" -> {
                            tvStatus.text = "❌ Ride Cancelled"
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                            driverMarker?.remove()
                            findNavController().popBackStack()
                        }
                        else -> {
                            tvStatus.text = "⏳ Searching for driver..."
                            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                        }
                    }
                }
        }
    }

    // ============================================================
    // ✅ LISTEN FOR DRIVER LOCATION
    // ============================================================

    private fun listenForDriverLocation(driverId: String) {
        db.collection("driver_locations").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val location = snapshot.getGeoPoint("currentLocation")
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateDriverMarker(latLng)

                    if (isFirstLocation && isMapReady) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                        isFirstLocation = false
                    }
                }
            }
    }

    // ============================================================
    // ✅ UPDATE DRIVER MARKER (GREEN POINT)
    // ============================================================

    private fun updateDriverMarker(latLng: LatLng) {
        if (!isMapReady) return

        if (driverMarker == null) {
            // ✅ Green Marker with Car Icon
            driverMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("🚗 Driver")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))  // ✅ Green Color
                    .anchor(0.5f, 0.5f)
            )
        } else {
            driverMarker?.position = latLng
        }
    }

    // ============================================================
    // ✅ BACK BUTTON HANDLE
    // ============================================================

    override fun onDetach() {
        super.onDetach()
        // Cleanup listener
        driverMarker?.remove()
    }
}