package com.gr.kakarwairider.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.R
import com.gr.kakarwairider.model.RideModel

class AdminFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnPending: MaterialButton
    private lateinit var btnAll: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnOnlineDrivers: MaterialButton
    private lateinit var adapter: AdminRideAdapter
    private val db = FirebaseFirestore.getInstance()
    private val rideList = mutableListOf<RideModel>()
    private var currentFilter = "PENDING"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        btnPending = view.findViewById(R.id.btnPending)
        btnAll = view.findViewById(R.id.btnAll)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnOnlineDrivers = view.findViewById(R.id.btnOnlineDrivers)

        setupRecyclerView()
        loadRides(currentFilter)

        // Filter: Pending
        btnPending.setOnClickListener {
            currentFilter = "PENDING"
            loadRides(currentFilter)
            btnPending.setBackgroundColor(resources.getColor(R.color.primary))
            btnAll.setBackgroundColor(0x00000000)
        }

        // Filter: All
        btnAll.setOnClickListener {
            currentFilter = "ALL"
            loadRides(currentFilter)
            btnAll.setBackgroundColor(resources.getColor(R.color.primary))
            btnPending.setBackgroundColor(0x00000000)
        }

        // ✅ Online Drivers Button
        btnOnlineDrivers.setOnClickListener {
            loadOnlineDrivers()
        }

        // Logout
        btnLogout.setOnClickListener {
            val sharedPref = requireActivity().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_admin_to_login)
        }
    }

    private fun setupRecyclerView() {
        adapter = AdminRideAdapter(rideList) { ride ->
            showAssignDriverDialog(ride)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadRides(filter: String) {
        var query = db.collection("rides")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)

        if (filter == "PENDING") {
            query = query.whereEqualTo("status", "PENDING")
        }

        query.get()
            .addOnSuccessListener { documents ->
                rideList.clear()
                for (doc in documents) {
                    val ride = doc.toObject(RideModel::class.java)
                    rideList.add(ride)
                }
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (rideList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun refreshRides() {
        loadRides(currentFilter)
    }

    // ============================================================
    // ✅ LOAD ONLINE DRIVERS
    // ============================================================

    private fun loadOnlineDrivers() {
        android.util.Log.d("AdminFragment", "🔍 Loading online drivers...")

        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .whereEqualTo("isAvailable", true)
            .get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("AdminFragment", "📄 Found ${documents.size()} drivers")

                if (documents.isEmpty()) {
                    Toast.makeText(requireContext(), "No online drivers available", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val driverNames = mutableListOf<String>()
                val driverIds = mutableListOf<String>()
                val driverDistances = mutableListOf<String>()

                for (doc in documents) {
                    val name = doc.getString("driverName") ?: "Unknown Driver"
                    val id = doc.id
                    val location = doc.getGeoPoint("currentLocation")

                    android.util.Log.d("AdminFragment", "👤 Driver: $name, ID: $id")

                    driverNames.add("🚗 $name")
                    driverIds.add(id)

                    if (location != null) {
                        driverDistances.add("📍 ${location.latitude}, ${location.longitude}")
                    } else {
                        driverDistances.add("📍 Location unknown")
                    }
                }

                android.util.Log.d("AdminFragment", "📊 DriverNames: $driverNames")

                // ✅ Call dialog directly
                try {
                    showDriverListDialog(driverNames, driverIds, driverDistances)
                } catch (e: Exception) {
                    android.util.Log.e("AdminFragment", "❌ Error showing dialog: ${e.message}")
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminFragment", "❌ Error: ${e.message}")
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================================================
    // ✅ SHOW DRIVER LIST DIALOG
    // ============================================================

    private fun showDriverListDialog(
        driverNames: List<String>,
        driverIds: List<String>,
        driverDistances: List<String>
    ) {
        android.util.Log.d("AdminFragment", "🔔 showDriverListDialog STARTED with ${driverNames.size} drivers")

        if (driverNames.isEmpty()) {
            Toast.makeText(requireContext(), "No drivers to show", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Create a simple string array with just names
        val displayList = driverNames.toTypedArray()

        try {
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("🟢 Online Drivers")
            builder.setItems(displayList) { _, which ->
                val selectedDriverName = driverNames[which]
                val selectedDriverId = driverIds[which]

                android.util.Log.d("AdminFragment", "✅ Selected: $selectedDriverName, ID: $selectedDriverId")

                Toast.makeText(
                    requireContext(),
                    "Selected: $selectedDriverName\nID: $selectedDriverId",
                    Toast.LENGTH_LONG
                ).show()
            }
            builder.setNegativeButton("Close", null)

            val dialog = builder.create()
            dialog.show()
            android.util.Log.d("AdminFragment", "✅ Dialog shown successfully!")

        } catch (e: Exception) {
            android.util.Log.e("AdminFragment", "❌ Dialog error: ${e.message}", e)
            Toast.makeText(requireContext(), "Error showing dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ✅ SHOW ASSIGN DRIVER DIALOG
    // ============================================================

    private fun showAssignDriverDialog(ride: RideModel) {
        android.util.Log.d("AdminFragment", "🔍 Assign Driver for ride: ${ride.rideId}")

        // ✅ Using driver_locations collection with status "ONLINE"
        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")   // ✅ MATCHES YOUR DB
            .whereEqualTo("isAvailable", true)
            .get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("AdminFragment", "📄 Found ${documents.size()} online drivers")

                if (documents.isEmpty()) {
                    Toast.makeText(requireContext(), "No online drivers available!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val driverNames = mutableListOf<String>()
                val driverIds = mutableListOf<String>()
                val driverDistances = mutableListOf<String>()

                for (doc in documents) {
                    val name = doc.getString("driverName") ?: "Unknown Driver"
                    val id = doc.id
                    val location = doc.getGeoPoint("currentLocation")

                    android.util.Log.d("AdminFragment", "👤 Driver: $name, ID: $id")

                    driverNames.add("🚗 $name")
                    driverIds.add(id)

                    if (location != null) {
                        driverDistances.add("📍 ${location.latitude}, ${location.longitude}")
                    } else {
                        driverDistances.add("📍 Location unknown")
                    }
                }

                android.util.Log.d("AdminFragment", "📊 DriverNames: $driverNames")

                // ✅ Call dialog
                showAssignDriverListDialog(ride, driverNames, driverIds, driverDistances)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminFragment", "❌ Error: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load drivers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun assignDriver(rideId: String, driverName: String, driverId: String) {
        db.collection("rides").document(rideId)
            .update(
                mapOf(
                    "driverId" to driverId,
                    "driverName" to driverName,
                    "status" to "DRIVER_ASSIGNED",
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ Driver $driverName assigned!", Toast.LENGTH_LONG).show()
                refreshRides()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // ADD NEW DIALOGE LIST
    private fun showAssignDriverListDialog(
        ride: RideModel,
        driverNames: List<String>,
        driverIds: List<String>,
        driverDistances: List<String>
    ) {
        android.util.Log.d("AdminFragment", "🔔 showAssignDriverListDialog with ${driverNames.size} drivers")

        if (driverNames.isEmpty()) {
            Toast.makeText(requireContext(), "No drivers available", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Get pickup location
        val pickupLat = ride.pickup?.lat ?: 0.0
        val pickupLng = ride.pickup?.lng ?: 0.0

        val displayMessage = StringBuilder("Select a driver for Ride #${ride.rideId.takeLast(8)}\n\n")
        for (i in driverNames.indices) {
            // ✅ Parse driver location
            val driverLatLng = driverDistances.getOrNull(i) ?: "0.0,0.0"
            val parts = driverLatLng.split(",")
            val driverLat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
            val driverLng = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0

            // ✅ Calculate distance
            val distance = calculateDistance(driverLat, driverLng, pickupLat, pickupLng)
            val distanceText = if (distance < 1) {
                "${(distance * 1000).toInt()} m"
            } else {
                "%.1f km".format(distance)
            }

            displayMessage.append("${i + 1}. ${driverNames[i]}\n")
            displayMessage.append("   📍 $distanceText from pickup\n\n")
        }

        try {
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("Assign Driver")
            builder.setMessage(displayMessage.toString())
            builder.setPositiveButton("Select") { _, _ ->
                showDriverSelectionList(ride, driverNames, driverIds, driverDistances)
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()

            android.util.Log.d("AdminFragment", "✅ Assign dialog shown successfully!")

        } catch (e: Exception) {
            android.util.Log.e("AdminFragment", "❌ Dialog error: ${e.message}")
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ New function for driver selection
    private fun showDriverSelectionList(
        ride: RideModel,
        driverNames: List<String>,
        driverIds: List<String>,
        driverDistances: List<String>
    ) {
        // ✅ Get area center from Firestore (already have in adapter)
        // For now, calculate distance from driver to pickup
        val pickupLat = ride.pickup?.lat ?: 0.0
        val pickupLng = ride.pickup?.lng ?: 0.0

        val displayList = driverNames.mapIndexed { index, name ->
            // ✅ Parse driver location from driverDistances
            val driverLatLng = driverDistances.getOrNull(index) ?: "0.0,0.0"
            val parts = driverLatLng.split(",")
            val driverLat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
            val driverLng = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0

            // ✅ Calculate distance from driver to pickup
            val distance = calculateDistance(driverLat, driverLng, pickupLat, pickupLng)
            val distanceText = if (distance < 1) {
                "${(distance * 1000).toInt()} m"
            } else {
                "%.1f km".format(distance)
            }

            "$name\n   📍 $distanceText from pickup"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Driver")
            .setItems(displayList) { _, which ->
                val selectedDriverName = driverNames[which]
                val selectedDriverId = driverIds[which]
                android.util.Log.d("AdminFragment", "✅ Selected: $selectedDriverName")
                assignDriver(ride.rideId, selectedDriverName, selectedDriverId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    //distance
    // ✅ Distance Calculator (Haversine Formula)
    private fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val R = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}