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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.adapter.VehicleSelectionAdapter
import com.gr.kakarwairider.model.VehicleOption
import com.gr.kakarwairider.ui.RideProcessingFragment
import com.gr.kakarwairider.utils.DistanceUtils
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
    private lateinit var vehicleRecyclerView: RecyclerView

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var pickupLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null
    private var isPickupSelected = true
    private var isLocationPermissionGranted = false
    private var currentLocation: LatLng? = null

    private var distanceValue: Double = 0.0
    private var durationValue: Int = 0
    private var fareValue: Double = 0.0

    private lateinit var vehicleAdapter: VehicleSelectionAdapter
    private var selectedVehicle: VehicleOption? = null
    private var areaVehicles: List<VehicleOption> = emptyList()

    private val viewModel: BookRideViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()
    private var locationDialog: AlertDialog? = null

    private val LOCATION_PERMISSION_REQUEST = 200

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
        setupVehicleSelection()

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupListeners()
        checkLocationPermissionAndEnable()

        // ✅ Observe Area Vehicles
        viewModel.areaVehicles.observe(viewLifecycleOwner) { vehicles ->
            areaVehicles = vehicles
            if (vehicles.isNotEmpty()) {
                vehicleAdapter.updateVehicles(vehicles)
                selectedVehicle = vehicles.firstOrNull()
                if (distanceValue > 0 && selectedVehicle != null) {
                    calculateFareWithVehicle(selectedVehicle!!)
                }
            } else {
                Toast.makeText(requireContext(), "No vehicles available in this area", Toast.LENGTH_SHORT).show()
                btnBookNow.isEnabled = false
            }
        }

        // ✅ Observe Ride Creation
        viewModel.rideCreated.observe(viewLifecycleOwner) { ride ->
            if (ride != null) {
                val bundle = Bundle().apply {
                    putString("rideId", ride.rideId)
                    putString("pickupAddress", ride.pickup?.address ?: "")
                    putString("destinationAddress", ride.destination?.address ?: "")
                    putDouble("distance", ride.distance)
                    putInt("duration", ride.duration)
                    putDouble("totalFare", ride.totalFare)
                    putString("vehicleType", ride.vehicleType)
                    putString("vehicleIcon", ride.vehicleIcon)
                    putString("vehicleName", ride.vehicleName)
                }
                val fragment = RideProcessingFragment()
                fragment.arguments = bundle
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
                btnBookNow.isEnabled = true
                btnBookNow.text = "🚗 Book Now"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            btnBookNow.isEnabled = !isLoading
            btnBookNow.text = if (isLoading) "⏳ Booking..." else "🚗 Book Now"
        }
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
        vehicleRecyclerView = view.findViewById(R.id.vehicleRecyclerView)
    }

    private fun setupVehicleSelection() {
        vehicleAdapter = VehicleSelectionAdapter(emptyList()) { vehicle ->
            selectedVehicle = vehicle
            if (distanceValue > 0) {
                calculateFareWithVehicle(vehicle)
            }
        }
        vehicleRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        vehicleRecyclerView.adapter = vehicleAdapter
    }

    private fun calculateFareWithVehicle(vehicle: VehicleOption) {
        val distanceFare = distanceValue * vehicle.perKmRate
        val totalFare = vehicle.basePrice + distanceFare
        fareValue = totalFare
        tvFare.text = "${vehicle.icon} ${vehicle.name}: ₹${totalFare.toInt()}"
        btnBookNow.isEnabled = true
    }

    // ✅ UPDATED: No API - Direct Haversine Calculation
    private fun calculateDistanceAndFare() {
        if (pickupLatLng == null || destinationLatLng == null) {
            Toast.makeText(requireContext(), "Please select both locations", Toast.LENGTH_SHORT).show()
            return
        }

        tvDistance.text = "📍 Calculating distance..."
        tvDuration.text = "⏱️ Calculating..."
        tvFare.text = "💰 Calculating fare..."
        btnBookNow.isEnabled = false

        // ✅ Haversine Formula - No API!
        val distance = DistanceUtils.calculateDistance(
            pickupLatLng!!.latitude,
            pickupLatLng!!.longitude,
            destinationLatLng!!.latitude,
            destinationLatLng!!.longitude
        )

        distanceValue = distance

        // ✅ Estimate duration (Average speed: 30 km/h = 0.5 km/min)
        val estimatedDuration = (distance / 0.5).toInt()
        durationValue = estimatedDuration

        tvDistance.text = "📍 Distance: %.1f km".format(distance)
        tvDuration.text = "⏱️ Time: %d min".format(estimatedDuration)

        // ✅ Draw route on map
        drawRoute()

        // ✅ Fetch vehicles for this area
        viewModel.fetchVehiclesForArea(
            pickupLatLng!!.latitude,
            pickupLatLng!!.longitude
        )
    }

    private fun drawRoute() {
        if (pickupLatLng == null || destinationLatLng == null) return

        mMap.clear()
        addMarker(pickupLatLng!!, "Pickup")
        addMarker(destinationLatLng!!, "Destination")

        val polylineOptions = PolylineOptions()
            .add(pickupLatLng!!, destinationLatLng!!)
            .width(8f)
            .color(0xFF2196F3.toInt())
            .geodesic(true)
        mMap.addPolyline(polylineOptions)

        // ✅ Center map on both points
        val builder = LatLngBounds.Builder()
        builder.include(pickupLatLng!!)
        builder.include(destinationLatLng!!)
        val bounds = builder.build()
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
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
                if (selectedVehicle == null) {
                    Toast.makeText(requireContext(), "Please select a vehicle", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                checkActiveRide {
                    val vehicle = selectedVehicle!!
                    viewModel.bookRide(
                        pickupAddress = pickup,
                        pickupLat = pickupLatLng?.latitude ?: 0.0,
                        pickupLng = pickupLatLng?.longitude ?: 0.0,
                        destinationAddress = destination,
                        destinationLat = destinationLatLng?.latitude ?: 0.0,
                        destinationLng = destinationLatLng?.longitude ?: 0.0,
                        vehicleType = vehicle.type,
                        vehicleIcon = vehicle.icon,
                        vehicleName = vehicle.name,
                        distance = distanceValue,
                        duration = durationValue,
                        basePrice = vehicle.basePrice,
                        perKmRate = vehicle.perKmRate,
                        totalFare = fareValue
                    )
                }
            } else {
                Toast.makeText(requireContext(), "Please select pickup and destination", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkActiveRide(callback: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("rides")
            .whereEqualTo("userId", userId)
            .whereIn("status", listOf("PENDING", "SEARCHING", "DRIVER_ASSIGNED", "STARTED"))
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    callback()
                } else {
                    Toast.makeText(requireContext(),
                        "⚠️ You already have an active ride!\nPlease complete it first.",
                        Toast.LENGTH_LONG).show()
                    val rideId = documents.first().id
                    val bundle = Bundle().apply {
                        putString("rideId", rideId)
                    }
                    val fragment = RideProcessingFragment()
                    fragment.arguments = bundle
                    requireActivity().supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
            .addOnFailureListener {
                callback()
            }
    }

    // ============================================================
    // LOCATION FUNCTIONS (Unchanged - Keep as is)
    // ============================================================

    private fun checkLocationPermissionAndEnable() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            isLocationPermissionGranted = true
            if (isLocationEnabled()) {
                getCurrentLocationForMap()
            } else {
                showUncancelableLocationDialog()
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(requireContext())
                .setTitle("Location Permission Required")
                .setMessage("This app needs location access to show nearby rides and book rides.")
                .setPositiveButton("Grant") { _, _ ->
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        LOCATION_PERMISSION_REQUEST
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
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
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

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

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun centerMapOnCurrentLocation() {
        currentLocation?.let { latLng ->
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
            mMap.clear()
            addMarker(latLng, "Your Location")
            drawRadiusCircle(latLng, 50000.0)
        }
    }

    private fun drawRadiusCircle(center: LatLng, radiusInMeters: Double) {
        val circleOptions = CircleOptions()
            .center(center)
            .radius(radiusInMeters)
            .strokeWidth(3f)
            .strokeColor(0xFF2196F3.toInt())
            .fillColor(0x332196F3.toInt())
        mMap.addCircle(circleOptions)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        val defaultLocation = LatLng(28.6139, 77.2090)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

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
        } else {
            destinationLatLng = latLng
        }

        if (pickupLatLng != null && destinationLatLng != null) {
            // ✅ Calculate distance without API
            calculateDistanceAndFare()
        }
    }

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

        val locationToSend = currentLocation ?: LatLng(28.6139, 77.2090)
        intent.putExtra("current_location", locationToSend)

        startActivityForResult(intent, 100)
    }

    private fun openMapPicker() {
        val intent = Intent(requireContext(), MapPickerActivity::class.java)
        startActivityForResult(intent, 101)
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    isLocationPermissionGranted = true
                    if (!isLocationEnabled()) {
                        showUncancelableLocationDialog()
                    } else {
                        getCurrentLocationForMap()
                    }
                } else {
                    Toast.makeText(requireContext(), "Location permission required to book rides", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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

    override fun onResume() {
        super.onResume()
        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
            getCurrentLocationForMap()
        }
    }
}