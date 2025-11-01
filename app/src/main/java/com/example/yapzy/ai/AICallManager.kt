package com.example.yapzy.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.yapzy.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AICallManager(
    private val context: Context,
    private val onTranscriptUpdate: (List<TranscriptItem>) -> Unit,
    private val onStateChange: (AICallState) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AICallManager"
        private const val SIMULATION_MODE = true // Set to false for real OpenAI integration
    }

    private var audioManager: AudioStreamManager? = null
    private var openAIClient: OpenAIRealtimeClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val transcript = mutableListOf<TranscriptItem>()

    private var isMuted = false
    private var isSpeakerOn = false
    private var isRecording = false

    /**
     * Check if audio recording permission is granted
     */
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start AI call handling
     */
    fun startAICall(callerNumber: String, callerName: String?) {
        try {
            Log.d(TAG, "Starting AI call for $callerNumber")

            // Check audio permission
            if (!hasAudioPermission()) {
                val error = "Microphone permission is required for AI calls"
                Log.e(TAG, error)
                onError(error)
                return
            }

            onStateChange(AICallState.CONNECTING)

            if (SIMULATION_MODE) {
                startSimulationMode(callerNumber, callerName)
            } else {
                initializeAIClient(callerNumber, callerName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AI call", e)
            onError("Failed to start AI call: ${e.message}")
        }
    }

    /**
     * Simulation mode for testing without OpenAI
     */
    private fun startSimulationMode(callerNumber: String, callerName: String?) {
        coroutineScope.launch {
            try {
                delay(1000)
                onStateChange(AICallState.AI_CALL)

                addToTranscript(
                    Speaker.AI,
                    "Hello, this is an AI assistant answering on behalf of the user. How may I help you?"
                )

                delay(2000)
                addToTranscript(
                    Speaker.CALLER,
                    "Hi, I'm calling about..."
                )

                delay(1500)
                addToTranscript(
                    Speaker.AI,
                    "I understand. Let me take a note of that for you."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in simulation mode", e)
                onError("Simulation error: ${e.message}")
            }
        }
    }

    /**
     * Initialize real OpenAI client
     */
    private fun initializeAIClient(callerNumber: String, callerName: String?) {
        coroutineScope.launch {
            try {
                // Check permission again before starting
                if (!hasAudioPermission()) {
                    onError("Microphone permission required")
                    return@launch
                }

                val apiKey = BuildConfig.OPENAI_API_KEY
                if (apiKey.isBlank() || apiKey == "YOUR_OPENAI_API_KEY_HERE") {
                    Log.w(TAG, "OpenAI API key not configured, using simulation mode")
                    startSimulationMode(callerNumber, callerName)
                    return@launch
                }

                openAIClient = OpenAIRealtimeClient(
                    apiKey = apiKey,
                    onTranscript = { speaker, text ->
                        addToTranscript(speaker, text)
                    },
                    onAudioReceived = { audioData ->
                        audioManager?.playAudio(audioData)
                    }
                ).apply {
                    setOnError { error ->
                        Log.e(TAG, "OpenAI error: $error")
                        onError(error)
                    }
                    setOnConnectionStateChanged { connected ->
                        if (connected) {
                            onStateChange(AICallState.AI_CALL)
                            startAudioRecording()
                        } else {
                            stopAudioRecording()
                        }
                    }
                }

                val instructions = """
                    You are an AI assistant answering phone calls on behalf of the user.
                    Be polite, professional, and helpful.
                    Take messages and offer to have the user call back.
                    Caller: ${callerName ?: callerNumber}
                """.trimIndent()

                openAIClient?.connect(instructions)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AI client", e)
                onError("Failed to initialize AI: ${e.message}")
            }
        }
    }

    /**
     * Start audio recording
     */
    private fun startAudioRecording() {
        try {
            if (!hasAudioPermission()) {
                Log.e(TAG, "Cannot start recording: no audio permission")
                onError("Microphone permission required")
                return
            }

            if (isRecording) {
                Log.w(TAG, "Already recording")
                return
            }

            audioManager = AudioStreamManager(context)
            audioManager?.startRecording { audioData ->
                openAIClient?.sendAudio(audioData)
            }
            isRecording = true
            Log.d(TAG, "Started audio recording")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            onError("Failed to start recording: ${e.message}")
        }
    }

    /**
     * Stop audio recording
     */
    private fun stopAudioRecording() {
        try {
            audioManager?.stopRecording()
            isRecording = false
            Log.d(TAG, "Stopped audio recording")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    /**
     * Hand an active call over to AI
     */
    fun handCallToAI(callerNumber: String, callerName: String?) {
        try {
            Log.d(TAG, "Handing call to AI")

            if (!hasAudioPermission()) {
                onError("Microphone permission required for AI features")
                return
            }

            onStateChange(AICallState.AI_TAKEOVER)

            coroutineScope.launch {
                delay(500)
                addToTranscript(
                    Speaker.AI,
                    "Hello, I'm taking over this call. How can I assist you?"
                )

                if (!SIMULATION_MODE) {
                    startAudioRecording()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handing call to AI", e)
            onError("Failed to hand call to AI: ${e.message}")
        }
    }

    /**
     * Have AI compose a message instead of answering
     */
    fun composeAIMessage(callerNumber: String, callerName: String?) {
        try {
            Log.d(TAG, "Composing AI message")
            onStateChange(AICallState.AI_MESSAGE)

            coroutineScope.launch {
                delay(1500)
                // Simulate message composition
                val message = "Sorry I missed your call. I'll get back to you soon!"
                // This would be shown in UI via viewModel
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error composing AI message", e)
            onError("Failed to compose message: ${e.message}")
        }
    }

    /**
     * Take back control from AI
     */
    fun takeBackCall() {
        try {
            Log.d(TAG, "Taking back call from AI")
            stopAudioRecording()
            onStateChange(AICallState.ACTIVE)
            addToTranscript(
                Speaker.USER,
                "I'm back on the line."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error taking back call", e)
            onError("Failed to take back call: ${e.message}")
        }
    }

    /**
     * Mute/unmute microphone
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted && isRecording) {
            audioManager?.stopRecording()
        } else if (!muted && !isRecording && openAIClient?.isConnected() == true) {
            startAudioRecording()
        }
    }

    /**
     * Toggle speaker on/off
     */
    fun setSpeaker(speaker: Boolean) {
        isSpeakerOn = speaker
        audioManager?.setSpeaker(speaker)
    }

    /**
     * Send SMS message
     */
    @RequiresPermission(android.Manifest.permission.SEND_SMS)
    fun sendSMS(phoneNumber: String, message: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val smsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Log.d(TAG, "SMS sent to $phoneNumber")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS", e)
                launch(Dispatchers.Main) {
                    onError("Failed to send SMS: ${e.message}")
                }
            }
        }
    }

    /**
     * End the call
     */
    fun endCall() {
        try {
            Log.d(TAG, "Ending call")
            stopAudioRecording()
            onStateChange(AICallState.ENDED)
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            onError("Failed to end call: ${e.message}")
        }
    }

    /**
     * Add item to transcript
     */
    private fun addToTranscript(speaker: Speaker, text: String) {
        try {
            val item = TranscriptItem.create(speaker, text)
            transcript.add(item)
            onTranscriptUpdate(transcript.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to transcript", e)
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up AICallManager")
        try {
            stopAudioRecording()
            audioManager?.cleanup()
            audioManager = null
            openAIClient?.disconnect()
            openAIClient = null
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}