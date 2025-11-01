package com.example.yapzy.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.yapzy.R
import com.example.yapzy.ui.screens.AICallActivity
import com.example.yapzy.ui.screens.InCallActivity

class YapzyInCallService : InCallService() {

    companion object {
        private const val TAG = "YapzyInCallService"
        private const val CHANNEL_ID = "yapzy_calls"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        CallManager.inCallService = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}")
        CallManager.currentCall = call

        // Show notification
        showCallNotification(call)

        // Launch appropriate activity based on call state
        when (call.details.state) {
            Call.STATE_RINGING -> {
                // Incoming call - launch AICallActivity with AI options
                val phoneNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
                val callerName = call.details.callerDisplayName ?: phoneNumber

                Log.d(TAG, "Incoming call from: $callerName ($phoneNumber)")

                val intent = AICallActivity.createIntent(
                    context = this,
                    phoneNumber = phoneNumber,
                    callerName = callerName,
                    callerType = "UNKNOWN"
                )
                startActivity(intent)
            }
            Call.STATE_DIALING, Call.STATE_ACTIVE -> {
                // Outgoing or active call - launch regular InCallActivity
                val intent = Intent(this, InCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        CallManager.currentCall = null
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows ongoing call notifications"
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showCallNotification(call: Call) {
        val phoneNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val contactsManager = ContactsManager(this)
        val contact = contactsManager.getContactByNumber(phoneNumber)
        val displayName = contact?.name ?: phoneNumber

        val callState = when (call.state) {
            Call.STATE_DIALING -> "Calling..."
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_ACTIVE -> "Ongoing call"
            Call.STATE_HOLDING -> "On hold"
            else -> "Call"
        }

        // Intent to return to call activity
        val returnIntent = if (call.state == Call.STATE_RINGING) {
            AICallActivity.createIntent(this, phoneNumber, contact?.name, "UNKNOWN")
        } else {
            Intent(this, InCallActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val returnPendingIntent = PendingIntent.getActivity(
            this,
            0,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End call action
        val endCallIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_END_CALL
        }
        val endCallPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayName)
            .setContentText(callState)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(returnPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "End Call",
                endCallPendingIntent
            )
            .setFullScreenIntent(returnPendingIntent, true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.inCallService = null
        Log.d(TAG, "Service destroyed")
    }
}