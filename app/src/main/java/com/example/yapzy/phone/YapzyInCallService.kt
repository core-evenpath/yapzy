package com.example.yapzy.phone

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.yapzy.ui.screens.InCallActivity

class YapzyInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.currentCall = call
        CallManager.inCallService = this

        // Launch the in-call UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.currentCall == call) {
            CallManager.currentCall = null
        }
        CallManager.inCallService = null
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
}