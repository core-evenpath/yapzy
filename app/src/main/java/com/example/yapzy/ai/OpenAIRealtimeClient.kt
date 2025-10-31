package com.example.yapzy.ai

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for communicating with OpenAI's Realtime API via WebSocket
 */
class OpenAIRealtimeClient(
    private val apiKey: String,
    private val onTranscript: (Speaker, String) -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "OpenAIRealtimeClient"
        private const val WEBSOCKET_URL =
            "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-12-17"
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var isConnected = false
    private var currentTranscript = StringBuilder()

    /**
     * Connect to OpenAI Realtime API with custom instructions
     */
    fun connect(instructions: String) {
        Log.d(TAG, "Connecting to OpenAI Realtime API...")

        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
                onConnectionStateChanged(true)

                // Configure session
                sendSessionUpdate(instructions)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                isConnected = false
                onConnectionStateChanged(false)
                onError("Connection failed: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                isConnected = false
                onConnectionStateChanged(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected = false
                onConnectionStateChanged(false)
            }
        })
    }

    /**
     * Send session configuration to OpenAI
     */
    private fun sendSessionUpdate(instructions: String) {
        val config = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray(listOf("text", "audio")))
                put("instructions", instructions)
                put("voice", "alloy")
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("input_audio_transcription", JSONObject().apply {
                    put("model", "whisper-1")
                })
                put("turn_detection", JSONObject().apply {
                    put("type", "server_vad")
                    put("threshold", 0.5)
                    put("prefix_padding_ms", 300)
                    put("silence_duration_ms", 500)
                })
            })
        }

        sendMessage(config.toString())
        Log.d(TAG, "Session configured with instructions")
    }

    /**
     * Handle incoming messages from OpenAI
     */
    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")

            Log.d(TAG, "Received message type: $type")

            when (type) {
                "session.created" -> {
                    Log.d(TAG, "Session created successfully")
                }

                "session.updated" -> {
                    Log.d(TAG, "Session updated successfully")
                }

                "response.audio_transcript.delta" -> {
                    val delta = json.getString("delta")
                    currentTranscript.append(delta)
                    onTranscript(Speaker.AI, currentTranscript.toString())
                }

                "response.audio_transcript.done" -> {
                    val transcript = json.getString("transcript")
                    onTranscript(Speaker.AI, transcript)
                    currentTranscript.clear()
                }

                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json.getString("transcript")
                    onTranscript(Speaker.CALLER, transcript)
                }

                "response.audio.delta" -> {
                    val audioBase64 = json.getString("delta")
                    val audioBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
                    onAudioReceived(audioBytes)
                }

                "error" -> {
                    val error = json.getJSONObject("error")
                    val errorMessage = error.getString("message")
                    Log.e(TAG, "OpenAI error: $errorMessage")
                    onError(errorMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }

    /**
     * Send audio data to OpenAI
     */
    fun sendAudio(audioData: ByteArray) {
        if (!isConnected) {
            Log.w(TAG, "Cannot send audio - not connected")
            return
        }

        try {
            val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
            val message = JSONObject().apply {
                put("type", "input_audio_buffer.append")
                put("audio", base64Audio)
            }
            sendMessage(message.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
        }
    }

    /**
     * Commit the audio buffer and trigger AI response
     */
    fun commitAudioBuffer() {
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.commit")
        }
        sendMessage(message.toString())
    }

    /**
     * Request AI to generate a response
     */
    fun createResponse() {
        val message = JSONObject().apply {
            put("type", "response.create")
        }
        sendMessage(message.toString())
    }

    /**
     * Send a text message to the AI
     */
    fun sendText(text: String) {
        val message = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", text)
                    })
                })
            })
        }
        sendMessage(message.toString())
        createResponse()
    }

    /**
     * Send raw message to WebSocket
     */
    private fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    /**
     * Disconnect from OpenAI
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from OpenAI")
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        isConnected = false
        onConnectionStateChanged(false)
    }

    /**
     * Check if connected
     */
    fun isConnected(): Boolean = isConnected
}