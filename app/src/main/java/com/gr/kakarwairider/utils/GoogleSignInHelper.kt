package com.gr.kakarwairider.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.gr.kakarwairider.R   // ✅ ADD THIS IMPORT

class GoogleSignInHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSignInHelper"
        private const val RC_SIGN_IN = 1001
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // ✅ Google Sign-In Client
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.google_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * ✅ Check if already signed in
     */
    fun isAlreadySignedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * ✅ Get Sign-In Intent
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * ✅ Handle Sign-In Result
     */
    fun handleSignInResult(
        data: Intent?,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val task: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            account?.let {
                Log.d(TAG, "✅ Google Sign-In Success: ${it.displayName}")
                onSuccess(it)
            } ?: run {
                onError("Account is null")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "❌ Google Sign-In Failed: ${e.message}")
            onError("Sign-in failed: ${e.message}")
        }
    }

    /**
     * ✅ Sign in with Firebase using Google Credential
     */
    fun signInWithFirebase(
        account: GoogleSignInAccount,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Firebase Auth Success: ${auth.currentUser?.uid}")
                    onComplete(true, auth.currentUser?.uid)
                } else {
                    Log.e(TAG, "❌ Firebase Auth Failed: ${task.exception?.message}")
                    onComplete(false, task.exception?.message)
                }
            }
    }

    /**
     * ✅ Check if user exists in Firestore
     */
    fun checkUserExists(
        userId: String,
        onResult: (Boolean) -> Unit
    ) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                onResult(document.exists())
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    /**
     * ✅ Get current user
     */
    fun getCurrentUser(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * ✅ Sign Out
     */
    fun signOut() {
        googleSignInClient.signOut()
            .addOnCompleteListener {
                auth.signOut()
                Log.d(TAG, "🚪 Signed out")
            }
    }

    /**
     * ✅ Revoke Access
     */
    fun revokeAccess() {
        googleSignInClient.revokeAccess()
            .addOnCompleteListener {
                auth.signOut()
                Log.d(TAG, "🔐 Access revoked")
            }
    }
}