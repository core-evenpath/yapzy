package com.example.yapzy.ai

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresPermission
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

    /**
     * Start AI call handling
     */
    fun startAICall(callerNumber: String, callerName: String?) {
        try {
            Log.d(TAG, "Starting AI call for $callerNumber")
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
        }
    }

    /**
     * Initialize real OpenAI client
     */
    private fun initializeAIClient(callerNumber: String, callerName: String?) {
        coroutineScope.launch {
            try {
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

                // Start recording and streaming audio
                audioManager = AudioStreamManager(context)
                audioManager?.startRecording { audioData ->
                    openAIClient?.sendAudio(audioData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AI client", e)
                onError("Failed to initialize AI: ${e.message}")
            }
        }
    }

    /**
     * Hand an active call over to AI
     */
    fun handCallToAI(callerNumber: String, callerName: String?) {
        try {
            Log.d(TAG, "Handing call to AI")
            onStateChange(AICallState.AI_TAKEOVER)

            coroutineScope.launch {
                delay(500)
                addToTranscript(
                    Speaker.AI,
                    "Hello, I'm taking over this call. How can I assist you?"
                )
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
                // Message composed - this would be shown in UI
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
        audioManager?.setMuted(muted)
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
        val item = TranscriptItem.create(speaker, text)
        transcript.add(item)
        onTranscriptUpdate(transcript.toList())
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up AICallManager")
        audioManager?.cleanup()
        audioManager = null
        openAIClient?.disconnect()
        openAIClient = null
        coroutineScope.cancel()
    }
}