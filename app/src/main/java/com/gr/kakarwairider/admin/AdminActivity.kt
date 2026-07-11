package com.gr.kakarwairider.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.fragments.AdminDriverFragment
import com.gr.kakarwairider.admin.fragments.AdminHomeFragment
import com.gr.kakarwairider.admin.fragments.AdminMapFragment
import com.gr.kakarwairider.admin.fragments.AdminPaymentFragment
import com.gr.kakarwairider.admin.fragments.AdminRideFragment

class AdminActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val containerId = R.id.admin_fragment_container

    private val homeFragment = AdminHomeFragment()
    private val rideFragment = AdminRideFragment()
    private val driverFragment = AdminDriverFragment()
    private val mapFragment = AdminMapFragment()   // ✅ Map Fragment
    private val paymentFragment = AdminPaymentFragment()

    private var currentFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        bottomNav = findViewById(R.id.admin_bottom_navigation)

        // ✅ Default fragment load
        if (savedInstanceState == null) {
            loadFragment(homeFragment, "HOME")
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
                    loadFragment(mapFragment, "MAP")   // ✅ Map click handler
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}