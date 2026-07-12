package com.gr.kakarwairider.driver.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.driver.adapter.DriverPendingRideAdapter
import com.gr.kakarwairider.driver.viewmodel.DriverPendingRidesViewModel
import com.gr.kakarwairider.model.RideModel

class DriverPendingRidesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: DriverPendingRideAdapter
    private val viewModel: DriverPendingRidesViewModel by viewModels()
    private var driverId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_pending_rides, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        if (driverId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("PendingRidesFrag", "✅ Driver ID: $driverId")

        setupRecyclerView()
        setupObservers()

        viewModel.loadPendingRides(driverId!!)
    }

    private fun setupRecyclerView() {
        adapter = DriverPendingRideAdapter(
            rides = emptyList(),
            onAccept = { ride ->
                Log.d("PendingRidesFrag", "✅ Accept: ${ride.rideId}")
                updateRideStatus(ride, "ACCEPTED")
            },
            onReject = { ride ->
                Log.d("PendingRidesFrag", "❌ Reject: ${ride.rideId}")
                updateRideStatus(ride, "CANCELLED")
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.rides.observe(viewLifecycleOwner, Observer { rides ->
            Log.d("PendingRidesFrag", "📋 LiveData update: ${rides.size} rides")
            rides.forEach {
                Log.d("PendingRidesFrag", "   - ${it.rideId}: ${it.status}")
            }

            adapter.updateDrivers(rides)
            tvEmpty.visibility = if (rides.isEmpty()) View.VISIBLE else View.GONE
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Log.e("PendingRidesFrag", "❌ Error: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })
    }

    private fun updateRideStatus(ride: RideModel, status: String) {
        viewModel.updateRideStatus(ride.rideId, status) { success ->
            if (success) {
                val message = if (status == "ACCEPTED") {
                    "✅ Ride Accepted!"
                } else {
                    "❌ Ride Rejected"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to update ride status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}