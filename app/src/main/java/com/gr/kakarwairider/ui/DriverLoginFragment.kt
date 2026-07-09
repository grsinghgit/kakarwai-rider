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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R

class DriverLoginFragment : Fragment() {

    private lateinit var etPhone: TextInputEditText
    private lateinit var etPIN: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvError: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhone = view.findViewById(R.id.etPhone)
        etPIN = view.findViewById(R.id.etPIN)
        btnLogin = view.findViewById(R.id.btnLogin)
        tvError = view.findViewById(R.id.tvError)

        btnLogin.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val pin = etPIN.text.toString().trim()

            if (phone.isEmpty()) {
                showError("Enter phone number")
                return@setOnClickListener
            }
            if (pin.isEmpty() || pin.length < 4) {
                showError("Enter valid PIN (4-6 digits)")
                return@setOnClickListener
            }

            verifyDriver(phone, pin)
        }
    }

    private fun verifyDriver(phone: String, pin: String) {
        btnLogin.isEnabled = false
        btnLogin.text = "Verifying..."

        db.collection("drivers")
            .whereEqualTo("phone", phone)
            .whereEqualTo("pin", pin)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    showError("Invalid phone or PIN")
                    btnLogin.isEnabled = true
                    btnLogin.text = "🔓 Login"
                    return@addOnSuccessListener
                }

                // ✅ Login success
                val doc = documents.first()
                val driverId = doc.id
                val driverName = doc.getString("name") ?: "Driver"

                // ✅ Save driver session
                val sharedPref = requireActivity().getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
                sharedPref.edit().putString("driverId", driverId).apply()

                Toast.makeText(requireContext(), "✅ Welcome $driverName!", Toast.LENGTH_LONG).show()

                // ✅ Navigate to Driver Dashboard
                findNavController().navigate(R.id.action_driver_login_to_dashboard)
            }
            .addOnFailureListener { e ->
                showError("Error: ${e.message}")
                btnLogin.isEnabled = true
                btnLogin.text = "🔓 Login"
            }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}