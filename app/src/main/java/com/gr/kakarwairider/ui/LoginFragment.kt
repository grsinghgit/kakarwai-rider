package com.gr.kakarwairider.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var btnSendOTP: MaterialButton
    private lateinit var btnResendOTP: MaterialButton
    private lateinit var etOTP: TextInputEditText
    private lateinit var btnVerifyOTP: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        btnSendOTP = view.findViewById(R.id.btnSendOTP)
        btnResendOTP = view.findViewById(R.id.btnResendOTP)
        etOTP = view.findViewById(R.id.etOTP)
        btnVerifyOTP = view.findViewById(R.id.btnVerifyOTP)

        // ✅ Send OTP Button
        btnSendOTP.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                authViewModel.sendOTP(phoneNumber, requireActivity())
            } else {
                Toast.makeText(requireContext(), "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Resend OTP Button
        btnResendOTP.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                authViewModel.resendOTP(phoneNumber, requireActivity())
            } else {
                Toast.makeText(requireContext(), "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Verify OTP Button
        btnVerifyOTP.setOnClickListener {
            val otp = etOTP.text.toString().trim()
            if (otp.isNotEmpty()) {
                authViewModel.verifyOTP(otp)
            } else {
                Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Observe LiveData
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            btnSendOTP.isEnabled = !isLoading
            btnVerifyOTP.isEnabled = !isLoading
            if (isLoading) {
                btnSendOTP.text = "Sending..."
                btnVerifyOTP.text = "Verifying..."
            } else {
                btnSendOTP.text = "Send OTP"
                btnVerifyOTP.text = "Verify OTP"
            }
        }

        authViewModel.verificationSent.observe(viewLifecycleOwner) { sent ->
            if (sent) {
                Toast.makeText(requireContext(), "OTP Sent Successfully!", Toast.LENGTH_SHORT).show()
                etOTP.requestFocus()
            }
        }

        authViewModel.otpVerified.observe(viewLifecycleOwner) { verified ->
            if (verified) {
                Toast.makeText(requireContext(), "Login Successful! 🎉", Toast.LENGTH_LONG).show()

                // ✅ Update MainActivity UI
                (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()

                // ✅ Navigate to Home
                findNavController().navigate(R.id.action_login_to_home)
            }
        }

        authViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                authViewModel.clearError()
            }
        }

        // ✅ Check if already logged in
        if (authViewModel.isUserLoggedIn()) {
            findNavController().navigate(R.id.action_login_to_home)
        }
    }
}