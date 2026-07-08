package com.gr.kakarwairider

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.model.RideModel
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: HistoryAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val rideList = mutableListOf<RideModel>()
    private val TAG = "HistoryFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(rideList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        val userId = auth.currentUser?.uid
        Log.d(TAG, "loadHistory: userId = $userId")

        if (userId == null) {
            tvEmpty.text = "⚠️ Please login first"
            tvEmpty.visibility = View.VISIBLE
            return
        }

        // ✅ Query with more logging
        db.collection("rides")
            .whereEqualTo("userId", userId)
            .whereIn("status", listOf("COMPLETED", "CANCELLED", "EXPIRED"))
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "✅ History loaded! Found ${documents.size()} rides")
                rideList.clear()
                for (doc in documents) {
                    try {
                        val ride = doc.toObject(RideModel::class.java)
                        rideList.add(ride)
                        Log.d(TAG, "Ride: ${ride.rideId}, Status: ${ride.status}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing ride: ${e.message}")
                    }
                }
                adapter.notifyDataSetChanged()
                if (rideList.isEmpty()) {
                    tvEmpty.text = "📭 No rides found"
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load history: ${e.message}", e)
                tvEmpty.text = "❌ Error: ${e.message}"
                tvEmpty.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================================================
    // ✅ ADAPTER
    // ============================================================

    class HistoryAdapter(private val rides: List<RideModel>) :
        RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val ride = rides[position]
            holder.bind(ride)
        }

        override fun getItemCount(): Int = rides.size

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvRideId: TextView = itemView.findViewById(R.id.tvRideId)
            private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
            private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
            private val tvFare: TextView = itemView.findViewById(R.id.tvFare)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

            fun bind(ride: RideModel) {
                tvRideId.text = "Ride #${ride.rideId.takeLast(8)}"
                tvPickup.text = "📍 ${ride.pickup?.address ?: "N/A"}"
                tvDestination.text = "🏁 ${ride.destination?.address ?: "N/A"}"
                tvFare.text = "₹${ride.totalFare.toInt()}"

                val statusText = when (ride.status) {
                    "COMPLETED" -> "✅ Completed"
                    "CANCELLED" -> "❌ Cancelled"
                    "EXPIRED" -> "⏰ Expired"
                    else -> ride.status
                }
                tvStatus.text = statusText

                val statusColor = when (ride.status) {
                    "COMPLETED" -> android.R.color.holo_green_dark
                    "CANCELLED", "EXPIRED" -> android.R.color.holo_red_dark
                    else -> android.R.color.black
                }
                tvStatus.setTextColor(itemView.context.getColor(statusColor))

                ride.updatedAt?.let {
                    tvDate.text = dateFormat.format(it.toDate())
                }
            }
        }
    }
}