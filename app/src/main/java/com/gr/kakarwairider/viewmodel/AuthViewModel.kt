package com.gr.kakarwairider.viewmodel

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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

    companion object {
        private const val TAG = "AuthViewModel"
        private const val PREF_NAME = "otp_prefs"
        private const val OTP_COUNT_KEY = "otp_count"
        private const val OTP_FIRST_TIME_KEY = "otp_first_time"
        private const val OTP_TIMER_KEY = "otp_timer"
        private const val OTP_COOLDOWN_KEY = "otp_cooldown"
        private const val MAX_OTP_ATTEMPTS = 3
        private const val OTP_LIMIT_HOURS = 24 // 24 hours
        private const val OTP_TIMER_SECONDS = 60 // 1 minute cooldown between OTPs
        private const val OTP_EXPIRY_SECONDS = 300 // 5 minutes
    }

    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private lateinit var sharedPrefs: SharedPreferences

    // LiveData
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _verificationSent = MutableLiveData(false)
    val verificationSent: LiveData<Boolean> = _verificationSent

    private val _otpVerified = MutableLiveData(false)
    val otpVerified: LiveData<Boolean> = _otpVerified

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _userUid = MutableLiveData<String?>()
    val userUid: LiveData<String?> = _userUid

    private val _otpTimerText = MutableLiveData("")
    val otpTimerText: LiveData<String> = _otpTimerText

    private val _otpAttemptsLeft = MutableLiveData(MAX_OTP_ATTEMPTS)
    val otpAttemptsLeft: LiveData<Int> = _otpAttemptsLeft

    private var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks? = null

    // ✅ Timer Runnable - NON-NULLABLE
    private var timerRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val callbacksInner = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(TAG, "✅ onVerificationCompleted: Auto-verified")
            _isLoading.value = false
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e(TAG, "❌ onVerificationFailed: ${e.message}")
            _isLoading.value = false
            _errorMessage.value = "Verification failed: ${e.message}"
        }

        override fun onCodeSent(
            vid: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(TAG, "✅ onCodeSent: OTP sent successfully")
            verificationId = vid
            resendToken = token
            _isLoading.value = false
            _verificationSent.value = true
            _errorMessage.value = null
            // ✅ Save OTP sent time
            sharedPrefs.edit().putLong(OTP_TIMER_KEY, System.currentTimeMillis()).apply()
            startOTPTimer()
        }
    }

    // ============================================================
    // ✅ INIT SHAREDPREFERENCES
    // ============================================================

    fun initPrefs(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        updateAttemptsLeft()
        checkAndResetCountIfNeeded()
    }

    // ============================================================
    // ✅ CHECK IF OTP CAN BE SENT
    // ============================================================

    fun canSendOTP(phoneNumber: String): Boolean {
        // ✅ Check if in cooldown period
        if (isInCooldown()) {
            _errorMessage.value = "⚠️ Please wait ${getCooldownTime()} seconds before trying again"
            return false
        }

        // ✅ Check 24-hour limit
        if (hasOTPLimitExceeded()) {
            _errorMessage.value = "⚠️ OTP limit reached (${MAX_OTP_ATTEMPTS} attempts in 24 hours). Try again tomorrow."
            return false
        }

        return true
    }

    private fun isInCooldown(): Boolean {
        val cooldownTime = sharedPrefs.getLong(OTP_COOLDOWN_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - cooldownTime) / 1000
        return elapsedSeconds < OTP_TIMER_SECONDS
    }

    private fun getCooldownTime(): Long {
        val cooldownTime = sharedPrefs.getLong(OTP_COOLDOWN_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - cooldownTime) / 1000
        return OTP_TIMER_SECONDS - elapsedSeconds
    }

    private fun hasOTPLimitExceeded(): Boolean {
        val count = sharedPrefs.getInt(OTP_COUNT_KEY, 0)
        val firstTime = sharedPrefs.getLong(OTP_FIRST_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()

        // ✅ Reset if 24 hours passed
        if (currentTime - firstTime > OTP_LIMIT_HOURS * 60 * 60 * 1000) {
            resetOTPLimit()
            return false
        }

        return count >= MAX_OTP_ATTEMPTS
    }

    private fun resetOTPLimit() {
        sharedPrefs.edit()
            .putInt(OTP_COUNT_KEY, 0)
            .putLong(OTP_FIRST_TIME_KEY, System.currentTimeMillis())
            .apply()
        updateAttemptsLeft()
    }

    private fun updateAttemptsLeft() {
        val count = sharedPrefs.getInt(OTP_COUNT_KEY, 0)
        val remaining = MAX_OTP_ATTEMPTS - count
        _otpAttemptsLeft.value = remaining.coerceAtLeast(0)
    }

    private fun checkAndResetCountIfNeeded() {
        val firstTime = sharedPrefs.getLong(OTP_FIRST_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()
        if (currentTime - firstTime > OTP_LIMIT_HOURS * 60 * 60 * 1000) {
            resetOTPLimit()
        }
    }

    // ============================================================
    // ✅ SEND OTP
    // ============================================================

    fun sendOTP(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isBlank() || phoneNumber.length < 10) {
            _errorMessage.value = "Please enter a valid phone number"
            return
        }

        if (!canSendOTP(phoneNumber)) {
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        // ✅ Increment OTP count
        incrementOTPCount()

        // ✅ Set cooldown timer
        setCooldownTimer()

        // ✅ Start OTP timer
        startOTPTimer()

        // ✅ Save phone number
        sharedPrefs.edit().putString("phone_number", phoneNumber).apply()

        callbacks = callbacksInner

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacksInner)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
        Log.d(TAG, "📤 Sending OTP to: $phoneNumber")
    }

    private fun incrementOTPCount() {
        val count = sharedPrefs.getInt(OTP_COUNT_KEY, 0)
        val firstTime = sharedPrefs.getLong(OTP_FIRST_TIME_KEY, 0)

        // ✅ Reset if 24 hours passed
        if (System.currentTimeMillis() - firstTime > OTP_LIMIT_HOURS * 60 * 60 * 1000) {
            resetOTPLimit()
            sharedPrefs.edit().putInt(OTP_COUNT_KEY, 1).apply()
        } else {
            sharedPrefs.edit().putInt(OTP_COUNT_KEY, count + 1).apply()
        }
        updateAttemptsLeft()
    }

    private fun setCooldownTimer() {
        sharedPrefs.edit().putLong(OTP_COOLDOWN_KEY, System.currentTimeMillis()).apply()
    }

    // ============================================================
    // ✅ RESEND OTP
    // ============================================================

    fun resendOTP(phoneNumber: String, activity: Activity) {
        if (verificationId == null) {
            _errorMessage.value = "Please request OTP first"
            return
        }

        if (!canSendOTP(phoneNumber)) {
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        // ✅ Increment OTP count
        incrementOTPCount()
        setCooldownTimer()
        startOTPTimer()

        val token = resendToken
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacksInner)
            .apply {
                if (token != null) {
                    setForceResendingToken(token)
                }
            }
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
        Log.d(TAG, "📤 Resending OTP to: $phoneNumber")
    }

    // ============================================================
    // ✅ VERIFY OTP
    // ============================================================

    fun verifyOTP(otp: String) {
        if (otp.isBlank() || otp.length < 6) {
            _errorMessage.value = "Please enter a valid OTP (6 digits)"
            return
        }

        val vid = verificationId
        if (vid.isNullOrEmpty()) {
            _errorMessage.value = "Verification ID not found. Please resend OTP."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val credential = PhoneAuthProvider.getCredential(vid, otp)
        signInWithPhoneAuthCredential(credential)
    }

    // ============================================================
    // ✅ SIGN IN WITH CREDENTIAL
    // ============================================================

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Sign in successful: ${auth.currentUser?.phoneNumber}")
                    _otpVerified.value = true
                    _userUid.value = auth.currentUser?.uid
                    _errorMessage.value = null
                    cancelOTPTimer()
                } else {
                    Log.e(TAG, "❌ Sign in failed: ${task.exception?.message}")
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        _errorMessage.value = "Invalid OTP. Please try again."
                    } else {
                        _errorMessage.value = "Authentication failed: ${task.exception?.message}"
                    }
                }
            }
    }

    // ============================================================
    // ✅ OTP TIMER (5 minutes expiry)
    // ============================================================

    private fun startOTPTimer() {
        cancelOTPTimer()
        _otpTimerText.value = "⏱️ ${OTP_EXPIRY_SECONDS / 60}:00 remaining"

        timerRunnable = object : Runnable {
            var secondsLeft = OTP_EXPIRY_SECONDS

            override fun run() {
                secondsLeft--
                if (secondsLeft <= 0) {
                    _otpTimerText.value = "⏰ OTP Expired! Please resend."
                    _verificationSent.value = false
                    handler.removeCallbacks(this)
                    timerRunnable = null
                    return
                }
                val minutes = secondsLeft / 60
                val seconds = secondsLeft % 60
                _otpTimerText.value = "⏱️ ${minutes}:${String.format("%02d", seconds)} remaining"
                handler.postDelayed(this, 1000)
            }
        }
        timerRunnable?.let { handler.post(it) }
    }

    fun cancelOTPTimer() {
        timerRunnable?.let {
            handler.removeCallbacks(it)
            timerRunnable = null
        }
        _otpTimerText.value = ""
    }

    fun getOTPAttemptsLeft(): Int {
        return _otpAttemptsLeft.value ?: MAX_OTP_ATTEMPTS
    }

    // ============================================================
    // ✅ CHECK USER LOGIN STATUS
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
    // ✅ LOGOUT
    // ============================================================

    fun logout() {
        auth.signOut()
        _otpVerified.value = false
        _userUid.value = null
        cancelOTPTimer()
        Log.d(TAG, "🚪 User logged out")
    }

    // ============================================================
    // ✅ CLEAR ERRORS
    // ============================================================

    fun clearError() {
        _errorMessage.value = null
    }

    fun resetOTPState() {
        _verificationSent.value = false
        _otpVerified.value = false
        verificationId = null
        resendToken = null
        _errorMessage.value = null
        cancelOTPTimer()
    }

    override fun onCleared() {
        super.onCleared()
        cancelOTPTimer()
        Log.d(TAG, "🧹 ViewModel cleared")
    }
}