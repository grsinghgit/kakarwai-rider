package com.gr.kakarwairider

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import java.io.IOException
import java.util.*

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: LatLng? = null
    private var selectedLatLng: LatLng? = null
    private var isMapReady = false

    // ✅ Views
    private lateinit var tvAddress: TextView
    private lateinit var btnToggleMapType: MaterialButton

    // ✅ Map type state
    private var isSatelliteView = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        tvAddress = findViewById(R.id.tvAddress)
        btnToggleMapType = findViewById(R.id.btnToggleMapType)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapPicker) as SupportMapFragment
        mapFragment.getMapAsync(this)

        getCurrentLocation()

        // ✅ Toggle Button Click
        btnToggleMapType.setOnClickListener {
            toggleMapType()
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                200
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = LatLng(location.latitude, location.longitude)
                if (isMapReady) {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
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
                    if (isMapReady) {
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
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
            mMap.addMarker(MarkerOptions().position(latLng).title("📍 Your Location"))
            getAddressFromLatLng(latLng) { address ->
                tvAddress.text = "📍 $address"
                tvAddress.visibility = View.VISIBLE
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true

        // ✅ Default: Satellite view
        mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        isSatelliteView = true
        btnToggleMapType.text = "🗺️ Normal"

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }

        if (currentLocation != null) {
            centerMapOnCurrentLocation()
        } else {
            val defaultLocation = LatLng(28.6139, 77.2090)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
            Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
        }

        // ✅ Map Click Listener
        mMap.setOnMapClickListener { latLng ->
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("📍 Selected Location"))
            selectedLatLng = latLng

            getAddressFromLatLng(latLng) { address ->
                tvAddress.text = "📍 $address"
                tvAddress.visibility = View.VISIBLE
            }
        }

        // ✅ Long press to confirm
        mMap.setOnMapLongClickListener { latLng ->
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("📍 Selected Location"))
            selectedLatLng = latLng

            getAddressFromLatLng(latLng) { address ->
                tvAddress.text = "📍 $address"
                tvAddress.visibility = View.VISIBLE
                Toast.makeText(this, "📍 Location selected: $address", Toast.LENGTH_LONG).show()
                confirmLocation(latLng)
            }
        }
    }

    // ✅ Toggle Map Type
    private fun toggleMapType() {
        if (isSatelliteView) {
            // ✅ Switch to Normal
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            btnToggleMapType.text = "🛰️ Satellite"
            isSatelliteView = false
            Toast.makeText(this, "🗺️ Normal View", Toast.LENGTH_SHORT).show()
        } else {
            // ✅ Switch to Satellite
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            btnToggleMapType.text = "🗺️ Normal"
            isSatelliteView = true
            Toast.makeText(this, "🛰️ Satellite View", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Get address from LatLng
    private fun getAddressFromLatLng(latLng: LatLng, callback: (String) -> Unit) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0].getAddressLine(0)
                callback(address ?: "${latLng.latitude}, ${latLng.longitude}")
            } else {
                callback("${latLng.latitude}, ${latLng.longitude}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            callback("${latLng.latitude}, ${latLng.longitude}")
        }
    }

    private fun confirmLocation(latLng: LatLng) {
        getAddressFromLatLng(latLng) { address ->
            val intent = Intent()
            intent.putExtra("location", latLng)
            intent.putExtra("address", address)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::mMap.isInitialized) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        }
    }
}