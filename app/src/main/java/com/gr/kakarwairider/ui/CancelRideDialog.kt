package com.gr.kakarwairider.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.R
import com.gr.kakarwairider.adapter.CancelReasonAdapter

class CancelRideDialog(
    private val rideId: String,
    private val onCancelConfirmed: (String) -> Unit
) : DialogFragment() {

    private val reasons = listOf(
        "Driver not available",
        "User not reachable",
        "Vehicle issue",
        "Weather conditions",
        "Changed my mind",
        "Other"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_cancel_reason, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = CancelReasonAdapter(reasons) { reason ->
            // ✅ Cancel ride with reason
            onCancelConfirmed(reason)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}