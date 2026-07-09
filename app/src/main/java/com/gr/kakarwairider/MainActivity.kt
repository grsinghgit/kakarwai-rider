package com.gr.kakarwairider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
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
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView
    private var locationDialog: AlertDialog? = null
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Kakarwai Rider"

        checkLocationAndProceed()

        bottomNav = findViewById(R.id.bottom_navigation)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.driverFragment,
                R.id.vendorFragment,
                R.id.adminFragment,
                R.id.loginFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav.setupWithNavController(navController)

        // ✅ Update UI based on login status
        updateUIBasedOnLoginStatus()

        // ✅ Set bottom navigation item selected listener for admin login check
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.adminFragment -> {
                    // ✅ Check if admin already logged in
                    val sharedPref = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                    val isAdminLoggedIn = sharedPref.getBoolean("isAdminLoggedIn", false)

                    if (isAdminLoggedIn) {
                        navController.navigate(R.id.adminFragment)
                    } else {
                        navController.navigate(R.id.adminLoginFragment)
                    }
                    true
                }
                else -> {
                    // ✅ Default navigation for other items
                    navController.navigate(item.itemId)
                    true
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            logout()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        // ✅ Clear admin login state on logout
        val sharedPref = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        FirebaseAuth.getInstance().signOut()
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
        }
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

        updateUIBasedOnLoginStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}