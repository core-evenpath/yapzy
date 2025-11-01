package com.example.yapzy.phone

import com.example.yapzy.YapzyInCallService
import android.telecom.Call
import android.telecom.CallAudioState

object CallManager {
    var currentCall: Call? = null
    var inCallService: YapzyInCallService? = null

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    fun setSpeaker(speaker: Boolean) {
        inCallService?.let { service: YapzyInCallService ->  // ✅ FIXED: Added explicit type
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

    fun answerCall() {
        currentCall?.answer(0)
    }

    fun rejectCall() {
        currentCall?.reject(false, null)
    }
}