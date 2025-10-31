package com.example.yapzy.ai

package com.example.yapzy.ai

/**
 * Represents the state of an AI call
 */
enum class AICallState {
    INCOMING,
    RINGING,
    ACTIVE,
    AI_HANDLING,
    COMPOSING_MESSAGE,
    ENDED
}

/**
 * Represents who is speaking in the transcript
 */
enum class Speaker {
    CALLER,
    AI,
    USER
}

/**
 * A single item in the call transcript
 */
data class TranscriptItem(
    val speaker: Speaker,
    val text: String,
    val timestamp: String
)

/**
 * Information about the caller
 */
data class CallerInfo(
    val name: String,
    val number: String,
    val photoUri: String? = null
)