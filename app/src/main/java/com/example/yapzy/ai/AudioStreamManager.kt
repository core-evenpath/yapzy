package com.example.yapzy.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/**
 * Manages audio recording and playback for AI calls
 */
class AudioStreamManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioStreamManager"
        private const val SAMPLE_RATE = 24000 // OpenAI requires 24kHz
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isPlaying = false

    /**
     * Check if recording permission is granted
     */
    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording audio from microphone
     */
    fun startRecording(onAudioData: (ByteArray) -> Unit) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // Check permission before starting
        if (!hasRecordPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_MULTIPLIER

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "Started recording audio at $SAMPLE_RATE Hz")

            // Start reading audio in background
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)
                while (isRecording && isActive) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        onAudioData(buffer.copyOf(read))
                    } else if (read < 0) {
                        Log.e(TAG, "Error reading audio: $read")
                        break
                    }
                }
                Log.d(TAG, "Recording loop ended")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting audio recording - permission not granted", e)
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            isRecording = false
        }
    }

    /**
     * Stop recording audio
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }

        audioRecord = null
        Log.d(TAG, "Stopped recording audio")
    }

    /**
     * Play received audio
     */
    fun playAudio(audioData: ByteArray) {
        try {
            if (audioTrack == null) {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT
                )

                audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack?.play()
                isPlaying = true
                Log.d(TAG, "Started audio playback")
            }

            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    /**
     * Mute/unmute microphone (stops/starts recording)
     */
    fun setMuted(muted: Boolean) {
        // Muting is handled by stopping recording in AICallManager
        Log.d(TAG, "Mute state changed: $muted")
    }

    /**
     * Toggle speaker on/off
     */
    fun setSpeaker(speaker: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = speaker
            Log.d(TAG, "Speaker ${if (speaker) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker", e)
        }
    }

    /**
     * Clean up audio resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up audio resources")
        stopRecording()

        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio track", e)
        }
        audioTrack = null

        Log.d(TAG, "Cleaned up audio resources")
    }
}