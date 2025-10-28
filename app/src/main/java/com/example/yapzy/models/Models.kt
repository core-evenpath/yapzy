package com.example.yapzy.models

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Message priority levels
enum class Priority {
    LOW, MEDIUM, HIGH, URGENT
}

// Message tone detection
enum class Tone {
    NEUTRAL, FRIENDLY, FORMAL, URGENT, CASUAL, CONCERNED, EXCITED, FRUSTRATED
}

// Intent detection
enum class Intent {
    QUESTION, INFORMATION, REQUEST, CONFIRMATION, GREETING, FAREWELL, COMPLAINT, PRAISE, SCHEDULING
}

// Individual message
data class Message(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: LocalDateTime,
    val isFromUser: Boolean,
    val priority: Priority = Priority.MEDIUM,
    val tone: Tone = Tone.NEUTRAL,
    val intent: Intent = Intent.INFORMATION
) {
    fun getFormattedTime(): String {
        val now = LocalDateTime.now()
        return when {
            timestamp.toLocalDate() == now.toLocalDate() -> 
                timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))
            timestamp.toLocalDate().year == now.year -> 
                timestamp.format(DateTimeFormatter.ofPattern("MMM dd"))
            else -> 
                timestamp.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        }
    }
}

// Conversation with AI analysis
data class Conversation(
    val id: String,
    val contactName: String,
    val contactAvatar: String,
    val messages: List<Message>,
    val lastMessage: Message,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val aiSummary: String? = null,
    val suggestedReplies: List<String> = emptyList(),
    val contextualInfo: ContextualInfo? = null
) {
    fun getPreviewText(): String {
        return if (lastMessage.content.length > 50) {
            lastMessage.content.take(50) + "..."
        } else {
            lastMessage.content
        }
    }
}

// Contextual information from calendar/email
data class ContextualInfo(
    val upcomingMeetings: List<MeetingInfo> = emptyList(),
    val relatedEmails: List<EmailInfo> = emptyList(),
    val sharedProjects: List<String> = emptyList(),
    val lastInteraction: String? = null
)

data class MeetingInfo(
    val title: String,
    val time: LocalDateTime,
    val location: String?
)

data class EmailInfo(
    val subject: String,
    val preview: String,
    val time: LocalDateTime
)

// AI-powered smart reply suggestion
data class SmartReply(
    val text: String,
    val tone: Tone,
    val confidence: Float
)

// Message draft with AI assistance
data class MessageDraft(
    val content: String,
    val suggestions: List<String> = emptyList(),
    val toneAnalysis: Tone = Tone.NEUTRAL,
    val improvedVersion: String? = null
)
