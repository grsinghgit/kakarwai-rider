package com.gr.kakarwairider.admin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.fragments.AdminDriverFragment
import com.gr.kakarwairider.admin.fragments.AdminHomeFragment
import com.gr.kakarwairider.admin.fragments.AdminMapFragment
import com.gr.kakarwairider.admin.fragments.AdminPaymentFragment
import com.gr.kakarwairider.admin.fragments.AdminRideFragment

class AdminActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AdminActivity"
    }

    private lateinit var bottomNav: BottomNavigationView
    private val containerId = R.id.admin_fragment_container

    private val homeFragment = AdminHomeFragment()
    private val rideFragment = AdminRideFragment()
    private val driverFragment = AdminDriverFragment()
    private val mapFragment = AdminMapFragment()
    private val paymentFragment = AdminPaymentFragment()

    private var currentFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        bottomNav = findViewById(R.id.admin_bottom_navigation)

        // ✅ Step 3.1: Save FCM Token when app starts
        saveFCMTokenToFirestore()

        // ✅ Step 3.2: Check if opened from notification
        val rideId = intent.getStringExtra("rideId")
        val fromNotification = intent.getBooleanExtra("from_notification", false)

        if (fromNotification && rideId != null) {
            Log.d(TAG, "🔔 Opened from notification: Ride ID = $rideId")

            // ✅ Default fragment load
            if (savedInstanceState == null) {
                // ✅ Load Ride Fragment with rideId argument
                val bundle = Bundle().apply {
                    putString("rideId", rideId)
                }
                val fragment = AdminRideFragment()
                fragment.arguments = bundle
                loadFragment(fragment, "RIDE")
                bottomNav.selectedItemId = R.id.admin_ride
            }
        } else {
            // ✅ Default fragment load
            if (savedInstanceState == null) {
                loadFragment(homeFragment, "HOME")
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.admin_home -> {
                    loadFragment(homeFragment, "HOME")
                    true
                }
                R.id.admin_ride -> {
                    loadFragment(rideFragment, "RIDE")
                    true
                }
                R.id.admin_driver -> {
                    loadFragment(driverFragment, "DRIVER")
                    true
                }
                R.id.admin_map -> {
                    loadFragment(mapFragment, "MAP")
                    true
                }
                R.id.admin_payment -> {
                    loadFragment(paymentFragment, "PAYMENT")
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        if (currentFragment.javaClass == fragment.javaClass && currentFragment.isAdded) {
            return
        }
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
            .commit()
    }

    /**
     * ✅ Step 3.1: Save FCM Token to Firestore
     */
    private fun saveFCMTokenToFirestore() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "📱 FCM Token: $token")
                saveTokenToFirestore(token)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get FCM token: ${e.message}")
            }
    }

    /**
     * ✅ Save token to Firestore for admin notifications
     */
    private fun saveTokenToFirestore(token: String) {
        val db = FirebaseFirestore.getInstance()
        val tokenData = mapOf(
            "token" to token,
            "deviceType" to "android",
            "updatedAt" to Timestamp.now(),
            "isActive" to true
        )

        db.collection("admin_tokens")
            .document("admin_device")
            .set(tokenData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ FCM Token saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save token: ${e.message}")
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ✅ Fixed: onNewIntent with proper override
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // ✅ Handle notification click when app is already open
        val rideId = intent.getStringExtra("rideId")
        val fromNotification = intent.getBooleanExtra("from_notification", false)

        if (fromNotification && rideId != null) {
            Log.d(TAG, "🔔 App already open, navigating to ride: $rideId")
            val bundle = Bundle().apply {
                putString("rideId", rideId)
            }
            val fragment = AdminRideFragment()
            fragment.arguments = bundle
            loadFragment(fragment, "RIDE")
            bottomNav.selectedItemId = R.id.admin_ride
        }
    }
}