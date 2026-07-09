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

    // ✅ REFRESH FUNCTION - YAHAN ADD KAREIN
    fun refreshRides() {
        loadRides(currentFilter)
    }

    // ============================================================
    // ✅ SHOW ASSIGN DRIVER DIALOG
    // ============================================================

    private fun showAssignDriverDialog(ride: RideModel) {
        db.collection("drivers")
            .whereEqualTo("isActive", true)
            .whereEqualTo("isAvailable", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    Toast.makeText(requireContext(), "No drivers available!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val driverNames = mutableListOf<String>()
                val driverIds = mutableListOf<String>()
                for (doc in documents) {
                    val name = doc.getString("name") ?: "Driver"
                    val id = doc.id
                    driverNames.add(name)
                    driverIds.add(id)
                }

                val driverArray = driverNames.toTypedArray()
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Assign Driver")
                    .setMessage("Select a driver for Ride #${ride.rideId.takeLast(8)}")
                    .setItems(driverArray) { _, which ->
                        val selectedDriverName = driverNames[which]
                        val selectedDriverId = driverIds[which]
                        assignDriver(ride.rideId, selectedDriverName, selectedDriverId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load drivers", Toast.LENGTH_SHORT).show()
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
                refreshRides()  // ✅ Refresh list
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}