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
                        // Copy only the bytes that were read
                        val audioChunk = buffer.copyOf(read)
                        onAudioData(audioChunk)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for audio recording", e)
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
        if (!isRecording) return

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Stopped recording audio")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    /**
     * Start audio playback
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

            audioTrack?.play()
            isPlaying = true
            Log.d(TAG, "Started audio playback at $SAMPLE_RATE Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio playback", e)
            isPlaying = false
        }
    }

    /**
     * Play audio data through speaker
     */
    fun playAudio(audioData: ByteArray) {
        if (!isPlaying || audioTrack == null) {
            Log.w(TAG, "Cannot play audio - playback not started")
            return
        }

        try {
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        if (!isPlaying) return

        isPlaying = false

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            Log.d(TAG, "Stopped audio playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        }
    }

    /**
     * Mute/unmute microphone
     */
    fun setMuted(muted: Boolean) {
        // Stop/start recording to mute
        if (muted && isRecording) {
            audioRecord?.stop()
        } else if (!muted && isRecording) {
            audioRecord?.startRecording()
        }
    }

    /**
     * Toggle speaker on/off
     */
    fun setSpeakerOn(speakerOn: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = speakerOn
        Log.d(TAG, "Speaker ${if (speakerOn) "enabled" else "disabled"}")
    }

    /**
     * Release all resources
     */
    fun release() {
        stopRecording()
        stopPlayback()
    }
}