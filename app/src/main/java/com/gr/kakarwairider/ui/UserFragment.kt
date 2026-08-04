package com.gr.kakarwairider.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.viewmodel.AuthViewModel

class UserFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()

    // Views
    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvTotalRides: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvMemberSince: TextView

    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText  // ✅ NEW: Phone input
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnGoToHome: MaterialButton  // ✅ NEW: Go to Home button
    private lateinit var btnLogout: MaterialButton

    private var userId: String? = null
    private var isFirstTimeUser = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Get arguments from bundle (for first-time users)
        val userName = arguments?.getString("userName", "")
        val userEmail = arguments?.getString("userEmail", "")
        val googleId = arguments?.getString("googleId", "")

        // ✅ Check if first-time user
        isFirstTimeUser = googleId?.isNotEmpty() == true

        loadUserProfile()

        // ✅ If first-time user, pre-fill name and email
        if (isFirstTimeUser && !userName.isNullOrEmpty()) {
            etName.setText(userName)
            etName.isEnabled = true
            etEmail.setText(userEmail ?: "")
            tvName.text = userName
        }

        // ✅ Edit Button
        btnEdit.setOnClickListener {
            toggleEditMode(true)
        }

        // ✅ Save Button
        btnSave.setOnClickListener {
            saveUserProfile()
        }

        // ✅ Go to Home Button — Only works if phone number is available
        btnGoToHome.setOnClickListener {
            checkPhoneAndNavigate()
        }

        // ✅ Logout Button
        btnLogout.setOnClickListener {
            authViewModel.logout()
            Toast.makeText(requireContext(), "🔓 Logged out", Toast.LENGTH_SHORT).show()
            (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()
            findNavController().navigate(R.id.action_home_to_login)
        }
    }

    private fun initViews(view: View) {
        tvName = view.findViewById(R.id.tvName)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvTotalRides = view.findViewById(R.id.tvTotalRides)
        tvTotalSpent = view.findViewById(R.id.tvTotalSpent)
        tvRating = view.findViewById(R.id.tvRating)
        tvMemberSince = view.findViewById(R.id.tvMemberSince)

        etName = view.findViewById(R.id.etName)
        etPhone = view.findViewById(R.id.etPhone)  // ✅ NEW
        etEmail = view.findViewById(R.id.etEmail)
        btnSave = view.findViewById(R.id.btnSave)
        btnEdit = view.findViewById(R.id.btnEdit)
        btnGoToHome = view.findViewById(R.id.btnGoToHome)  // ✅ NEW
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun loadUserProfile() {
        userId?.let { id ->
            db.collection("users").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name") ?: "User"
                        val phone = document.getString("phone") ?: ""
                        val email = document.getString("email") ?: ""
                        val totalRides = document.getLong("totalRides") ?: 0
                        val totalSpent = document.getDouble("totalSpent") ?: 0.0
                        val rating = document.getDouble("rating") ?: 0.0
                        val createdAt = document.getTimestamp("createdAt")

                        tvName.text = name
                        tvPhone.text = if (phone.isNotEmpty()) "📱 $phone" else "📱 Not set"
                        tvTotalRides.text = "$totalRides"
                        tvTotalSpent.text = "₹${String.format("%.2f", totalSpent)}"
                        tvRating.text = String.format("%.1f⭐", rating)

                        createdAt?.let {
                            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                            tvMemberSince.text = "📅 Member since ${dateFormat.format(it.toDate())}"
                        }

                        // ✅ Set EditTexts with current values
                        etName.setText(name)
                        etPhone.setText(phone)
                        etEmail.setText(email)

                        // ✅ Initially disable editing
                        toggleEditMode(false)

                        // ✅ Check if phone is available
                        if (phone.isNotEmpty()) {
                            btnGoToHome.isEnabled = true
                            btnGoToHome.alpha = 1.0f
                        } else {
                            btnGoToHome.isEnabled = false
                            btnGoToHome.alpha = 0.5f
                        }

                    } else {
                        createUserDocument()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun createUserDocument() {
        userId?.let { id ->
            val phone = FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
            val userName = arguments?.getString("userName", "") ?: ""
            val userEmail = arguments?.getString("userEmail", "") ?: ""
            val googleId = arguments?.getString("googleId", "") ?: ""

            val userData = hashMapOf(
                "uid" to id,
                "phone" to phone,
                "name" to userName,
                "email" to userEmail,
                "googleId" to googleId,
                "totalRides" to 0,
                "totalSpent" to 0.0,
                "rating" to 0.0,
                "isActive" to true,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "updatedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("users").document(id)
                .set(userData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "✅ Profile created!", Toast.LENGTH_SHORT).show()
                    loadUserProfile()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to create profile", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun toggleEditMode(edit: Boolean) {
        if (edit) {
            etName.isEnabled = true
            etPhone.isEnabled = true
            etEmail.isEnabled = true
            etName.requestFocus()
            btnSave.visibility = View.VISIBLE
            btnEdit.visibility = View.GONE
        } else {
            etName.isEnabled = false
            etPhone.isEnabled = false
            etEmail.isEnabled = false
            btnSave.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
        }
    }

    private fun saveUserProfile() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            Toast.makeText(requireContext(), "Phone number is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.length != 10 || !phone.all { it.isDigit() }) {
            Toast.makeText(requireContext(), "Enter valid 10 digit phone number", Toast.LENGTH_SHORT).show()
            return
        }

        userId?.let { id ->
            val updates = mapOf(
                "name" to name,
                "phone" to phone,
                "email" to email,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("users").document(id)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "✅ Profile updated!", Toast.LENGTH_SHORT).show()
                    tvName.text = name
                    tvPhone.text = "📱 $phone"

                    // ✅ Enable Go to Home button
                    btnGoToHome.isEnabled = true
                    btnGoToHome.alpha = 1.0f

                    toggleEditMode(false)
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ✅ Check phone and navigate to Home
    private fun checkPhoneAndNavigate() {
        userId?.let { id ->
            db.collection("users").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val phone = document.getString("phone") ?: ""
                        if (phone.isNotEmpty()) {
                            // ✅ Phone available → Navigate to Home
                            (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()
                            findNavController().navigate(R.id.action_user_to_home)
                        } else {
                            Toast.makeText(requireContext(), "⚠️ Please add your phone number first", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to check profile", Toast.LENGTH_SHORT).show()
                }
        }
    }
}