package com.gr.kakarwairider.driver.fragments

import android.content.Context
import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.driver.adapter.WalletTransactionAdapter
import com.gr.kakarwairider.driver.viewmodel.DriverWalletViewModel
import com.gr.kakarwairider.PaymentActivity

class DriverWalletFragment : Fragment() {

    private lateinit var tvBalance: TextView
    private lateinit var tvTotalEarnings: TextView
    private lateinit var tvTotalFee: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WalletTransactionAdapter
    private lateinit var btnTestPayment: MaterialButton  // ✅ NEW
    private lateinit var cardPaymentTest: MaterialCardView  // ✅ NEW

    private val viewModel: DriverWalletViewModel by viewModels()
    private var driverId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()  // ✅ NEW

        val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        driverId = sharedPref.getString("driverId", null)

        if (driverId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        setupRecyclerView()
        setupObservers()

        viewModel.loadWalletData(driverId!!)
    }

    private fun initViews(view: View) {
        tvBalance = view.findViewById(R.id.tvBalance)
        tvTotalEarnings = view.findViewById(R.id.tvTotalEarnings)
        tvTotalFee = view.findViewById(R.id.tvTotalFee)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        recyclerView = view.findViewById(R.id.recyclerView)
        btnTestPayment = view.findViewById(R.id.btnTestPayment)  // ✅ NEW
        cardPaymentTest = view.findViewById(R.id.cardPaymentTest)  // ✅ NEW
    }

    // ✅ NEW: Setup Listeners
    private fun setupListeners() {
        btnTestPayment.setOnClickListener {
            android.util.Log.d("DriverWallet", "💳 Payment Test Button Clicked")
            val intent = Intent(requireContext(), PaymentActivity::class.java).apply {
                putExtra("rideId", "test_ride_${System.currentTimeMillis()}")
                putExtra("amount", 10.0)  // ₹10 test payment
            }
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = WalletTransactionAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.balance.observe(viewLifecycleOwner, Observer { balance ->
            tvBalance.text = "₹${String.format("%.2f", balance)}"
        })

        viewModel.transactions.observe(viewLifecycleOwner, Observer { transactions ->
            adapter = WalletTransactionAdapter(transactions)
            recyclerView.adapter = adapter

            // Calculate total fee
            val totalFee = transactions
                .filter { it.type == "FEE" || it.type == "DEBIT" }
                .sumOf { it.amount }

            tvTotalFee.text = "Fee: ₹${totalFee.toInt()}"

            tvEmpty.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
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
}