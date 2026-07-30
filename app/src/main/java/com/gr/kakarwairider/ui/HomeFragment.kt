package com.gr.kakarwairider.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.MainActivity2
import com.gr.kakarwairider.R
import com.gr.kakarwairider.utils.ServiceAreaChecker
import com.gr.kakarwairider.viewmodel.AuthViewModel

class HomeFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var tvWelcome: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var progressServiceCheck: ProgressBar
    private lateinit var cardServiceUnavailable: MaterialCardView
    private lateinit var cardBookRide: CardView
    private lateinit var btnLogout: MaterialButton

    private var isInServiceArea = false
    private var locationDialog: AlertDialog? = null
    private var isCheckingLocation = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        tvWelcome = view.findViewById(R.id.tvWelcome)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        progressServiceCheck = view.findViewById(R.id.progressServiceCheck)
        cardServiceUnavailable = view.findViewById(R.id.cardServiceUnavailable)
        cardBookRide = view.findViewById(R.id.cardBookRide)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Show User Info
        val phoneNumber = authViewModel.getCurrentUserPhone()
        tvWelcome.text = "👋 Welcome, ${phoneNumber?.takeLast(10) ?: "User"}!"
        tvPhone.text = "📱 $phoneNumber"

        // Check Service Area
        checkServiceArea()

        // Card Click Listener
        cardBookRide.setOnClickListener {
            if (isInServiceArea) {
                startActivity(Intent(requireContext(), MainActivity2::class.java))
            } else {
                Toast.makeText(requireContext(), "Service not available in your area", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Logout Button Click
        btnLogout.setOnClickListener {
            authViewModel.logout()
            Toast.makeText(requireContext(), "🔓 Logged out successfully", Toast.LENGTH_SHORT).show()

            // ✅ Navigate back to Login
            findNavController().navigate(R.id.action_home_to_login)

            // ✅ Update MainActivity UI
            (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()
        }
    }

    // ============================================================
    // CHECK SERVICE AREA
    // ============================================================

    private fun checkServiceArea() {
        if (isCheckingLocation) return

        if (!isAdded || context == null) {
            return
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            tvServiceStatus.text = "⚠️ Enable location to check service availability"
            tvServiceStatus.visibility = View.VISIBLE
            progressServiceCheck.visibility = View.GONE
            requestLocationPermission()
            return
        }

        if (!isLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }

        tvServiceStatus.text = "📍 Checking service availability..."
        tvServiceStatus.visibility = View.VISIBLE
        progressServiceCheck.visibility = View.VISIBLE

        isCheckingLocation = true

        val checker = ServiceAreaChecker(requireContext())
        checker.checkUserLocation(object : ServiceAreaChecker.ServiceAreaCallback {
            override fun onResult(isInService: Boolean, distance: Double, userLocation: LatLng?) {
                isCheckingLocation = false

                if (!isAdded || context == null) {
                    return
                }

                this@HomeFragment.isInServiceArea = isInService
                progressServiceCheck.visibility = View.GONE

                val areaName = checker.getAreaName()
                val radiusKm = checker.getServiceRadius() / 1000

                if (isInService) {
                    tvServiceStatus.text = "✅ Service Available ($areaName, ${(distance / 1000).toInt()}km away)"
                    tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                    cardServiceUnavailable.visibility = View.GONE
                    cardBookRide.isEnabled = true
                    cardBookRide.alpha = 1.0f
                } else {
                    tvServiceStatus.text = "❌ Service not available ($areaName, ${(distance / 1000).toInt()}km away)"
                    tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    cardServiceUnavailable.visibility = View.VISIBLE
                    cardBookRide.isEnabled = false
                    cardBookRide.alpha = 0.5f
                }
            }

            override fun onError(message: String) {
                isCheckingLocation = false

                if (!isAdded || context == null) {
                    return
                }

                progressServiceCheck.visibility = View.GONE
                tvServiceStatus.text = "⚠️ Unable to check service area: $message"
                tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationSettingsDialog() {
        if (locationDialog?.isShowing == true) return

        locationDialog = AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Location Required")
            .setMessage("Please enable location services to check service availability.")
            .setCancelable(false)
            .setPositiveButton("Enable Location") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { _, _ ->
                tvServiceStatus.text = "⚠️ Location required to check service area"
                tvServiceStatus.visibility = View.VISIBLE
                progressServiceCheck.visibility = View.GONE
                cardServiceUnavailable.visibility = View.VISIBLE
                cardBookRide.isEnabled = false
                cardBookRide.alpha = 0.5f
            }
            .create()
        locationDialog?.show()
    }

    private fun dismissLocationDialog() {
        locationDialog?.dismiss()
        locationDialog = null
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            200
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!isLocationEnabled()) {
                    showLocationSettingsDialog()
                } else {
                    checkServiceArea()
                }
            } else {
                if (isAdded && context != null) {
                    tvServiceStatus.text = "⚠️ Location permission required"
                    tvServiceStatus.visibility = View.VISIBLE
                    progressServiceCheck.visibility = View.GONE
                    cardServiceUnavailable.visibility = View.VISIBLE
                    cardBookRide.isEnabled = false
                    cardBookRide.alpha = 0.5f
                    Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
            view?.postDelayed({
                if (isAdded && context != null) {
                    checkServiceArea()
                }
            }, 300)
            return
        }

        if (!isInServiceArea && isAdded && context != null) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isLocationEnabled()) {
                    view?.postDelayed({
                        if (isAdded && context != null) {
                            checkServiceArea()
                        }
                    }, 300)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissLocationDialog()
        isCheckingLocation = false
    }
}