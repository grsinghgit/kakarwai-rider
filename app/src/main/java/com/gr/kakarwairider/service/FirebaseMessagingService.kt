package com.gr.kakarwairider.admin.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.gr.kakarwairider.R
import com.gr.kakarwairider.admin.AdminActivity

class FirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AdminFCM"
        private const val CHANNEL_ID = "admin_notification_channel"
        private const val CHANNEL_NAME = "Admin Notifications"
        private const val NOTIFICATION_ID = 1001
    }

    private val db = FirebaseFirestore.getInstance()

    /**
     * ✅ Called when notification is received while app is in foreground
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "📨 onMessageReceived: ${message.from}")

        // ✅ Extract notification data
        val title = message.notification?.title ?: "🚗 New Ride Booked!"
        val body = message.notification?.body ?: "A new ride has been booked."

        // ✅ Extract ride ID from data payload (if any)
        val rideId = message.data["rideId"] ?: ""
        val pickupAddress = message.data["pickupAddress"] ?: ""
        val destinationAddress = message.data["destinationAddress"] ?: ""
        val vehicleName = message.data["vehicleName"] ?: ""
        val totalFare = message.data["totalFare"] ?: ""

        Log.d(TAG, "📋 Ride ID: $rideId")
        Log.d(TAG, "📋 Data: $message.data")

        // ✅ Build notification
        sendNotification(title, body, rideId)
    }

    /**
     * ✅ Called when FCM token is refreshed
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔄 onNewToken: $token")

        // ✅ Save token to Firestore
        saveTokenToFirestore(token)
    }

    /**
     * ✅ Send notification to user
     */
    private fun sendNotification(title: String, body: String, rideId: String) {
        // ✅ Create intent to open AdminActivity
        val intent = Intent(this, AdminActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // ✅ Pass ride ID to open ride directly
        if (rideId.isNotEmpty()) {
            intent.putExtra("rideId", rideId)        // ✅ FIXED: intent.putExtra
            intent.putExtra("from_notification", true)  // ✅ FIXED: intent.putExtra
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ Create notification channel (Android 8+)
        createNotificationChannel()

        // ✅ Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // ✅ Show notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "🔔 Notification sent: $title")
    }

    /**
     * ✅ Create notification channel for Android 8+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for admin about new rides and updates"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }

    /**
     * ✅ Save FCM token to Firestore for admin notifications
     */
    private fun saveTokenToFirestore(token: String) {
        // ✅ Store token under admin_tokens collection
        val tokenData = mapOf(
            "token" to token,
            "deviceType" to "android",
            "updatedAt" to Timestamp.now(),
            "isActive" to true
        )

        db.collection("admin_tokens")
            .document("admin_device")  // Single admin device ke liye
            .set(tokenData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Token saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save token: ${e.message}")
            }
    }

    /**
     * ✅ Get current token (for debugging)
     */
    fun getCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d(TAG, "📱 Current FCM Token: $token")
        }.addOnFailureListener { e ->
            Log.e(TAG, "❌ Failed to get token: ${e.message}")
        }
    }
}