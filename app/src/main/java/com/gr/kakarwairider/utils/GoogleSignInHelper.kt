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
import com.gr.kakarwairider.R

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
     * ✅ Handle Sign-In Result with Detailed Logging
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
                Log.d(TAG, "   Email: ${it.email}")
                Log.d(TAG, "   ID Token: ${it.idToken?.take(20)}...")
                onSuccess(it)
            } ?: run {
                Log.e(TAG, "❌ Account is null")
                onError("Account is null")
            }
        } catch (e: ApiException) {
            // ✅ DETAILED ERROR LOGGING FOR ERROR 10
            Log.e(TAG, "========== GOOGLE SIGN-IN FAILED ==========")
            Log.e(TAG, "❌ Status Code: ${e.statusCode}")
            Log.e(TAG, "❌ Message: ${e.message}")
            Log.e(TAG, "❌ Status Message: ${e.status?.statusMessage}")

            // ✅ Error 10 = DEVELOPER_ERROR (SHA-1 mismatch)
            if (e.statusCode == 10) {
                Log.e(TAG, "❌❌❌ ERROR 10: DEVELOPER_ERROR ❌❌❌")
                Log.e(TAG, "🔍 This means SHA-1 fingerprint mismatch!")
                Log.e(TAG, "🔍 Play Store signing key SHA-1 is NOT in Firebase Console")
                Log.e(TAG, "🔍 Solution: Add Play Console's SHA-1 to Firebase Console")
                Log.e(TAG, "🔍 Steps:")
                Log.e(TAG, "   1. Play Console → Setup → App integrity")
                Log.e(TAG, "   2. Copy SHA-1 from App signing key certificate")
                Log.e(TAG, "   3. Firebase Console → Project Settings → Your apps")
                Log.e(TAG, "   4. Add fingerprint → Paste SHA-1 → Save")
                Log.e(TAG, "   5. Download new google-services.json")
                Log.e(TAG, "   6. Clean + Rebuild + Upload new AAB")
            }

            // ✅ Check if it's a resolution error
            if (e.status?.hasResolution() == true) {
                Log.d(TAG, "⚠️ Status has resolution - maybe user needs to interact")
            }

            Log.e(TAG, "=============================================")
            onError("Sign-in failed: ${e.message} (Code: ${e.statusCode})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error: ${e.message}")
            e.printStackTrace()
            onError("Unexpected error: ${e.message}")
        }
    }

    /**
     * ✅ Sign in with Firebase using Google Credential
     */
    fun signInWithFirebase(
        account: GoogleSignInAccount,
        onComplete: (Boolean, String?) -> Unit
    ) {
        try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            Log.d(TAG, "🔐 Signing in with Firebase...")
            Log.d(TAG, "   ID Token: ${account.idToken?.take(20)}...")

            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "✅ Firebase Auth Success: ${auth.currentUser?.uid}")
                        Log.d(TAG, "   User: ${auth.currentUser?.displayName}")
                        Log.d(TAG, "   Email: ${auth.currentUser?.email}")
                        onComplete(true, auth.currentUser?.uid)
                    } else {
                        val exception = task.exception
                        Log.e(TAG, "❌ Firebase Auth Failed")
                        Log.e(TAG, "   Error: ${exception?.message}")
                        Log.e(TAG, "   Cause: ${exception?.cause}")
                        exception?.printStackTrace()
                        onComplete(false, exception?.message)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Firebase Auth Failure Listener: ${e.message}")
                    onComplete(false, e.message)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during Firebase sign-in: ${e.message}")
            e.printStackTrace()
            onComplete(false, e.message)
        }
    }

    /**
     * ✅ Check if user exists in Firestore
     */
    fun checkUserExists(
        userId: String,
        onResult: (Boolean) -> Unit
    ) {
        Log.d(TAG, "🔍 Checking if user exists in Firestore: $userId")
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val exists = document.exists()
                Log.d(TAG, "   User exists: $exists")
                onResult(exists)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore check failed: ${e.message}")
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
        Log.d(TAG, "🚪 Signing out...")
        googleSignInClient.signOut()
            .addOnCompleteListener {
                auth.signOut()
                Log.d(TAG, "🚪 Signed out successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Sign out failed: ${e.message}")
            }
    }

    /**
     * ✅ Revoke Access
     */
    fun revokeAccess() {
        Log.d(TAG, "🔐 Revoking access...")
        googleSignInClient.revokeAccess()
            .addOnCompleteListener {
                auth.signOut()
                Log.d(TAG, "🔐 Access revoked")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Revoke access failed: ${e.message}")
            }
    }
}