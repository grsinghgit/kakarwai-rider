package com.gr.kakarwairider.admin.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.adapter.AdminDriverAdapter
import com.gr.kakarwairider.admin.viewmodel.AdminDriverViewModel
import com.gr.kakarwairider.model.DriverModel

class AdminDriverFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvStats: TextView
    private lateinit var btnAddDriver: MaterialButton
    private lateinit var adapter: AdminDriverAdapter

    private val viewModel: AdminDriverViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_driver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvStats = view.findViewById(R.id.tvStats)
        btnAddDriver = view.findViewById(R.id.btnAddDriver)

        setupRecyclerView()
        setupObservers()

        // ✅ ONE TIME - Attach Snapshot Listener
        viewModel.attachSnapshotListener()

        btnAddDriver.setOnClickListener {
            showAddDriverDialog()
        }
    }

    private fun setupRecyclerView() {
        // ✅ Adapter create ONLY ONCE
        adapter = AdminDriverAdapter(
            onRechargeClick = { driver ->
                showRechargeDialog(driver)
            },
            onToggleSpecial = { driver ->
                viewModel.toggleSpecialStatus(driver.id, !driver.isSpecial) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "✅ Special updated", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onToggleStatus = { driver, isChecked ->
                Log.d("FragmentDebug", "🔄 Toggle: ${driver.name} → $isChecked")
                viewModel.toggleDriverStatus(driver.id, isChecked) { success ->
                    if (success) {
                        val status = if (isChecked) "Activated" else "Deactivated"
                        Toast.makeText(requireContext(), "✅ Driver $status!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = { driver ->
                viewModel.deleteDriver(driver.id) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "🗑️ ${driver.name} deleted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        // ✅ ONLY update drivers, NOT recreate adapter
        viewModel.drivers.observe(viewLifecycleOwner, Observer { drivers ->
            Log.d("FragmentDebug", "📋 LiveData: ${drivers.size} drivers")

            adapter.updateDrivers(drivers)

            val activeCount = drivers.count { it.isActive }
            val inactiveCount = drivers.size - activeCount
            tvStats.text = "Total: ${drivers.size} | 🟢 Active: $activeCount | 🔴 Inactive: $inactiveCount"
            tvEmpty.visibility = if (drivers.isEmpty()) View.VISIBLE else View.GONE
        })

        viewModel.successMessage.observe(viewLifecycleOwner, Observer { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            error?.let {
                Toast.makeText(requireContext(), "❌ $it", Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        })
    }

    private fun showAddDriverDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_driver_details, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etPhone)
        val etPin = dialogView.findViewById<TextInputEditText>(R.id.etPin)
        val etVehicleType = dialogView.findViewById<TextInputEditText>(R.id.etVehicleType)
        val etVehicleModel = dialogView.findViewById<TextInputEditText>(R.id.etVehicleModel)
        val etVehicleNumber = dialogView.findViewById<TextInputEditText>(R.id.etVehicleNumber)

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Driver")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val pin = etPin.text.toString().trim()
                val vehicleType = etVehicleType.text.toString().trim()
                val vehicleModel = etVehicleModel.text.toString().trim()
                val vehicleNumber = etVehicleNumber.text.toString().trim()

                if (name.isEmpty() || phone.isEmpty() || pin.isEmpty()) {
                    Toast.makeText(requireContext(), "Name, Phone and PIN required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val driver = DriverModel(
                    name = name,
                    phone = phone,
                    pin = pin,
                    vehicleType = vehicleType,
                    vehicleModel = vehicleModel,
                    vehicleNumber = vehicleNumber,
                    isSpecial = false
                )

                viewModel.addDriver(driver) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "✅ Driver added!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRechargeDialog(driver: DriverModel) {
        val input = EditText(requireContext())
        input.hint = "Enter amount (₹)"
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("💳 Recharge Wallet")
            .setMessage("Driver: ${driver.name}\nBalance: ₹${driver.walletBalance}")
            .setView(input)
            .setPositiveButton("Recharge") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "Enter valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.rechargeWallet(driver.id, amount) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "✅ ₹$amount recharged!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}