package com.gr.kakarwairider

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import com.gr.kakarwairider.R
import org.json.JSONObject

class PaymentActivity : AppCompatActivity(), PaymentResultListener {

    private lateinit var tvAmount: TextView
    private lateinit var tvRideId: TextView
    private lateinit var btnPay: MaterialButton

    private var rideId: String? = null
    private var amount: Double = 0.0

    companion object {
        private const val TAG = "PaymentActivity"
        // ✅ Test API Key - Dashboard se copy karein
        private const val RAZORPAY_KEY_ID = "rzp_test_TCjuAVu0nSLDFH"  // 🔥 CHANGE KAREIN
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        // ✅ Views
        tvAmount = findViewById(R.id.tvAmount)
        tvRideId = findViewById(R.id.tvRideId)
        btnPay = findViewById(R.id.btnPay)

        // ✅ Intent se data lo
        rideId = intent.getStringExtra("rideId")
        amount = intent.getDoubleExtra("amount", 0.0)

        tvAmount.text = "Amount: ₹${amount.toInt()}"
        tvRideId.text = "Ride ID: ${rideId?.takeLast(8) ?: "--"}"

        // ✅ Preload Checkout (faster loading ke liye)
        Checkout.preload(applicationContext)  // [citation:1]

        btnPay.setOnClickListener {
            startPayment()
        }
    }

    private fun startPayment() {
        // ✅ Step 6.1 - Payment Options JSON Banayein
        try {
            val options = JSONObject()
            options.put("name", "Kakarwai Rider")
            options.put("description", "Ride Payment - #${rideId?.takeLast(8)}")
            options.put("image", "https://your-logo-url.com/logo.png")  // 🔥 Logo URL
            options.put("currency", "INR")

            // ✅ Amount in paise (smallest currency unit) [citation:1]
            // For ₹250.00 → 25000 paise
            val amountInPaise = (amount * 100).toInt()
            options.put("amount", amountInPaise.toString())  // [citation:1]

            // ✅ Prefill Customer Details
            val prefill = JSONObject()
            prefill.put("email", "user@example.com")
            prefill.put("contact", "+919876543210")
            options.put("prefill", prefill)

            // ✅ Theme Color
            val theme = JSONObject()
            theme.put("color", "#FF6B00")  // Your brand color
            options.put("theme", theme)

            // ✅ Retry Options
            val retryObj = JSONObject()
            retryObj.put("enabled", true)
            retryObj.put("max_count", 4)
            options.put("retry", retryObj)

            // ✅ Step 6.2 - Checkout Open Karein
            val checkout = Checkout()
            checkout.setKeyID(RAZORPAY_KEY_ID)  // [citation:1]
            checkout.open(this, options)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Step 6.3 - Payment Success Callback
    override fun onPaymentSuccess(razorpayPaymentID: String?) {
        Toast.makeText(this, "✅ Payment Successful! ID: $razorpayPaymentID", Toast.LENGTH_LONG).show()
        // 🔥 Next step: Server ko payment ID bhejna hai
        // aur ride status COMPLETED karna hai
    }

    // ✅ Step 6.4 - Payment Failure Callback
    override fun onPaymentError(code: Int, description: String?) {
        Toast.makeText(this, "❌ Payment Failed: $description", Toast.LENGTH_LONG).show()
    }
}