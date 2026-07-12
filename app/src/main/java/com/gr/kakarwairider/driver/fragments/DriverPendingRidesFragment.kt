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
import com.google.firebase.Timestamp
import com.gr.kakarwairider.R
import com.gr.kakarwairider.driver.adapter.DriverPendingRideAdapter
import com.gr.kakarwairider.driver.viewmodel.DriverPendingRidesViewModel
import com.gr.kakarwairider.model.RideModel
import com.gr.kakarwairider.utils.DistanceUtils

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

                val driverId = driverId ?: ""
                val areaId = ride.areaId
                val pickupLat = ride.pickup?.lat ?: 0.0
                val pickupLng = ride.pickup?.lng ?: 0.0
                val destLat = ride.destination?.lat ?: 0.0
                val destLng = ride.destination?.lng ?: 0.0

                // ✅ Positive check - data available hai toh hi aage badho
                if (areaId.isNotEmpty() && pickupLat != 0.0 && destLat != 0.0) {
                    viewModel.calculateFareForRide(
                        rideId = ride.rideId,
                        driverId = driverId,
                        areaId = areaId,
                        pickupLat = pickupLat,
                        pickupLng = pickupLng,
                        destLat = destLat,
                        destLng = destLng
                    ) { fareSuccess ->
                        if (fareSuccess) {
                            viewModel.updateRideStatus(ride.rideId, "ACCEPTED") { success ->
                                if (success) {
                                    Toast.makeText(
                                        requireContext(),
                                        "✅ Ride Accepted! Fare: ₹${DistanceUtils.formatFareInt(ride.totalFare)}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "❌ Failed to calculate fare", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "❌ Missing location data", Toast.LENGTH_SHORT).show()
                }
            },
            onReject = { ride ->
                Log.d("PendingRidesFrag", "❌ Reject: ${ride.rideId}")
                viewModel.updateRideStatus(ride.rideId, "CANCELLED") { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "❌ Ride Rejected", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onArrivedPickup = { ride ->
                Log.d("PendingRidesFrag", "📍 Arrived at pickup: ${ride.rideId}")

                val pickupPin = (1000..9999).random().toString()

                viewModel.updateRideWithPin(
                    rideId = ride.rideId,
                    status = "ARRIVED_PICKUP",
                    pickupPin = pickupPin,
                    pickupTime = Timestamp.now()
                ) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "📍 Arrived! PIN: $pickupPin", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSubmitPin = { ride, enteredPin ->
                Log.d("PendingRidesFrag", "🔑 Submit PIN: ${ride.rideId}, PIN: $enteredPin")

                if (ride.pickupPin == enteredPin) {
                    viewModel.updateRideStatus(ride.rideId, "ON_THE_WAY") { success ->
                        if (success) {
                            Toast.makeText(requireContext(), "✅ PIN Verified! Ride Started!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "❌ Invalid PIN! Please try again.", Toast.LENGTH_SHORT).show()
                }
            },
            onArrivedDestination = { ride ->
                Log.d("PendingRidesFrag", "📍 Destination Reached: ${ride.rideId}")

                val completePin = (1000..9999).random().toString()

                viewModel.updateRideWithCompletePin(
                    rideId = ride.rideId,
                    status = "DESTINATION_REACHED",
                    completePin = completePin
                ) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "📍 Destination Reached! Complete PIN: $completePin", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSubmitCompletePin = { ride, enteredPin ->
                Log.d("PendingRidesFrag", "🔑 Complete PIN: ${ride.rideId}, PIN: $enteredPin")

                viewModel.completeRideWithPin(
                    rideId = ride.rideId,
                    enteredPin = enteredPin
                ) { success ->
                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "✅ Ride Completed! Fare: ₹${DistanceUtils.formatFareInt(ride.totalFare)}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), "❌ Invalid PIN! Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.rides.observe(viewLifecycleOwner, Observer { rides ->
            Log.d("PendingRidesFrag", "📋 LiveData update: ${rides.size} rides")
            rides.forEach {
                Log.d("PendingRidesFrag", "   - ${it.rideId}: ${it.status}, Fare: ₹${it.totalFare}")
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

    override fun onDestroyView() {
        super.onDestroyView()
    }
}