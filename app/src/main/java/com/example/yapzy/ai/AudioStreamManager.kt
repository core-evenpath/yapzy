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
    private var audioManager: AudioManager? = null

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

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
                        val audioData = buffer.copyOf(read)
                        withContext(Dispatchers.Main) {
                            onAudioData(audioData)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            stopRecording()
        }
    }

    /**
     * Stop recording audio
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }

        try {
            isRecording = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            Log.d(TAG, "Stopped recording audio")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    /**
     * Start playing audio
     */
    fun startPlayback() {
        if (isPlaying) {
            Log.w(TAG, "Already playing")
            return
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_MULTIPLIER

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack failed to initialize")
                return
            }

            audioTrack?.play()
            isPlaying = true
            Log.d(TAG, "Started audio playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            stopPlayback()
        }
    }

    /**
     * Play audio data
     */
    fun playAudio(audioData: ByteArray) {
        if (!isPlaying) {
            startPlayback()
        }

        try {
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    /**
     * Stop playing audio
     */
    fun stopPlayback() {
        if (!isPlaying) {
            return
        }

        try {
            isPlaying = false

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            Log.d(TAG, "Stopped audio playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * Set speaker on/off
     */
    fun setSpeaker(speaker: Boolean) {
        try {
            audioManager?.let { audio ->
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                audio.isSpeakerphoneOn = speaker
                Log.d(TAG, "Speaker set to: $speaker")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speaker", e)
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * Clean up all resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up AudioStreamManager")
        try {
            stopRecording()
            stopPlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}