package com.gr.kakarwairider.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.R
import com.gr.kakarwairider.adapter.DriverRideAdapter
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.service.DriverLocationService

class DriverDashboardFragment : Fragment() {

    private lateinit var tvDriverName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnGoOnline: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DriverRideAdapter
    private val db = FirebaseFirestore.getInstance()
    private var driverId: String? = null
    private var isOnline = false
    private val rideList = mutableListOf<RideModel>()
    private val LOCATION_PERMISSION_REQUEST = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvStatus = view.findViewById(R.id.tvStatus)
        btnGoOnline = view.findViewById(R.id.btnGoOnline)
        btnLogout = view.findViewById(R.id.btnLogout)
        recyclerView = view.findViewById(R.id.recyclerView)

        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        android.util.Log.d("DriverDashboard", "📌 SharedPref driverId: $driverId")

        // ✅ Check null using local variable
        val id = driverId
        if (id == null || id.isEmpty()) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setupRecyclerView()
        loadDriverDetails()
        listenForRides()

        btnGoOnline.setOnClickListener {
            toggleOnlineStatus()
        }

        btnLogout.setOnClickListener {
            requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
            val pref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
            pref.edit().clear().apply()
            findNavController().navigate(R.id.action_driver_dashboard_to_login)
        }
    }

    private fun setupRecyclerView() {
        adapter = DriverRideAdapter(
            rides = rideList,
            onAccept = { ride ->
                updateRideStatus(ride.rideId, "ACCEPTED")
            },
            onReject = { ride ->
                updateRideStatus(ride.rideId, "REJECTED")
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun listenForRides() {
        // ✅ Local variable for smart cast
        val id = driverId
        if (id == null || id.isEmpty()) {
            return
        }

        db.collection("rides")
            .whereEqualTo("driverId", id)
            .whereIn("status", listOf("DRIVER_ASSIGNED", "ACCEPTED", "STARTED"))
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                rideList.clear()
                for (doc in snapshots) {
                    val ride = doc.toObject(RideModel::class.java)
                    rideList.add(ride)
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun updateRideStatus(rideId: String, status: String) {
        db.collection("rides").document(rideId)
            .update("status", status)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ Ride $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleOnlineStatus()
            } else {
                Toast.makeText(requireContext(), "Location permission required!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleOnlineStatus() {
        android.util.Log.d("DriverDashboard", "🔄 toggleOnlineStatus called, isOnline: $isOnline")

        if (isOnline) {
            requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
            updateUIStatus(false)
            updateDriverStatus(false)
            Toast.makeText(requireContext(), "🔴 You are offline", Toast.LENGTH_SHORT).show()
        } else {
            // ✅ Local variable for smart cast
            val driverIdLocal = driverId
            android.util.Log.d("DriverDashboard", "📌 driverIdLocal: $driverIdLocal")

            if (driverIdLocal == null || driverIdLocal.isEmpty()) {
                android.util.Log.e("DriverDashboard", "❌ driverId is null!")
                Toast.makeText(requireContext(), "Error: Driver ID not found. Please login again.", Toast.LENGTH_LONG).show()
                return
            }

            if (!hasLocationPermission()) {
                requestLocationPermission()
                return
            }

            val intent = Intent(requireContext(), DriverLocationService::class.java)
            intent.putExtra("driverId", driverIdLocal)
            android.util.Log.d("DriverDashboard", "📤 Starting service with driverId: $driverIdLocal")

            ContextCompat.startForegroundService(requireContext(), intent)
            updateUIStatus(true)
            updateDriverStatus(true)
            Toast.makeText(requireContext(), "🟢 You are online", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDriverStatus(online: Boolean) {
        // ✅ Local variable for smart cast
        val id = driverId
        if (id == null || id.isEmpty()) {
            return
        }

        val status = if (online) "ONLINE" else "OFFLINE"
        db.collection("driver_locations").document(id)
            .update(
                mapOf(
                    "status" to status,
                    "isAvailable" to online,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
            )
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

    private fun loadDriverDetails() {
        // ✅ Local variable for smart cast
        val id = driverId
        if (id == null || id.isEmpty()) {
            return
        }

        db.collection("drivers").document(id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "Driver"
                    tvDriverName.text = "Welcome, $name"
                }
            }

        db.collection("driver_locations").document(id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status") ?: "OFFLINE"
                    updateUIStatus(status == "ONLINE")
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isOnline) {
            requireActivity().stopService(Intent(requireContext(), DriverLocationService::class.java))
        }
    }
}