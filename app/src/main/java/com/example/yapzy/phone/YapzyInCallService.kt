package com.example.yapzy.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.example.yapzy.R
import com.example.yapzy.ui.screens.InCallActivity

class YapzyInCallService : InCallService() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "yapzy_call_channel"
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.currentCall = call
        CallManager.inCallService = this

        // Create notification channel
        createNotificationChannel()

        // Show persistent notification
        showCallNotification(call)

        // Launch the in-call UI with proper flags for prominence
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

            // Pass call state to help the activity configure itself
            putExtra("CALL_STATE", call.state)
        }
        startActivity(intent)

        // Register callback to update notification
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                showCallNotification(call)
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.currentCall == call) {
            CallManager.currentCall = null
        }
        CallManager.inCallService = null

        // Remove notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows ongoing call information"
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showCallNotification(call: Call) {
        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
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

        // Intent to return to InCallActivity
        val returnIntent = Intent(this, InCallActivity::class.java).apply {
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

object CallManager {
    var currentCall: Call? = null
    var inCallService: YapzyInCallService? = null

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    fun setSpeaker(speaker: Boolean) {
        inCallService?.let { service ->
            val route = if (speaker) {
                CallAudioState.ROUTE_SPEAKER
            } else {
                CallAudioState.ROUTE_EARPIECE
            }
            service.setAudioRoute(route)
        }
    }

    fun endCall() {
        currentCall?.disconnect()
    }
}