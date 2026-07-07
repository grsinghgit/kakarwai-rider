package com.gr.kakarwairider.viewmodel

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private lateinit var verificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _verificationSent = MutableLiveData(false)
    val verificationSent: LiveData<Boolean> = _verificationSent

    private val _otpVerified = MutableLiveData(false)
    val otpVerified: LiveData<Boolean> = _otpVerified

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _userUid = MutableLiveData<String?>(null)
    val userUid: LiveData<String?> = _userUid

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification (OTP auto-read)
            _isLoading.value = true
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            _isLoading.value = false
            _errorMessage.value = "Verification failed: ${e.message}"
        }

        override fun onCodeSent(
            vid: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            verificationId = vid
            resendToken = token
            _isLoading.value = false
            _verificationSent.value = true
            _errorMessage.value = null
        }
    }

    // ============================================================
    // SEND OTP
    // ============================================================

    fun sendOTP(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isBlank() || phoneNumber.length < 10) {
            _errorMessage.value = "Please enter a valid phone number"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // ============================================================
    // RESEND OTP
    // ============================================================

    fun resendOTP(phoneNumber: String, activity: Activity) {
        if (::resendToken.isInitialized) {
            _isLoading.value = true
            _errorMessage.value = null

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .setForceResendingToken(resendToken)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } else {
            sendOTP(phoneNumber, activity)
        }
    }

    // ============================================================
    // VERIFY OTP
    // ============================================================

    fun verifyOTP(otp: String) {
        if (otp.isBlank() || otp.length < 6) {
            _errorMessage.value = "Please enter a valid OTP (6 digits)"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        signInWithPhoneAuthCredential(credential)
    }

    // ============================================================
    // SIGN IN WITH CREDENTIAL
    // ============================================================

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    _otpVerified.value = true
                    _userUid.value = auth.currentUser?.uid
                    _errorMessage.value = null
                } else {
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        _errorMessage.value = "Invalid OTP. Please try again."
                    } else {
                        _errorMessage.value = "Authentication failed: ${task.exception?.message}"
                    }
                }
            }
    }

    // ============================================================
    // CHECK USER LOGIN STATUS
    // ============================================================

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }

    fun getCurrentUserPhone(): String? {
        return auth.currentUser?.phoneNumber
    }

    // ============================================================
    // LOGOUT
    // ============================================================

    fun logout() {
        auth.signOut()
        _otpVerified.value = false
        _userUid.value = null
    }

    // ============================================================
    // CLEAR ERRORS
    // ============================================================

    fun clearError() {
        _errorMessage.value = null
    }
}