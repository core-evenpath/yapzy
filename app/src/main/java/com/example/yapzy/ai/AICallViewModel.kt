package com.example.yapzy.ai

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AICallViewModel : ViewModel() {

    // Call state
    private val _callState = MutableStateFlow(AICallState.INCOMING)
    val callState: StateFlow<AICallState> = _callState.asStateFlow()

    // Transcript
    private val _transcript = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcript: StateFlow<List<TranscriptItem>> = _transcript.asStateFlow()

    // AI composed message
    private val _aiMessage = MutableStateFlow("")
    val aiMessage: StateFlow<String> = _aiMessage.asStateFlow()

    // Typing indicator
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // Call duration in seconds
    private val _callDuration = MutableStateFlow(0)
    val callDuration: StateFlow<Int> = _callDuration.asStateFlow()

    // Audio controls
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaker = MutableStateFlow(false)
    val isSpeaker: StateFlow<Boolean> = _isSpeaker.asStateFlow()

    // Caller info
    private val _callerInfo = MutableStateFlow<CallerInfo?>(null)
    val callerInfo: StateFlow<CallerInfo?> = _callerInfo.asStateFlow()

    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Methods to update state
    fun setCallState(state: AICallState) {
        _callState.value = state
    }

    fun setTranscript(transcript: List<TranscriptItem>) {
        _transcript.value = transcript
    }

    fun addTranscriptItem(item: TranscriptItem) {
        _transcript.value = _transcript.value + item
    }

    fun setAiMessage(message: String) {
        _aiMessage.value = message
    }

    fun setTyping(isTyping: Boolean) {
        _isTyping.value = isTyping
    }

    fun setCallDuration(duration: Int) {
        _callDuration.value = duration
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setSpeaker(speaker: Boolean) {
        _isSpeaker.value = speaker
    }

    fun toggleSpeaker() {
        _isSpeaker.value = !_isSpeaker.value
    }

    fun setCallerInfo(info: CallerInfo) {
        _callerInfo.value = info
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // AI call specific methods
    fun startAiCall() {
        _callState.value = AICallState.AI_CALL
        _callDuration.value = 0
    }

    fun handToAI() {
        _callState.value = AICallState.AI_TAKEOVER
    }

    fun answerCall() {
        _callState.value = AICallState.ACTIVE
        _callDuration.value = 0
    }

    fun declineCall() {
        _callState.value = AICallState.DECLINED
    }

    fun startAiMessage() {
        _callState.value = AICallState.AI_MESSAGE
        _isTyping.value = true
    }

    fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}