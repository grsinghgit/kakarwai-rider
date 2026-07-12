package com.gr.kakarwairider.admin.fragments

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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.adapter.AdminRideAdapter
import com.gr.kakarwairider.admin.repository.DriverInfo
import com.gr.kakarwairider.admin.viewmodel.AdminRideViewModel
import com.gr.kakarwairider.model.RideModel

class AdminRideFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvTotalRides: TextView
    private lateinit var tvPendingRides: TextView
    private lateinit var tvActiveRides: TextView
    private lateinit var tvEarnings: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var adapter: AdminRideAdapter

    private val viewModel: AdminRideViewModel by viewModels()
    private var currentRides: List<RideModel> = emptyList()
    private var currentDrivers: List<DriverInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_ride, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupObservers()
        setupChips()

        viewModel.applyFilter()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvTotalRides = view.findViewById(R.id.tvTotalRides)
        tvPendingRides = view.findViewById(R.id.tvPendingRides)
        tvActiveRides = view.findViewById(R.id.tvActiveRides)
        tvEarnings = view.findViewById(R.id.tvEarnings)
        chipGroup = view.findViewById(R.id.chipGroup)
    }

    private fun setupRecyclerView() {
        adapter = AdminRideAdapter(
            rides = emptyList(),
            availableDrivers = emptyList(),
            onAssign = { ride, driverId, driverName ->
                Log.d("AdminRideFrag", "✅ Assign: ${ride.rideId} → $driverName")
                viewModel.assignDriver(ride.rideId, driverId, driverName)
            },
            onReassign = { ride, driverId, driverName ->
                Log.d("AdminRideFrag", "🔄 Reassign: ${ride.rideId} → $driverName")
                viewModel.reassignDriver(ride.rideId, driverId, driverName)
            },
            onCancel = { ride, reason ->
                Log.d("AdminRideFrag", "❌ Cancel: ${ride.rideId}, reason: $reason")
                viewModel.cancelRide(ride.rideId, reason)
            },
            onComplete = { ride ->
                Log.d("AdminRideFrag", "✅ Complete: ${ride.rideId}")
                viewModel.completeRide(ride.rideId)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.filteredRides.observe(viewLifecycleOwner, Observer { rides ->
            Log.d("AdminRideFrag", "📋 filteredRides update: ${rides.size}")
            currentRides = rides
            updateAdapter()
            tvEmpty.visibility = if (rides.isEmpty()) View.VISIBLE else View.GONE
        })

        viewModel.stats.observe(viewLifecycleOwner, Observer { stats ->
            tvTotalRides.text = "📊 Total: ${stats.totalRides}"
            tvPendingRides.text = "🟠 Pending: ${stats.pendingRides}"
            tvActiveRides.text = "🟢 Active: ${stats.activeRides}"
            tvEarnings.text = "💰 ₹${stats.todayEarnings.toInt()}"
        })

        viewModel.availableDrivers.observe(viewLifecycleOwner, Observer { drivers ->
            Log.d("AdminRideFrag", "👤 availableDrivers update: ${drivers.size}")
            currentDrivers = drivers
            updateAdapter()
        })

        viewModel.assignmentSuccess.observe(viewLifecycleOwner, Observer { success ->
            if (success) {
                Toast.makeText(requireContext(), "✅ Driver assigned successfully!", Toast.LENGTH_SHORT).show()
            }
        })

        viewModel.isLoading.observe(viewLifecycleOwner, Observer { loading ->
            Log.d("AdminRideFrag", "⏳ Loading: $loading")
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Log.e("AdminRideFrag", "❌ Error: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })
    }

    private fun updateAdapter() {
        val rides = currentRides
        val drivers = currentDrivers
        Log.d("AdminRideFrag", "🔄 Updating adapter: ${rides.size} rides, ${drivers.size} drivers")

        adapter = AdminRideAdapter(
            rides = rides,
            availableDrivers = drivers,
            onAssign = { ride, driverId, driverName ->
                viewModel.assignDriver(ride.rideId, driverId, driverName)
            },
            onReassign = { ride, driverId, driverName ->
                viewModel.reassignDriver(ride.rideId, driverId, driverName)
            },
            onCancel = { ride, reason ->
                viewModel.cancelRide(ride.rideId, reason)
            },
            onComplete = { ride ->
                viewModel.completeRide(ride.rideId)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun setupChips() {
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = chipGroup.findViewById<Chip>(checkedIds[0])
                val status = when (chip?.id) {
                    R.id.chipAll -> "ALL"
                    R.id.chipPending -> "PENDING"
                    R.id.chipAssigned -> "ASSIGNED"
                    R.id.chipStarted -> "STARTED"
                    R.id.chipCompleted -> "COMPLETED"
                    R.id.chipCancelled -> "CANCELLED"
                    else -> "ALL"
                }
                Log.d("AdminRideFrag", "🔍 Filter: $status")
                viewModel.applyFilter(status = status)
            }
        }
    }
}