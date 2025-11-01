package com.example.yapzy.phone

import android.content.Context
import android.media.AudioManager
import android.telecom.Call
import android.util.Log

object CallManager {
    private const val TAG = "CallManager"

    var currentCall: Call? = null
    private var audioManager: AudioManager? = null

    fun initialize(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun answerCall() {
        try {
            currentCall?.answer(0)
            Log.d(TAG, "Call answered")
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
        }
    }

    fun rejectCall() {
        try {
            currentCall?.reject(false, null)
            Log.d(TAG, "Call rejected")
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call", e)
        }
    }

    fun endCall() {
        try {
            currentCall?.disconnect()
            Log.d(TAG, "Call ended")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
        }
    }

    fun setMuted(muted: Boolean) {
        try {
            audioManager?.isMicrophoneMute = muted
            Log.d(TAG, "Mute set to: $muted")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting mute", e)
        }
    }

    fun setSpeaker(speaker: Boolean) {
        try {
            audioManager?.let { audio ->
                audio.mode = AudioManager.MODE_IN_CALL
                audio.isSpeakerphoneOn = speaker
            }
            Log.d(TAG, "Speaker set to: $speaker")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speaker", e)
        }
    }

    fun isMuted(): Boolean {
        return try {
            audioManager?.isMicrophoneMute ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isSpeakerOn(): Boolean {
        return try {
            audioManager?.isSpeakerphoneOn ?: false
        } catch (e: Exception) {
            false
        }
    }
}