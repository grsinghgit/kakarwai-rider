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

class AdminLoginFragment : Fragment() {

    private lateinit var etPIN: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var tvError: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPIN = view.findViewById(R.id.etPIN)
        btnLogin = view.findViewById(R.id.btnLogin)
        btnBack = view.findViewById(R.id.btnBack)
        tvError = view.findViewById(R.id.tvError)

        btnLogin.setOnClickListener {
            val pin = etPIN.text.toString().trim()
            if (pin.isEmpty()) {
                showError("Please enter PIN")
                return@setOnClickListener
            }
            if (pin.length < 4) {
                showError("PIN must be at least 4 digits")
                return@setOnClickListener
            }
            verifyAdminPIN(pin)
        }

        btnBack.setOnClickListener {
            // ✅ Go back to login screen using NavController
            findNavController().navigate(R.id.action_admin_login_to_home)
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
        etPIN.text?.clear()
    }

    private fun verifyAdminPIN(pin: String) {
        db.collection("admins")
            .whereEqualTo("pin", pin)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    showError("Invalid PIN!")
                } else {
                    // ✅ Save admin login state
                    val sharedPref = requireActivity().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putBoolean("isAdminLoggedIn", true).apply()

                    Toast.makeText(requireContext(), "✅ Admin Login Successful!", Toast.LENGTH_SHORT).show()

                    // ✅ Navigate to AdminFragment using NavController
                    findNavController().navigate(R.id.action_admin_login_to_admin)
                }
            }
            .addOnFailureListener { e ->
                showError("Error: ${e.message}")
            }
    }
}