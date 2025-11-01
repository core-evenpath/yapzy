package com.example.yapzy.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_END_CALL = "com.example.yapzy.ACTION_END_CALL"
        private const val TAG = "CallActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_END_CALL -> {
                Log.d(TAG, "Ending call via broadcast")
                CallManager.endCall()
            }
        }
    }
}