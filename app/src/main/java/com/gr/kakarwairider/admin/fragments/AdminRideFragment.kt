package com.gr.kakarwairider.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.adapter.AdminRideAdapter
import com.gr.kakarwairider.model.RideModel

class AdminRideFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdminRideAdapter
    private val db = FirebaseFirestore.getInstance()
    private val rideList = mutableListOf<RideModel>()
    private var currentFilter = "PENDING"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_ride, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        val btnPending = view.findViewById<MaterialButton>(R.id.btnPending)
        val btnAll = view.findViewById<MaterialButton>(R.id.btnAll)

        setupRecyclerView()
        loadRides(currentFilter)

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
    }

    private fun setupRecyclerView() {
        adapter = AdminRideAdapter(
            rides = rideList,
            onAssignClick = { /* Assign logic */ },
            onReassignClick = { /* Reassign logic */ },
            onRefresh = { loadRides(currentFilter) }
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
            }
            .addOnFailureListener {
                // Handle error
            }
    }
}