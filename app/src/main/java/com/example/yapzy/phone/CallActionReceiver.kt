package com.example.yapzy.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_END_CALL = "com.example.yapzy.ACTION_END_CALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_END_CALL -> {
                CallManager.endCall()
            }
        }
    }
}