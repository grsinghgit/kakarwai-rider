package com.gr.kakarwairider.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.utils.ServiceAreaChecker
import com.gr.kakarwairider.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var btnSendOTP: MaterialButton
    private lateinit var btnResendOTP: MaterialButton
    private lateinit var etOTP: TextInputEditText
    private lateinit var btnVerifyOTP: MaterialButton
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvPrivacyPolicy: TextView
    private lateinit var tvTermsConditions: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var cbAcceptTerms: CheckBox

    private var isInServiceArea = false
    private var locationDialog: AlertDialog? = null
    private var isCheckingLocation = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        btnSendOTP = view.findViewById(R.id.btnSendOTP)
        btnResendOTP = view.findViewById(R.id.btnResendOTP)
        etOTP = view.findViewById(R.id.etOTP)
        btnVerifyOTP = view.findViewById(R.id.btnVerifyOTP)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        tvPrivacyPolicy = view.findViewById(R.id.tvPrivacyPolicy)
        tvTermsConditions = view.findViewById(R.id.tvTermsConditions)
        tvContactInfo = view.findViewById(R.id.tvContactInfo)
        cbAcceptTerms = view.findViewById(R.id.cbAcceptTerms)

        // Initially hide OTP fields
        etOTP.visibility = View.GONE
        btnVerifyOTP.visibility = View.GONE
        btnResendOTP.visibility = View.GONE

        // Disable buttons until service area is checked
        btnSendOTP.isEnabled = false
        btnVerifyOTP.isEnabled = false

        // Check Service Area First
        checkServiceArea()

        // Privacy Policy Click
        tvPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/kakarwairidertc/privacy-policy"))
            startActivity(intent)
        }

        // Terms and Conditions Click
        tvTermsConditions.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/kakarwairidertc/term-condition"))
            startActivity(intent)
        }

        // Send OTP Button
        btnSendOTP.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(requireContext(), "Enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phoneNumber.length != 10 || !phoneNumber.all { it.isDigit() }) {
                Toast.makeText(requireContext(), "Enter valid 10 digit phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!cbAcceptTerms.isChecked) {
                Toast.makeText(requireContext(), "Please accept Terms and Conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isInServiceArea) {
                val fullPhoneNumber = "+91$phoneNumber"
                authViewModel.sendOTP(fullPhoneNumber, requireActivity())
            } else {
                Toast.makeText(requireContext(), "🚧 Service not available in your area", Toast.LENGTH_SHORT).show()
            }
        }

        // Resend OTP Button
        btnResendOTP.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(requireContext(), "Enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phoneNumber.length != 10 || !phoneNumber.all { it.isDigit() }) {
                Toast.makeText(requireContext(), "Enter valid 10 digit phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!cbAcceptTerms.isChecked) {
                Toast.makeText(requireContext(), "Please accept Terms and Conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isInServiceArea) {
                val fullPhoneNumber = "+91$phoneNumber"
                authViewModel.resendOTP(fullPhoneNumber, requireActivity())
            } else {
                Toast.makeText(requireContext(), "🚧 Service not available in your area", Toast.LENGTH_SHORT).show()
            }
        }

        // Verify OTP Button
        btnVerifyOTP.setOnClickListener {
            val otp = etOTP.text.toString().trim()
            if (otp.isNotEmpty()) {
                authViewModel.verifyOTP(otp)
            } else {
                Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe LiveData
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            btnSendOTP.isEnabled = !isLoading && isInServiceArea && cbAcceptTerms.isChecked
            btnVerifyOTP.isEnabled = !isLoading && isInServiceArea
            if (isLoading) {
                btnSendOTP.text = "Sending..."
                btnVerifyOTP.text = "Verifying..."
            } else {
                btnSendOTP.text = "Send OTP"
                btnVerifyOTP.text = "Verify OTP"
            }
        }

        authViewModel.verificationSent.observe(viewLifecycleOwner) { sent ->
            if (sent) {
                Toast.makeText(requireContext(), "OTP Sent Successfully!", Toast.LENGTH_SHORT).show()
                etOTP.visibility = View.VISIBLE
                btnVerifyOTP.visibility = View.VISIBLE
                btnResendOTP.visibility = View.VISIBLE
                btnSendOTP.visibility = View.GONE
                etOTP.requestFocus()
            }
        }

        authViewModel.otpVerified.observe(viewLifecycleOwner) { verified ->
            if (verified) {
                Toast.makeText(requireContext(), "Login Successful! 🎉", Toast.LENGTH_LONG).show()
                (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()
                findNavController().navigate(R.id.action_login_to_home)
            }
        }

        authViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                authViewModel.clearError()
            }
        }

        // Check if already logged in
        if (authViewModel.isUserLoggedIn()) {
            findNavController().navigate(R.id.action_login_to_home)
        }
    }

    // ============================================================
    // ✅ CHECK SERVICE AREA (Firebase se fetch) — WITH DELAY
    // ============================================================

    private fun checkServiceArea() {
        // Prevent multiple concurrent checks
        if (isCheckingLocation) return

        if (!isAdded || context == null) {
            return
        }

        // Check Location Permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            tvServiceStatus.text = "⚠️ Enable location to check service availability"
            tvServiceStatus.visibility = View.VISIBLE
            btnSendOTP.isEnabled = false
            requestLocationPermission()
            return
        }

        // Check if Location is enabled
        if (!isLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }

        tvServiceStatus.text = "📍 Checking service availability..."
        tvServiceStatus.visibility = View.VISIBLE

        isCheckingLocation = true

        val checker = ServiceAreaChecker(requireContext())
        checker.checkUserLocation(object : ServiceAreaChecker.ServiceAreaCallback {
            override fun onResult(isInService: Boolean, distance: Double, userLocation: LatLng?) {
                isCheckingLocation = false

                if (!isAdded || context == null) {
                    return
                }

                isInServiceArea = isInService

                val areaName = checker.getAreaName()
                val radiusKm = checker.getServiceRadius() / 1000

                if (isInService) {
                    tvServiceStatus.text = "✅ Service Available ($areaName, ${(distance / 1000).toInt()}km away)"
                    tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                    btnSendOTP.isEnabled = true
                    btnSendOTP.alpha = 1.0f
                } else {
                    tvServiceStatus.text = "🚧 Coming Soon! ($areaName, ${(distance / 1000).toInt()}km away)"
                    tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                    btnSendOTP.isEnabled = false
                    btnSendOTP.alpha = 0.5f
                    Toast.makeText(requireContext(), "🚧 Service coming soon in your area!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(message: String) {
                isCheckingLocation = false

                if (!isAdded || context == null) {
                    return
                }

                tvServiceStatus.text = "⚠️ Unable to check service area: $message"
                tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                btnSendOTP.isEnabled = false
                Toast.makeText(requireContext(), "Unable to check service area", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Check if Location is enabled
    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Show Location Settings Dialog
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
                btnSendOTP.isEnabled = false
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
                // Permission granted, check location again with delay
                if (!isLocationEnabled()) {
                    showLocationSettingsDialog()
                } else {
                    // ✅ DELAY: Wait for location to be available
                    handler.postDelayed({
                        if (isAdded && context != null) {
                            checkServiceArea()
                        }
                    }, 1500)
                }
            } else {
                if (isAdded && context != null) {
                    tvServiceStatus.text = "⚠️ Location permission required"
                    tvServiceStatus.visibility = View.VISIBLE
                    btnSendOTP.isEnabled = false
                    Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // When user returns from Location Settings, force refresh with delay
        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
            // ✅ DELAY: Wait for location to be available
            handler.postDelayed({
                if (isAdded && context != null) {
                    checkServiceArea()
                }
            }, 1500)
            return
        }

        // If location was previously disabled but now enabled, refresh with delay
        if (!isInServiceArea && isAdded && context != null) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isLocationEnabled()) {
                    handler.postDelayed({
                        if (isAdded && context != null) {
                            checkServiceArea()
                        }
                    }, 1500)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissLocationDialog()
        isCheckingLocation = false
        handler.removeCallbacksAndMessages(null)
    }
}