package com.gr.kakarwairider

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.ui.RideProcessingFragment
import com.gr.kakarwairider.ui.RideTrackingFragment

class MainActivity2 : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var bottomNav: BottomNavigationView

    private val bookRideFragment = BookRideFragment()
    private val historyFragment = HistoryFragment()
    private val profileFragment = ProfileFragment()

    private var currentFragment: Fragment = bookRideFragment
    private var activeRideId: String? = null
    private var isRideActive = false
    private var rideStatus: String? = null

    private val containerId = R.id.fragment_container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        bottomNav = findViewById(R.id.bottom_navigation)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRideActive) {
                    Toast.makeText(this@MainActivity2, "⚠️ Ride in progress! Cannot go back.", Toast.LENGTH_SHORT).show()
                    return
                }
                finish()
            }
        })

        // ✅ Check active ride only once on create
        checkActiveRideAndNavigate { hasActiveRide ->
            if (hasActiveRide) {
                showActiveRideFragment()
            } else {
                loadFragment(bookRideFragment, "HOME")
                bottomNav.visibility = View.VISIBLE
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    if (isRideActive) {
                        Toast.makeText(this, "⚠️ You have an active ride!", Toast.LENGTH_SHORT).show()
                        return@setOnItemSelectedListener true
                    }
                    loadFragment(bookRideFragment, "HOME")
                    true
                }
                R.id.historyFragment -> {
                    loadFragment(historyFragment, "HISTORY")
                    true
                }
                R.id.profileFragment -> {
                    loadFragment(profileFragment, "PROFILE")
                    true
                }
                else -> false
            }
        }
    }

    private fun checkActiveRideAndNavigate(callback: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            callback(false)
            return
        }

        db.collection("rides")
            .whereEqualTo("userId", userId)
            .whereIn("status", listOf("PENDING", "SEARCHING", "DRIVER_ASSIGNED", "ACCEPTED", "STARTED"))
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty()) {
                    val doc = documents.first()
                    activeRideId = doc.id
                    rideStatus = doc.getString("status")
                    isRideActive = true
                    callback(true)
                } else {
                    isRideActive = false
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun showActiveRideFragment() {
        val bundle = Bundle().apply {
            putString("rideId", activeRideId)
        }

        val fragment = when (rideStatus) {
            "STARTED" -> {
                RideTrackingFragment().apply { arguments = bundle }
            }
            else -> {
                RideProcessingFragment().apply { arguments = bundle }
            }
        }

        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commit()

        bottomNav.visibility = View.GONE
    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        // ✅ Agar fragment already loaded hai toh skip karein
        if (currentFragment.javaClass == fragment.javaClass && currentFragment.isAdded) {
            return
        }

        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
            .commit()

        // ✅ Show bottom nav only if no active ride
        if (!isRideActive) {
            bottomNav.visibility = View.VISIBLE
        }
    }

    // ✅ Fixed: Status change handler without loop
    fun onRideStatusChanged(status: String?) {
        when (status) {
            "COMPLETED", "CANCELLED", "EXPIRED" -> {
                isRideActive = false
                activeRideId = null
                bottomNav.visibility = View.VISIBLE
                // ✅ Directly load home without checking active ride
                currentFragment = bookRideFragment
                supportFragmentManager.beginTransaction()
                    .replace(containerId, bookRideFragment, "HOME")
                    .commit()
            }
            null -> {
                isRideActive = false
                activeRideId = null
                bottomNav.visibility = View.VISIBLE
            }
        }
    }

    fun hideBottomNav(hide: Boolean) {
        bottomNav.visibility = if (hide) View.GONE else View.VISIBLE
    }

    fun setActiveRide(rideId: String, status: String) {
        activeRideId = rideId
        rideStatus = status
        isRideActive = true
        bottomNav.visibility = View.GONE
        showActiveRideFragment()
    }

    override fun onResume() {
        super.onResume()
        // ✅ Only check if no active ride
        if (!isRideActive) {
            checkActiveRideAndNavigate { hasActiveRide ->
                if (hasActiveRide) {
                    showActiveRideFragment()
                }
            }
        }
    }
}