package com.gr.kakarwairider.driver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.driver.fragments.DriverHomeFragment
import com.gr.kakarwairider.driver.fragments.DriverProfileFragment
import com.gr.kakarwairider.driver.fragments.DriverRidesFragment
import com.gr.kakarwairider.driver.fragments.DriverWalletFragment
import com.gr.kakarwairider.driver.fragments.DriverPendingRidesFragment

class DriverActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val containerId = R.id.driver_fragment_container

    private val homeFragment = DriverHomeFragment()
    private val ridesFragment = DriverRidesFragment()
    private val walletFragment = DriverWalletFragment()
    private val profileFragment = DriverProfileFragment()

    private var currentFragment: Fragment = homeFragment
    private val pendingRidesFragment = DriverPendingRidesFragment()  // ✅ NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        // ✅ Set Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "🚗 Driver"

        bottomNav = findViewById(R.id.driver_bottom_navigation)

        // ✅ Default fragment load
        if (savedInstanceState == null) {
            loadFragment(homeFragment, "HOME")
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.driver_home -> {
                    loadFragment(homeFragment, "HOME")
                    true
                }
                R.id.driver_rides -> {
                    loadFragment(ridesFragment, "RIDES")
                    true
                }
                R.id.driver_pending_rides -> {  // ✅ NEW
                    loadFragment(pendingRidesFragment, "PENDING_RIDES")
                    true
                }

                R.id.driver_wallet -> {
                    loadFragment(walletFragment, "WALLET")
                    true
                }
                R.id.driver_profile -> {
                    loadFragment(profileFragment, "PROFILE")
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