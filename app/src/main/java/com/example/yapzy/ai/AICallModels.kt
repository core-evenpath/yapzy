package com.example.yapzy.ai

/**
 * Represents the state of an AI call
 */
enum class AICallState {
    INCOMING,       // Initial state when call comes in
    CONNECTING,     // Connecting to AI service
    ACTIVE,         // User is on the call (not AI)
    AI_CALL,        // AI is handling the call
    AI_TAKEOVER,    // AI took over from active call
    AI_MESSAGE,     // AI is composing a message
    DECLINED,       // Call was declined
    ENDED           // Call ended
}

/**
 * Represents who is speaking in the conversation
 */
enum class Speaker {
    CALLER,     // The person calling
    AI,         // The AI assistant
    USER        // The phone owner
}

/**
 * Represents the type of caller
 */
enum class CallerType {
    CONTACT,        // Known contact
    SPAM_LIKELY,    // Likely spam
    UNKNOWN         // Unknown number
}

/**
 * Information about the caller
 */
data class CallerInfo(
    val name: String?,
    val number: String,
    val type: CallerType = CallerType.UNKNOWN
)

/**
 * A single item in the conversation transcript
 */
data class TranscriptItem(
    val speaker: Speaker,
    val text: String,
    val timestamp: String
) {
    companion object {
        fun create(speaker: Speaker, text: String): TranscriptItem {
            val seconds = System.currentTimeMillis() / 1000
            val mins = (seconds / 60) % 60
            val secs = seconds % 60
            val timestamp = String.format("%02d:%02d", mins, secs)
            return TranscriptItem(speaker, text, timestamp)
        }
    }
}