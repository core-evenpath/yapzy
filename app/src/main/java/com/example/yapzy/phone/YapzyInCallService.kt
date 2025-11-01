package com.example.yapzy

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.example.yapzy.phone.CallManager
import com.example.yapzy.ui.screens.InCallActivity  // ← THIS WAS THE MISSING IMPORT!

class YapzyInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        // Store the call in CallManager
        CallManager.currentCall = call
        CallManager.inCallService = this

        // Start the InCallActivity
        val intent = Intent(this, InCallActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra("call_state", call.state)
        startActivity(intent)

        // Listen for call state changes
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)

                when (state) {
                    Call.STATE_DISCONNECTED -> {
                        // Call ended
                        CallManager.currentCall = null
                    }
                    Call.STATE_ACTIVE -> {
                        // Call is active
                    }
                    Call.STATE_HOLDING -> {
                        // Call is on hold
                    }
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        if (CallManager.currentCall == call) {
            CallManager.currentCall = null
        }

        // If no more calls, clear the service reference
        if (calls.isEmpty()) {
            CallManager.inCallService = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.inCallService = null
    }
}