package com.gr.kakarwairider.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.gr.kakarwairider.MainActivity
import com.gr.kakarwairider.R
import com.gr.kakarwairider.viewmodel.AuthViewModel

class HomeFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvUserInfo = view.findViewById<TextView>(R.id.tvFragmentTitle)
        val phoneNumber = authViewModel.getCurrentUserPhone()
        tvUserInfo.text = "👤 $phoneNumber"

        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            authViewModel.logout()
            (requireActivity() as? MainActivity)?.updateUIBasedOnLoginStatus()
            findNavController().navigate(R.id.action_home_to_login)
        }

    }
}