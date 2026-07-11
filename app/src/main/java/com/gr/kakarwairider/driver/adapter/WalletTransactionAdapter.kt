package com.gr.kakarwairider.driver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gr.kakarwairider.R
import com.gr.kakarwairider.driver.model.WalletTransactionModel
import java.text.SimpleDateFormat
import java.util.*

class WalletTransactionAdapter(
    private val transactions: List<WalletTransactionModel>
) : RecyclerView.Adapter<WalletTransactionAdapter.TransactionViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallet_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(transaction: WalletTransactionModel) {
            // Type icon
            val typeIcon = when (transaction.type) {
                "CREDIT", "RECHARGE" -> "🟢"
                "DEBIT", "FEE" -> "🔴"
                else -> "⚪"
            }
            tvType.text = "$typeIcon ${transaction.type}"

            // Amount with sign
            val amountText = when (transaction.type) {
                "CREDIT", "RECHARGE" -> "+₹${transaction.amount}"
                "DEBIT", "FEE" -> "-₹${transaction.amount}"
                else -> "₹${transaction.amount}"
            }
            tvAmount.text = amountText

            val color = when (transaction.type) {
                "CREDIT", "RECHARGE" -> itemView.context.getColor(R.color.green)
                "DEBIT", "FEE" -> itemView.context.getColor(R.color.red)
                else -> itemView.context.getColor(R.color.grey)
            }
            tvAmount.setTextColor(color)

            tvDescription.text = transaction.description
            transaction.createdAt?.let {
                tvDate.text = dateFormat.format(it.toDate())
            }
        }
    }
}