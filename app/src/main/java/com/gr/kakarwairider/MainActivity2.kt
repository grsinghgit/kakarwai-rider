package com.gr.kakarwairider
import androidx.fragment.app.Fragment

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity2 : AppCompatActivity() {

    private lateinit var navController: NavController
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        // ✅ NavHostFragment Setup
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        navController = navHostFragment.navController

        val navView: BottomNavigationView = findViewById(R.id.bottom_navigation)

        // ✅ Check if user has active ride
        checkActiveRideAndNavigate { hasActiveRide ->
            if (!hasActiveRide) {
                loadFragment(BookRideFragment())
            }
        }

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    loadFragment(BookRideFragment())
                    true
                }
                R.id.historyFragment -> {
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.profileFragment -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun checkActiveRideAndNavigate(callback: (Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            callback(false)
            return
        }

        db.collection("rides")
            .whereEqualTo("userId", userId)
            .whereIn("status", listOf("PENDING", "SEARCHING", "DRIVER_ASSIGNED", "STARTED"))
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty()) {
                    val rideId = documents.first().id
                    val bundle = Bundle().apply {
                        putString("rideId", rideId)
                    }
                    // ✅ Use NavController for navigation
                    navController.navigate(R.id.rideProcessingFragment, bundle)
                    callback(true)
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}