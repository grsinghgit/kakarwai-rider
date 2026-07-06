package com.gr.kakarwairider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var locationDialog: AlertDialog? = null
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ Location Check - Mandatory
        checkLocationAndProceed()

        // ✅ Login Buttons Setup
        setupLoginButtons()
    }

    private fun setupLoginButtons() {
        val btnUserLogin: MaterialButton = findViewById(R.id.btnUserLogin)
        val btnDriverLogin: MaterialButton = findViewById(R.id.btnDriverLogin)
        val btnVendorLogin: MaterialButton = findViewById(R.id.btnVendorLogin)
        val btnAdminLogin: MaterialButton = findViewById(R.id.btnAdminLogin)

        btnUserLogin.setOnClickListener {
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
            // TODO: Open User Login Fragment/Activity
        }

        btnDriverLogin.setOnClickListener {
            showToast("Driver Login Clicked - Authentication coming soon")
            // TODO: Open Driver Login Fragment/Activity
        }

        btnVendorLogin.setOnClickListener {
            showToast("Vendor Login Clicked - Authentication coming soon")
            // TODO: Open Vendor Login Fragment/Activity
        }

        btnAdminLogin.setOnClickListener {
            showToast("Admin Login Clicked - Authentication coming soon")
            // TODO: Open Admin Login Fragment/Activity
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // LOCATION CHECK (Mandatory)
    // ============================================================

    private fun checkLocationAndProceed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
            return
        }

        if (!isLocationEnabled()) {
            showUncancelableLocationDialog()
            return
        }

        // ✅ Location is ON - No need to proceed automatically, user will click login
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showUncancelableLocationDialog() {
        if (locationDialog?.isShowing == true) return

        locationDialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Location Required")
            .setMessage("This app needs location access. Please enable location services to continue.")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!isLocationEnabled()) {
                    showUncancelableLocationDialog()
                }
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Permission Required")
            .setMessage("Location permission is mandatory to use this app. Please grant location permission.")
            .setCancelable(false)
            .setPositiveButton("Grant Permission") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Exit") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()

        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
        }
    }
}