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
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var userId: String? = null

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

        loadUserProfile()

        // ✅ Edit Button
        btnEdit.setOnClickListener {
            toggleEditMode(true)
        }

        // ✅ Save Button
        btnSave.setOnClickListener {
            saveUserProfile()
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
        etEmail = view.findViewById(R.id.etEmail)
        btnSave = view.findViewById(R.id.btnSave)
        btnEdit = view.findViewById(R.id.btnEdit)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun loadUserProfile() {
        userId?.let { id ->
            db.collection("users").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name") ?: "User"
                        val phone = document.getString("phone") ?: "N/A"
                        val email = document.getString("email") ?: ""
                        val totalRides = document.getLong("totalRides") ?: 0
                        val totalSpent = document.getDouble("totalSpent") ?: 0.0
                        val rating = document.getDouble("rating") ?: 0.0
                        val createdAt = document.getTimestamp("createdAt")

                        tvName.text = name
                        tvPhone.text = phone
                        tvTotalRides.text = "$totalRides"
                        tvTotalSpent.text = "₹${String.format("%.2f", totalSpent)}"
                        tvRating.text = String.format("%.1f⭐", rating)

                        createdAt?.let {
                            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                            tvMemberSince.text = "📅 Member since ${dateFormat.format(it.toDate())}"
                        }

                        // ✅ Set EditTexts with current values
                        etName.setText(name)
                        etEmail.setText(email)

                        // ✅ Initially disable editing
                        toggleEditMode(false)
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
            val userData = hashMapOf(
                "uid" to id,
                "phone" to phone,
                "name" to "",
                "email" to "",
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
                    Toast.makeText(requireContext(), "✅ Profile created", Toast.LENGTH_SHORT).show()
                    loadUserProfile()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to create profile", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ✅ Fixed: Edit mode toggle
    private fun toggleEditMode(edit: Boolean) {
        if (edit) {
            // ✅ Enable editing
            etName.isEnabled = true
            etEmail.isEnabled = true
            etName.requestFocus()
            btnSave.visibility = View.VISIBLE
            btnEdit.visibility = View.GONE
        } else {
            // ✅ Disable editing
            etName.isEnabled = false
            etEmail.isEnabled = false
            btnSave.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
        }
    }

    private fun saveUserProfile() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        userId?.let { id ->
            val updates = mapOf(
                "name" to name,
                "email" to email,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("users").document(id)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "✅ Profile updated!", Toast.LENGTH_SHORT).show()
                    tvName.text = name
                    toggleEditMode(false)
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                }
        }
    }
}