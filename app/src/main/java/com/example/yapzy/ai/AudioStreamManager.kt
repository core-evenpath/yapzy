package com.example.yapzy.ai

import android.content.Context
import android.media.*
import android.util.Log
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
     * Start recording audio from microphone
     */
    fun startRecording(onAudioData: (ByteArray) -> Unit) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
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
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            isRecording = false
        }
    }

    /**
     * Stop recording audio
     */
    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
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
            }

            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    /**
     * Mute/unmute microphone
     */
    fun setMuted(muted: Boolean) {
        audioRecord?.let {
            if (muted && isRecording) {
                stopRecording()
            }
        }
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
        stopRecording()
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "Cleaned up audio resources")
    }
}