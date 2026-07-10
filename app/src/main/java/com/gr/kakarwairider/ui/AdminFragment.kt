package com.gr.kakarwairider.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.R
import com.gr.kakarwairider.adapter.AdminRideAdapter
import com.gr.kakarwairider.model.RideModel

class AdminFragment : Fragment(), OnMapReadyCallback {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnPending: MaterialButton
    private lateinit var btnAll: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnOnlineDrivers: MaterialButton
    private lateinit var btnAddDriver: MaterialButton
    private lateinit var adapter: AdminRideAdapter
    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val rideList = mutableListOf<RideModel>()
    private var currentFilter = "PENDING"
    private val driverMarkers = mutableMapOf<String, Marker>()
    private var isMapReady = false

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
        btnAddDriver = view.findViewById(R.id.btnAddDriver)

        setupRecyclerView()
        loadRides(currentFilter)

        // ✅ Setup Map
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // ✅ Listen for driver locations (after map ready)
        listenForDriverLocations()

        btnPending.setOnClickListener {
            currentFilter = "PENDING"
            loadRides(currentFilter)
            btnPending.setBackgroundColor(resources.getColor(R.color.primary))
            btnAll.setBackgroundColor(0x00000000)
        }

        btnAll.setOnClickListener {
            currentFilter = "ALL"
            loadRides(currentFilter)
            btnAll.setBackgroundColor(resources.getColor(R.color.primary))
            btnPending.setBackgroundColor(0x00000000)
        }

        btnOnlineDrivers.setOnClickListener {
            loadOnlineDrivers()
        }

        btnAddDriver.setOnClickListener {
            findNavController().navigate(R.id.driverFormFragment)
        }

        btnLogout.setOnClickListener {
            val sharedPref = requireActivity().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_admin_to_login)
        }
    }

    // ============================================================
    // ✅ MAP SETUP
    // ============================================================

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }

        val defaultLocation = LatLng(28.6139, 77.2090)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
    }

    // ============================================================
    // ✅ LISTEN FOR DRIVER LOCATIONS
    // ============================================================

    private fun listenForDriverLocations() {
        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) {
                    return@addSnapshotListener
                }

                driverMarkers.values.forEach { it.remove() }
                driverMarkers.clear()

                if (!isMapReady) {
                    return@addSnapshotListener
                }

                if (snapshots.isEmpty()) {
                    return@addSnapshotListener
                }

                var firstLocation: LatLng? = null

                for (doc in snapshots) {
                    val driverId = doc.id
                    val driverName = doc.getString("driverName") ?: "Driver"
                    val location = doc.getGeoPoint("currentLocation")

                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        addDriverMarker(driverId, driverName, latLng)

                        if (firstLocation == null) {
                            firstLocation = latLng
                        }
                    }
                }

                firstLocation?.let {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f))
                }
            }
    }

    private fun addDriverMarker(driverId: String, driverName: String, latLng: LatLng) {
        if (!isMapReady) return

        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("🚗 $driverName")
                .snippet("Driver ID: $driverId")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        marker?.showInfoWindow()
        driverMarkers[driverId] = marker!!
    }

    // ============================================================
    // ✅ RIDE LIST METHODS
    // ============================================================

    private fun setupRecyclerView() {
        adapter = AdminRideAdapter(
            rides = rideList,
            onAssignClick = { ride ->
                showAssignDriverDialog(ride)
            },
            onReassignClick = { ride ->
                showReassignDriverDialog(ride)
            },
            onRefresh = {
                refreshRides()
            }
        )
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
    // ✅ ONLINE DRIVERS LIST
    // ============================================================

    private fun loadOnlineDrivers() {
        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .get()
            .addOnSuccessListener { documents ->
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

                    driverNames.add("🚗 $name")
                    driverIds.add(id)

                    if (location != null) {
                        driverDistances.add("📍 ${location.latitude}, ${location.longitude}")
                    } else {
                        driverDistances.add("📍 Location unknown")
                    }
                }

                showDriverListDialog(driverNames, driverIds, driverDistances)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDriverListDialog(
        driverNames: List<String>,
        driverIds: List<String>,
        driverDistances: List<String>
    ) {
        val displayList = driverNames.mapIndexed { index, name ->
            "$name\n   📍 ${driverDistances.getOrNull(index) ?: "Unknown"}"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🟢 Online Drivers")
            .setItems(displayList) { _, which ->
                val selectedDriverName = driverNames[which]
                val selectedDriverId = driverIds[which]
                Toast.makeText(requireContext(), "Selected: $selectedDriverName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ============================================================
    // ✅ ASSIGN DRIVER
    // ============================================================

    private fun showAssignDriverDialog(ride: RideModel) {
        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .get()
            .addOnSuccessListener { documents ->
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

                    driverNames.add("🚗 $name")
                    driverIds.add(id)

                    if (location != null) {
                        driverDistances.add("📍 ${location.latitude}, ${location.longitude}")
                    } else {
                        driverDistances.add("📍 Location unknown")
                    }
                }

                showAssignDriverListDialog(ride, driverNames, driverIds, driverDistances)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load drivers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAssignDriverListDialog(
        ride: RideModel,
        driverNames: List<String>,
        driverIds: List<String>,
        driverDistances: List<String>
    ) {
        if (driverNames.isEmpty()) {
            Toast.makeText(requireContext(), "No drivers available", Toast.LENGTH_SHORT).show()
            return
        }

        val listView = android.widget.ListView(requireContext())
        val arrayAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            driverNames
        )
        listView.adapter = arrayAdapter

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Select Driver for Ride #${ride.rideId.takeLast(8)}")
        builder.setView(listView)
        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDriverName = driverNames[position]
            val selectedDriverId = driverIds[position]
            dialog.dismiss()
            assignDriver(ride.rideId, selectedDriverName, selectedDriverId)
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

    // ============================================================
    // ✅ REASSIGN DRIVER
    // ============================================================

    private fun showReassignDriverDialog(ride: RideModel) {
        db.collection("driver_locations")
            .whereEqualTo("status", "ONLINE")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    Toast.makeText(requireContext(), "No online drivers available!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val driverNames = mutableListOf<String>()
                val driverIds = mutableListOf<String>()

                for (doc in documents) {
                    val name = doc.getString("driverName") ?: "Unknown Driver"
                    val id = doc.id
                    // ✅ Exclude current driver
                    if (id != ride.driverId) {
                        driverNames.add("🚗 $name")
                        driverIds.add(id)
                    }
                }

                if (driverNames.isEmpty()) {
                    Toast.makeText(requireContext(), "No other drivers available!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val listView = android.widget.ListView(requireContext())
                val arrayAdapter = android.widget.ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    driverNames
                )
                listView.adapter = arrayAdapter

                val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                builder.setTitle("Reassign Driver")
                builder.setMessage("Select new driver for Ride #${ride.rideId.takeLast(8)}")
                builder.setView(listView)
                builder.setNegativeButton("Cancel", null)

                val dialog = builder.create()
                dialog.show()

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedDriverName = driverNames[position]
                    val selectedDriverId = driverIds[position]
                    dialog.dismiss()
                    reassignDriver(ride.rideId, selectedDriverName, selectedDriverId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load drivers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun reassignDriver(rideId: String, driverName: String, driverId: String) {
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
                Toast.makeText(requireContext(), "✅ Driver reassigned to $driverName!", Toast.LENGTH_LONG).show()
                refreshRides()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}