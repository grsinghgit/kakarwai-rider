package com.gr.kakarwairider.admin.fragments

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.model.DriverWithDistance
import com.gr.kakarwairider.admin.viewmodel.AdminMapViewModel

class AdminMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val viewModel: AdminMapViewModel by viewModels()
    private var isMapReady = false
    private val driverMarkers = mutableMapOf<String, Marker>()
    private val rideMarkers = mutableMapOf<String, Marker>()
    private var currentPolyline: Polyline? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // ✅ Observe drivers
        viewModel.drivers.observe(viewLifecycleOwner, Observer { drivers ->
            if (isMapReady) {
                updateMapMarkers(drivers)
            }
        })

        // ✅ Observe rides
        viewModel.rides.observe(viewLifecycleOwner, Observer { rides ->
            if (isMapReady) {
                updateRideMarkers(rides)
            }
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })

        viewModel.loadOnlineDrivers()
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

        viewModel.drivers.value?.let { drivers ->
            if (drivers.isNotEmpty()) {
                updateMapMarkers(drivers)
            }
        }

        viewModel.rides.value?.let { rides ->
            if (rides.isNotEmpty()) {
                updateRideMarkers(rides)
            }
        }
    }

    private fun updateMapMarkers(drivers: List<DriverWithDistance>) {
        driverMarkers.values.forEach { it.remove() }
        driverMarkers.clear()

        var firstLocation: LatLng? = null

        drivers.forEach { driverWithDistance ->
            val driver = driverWithDistance.driver
            val latLng = driverWithDistance.latLng

            val distanceText = if (driverWithDistance.distance < 1) {
                "${(driverWithDistance.distance * 1000).toInt()} m"
            } else {
                "%.1f km".format(driverWithDistance.distance)
            }

            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("🚗 ${driver.driverName}")
                    .snippet("📱 ${driver.driverPhone}\n📏 $distanceText from center")
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        if (driver.rideId != null) BitmapDescriptorFactory.HUE_BLUE
                        else BitmapDescriptorFactory.HUE_GREEN
                    ))
            )
            marker?.showInfoWindow()
            driverMarkers[driver.driverId] = marker!!

            if (firstLocation == null) {
                firstLocation = latLng
            }
        }

        firstLocation?.let {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
        }

        updateDriverCount()
    }

    private fun updateRideMarkers(rides: List<Map<String, Any>>) {
        rideMarkers.values.forEach { it.remove() }
        rideMarkers.clear()

        android.util.Log.d("AdminMapFragment", "Rides to show: ${rides.size}")

        rides.forEach { rideData ->
            val rideId = rideData["rideId"] as? String ?: return@forEach
            val status = rideData["status"] as? String ?: "PENDING"
            val pickup = rideData["pickup"] as? Map<*, *>
            val destination = rideData["destination"] as? Map<*, *>
            val driverName = rideData["driverName"] as? String ?: "No Driver Assigned"

            val pickupLat = (pickup?.get("lat") as? Number)?.toDouble() ?: 0.0
            val pickupLng = (pickup?.get("lng") as? Number)?.toDouble() ?: 0.0

            if (pickupLat != 0.0 && pickupLng != 0.0) {
                val latLng = LatLng(pickupLat, pickupLng)

                val markerColor = when (status) {
                    "PENDING", "SEARCHING" -> BitmapDescriptorFactory.HUE_ORANGE
                    "DRIVER_ASSIGNED" -> BitmapDescriptorFactory.HUE_YELLOW
                    "ACCEPTED" -> BitmapDescriptorFactory.HUE_BLUE
                    "STARTED" -> BitmapDescriptorFactory.HUE_GREEN
                    else -> BitmapDescriptorFactory.HUE_RED
                }

                val statusEmoji = when (status) {
                    "PENDING", "SEARCHING" -> "⏳"
                    "DRIVER_ASSIGNED" -> "📌"
                    "ACCEPTED" -> "✅"
                    "STARTED" -> "🚗"
                    else -> "❓"
                }

                val fare = rideData["totalFare"] as? Double ?: 0.0

                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("$statusEmoji $status Ride")
                        .snippet(
                            "🆔 ${rideId.takeLast(8)}\n" +
                                    "👤 Driver: $driverName\n" +
                                    "💰 ₹${fare.toInt()}\n" +
                                    "📍 ${pickup?.get("address") ?: "N/A"}"
                        )
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                )
                marker?.showInfoWindow()
                rideMarkers[rideId] = marker!!

                if (status == "ACCEPTED" || status == "STARTED") {
                    val destLat = (destination?.get("lat") as? Number)?.toDouble() ?: 0.0
                    val destLng = (destination?.get("lng") as? Number)?.toDouble() ?: 0.0
                    if (destLat != 0.0 && destLng != 0.0) {
                        drawRideRoute(rideData)
                    }
                }
            }
        }

        updateDriverCount()
    }

    private fun drawRideRoute(rideData: Map<String, Any>) {
        currentPolyline?.remove()

        val pickup = rideData["pickup"] as? Map<*, *>
        val destination = rideData["destination"] as? Map<*, *>

        val pickupLat = (pickup?.get("lat") as? Number)?.toDouble() ?: 0.0
        val pickupLng = (pickup?.get("lng") as? Number)?.toDouble() ?: 0.0
        val destLat = (destination?.get("lat") as? Number)?.toDouble() ?: 0.0
        val destLng = (destination?.get("lng") as? Number)?.toDouble() ?: 0.0

        if (pickupLat == 0.0 || pickupLng == 0.0 || destLat == 0.0 || destLng == 0.0) {
            return
        }

        val pickupLatLng = LatLng(pickupLat, pickupLng)
        val destLatLng = LatLng(destLat, destLng)

        val polylineOptions = PolylineOptions()
            .add(pickupLatLng, destLatLng)
            .width(6f)
            .color(0xFF2196F3.toInt())
            .geodesic(true)

        currentPolyline = mMap.addPolyline(polylineOptions)
    }

    private fun updateDriverCount() {
        val tvDriverCount = view?.findViewById<TextView>(R.id.tvDriverCount)
        val totalRides = rideMarkers.size
        tvDriverCount?.text = "🚗 ${driverMarkers.size} | 🚖 $totalRides"
    }
}