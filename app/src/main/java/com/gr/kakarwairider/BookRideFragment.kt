package com.gr.kakarwairider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.gr.kakarwairider.viewmodel.BookRideViewModel
import java.io.IOException
import java.util.*

class BookRideFragment : Fragment(), OnMapReadyCallback {

    private lateinit var etPickup: TextInputEditText
    private lateinit var etDestination: TextInputEditText
    private lateinit var btnPickupOptions: ImageButton
    private lateinit var btnDestinationOptions: ImageButton
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvFare: TextView
    private lateinit var btnBookNow: MaterialButton

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var pickupLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null
    private var isPickupSelected = true
    private var isLocationPermissionGranted = false
    private var currentLocation: LatLng? = null

    private val viewModel: BookRideViewModel by viewModels()
    private var locationDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book_ride, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        // ✅ Fixed: findFragmentById without generic + safe call
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupListeners()
        checkLocationPermissionAndEnable()
    }

    private fun initViews(view: View) {
        etPickup = view.findViewById(R.id.etPickup)
        etDestination = view.findViewById(R.id.etDestination)
        btnPickupOptions = view.findViewById(R.id.btnPickupOptions)
        btnDestinationOptions = view.findViewById(R.id.btnDestinationOptions)
        tvDistance = view.findViewById(R.id.tvDistance)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvFare = view.findViewById(R.id.tvFare)
        btnBookNow = view.findViewById(R.id.btnBookNow)
    }

    private fun setupListeners() {
        etPickup.setOnClickListener {
            isPickupSelected = true
            showLocationOptions()
        }

        etDestination.setOnClickListener {
            isPickupSelected = false
            showLocationOptions()
        }

        btnPickupOptions.setOnClickListener {
            isPickupSelected = true
            showLocationOptions()
        }

        btnDestinationOptions.setOnClickListener {
            isPickupSelected = false
            showLocationOptions()
        }

        btnBookNow.setOnClickListener {
            val pickup = etPickup.text.toString()
            val destination = etDestination.text.toString()
            if (pickup.isNotEmpty() && destination.isNotEmpty()) {
                viewModel.bookRide(pickup, destination, "Mini")
            } else {
                Toast.makeText(requireContext(), "Please select pickup and destination", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // LOCATION PERMISSION & ENABLE CHECK
    // ============================================================

    private fun checkLocationPermissionAndEnable() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
            return
        }
        isLocationPermissionGranted = true

        if (!isLocationEnabled()) {
            showUncancelableLocationDialog()
        } else {
            getCurrentLocationForMap()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showUncancelableLocationDialog() {
        if (locationDialog?.isShowing == true) return

        locationDialog = AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Location Required")
            .setMessage("This app needs location access to show nearby rides. Please enable location services.")
            .setCancelable(false)
            .setPositiveButton("Enable Location") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .create()
        locationDialog?.show()
    }

    private fun dismissLocationDialog() {
        locationDialog?.dismiss()
        locationDialog = null
    }

    // ============================================================
    // GET CURRENT LOCATION FOR MAP CENTER
    // ============================================================

    private fun getCurrentLocationForMap() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = LatLng(location.latitude, location.longitude)
                if (::mMap.isInitialized) {
                    centerMapOnCurrentLocation()
                }
            } else {
                requestNewLocation()
            }
        }.addOnFailureListener {
            requestNewLocation()
        }
    }

    private fun requestNewLocation() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    if (::mMap.isInitialized) {
                        centerMapOnCurrentLocation()
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    // ============================================================
    // CENTER MAP ON CURRENT LOCATION WITH 50KM RADIUS
    // ============================================================

    private fun centerMapOnCurrentLocation() {
        currentLocation?.let { latLng ->
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
            mMap.clear()
            addMarker(latLng, "Your Location")
            drawRadiusCircle(latLng, 50000.0)
        }
    }

    // ✅ Fixed: radiusInMeters as Double
    private fun drawRadiusCircle(center: LatLng, radiusInMeters: Double) {
        val circleOptions = CircleOptions()
            .center(center)
            .radius(radiusInMeters)
            .strokeWidth(4f)
            .strokeColor(ContextCompat.getColor(requireContext(), R.color.primary))
            .fillColor(0x99FF6200EE.toInt())  // ~60% transparent
        mMap.addCircle(circleOptions)
    }

    // ============================================================
    // MAP READY CALLBACK
    // ============================================================

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        val defaultLocation = LatLng(28.6139, 77.2090)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))

        if (isLocationPermissionGranted && isLocationEnabled()) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
            }
            if (currentLocation != null) {
                centerMapOnCurrentLocation()
            } else {
                getCurrentLocationForMap()
            }
        } else {
            mMap.clear()
            addMarker(defaultLocation, "Delhi (Default Location)")
            Toast.makeText(requireContext(), "Enable location for nearby places", Toast.LENGTH_LONG).show()
        }
    }

    // ============================================================
    // HELPER FUNCTIONS
    // ============================================================

    private fun addMarker(latLng: LatLng, title: String) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(
                    if (title == "Pickup") BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_RED
                ))
        )
        marker?.showInfoWindow()
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
    }

    private fun getAddressFromLatLng(latLng: LatLng): String? {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0)
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun setLocation(latLng: LatLng, address: String?) {
        val editText = if (isPickupSelected) etPickup else etDestination
        editText.setText(address ?: "${latLng.latitude}, ${latLng.longitude}")

        if (isPickupSelected) {
            pickupLatLng = latLng
            mMap.clear()
            addMarker(latLng, "Pickup")
            currentLocation?.let { drawRadiusCircle(it, 50000.0) }
        } else {
            destinationLatLng = latLng
            addMarker(latLng, "Destination")
        }

        if (pickupLatLng != null && destinationLatLng != null) {
            calculateRouteAndFare(pickupLatLng!!, destinationLatLng!!)
        }
    }

    // ============================================================
    // LOCATION OPTIONS DIALOG
    // ============================================================

    private fun showLocationOptions() {
        val options = arrayOf("🔍 Search", "📍 Select from Map", "📌 Use Current Location")
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Location")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSearchPlace()
                    1 -> openMapPicker()
                    2 -> getCurrentLocation()
                }
            }
            .show()
    }

    private fun openSearchPlace() {
        val intent = Intent(requireContext(), SearchPlaceActivity::class.java)
        currentLocation?.let {
            intent.putExtra("current_location", it)
        }
        startActivityForResult(intent, 100)
    }

    private fun openMapPicker() {
        val intent = Intent(requireContext(), MapPickerActivity::class.java)
        startActivityForResult(intent, 101)
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
            return
        }

        if (!isLocationEnabled()) {
            showUncancelableLocationDialog()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                setLocation(latLng, getAddressFromLatLng(latLng))
            } else {
                requestNewLocation()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ROUTE AND FARE CALCULATION (Fake for now)
    // ============================================================

    private fun calculateRouteAndFare(origin: LatLng, destination: LatLng) {
        val distanceInKm = 10.5
        val durationInMin = 25
        val fare = distanceInKm * 12 + 50

        tvDistance.text = "Distance: %.1f km".format(distanceInKm)
        tvDuration.text = "Time: %d min".format(durationInMin)
        tvFare.text = "Fare: ₹ %.0f".format(fare)
        btnBookNow.isEnabled = true

        drawRoute(origin, destination)
    }

    private fun drawRoute(origin: LatLng, destination: LatLng) {
        val polylineOptions = PolylineOptions()
            .add(origin, destination)
            .width(8f)
            .color(ContextCompat.getColor(requireContext(), R.color.primary))
            .geodesic(true)
        mMap.addPolyline(polylineOptions)
    }

    // ============================================================
    // PERMISSION RESULT
    // ============================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            isLocationPermissionGranted = true
            if (!isLocationEnabled()) {
                showUncancelableLocationDialog()
            } else {
                getCurrentLocationForMap()
            }
        } else {
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ACTIVITY RESULT
    // ============================================================

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == android.app.Activity.RESULT_OK) {
            when (requestCode) {
                100 -> {
                    val latLng = data?.getParcelableExtra<LatLng>("location")
                    val address = data?.getStringExtra("address")
                    if (latLng != null) {
                        setLocation(latLng, address)
                    }
                }
                101 -> {
                    val latLng = data?.getParcelableExtra<LatLng>("location")
                    val address = data?.getStringExtra("address")
                    if (latLng != null) {
                        setLocation(latLng, address)
                    }
                }
            }
        }
    }

    // ============================================================
    // RESUME - Check if location was enabled
    // ============================================================

    override fun onResume() {
        super.onResume()
        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
            getCurrentLocationForMap()
        }
    }
}