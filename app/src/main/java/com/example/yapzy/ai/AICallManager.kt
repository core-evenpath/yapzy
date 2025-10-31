package com.example.yapzy.ai

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AICallManager(
    private val context: Context,
    private val viewModel: AICallViewModel
) {
    private var openAIClient: OpenAIRealtimeClient? = null
    private var audioManager: AudioStreamManager? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // You need to set your OpenAI API key here or pass it in
    private val apiKey: String = "YOUR_OPENAI_API_KEY_HERE"
    // OR get it from BuildConfig if you configure it in build.gradle:
    // private val apiKey: String = BuildConfig.OPENAI_API_KEY

    fun startAICall(callerNumber: String) {
        viewModel.startAiCall()

        val instructions = """
            You are an AI phone assistant. You are answering a call on behalf of the user. 
            Be polite, concise, and helpful. If it's spam, politely decline and ask to be removed from the list.
            Keep responses under 20 seconds.
        """.trimIndent()

        audioManager = AudioStreamManager(context)

        openAIClient = OpenAIRealtimeClient(
            apiKey = apiKey,
            onTranscript = { speaker, text ->
                val time = viewModel.formatTime(viewModel.callDuration.value)
                viewModel.addTranscriptItem(TranscriptItem(speaker, text, time))
            },
            onAudioReceived = { audioData ->
                audioManager?.playAudio(audioData)
            }
        )

        openAIClient?.connect(instructions)

        // Start recording and streaming to OpenAI
        audioManager?.startRecording { audioData ->
            openAIClient?.sendAudio(audioData)
        }

        audioManager?.startPlayback()
    }

    fun handCallToAI() {
        viewModel.handToAI()

        val instructions = """
            You are an AI assistant that just took over an ongoing phone call.
            Introduce yourself briefly and ask how you can help.
            Be professional and helpful.
        """.trimIndent()

        // Re-initialize with new instructions
        openAIClient?.disconnect()
        startAICall(viewModel.callerInfo.value?.number ?: "")
    }

    fun takeBackCall() {
        // Stop AI, but keep call active
        openAIClient?.disconnect()
        audioManager?.stopRecording()
        audioManager?.stopPlayback()
        viewModel.answerCall()
    }

    fun composeAIMessage(reason: String = "busy") {
        viewModel.startAiMessage()

        coroutineScope.launch {
            // Use OpenAI Chat API for message composition
            val message = generateAIMessage(reason)

            // Simulate typing effect
            message.forEachIndexed { index, _ ->
                delay(200)
                viewModel.setAiMessage(message.substring(0, index + 1))
            }

            viewModel.setTyping(false)
        }
    }

    private suspend fun generateAIMessage(reason: String): String {
        // Call OpenAI Chat API (not Realtime API)
        // This would use OkHttp to call the REST API
        return when (reason) {
            "busy" -> "I can't talk right now. Can you text me instead?"
            "meeting" -> "I'm in a meeting. I'll call you back later."
            else -> "Can't answer right now. Please text me."
        }
    }

    fun sendSMS(phoneNumber: String, message: String) {
        try {
            // Use getDefault() for better compatibility across Android versions
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AICallManager", "Failed to send SMS", e)
            Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
        }
    }

    fun endCall() {
        openAIClient?.disconnect()
        audioManager?.stopRecording()
        audioManager?.stopPlayback()
        viewModel.declineCall()
    }

    fun cleanup() {
        openAIClient?.disconnect()
        audioManager?.stopRecording()
        audioManager?.stopPlayback()
        coroutineScope.cancel()
    }
}