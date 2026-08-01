package com.gr.kakarwairider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.gr.kakarwairider.R

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var appUpdateManager: AppUpdateManager

    private var locationDialog: AlertDialog? = null
    private var isPermissionFlowActive = false

    // ✅ In-App Update Request Code
    private val UPDATE_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Kakarwai Rider"

        bottomNav = findViewById(R.id.bottom_navigation)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.historyFragment,
                R.id.userFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav.setupWithNavController(navController)

        updateUIBasedOnLoginStatus()

        // ✅ Initialize App Update Manager
        appUpdateManager = AppUpdateManagerFactory.create(this)

        // ✅ Check for App Updates (FORCE UPDATE)
        checkForForceUpdate()

        // ✅ Check permissions in sequence
        checkAndRequestPermissions()
    }

    // ============================================================
    // ✅ FORCE UPDATE — ONLY IMMEDIATE
    // ============================================================

    private fun checkForForceUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            // ✅ Check if update is available
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // ✅ Check if IMMEDIATE update is allowed
                if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    // ✅ Start IMMEDIATE update (full-screen, user must update)
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        this,
                        UPDATE_REQUEST_CODE
                    )
                }
            }
        }.addOnFailureListener {
            android.util.Log.e("MainActivity", "Update check failed: ${it.message}")
        }
    }

    // ============================================================
    // ✅ PERMISSION FLOW
    // ============================================================

    private fun checkAndRequestPermissions() {
        if (isPermissionFlowActive) return
        isPermissionFlowActive = true

        // ✅ Step 1: Check Location Permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
            return
        }

        // ✅ Step 2: Check if Location is enabled
        if (!isLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }

        // ✅ Step 3: Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission()
                return
            }
        }

        isPermissionFlowActive = false
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            200
        )
    }

    private fun requestNotificationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            300
        )
    }

    private fun showLocationSettingsDialog() {
        if (locationDialog?.isShowing == true) return

        locationDialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Location Required")
            .setMessage("Please enable location services to check service availability and book rides.")
            .setCancelable(false)
            .setPositiveButton("Enable Location") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { _, _ ->
                isPermissionFlowActive = false
                Toast.makeText(this, "Location required for service area check", Toast.LENGTH_SHORT).show()
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

        when (requestCode) {
            200 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✅ Location permission granted", Toast.LENGTH_SHORT).show()
                    if (!isLocationEnabled()) {
                        showLocationSettingsDialog()
                    } else {
                        checkNotificationPermission()
                    }
                } else {
                    isPermissionFlowActive = false
                    Toast.makeText(this, "⚠️ Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
            300 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✅ Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠️ Notification permission denied", Toast.LENGTH_SHORT).show()
                }
                isPermissionFlowActive = false
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission()
                return
            }
        }
        isPermissionFlowActive = false
    }

    override fun onResume() {
        super.onResume()

        // ✅ When user returns from Location Settings, recheck
        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
            checkNotificationPermission()
        }

        // ✅ If location was disabled but now enabled, refresh
        if (isLocationEnabled() && !isPermissionFlowActive) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // All good
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissLocationDialog()
    }

    // ✅ Inflate menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_logout -> {
                logout()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
        updateUIBasedOnLoginStatus()
        navController.navigate(R.id.action_home_to_login)
    }

    fun updateUIBasedOnLoginStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            bottomNav.visibility = BottomNavigationView.VISIBLE
            supportActionBar?.title = "👤 ${currentUser.phoneNumber}"
        } else {
            bottomNav.visibility = BottomNavigationView.GONE
            supportActionBar?.title = "Kakarwai Rider"
        }
    }
}