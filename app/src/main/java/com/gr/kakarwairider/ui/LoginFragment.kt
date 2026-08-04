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
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.utils.GoogleSignInHelper
import com.gr.kakarwairider.utils.ServiceAreaChecker
import com.gr.kakarwairider.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvPrivacyPolicy: TextView
    private lateinit var tvTermsConditions: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var cbAcceptTerms: CheckBox

    private var isInServiceArea = false
    private var locationDialog: AlertDialog? = null
    private var isCheckingLocation = false
    private val handler = Handler(Looper.getMainLooper())

    // ✅ Google Sign-In Request Code
    private val RC_GOOGLE_SIGN_IN = 1001

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Initialize Views
        btnGoogleSignIn = view.findViewById(R.id.btnGoogleSignIn)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        tvPrivacyPolicy = view.findViewById(R.id.tvPrivacyPolicy)
        tvTermsConditions = view.findViewById(R.id.tvTermsConditions)
        tvContactInfo = view.findViewById(R.id.tvContactInfo)
        cbAcceptTerms = view.findViewById(R.id.cbAcceptTerms)

        // ✅ Initialize Google Sign-In Helper
        googleSignInHelper = GoogleSignInHelper(requireContext())

        // ✅ Check Service Area First
        checkServiceArea()

        // ✅ Privacy Policy Click
        tvPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/kakarwairidertc/privacy-policy"))
            startActivity(intent)
        }

        // ✅ Terms and Conditions Click
        tvTermsConditions.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/kakarwairidertc/term-condition"))
            startActivity(intent)
        }

        // ✅ Google Sign-In Button
        btnGoogleSignIn.setOnClickListener {
            if (!cbAcceptTerms.isChecked) {
                Toast.makeText(requireContext(), "Please accept Terms and Conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isInServiceArea) {
                Toast.makeText(requireContext(), "🚧 Service not available in your area", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Start Google Sign-In
            startGoogleSignIn()
        }

        // ✅ Check if already signed in
        if (googleSignInHelper.isAlreadySignedIn()) {
            navigateToHome()
        }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInHelper.getSignInIntent()
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            googleSignInHelper.handleSignInResult(
                data = data,
                onSuccess = { account ->
                    // ✅ Google Sign-In Success
                    Toast.makeText(requireContext(), "✅ Welcome ${account.displayName}!", Toast.LENGTH_SHORT).show()

                    // ✅ Sign in with Firebase
                    googleSignInHelper.signInWithFirebase(
                        account = account,
                        onComplete = { success, uid ->
                            if (success && uid != null) {
                                // ✅ Check if user exists in Firestore
                                googleSignInHelper.checkUserExists(uid) { exists ->
                                    if (exists) {
                                        // ✅ Existing user → Direct Home
                                        navigateToHome()
                                    } else {
                                        // ✅ New user → Navigate to UserFragment
                                        val bundle = Bundle().apply {
                                            putString("userName", account.displayName)
                                            putString("userEmail", account.email)
                                            putString("googleId", account.id)
                                        }
                                        findNavController().navigate(R.id.action_login_to_user, bundle)
                                    }
                                }
                            } else {
                                Toast.makeText(requireContext(), "❌ Firebase sign-in failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                onError = { error ->
                    Toast.makeText(requireContext(), "❌ $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun navigateToHome() {
        (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()
        findNavController().navigate(R.id.action_login_to_home)
    }

    // ============================================================
    // ✅ CHECK SERVICE AREA
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
            btnGoogleSignIn.isEnabled = false
            requestLocationPermission()
            return
        }

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

                this@LoginFragment.isInServiceArea = isInService

                val areaName = checker.getAreaName()
                val radiusKm = checker.getServiceRadius() / 1000

                if (isInService) {
                    tvServiceStatus.text = "✅ Service Available ($areaName, ${(distance / 1000).toInt()}km away)"
                    tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                    btnGoogleSignIn.isEnabled = true
                    btnGoogleSignIn.alpha = 1.0f
                } else {
                    tvServiceStatus.text = "🚧 Coming Soon! ($areaName, ${(distance / 1000).toInt()}km away)"
                    tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                    btnGoogleSignIn.isEnabled = false
                    btnGoogleSignIn.alpha = 0.5f
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
                btnGoogleSignIn.isEnabled = false
                Toast.makeText(requireContext(), "Unable to check service area", Toast.LENGTH_SHORT).show()
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
                btnGoogleSignIn.isEnabled = false
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
                    btnGoogleSignIn.isEnabled = false
                    Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (locationDialog?.isShowing == true && isLocationEnabled()) {
            dismissLocationDialog()
            handler.postDelayed({
                if (isAdded && context != null) {
                    checkServiceArea()
                }
            }, 1500)
            return
        }

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