package com.gr.kakarwairider.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gr.kakarwairider.R

class VendorFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_vendor, container, false)
        val textView = view.findViewById<TextView>(R.id.tvFragmentTitle)
        textView.text = "🏪 Vendor Login"
        return view
    }
}