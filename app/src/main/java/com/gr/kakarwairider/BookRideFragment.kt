package com.gr.kakarwairider

import android.Manifest

import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
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
import com.gr.kakarwairider.MapPickerActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.SearchPlaceActivity
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

    // Pickup या Destination कौन सा चुना जा रहा है, इसके लिए flag
    private var isPickupSelected = true

    private val viewModel: BookRideViewModel by viewModels()

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

        // MapFragment initialize
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Click listeners
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
            // Ride Book करने का कोड (पिछले उदाहरण से लें)
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
    }

    /**
     * Location Options Dialog दिखाएँ: Search, Map Cursor, Current Location
     */
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

    // ---------- Option 1: Search (Google Places Autocomplete) ----------
    private fun openSearchPlace() {
        val intent = Intent(requireContext(), SearchPlaceActivity::class.java)
        startActivityForResult(intent, 100)
    }

    // ---------- Option 2: Map Picker (Select by tapping on map) ----------
    private fun openMapPicker() {
        val intent = Intent(requireContext(), MapPickerActivity::class.java)
        startActivityForResult(intent, 101)
    }

    // ---------- Option 3: Current Location ----------
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                setLocation(latLng, getAddressFromLatLng(latLng))
            } else {
                // अगर lastLocation null है तो नया location request करें
                requestNewLocation()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNewLocation() {
        // LocationRequest बनाएँ
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    setLocation(latLng, getAddressFromLatLng(latLng))
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    // ---------- लोकेशन सेट करने का Common Function ----------
    private fun setLocation(latLng: LatLng, address: String?) {
        val editText = if (isPickupSelected) etPickup else etDestination
        editText.setText(address ?: "${latLng.latitude}, ${latLng.longitude}")

        if (isPickupSelected) {
            pickupLatLng = latLng
            // मैप पर मार्कर
            mMap.clear()
            addMarker(latLng, "Pickup")
        } else {
            destinationLatLng = latLng
            addMarker(latLng, "Destination")
        }

        // दोनों सेट होने पर दूरी और फेयर कैलकुलेट करें
        if (pickupLatLng != null && destinationLatLng != null) {
            calculateRouteAndFare(pickupLatLng!!, destinationLatLng!!)
        }
    }

    private fun getAddressFromLatLng(latLng: LatLng): String? {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            // ✅ Null Safety Check
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

    // ---------- Map Ready ----------
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        // डिफ़ॉल्ट लोकेशन (दिल्ली)
        val defaultLocation = LatLng(28.6139, 77.2090)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))

        // My Location Enable
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
    }

    // ---------- Map पर Marker Add करें ----------
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

    // ---------- Route Drawing and Fare Calculation ----------
    private fun calculateRouteAndFare(origin: LatLng, destination: LatLng) {
        // यहाँ आप Google Maps Distance Matrix API या अपने बैकएंड को कॉल करेंगे
        // अभी हम एक सिमुलेशन करते हैं (fake distance & fare)
        val distanceInKm = 10.5 // मान लिया
        val durationInMin = 25
        val fare = distanceInKm * 12 + 50 // ₹12 per km + base fare

        tvDistance.text = "Distance: %.1f km".format(distanceInKm)
        tvDuration.text = "Time: %d min".format(durationInMin)
        tvFare.text = "Fare: ₹ %.0f".format(fare)
        btnBookNow.isEnabled = true

        // मैप पर Polyline Draw करें (सिमुलेटेड)
        drawRoute(origin, destination)
    }

    private fun drawRoute(origin: LatLng, destination: LatLng) {
        // यहाँ आप Directions API से Polyline points लाकर draw करेंगे
        // सरलता के लिए सीधी रेखा खींचते हैं
        val polylineOptions = PolylineOptions()
            .add(origin, destination)
            .width(8f)
            .color(ContextCompat.getColor(requireContext(), R.color.primary))
            .geodesic(true)

        mMap.addPolyline(polylineOptions)
    }

    // ---------- Permission Result ----------
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Activity Result (Search / MapPicker से वापसी) ----------
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == android.app.Activity.RESULT_OK) {
            when (requestCode) {
                100 -> { // SearchPlace से
                    val latLng = data?.getParcelableExtra<LatLng>("location")
                    val address = data?.getStringExtra("address")
                    if (latLng != null) {
                        setLocation(latLng, address)
                    }
                }
                101 -> { // MapPicker से
                    val latLng = data?.getParcelableExtra<LatLng>("location")
                    val address = data?.getStringExtra("address")
                    if (latLng != null) {
                        setLocation(latLng, address)
                    }
                }
            }
        }
    }
}