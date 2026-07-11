package com.gr.kakarwairider.driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.gr.kakarwairider.driver.model.WalletTransactionModel
import kotlinx.coroutines.launch

class DriverWalletViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _balance = MutableLiveData(0.0)
    val balance: LiveData<Double> = _balance

    private val _transactions = MutableLiveData<List<WalletTransactionModel>>(emptyList())
    val transactions: LiveData<List<WalletTransactionModel>> = _transactions

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var balanceListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var transactionListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun loadWalletData(driverId: String) {
        _isLoading.value = true

        // ✅ Load balance
        balanceListener?.remove()
        balanceListener = db.collection("drivers").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val balance = snapshot?.getDouble("walletBalance") ?: 0.0
                _balance.value = balance
                _isLoading.value = false
            }

        // ✅ Load transactions
        transactionListener?.remove()
        transactionListener = db.collection("driver_transactions")
            .whereEqualTo("driverId", driverId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _errorMessage.value = error.message
                    return@addSnapshotListener
                }

                val transactions = snapshots?.documents?.mapNotNull {
                    it.toObject<WalletTransactionModel>()
                } ?: emptyList()
                _transactions.value = transactions
                _isLoading.value = false
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        balanceListener?.remove()
        transactionListener?.remove()
    }
}