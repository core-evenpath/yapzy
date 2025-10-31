package com.example.yapzy.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for AI call screen state management
 */
class AICallViewModel : ViewModel() {

    private val _callState = MutableStateFlow(AICallState.INCOMING)
    val callState: StateFlow<AICallState> = _callState.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcript: StateFlow<List<TranscriptItem>> = _transcript.asStateFlow()

    private val _aiMessage = MutableStateFlow("")
    val aiMessage: StateFlow<String> = _aiMessage.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _callDuration = MutableStateFlow(0)
    val callDuration: StateFlow<Int> = _callDuration.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaker = MutableStateFlow(false)
    val isSpeaker: StateFlow<Boolean> = _isSpeaker.asStateFlow()

    private val _callerInfo = MutableStateFlow<CallerInfo?>(null)
    val callerInfo: StateFlow<CallerInfo?> = _callerInfo.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setCallState(state: AICallState) {
        viewModelScope.launch {
            _callState.emit(state)
        }
    }

    fun setTranscript(items: List<TranscriptItem>) {
        viewModelScope.launch {
            _transcript.emit(items)
        }
    }

    fun setAiMessage(message: String) {
        viewModelScope.launch {
            _aiMessage.emit(message)
        }
    }

    fun setTyping(typing: Boolean) {
        viewModelScope.launch {
            _isTyping.emit(typing)
        }
    }

    fun setCallDuration(duration: Int) {
        viewModelScope.launch {
            _callDuration.emit(duration)
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            _isMuted.emit(!_isMuted.value)
        }
    }

    fun toggleSpeaker() {
        viewModelScope.launch {
            _isSpeaker.emit(!_isSpeaker.value)
        }
    }

    fun setCallerInfo(info: CallerInfo) {
        viewModelScope.launch {
            _callerInfo.emit(info)
        }
    }

    fun setError(message: String?) {
        viewModelScope.launch {
            _errorMessage.emit(message)
        }
    }

    fun clearError() {
        setError(null)
    }

    fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}