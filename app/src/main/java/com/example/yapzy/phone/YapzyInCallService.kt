package com.example.yapzy.phone

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class YapzyInCallService : InCallService() {

    companion object {
        private const val TAG = "YapzyInCallService"
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}")

        // Store the call in CallManager
        CallManager.currentCall = call

        // Launch InCallActivity
        launchInCallActivity(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed: ${call.details.handle}")

        // Clear the call from CallManager
        if (CallManager.currentCall == call) {
            CallManager.currentCall = null
        }
    }

    private fun launchInCallActivity(call: Call) {
        try {
            val intent = Intent()
            intent.setClassName(
                packageName,
                "com.example.yapzy.ui.screens.InCallActivity"
            )

            // Determine if incoming or outgoing
            val isIncoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
            intent.putExtra("OUTGOING_CALL", !isIncoming)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            startActivity(intent)
            Log.d(TAG, "InCallActivity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching InCallActivity", e)
        }
    }
}