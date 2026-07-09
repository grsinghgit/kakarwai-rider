package com.gr.kakarwairider.ui

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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R

class DriverFormFragment : Fragment() {

    private lateinit var etDriverName: TextInputEditText
    private lateinit var etDriverPhone: TextInputEditText
    private lateinit var etDriverPIN: TextInputEditText
    private lateinit var etVehicleType: TextInputEditText
    private lateinit var etVehicleModel: TextInputEditText
    private lateinit var etVehicleNumber: TextInputEditText
    private lateinit var etArea: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var adminId: String? = null
    private var areaId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadAdminAndArea()
        setupListeners()
    }

    private fun initViews(view: View) {
        etDriverName = view.findViewById(R.id.etDriverName)
        etDriverPhone = view.findViewById(R.id.etDriverPhone)
        etDriverPIN = view.findViewById(R.id.etDriverPIN)
        etVehicleType = view.findViewById(R.id.etVehicleType)
        etVehicleModel = view.findViewById(R.id.etVehicleModel)
        etVehicleNumber = view.findViewById(R.id.etVehicleNumber)
        etArea = view.findViewById(R.id.etArea)
        btnRegister = view.findViewById(R.id.btnRegisterDriver)
        btnCancel = view.findViewById(R.id.btnCancel)
    }

    private fun loadAdminAndArea() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please login as admin", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        adminId = currentUser.uid

        // ✅ Temporary: Hardcoded area
        // TODO: Remove this after Firestore areaId is set
        areaId = "area_kakarwai"
        etArea.setText("Kakarwai (Default)")

        // ✅ Real code (Firestore se fetch)
        /*
        db.collection("admins")
            .document(adminId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val areaId = document.getString("areaId")
                    val areaName = document.getString("areaName") ?: "Unknown Area"
                    if (!areaId.isNullOrEmpty()) {
                        this.areaId = areaId
                        etArea.setText(areaName)
                    } else {
                        loadAreaFromAdmin()
                    }
                } else {
                    loadAreaFromAdmin()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load admin data", Toast.LENGTH_SHORT).show()
            }
        */
    }

    private fun loadAreaFromAdmin() {
        db.collection("areas")
            .whereEqualTo("adminId", adminId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty()) {
                    val doc = documents.first()
                    areaId = doc.id
                    val areaName = doc.getString("areaName") ?: "Unknown Area"
                    etArea.setText(areaName)
                } else {
                    etArea.setText("No area assigned")
                }
            }
    }

    private fun setupListeners() {
        btnRegister.setOnClickListener {
            registerDriver()
        }

        btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun registerDriver() {
        val name = etDriverName.text.toString().trim()
        val phone = etDriverPhone.text.toString().trim()
        val pin = etDriverPIN.text.toString().trim()
        val vehicleType = etVehicleType.text.toString().trim()
        val vehicleModel = etVehicleModel.text.toString().trim()
        val vehicleNumber = etVehicleNumber.text.toString().trim()

        // ✅ Validation
        if (name.isEmpty()) {
            etDriverName.error = "Name is required"
            return
        }
        if (phone.isEmpty() || phone.length < 10) {
            etDriverPhone.error = "Valid phone number required"
            return
        }
        if (pin.isEmpty() || pin.length < 4) {
            etDriverPIN.error = "PIN must be 4-6 digits"
            return
        }
        if (vehicleType.isEmpty()) {
            etVehicleType.error = "Vehicle type is required"
            return
        }
        if (vehicleModel.isEmpty()) {
            etVehicleModel.error = "Vehicle model is required"
            return
        }
        if (vehicleNumber.isEmpty()) {
            etVehicleNumber.error = "Vehicle number is required"
            return
        }

        if (areaId == null) {
            Toast.makeText(requireContext(), "No area assigned to this admin", Toast.LENGTH_SHORT).show()
            return
        }

        btnRegister.isEnabled = false
        btnRegister.text = "Saving..."

        // ✅ 1. Save to drivers collection
        val driverData = hashMapOf(
            "name" to name,
            "phone" to phone,
            "pin" to pin,
            "areaId" to areaId,
            "adminId" to adminId,
            "isActive" to true,
            "isAvailable" to true,
            "vehicleType" to vehicleType,
            "vehicleModel" to vehicleModel,
            "vehicleNumber" to vehicleNumber,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        db.collection("drivers")
            .add(driverData)
            .addOnSuccessListener { documentRef ->
                // ✅ 2. Also save to driver_locations for online tracking
                val locationData = hashMapOf(
                    "driverId" to documentRef.id,
                    "driverName" to name,
                    "driverPhone" to phone,
                    "currentLocation" to null,  // ⬅️ Location update baad mein hogi
                    "status" to "OFFLINE",      // ⬅️ Initially offline
                    "isAvailable" to false,
                    "updatedAt" to Timestamp.now()
                )

                db.collection("driver_locations")
                    .document(documentRef.id)
                    .set(locationData)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "✅ Driver $name registered successfully!", Toast.LENGTH_LONG).show()
                        btnRegister.isEnabled = true
                        btnRegister.text = "🚗 Register Driver"
                        clearForm()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "⚠️ Driver saved but location entry failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnRegister.isEnabled = true
                        btnRegister.text = "🚗 Register Driver"
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "❌ Failed to register driver: ${e.message}", Toast.LENGTH_SHORT).show()
                btnRegister.isEnabled = true
                btnRegister.text = "🚗 Register Driver"
            }
    }

    private fun clearForm() {
        etDriverName.text?.clear()
        etDriverPhone.text?.clear()
        etDriverPIN.text?.clear()
        etVehicleType.text?.clear()
        etVehicleModel.text?.clear()
        etVehicleNumber.text?.clear()
        etDriverName.requestFocus()
    }
}