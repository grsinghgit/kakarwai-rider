package com.gr.kakarwairider.driver.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.driver.viewmodel.DriverHomeViewModel
import com.gr.kakarwairider.service.DriverLocationService

class DriverHomeFragment : Fragment() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnGoOnline: MaterialButton
    private lateinit var tvTotalRides: TextView
    private lateinit var tvEarnings: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvWalletBalance: TextView
    private lateinit var btnLogout: MaterialButton

    private val viewModel: DriverHomeViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()
    private var driverId: String? = null
    private var isOnline = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        if (driverId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return
        }

        loadDriverData()
        checkOnlineStatus()

        btnGoOnline.setOnClickListener {
            toggleOnlineStatus()
        }

        btnLogout.setOnClickListener {
            // ✅ Stop location service if running
            requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))

            val pref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
            pref.edit().clear().apply()

            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun initViews(view: View) {
        tvWelcome = view.findViewById(R.id.tvWelcome)
        tvStatus = view.findViewById(R.id.tvDriverStatus)
        btnGoOnline = view.findViewById(R.id.btnGoOnline)
        tvTotalRides = view.findViewById(R.id.tvTotalRides)
        tvEarnings = view.findViewById(R.id.tvEarnings)
        tvRating = view.findViewById(R.id.tvRating)
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun loadDriverData() {
        driverId?.let { id ->
            db.collection("drivers").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name") ?: "Driver"
                        val totalRides = document.getLong("totalRides") ?: 0
                        val earnings = document.getDouble("totalEarnings") ?: 0.0
                        val rating = document.getDouble("rating") ?: 0.0
                        val balance = document.getDouble("walletBalance") ?: 0.0

                        tvWelcome.text = "🚗 Welcome, $name!"
                        tvTotalRides.text = "$totalRides"
                        tvEarnings.text = "₹${earnings.toInt()}"
                        tvRating.text = String.format("%.1f⭐", rating)
                        tvWalletBalance.text = "₹${String.format("%.2f", balance)}"
                    }
                }
        }
    }

    private fun checkOnlineStatus() {
        driverId?.let { id ->
            db.collection("driver_locations").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val status = document.getString("status") ?: "OFFLINE"
                        updateUIStatus(status == "ONLINE")
                    }
                }
        }
    }

    private fun toggleOnlineStatus() {
        val newStatus = !isOnline
        val statusText = if (newStatus) "ONLINE" else "OFFLINE"

        driverId?.let { id ->
            db.collection("driver_locations").document(id)
                .update(
                    mapOf(
                        "status" to statusText,
                        "isAvailable" to newStatus,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    updateUIStatus(newStatus)
                    Toast.makeText(requireContext(),
                        if (newStatus) "🟢 You are online" else "🔴 You are offline",
                        Toast.LENGTH_SHORT).show()

                    // ✅ Start/stop location service
                    if (newStatus) {
                        val intent = Intent(requireContext(), DriverLocationService::class.java)
                        intent.putExtra("driverId", driverId)
                        requireActivity().startService(intent)
                    } else {
                        requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to update status", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUIStatus(online: Boolean) {
        isOnline = online
        if (online) {
            tvStatus.text = "🟢 ONLINE"
            tvStatus.setTextColor(resources.getColor(R.color.green, null))
            btnGoOnline.text = "🔴 Go Offline"
            btnGoOnline.setBackgroundColor(resources.getColor(R.color.red, null))
        } else {
            tvStatus.text = "🔴 OFFLINE"
            tvStatus.setTextColor(resources.getColor(R.color.red, null))
            btnGoOnline.text = "🟢 Go Online"
            btnGoOnline.setBackgroundColor(resources.getColor(R.color.green, null))
        }
    }
}