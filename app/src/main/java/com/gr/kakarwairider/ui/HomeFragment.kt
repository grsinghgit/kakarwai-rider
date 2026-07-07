package com.gr.kakarwairider.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.card.MaterialCardView
import com.gr.kakarwairider.MainActivity2
import com.gr.kakarwairider.R
import com.gr.kakarwairider.utils.ServiceAreaChecker
import com.gr.kakarwairider.viewmodel.AuthViewModel

class HomeFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var tvWelcome: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var progressServiceCheck: ProgressBar
    private lateinit var cardServiceUnavailable: MaterialCardView
    private lateinit var cardBookRide: CardView
    private lateinit var cardBuySell: CardView
    private lateinit var cardRoomRent: CardView
    private lateinit var cardFoodDelivery: CardView
    private lateinit var cardGoodsDelivery: CardView

    private var isInServiceArea = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        tvWelcome = view.findViewById(R.id.tvWelcome)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        progressServiceCheck = view.findViewById(R.id.progressServiceCheck)
        cardServiceUnavailable = view.findViewById(R.id.cardServiceUnavailable)
        cardBookRide = view.findViewById(R.id.cardBookRide)
        cardBuySell = view.findViewById(R.id.cardBuySell)
        cardRoomRent = view.findViewById(R.id.cardRoomRent)
        cardFoodDelivery = view.findViewById(R.id.cardFoodDelivery)
        cardGoodsDelivery = view.findViewById(R.id.cardGoodsDelivery)

        // Show User Info
        val phoneNumber = authViewModel.getCurrentUserPhone()
        tvWelcome.text = "👋 Welcome, ${phoneNumber?.takeLast(10) ?: "User"}!"
        tvPhone.text = "📱 $phoneNumber"

        // ✅ Check Service Area
        checkServiceArea()

        // ✅ Card Click Listeners
        cardBookRide.setOnClickListener {
            if (isInServiceArea) {
                startActivity(Intent(requireContext(), MainActivity2::class.java))
            } else {
                Toast.makeText(requireContext(), "Service not available in your area", Toast.LENGTH_SHORT).show()
            }
        }

        cardBuySell.setOnClickListener {
            Toast.makeText(requireContext(), "🛒 Buy/Sell - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardRoomRent.setOnClickListener {
            Toast.makeText(requireContext(), "🏠 Room Rent - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardFoodDelivery.setOnClickListener {
            Toast.makeText(requireContext(), "🍔 Food Delivery - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardGoodsDelivery.setOnClickListener {
            Toast.makeText(requireContext(), "📦 Goods Delivery - Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ✅ CHECK SERVICE AREA (50km Radius)
    // ============================================================

    private fun checkServiceArea() {
        // ✅ Permission Check
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val checker = ServiceAreaChecker(requireContext())
        checker.checkUserLocation(object : ServiceAreaChecker.ServiceAreaCallback {
            override fun onResult(isInServiceArea: Boolean, distance: Double, userLocation: LatLng?) {
                this@HomeFragment.isInServiceArea = isInServiceArea
                progressServiceCheck.visibility = View.GONE

                if (isInServiceArea) {
                    tvServiceStatus.text = "✅ Service Available in your area (${(distance / 1000).toInt()}km away)"
                    cardServiceUnavailable.visibility = View.GONE
                    enableAllCards(true)
                } else {
                    tvServiceStatus.text = "❌ Service not available (${(distance / 1000).toInt()}km away)"
                    cardServiceUnavailable.visibility = View.VISIBLE
                    enableAllCards(false)
                }
            }

            override fun onError(message: String) {
                progressServiceCheck.visibility = View.GONE
                tvServiceStatus.text = "⚠️ Unable to check service area"
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun enableAllCards(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        cardBookRide.isEnabled = enabled
        cardBookRide.alpha = alpha

        // Other cards disabled for now (Coming Soon)
        cardBuySell.isEnabled = false
        cardBuySell.alpha = 0.5f
        cardRoomRent.isEnabled = false
        cardRoomRent.alpha = 0.5f
        cardFoodDelivery.isEnabled = false
        cardFoodDelivery.alpha = 0.5f
        cardGoodsDelivery.isEnabled = false
        cardGoodsDelivery.alpha = 0.5f
    }
}