package com.gr.kakarwairider.driver.fragments

import android.content.Context
import android.os.Bundle
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
import com.gr.kakarwairider.driver.adapter.DriverRideAdapter
import com.gr.kakarwairider.driver.viewmodel.DriverRidesViewModel
import com.gr.kakarwairider.model.RideModel

class DriverRidesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: DriverRideAdapter
    private val viewModel: DriverRidesViewModel by viewModels()
    private var driverId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_rides, container, false)
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

        setupRecyclerView()
        setupObservers()

        viewModel.loadRides(driverId!!)
    }

    private fun setupRecyclerView() {
        adapter = DriverRideAdapter(
            rides = emptyList(),
            onAccept = { ride ->
                updateRideStatus(ride, "ACCEPTED")
            },
            onReject = { ride ->
                updateRideStatus(ride, "REJECTED")
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.rides.observe(viewLifecycleOwner, Observer { rides ->
            adapter = DriverRideAdapter(
                rides = rides,
                onAccept = { ride ->
                    updateRideStatus(ride, "ACCEPTED")
                },
                onReject = { ride ->
                    updateRideStatus(ride, "REJECTED")
                }
            )
            recyclerView.adapter = adapter

            tvEmpty.visibility = if (rides.isEmpty()) View.VISIBLE else View.GONE
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })

        viewModel.isLoading.observe(viewLifecycleOwner, Observer { loading ->
            // Show/hide progress if needed
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

                // ✅ Refresh list
                driverId?.let { viewModel.loadRides(it) }
            } else {
                Toast.makeText(requireContext(), "Failed to update ride status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ViewModel will clean up listener
    }
}