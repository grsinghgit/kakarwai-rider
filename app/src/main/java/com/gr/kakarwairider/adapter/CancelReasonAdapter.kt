package com.gr.kakarwairider.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gr.kakarwairider.R

class CancelReasonAdapter(
    private val reasons: List<String>,
    private val onReasonClick: (String) -> Unit
) : RecyclerView.Adapter<CancelReasonAdapter.ReasonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReasonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cancel_reason, parent, false)
        return ReasonViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReasonViewHolder, position: Int) {
        val reason = reasons[position]
        holder.bind(reason)
    }

    override fun getItemCount(): Int = reasons.size

    inner class ReasonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvReason: TextView = itemView.findViewById(R.id.tvReason)

        fun bind(reason: String) {
            tvReason.text = reason
            itemView.setOnClickListener {
                onReasonClick(reason)
            }
        }
    }
}