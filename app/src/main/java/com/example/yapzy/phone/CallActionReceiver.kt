package com.example.yapzy.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_END_CALL = "com.example.yapzy.ACTION_END_CALL"
        const val ACTION_ANSWER_CALL = "com.example.yapzy.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.yapzy.ACTION_DECLINE_CALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_END_CALL -> {
                Log.d(TAG, "Ending call")
                CallManager.endCall()
            }
            ACTION_ANSWER_CALL -> {
                Log.d(TAG, "Answering call")
                CallManager.answerCall()
            }
            ACTION_DECLINE_CALL -> {
                Log.d(TAG, "Declining call")
                CallManager.rejectCall()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }
}