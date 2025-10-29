package com.example.yapzy.models

import java.text.SimpleDateFormat
import java.util.*

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
    val timestamp: Long,
    val isFromUser: Boolean,
    val priority: Priority = Priority.MEDIUM,
    val tone: Tone = Tone.NEUTRAL,
    val intent: Intent = Intent.INFORMATION
) {
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val nowCalendar = Calendar.getInstance()
        nowCalendar.timeInMillis = now

        return when {
            isSameDay(calendar, nowCalendar) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
            calendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) -> {
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
            }
            else -> {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}

// Conversation
data class Conversation(
    val id: String,
    val contactName: String,
    val contactAvatar: String,
    val photoUri: String? = null,
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
    val time: Long,
    val location: String?
)

data class EmailInfo(
    val subject: String,
    val preview: String,
    val time: Long
)

// Smart reply suggestion
data class SmartReply(
    val text: String,
    val tone: Tone,
    val confidence: Float
)

// Message draft
data class MessageDraft(
    val content: String,
    val suggestions: List<String> = emptyList(),
    val toneAnalysis: Tone = Tone.NEUTRAL,
    val improvedVersion: String? = null
)