package com.example.yapzy.data

import com.example.yapzy.models.*

object SampleData {

    private val now = System.currentTimeMillis()
    private val HOUR = 3600000L
    private val DAY = 86400000L

    val conversations = listOf(
        Conversation(
            id = "1",
            contactName = "Sarah Chen",
            contactAvatar = "SC",
            messages = listOf(
                Message(
                    id = "m1",
                    senderId = "sarah_chen",
                    senderName = "Sarah Chen",
                    content = "Hey! Are we still on for the project meeting tomorrow at 2 PM?",
                    timestamp = now - (2 * HOUR),
                    isFromUser = false,
                    priority = Priority.HIGH,
                    tone = Tone.FRIENDLY,
                    intent = Intent.QUESTION
                )
            ),
            lastMessage = Message(
                id = "m1",
                senderId = "sarah_chen",
                senderName = "Sarah Chen",
                content = "Hey! Are we still on for the project meeting tomorrow at 2 PM?",
                timestamp = now - (2 * HOUR),
                isFromUser = false,
                priority = Priority.HIGH,
                tone = Tone.FRIENDLY,
                intent = Intent.QUESTION
            ),
            unreadCount = 1,
            isPinned = true,
            aiSummary = "Sarah is confirming tomorrow's 2 PM project meeting",
            suggestedReplies = listOf(
                "Yes, looking forward to it!",
                "Sorry, can we reschedule to 3 PM?",
                "Yes, I'll be there. Do you need me to prepare anything?"
            ),
            contextualInfo = ContextualInfo(
                upcomingMeetings = listOf(
                    MeetingInfo(
                        title = "Q4 Project Review",
                        time = now + DAY,
                        location = "Conference Room B"
                    )
                ),
                relatedEmails = listOf(
                    EmailInfo(
                        subject = "Project Timeline Updates",
                        preview = "Attached are the latest updates to our project timeline...",
                        time = now - (3 * DAY)
                    )
                ),
                sharedProjects = listOf("Q4 Marketing Campaign", "Website Redesign")
            )
        ),

        Conversation(
            id = "2",
            contactName = "Tech Support",
            contactAvatar = "TS",
            messages = listOf(
                Message(
                    id = "m2",
                    senderId = "support",
                    senderName = "Tech Support",
                    content = "Your ticket #12345 has been resolved. The billing issue should now be fixed.",
                    timestamp = now - (5 * HOUR),
                    isFromUser = false,
                    priority = Priority.MEDIUM,
                    tone = Tone.FORMAL,
                    intent = Intent.INFORMATION
                )
            ),
            lastMessage = Message(
                id = "m2",
                senderId = "support",
                senderName = "Tech Support",
                content = "Your ticket #12345 has been resolved. The billing issue should now be fixed.",
                timestamp = now - (5 * HOUR),
                isFromUser = false,
                priority = Priority.MEDIUM,
                tone = Tone.FORMAL,
                intent = Intent.INFORMATION
            ),
            unreadCount = 1,
            aiSummary = "Support ticket resolved - billing issue fixed",
            suggestedReplies = listOf(
                "Thank you! Confirmed it's working now.",
                "Thanks for the update",
                "Can you confirm the charges were reversed?"
            )
        ),

        Conversation(
            id = "3",
            contactName = "Mom",
            contactAvatar = "M",
            messages = listOf(
                Message(
                    id = "m3",
                    senderId = "mom",
                    senderName = "Mom",
                    content = "Don't forget about dinner this Sunday! Bring your favorite dessert 🍰",
                    timestamp = now - DAY,
                    isFromUser = false,
                    priority = Priority.LOW,
                    tone = Tone.FRIENDLY,
                    intent = Intent.REQUEST
                )
            ),
            lastMessage = Message(
                id = "m3",
                senderId = "mom",
                senderName = "Mom",
                content = "Don't forget about dinner this Sunday! Bring your favorite dessert 🍰",
                timestamp = now - DAY,
                isFromUser = false,
                priority = Priority.LOW,
                tone = Tone.FRIENDLY,
                intent = Intent.REQUEST
            ),
            unreadCount = 0,
            aiSummary = "Reminder: Family dinner Sunday - bring dessert",
            contextualInfo = ContextualInfo(
                upcomingMeetings = listOf(
                    MeetingInfo(
                        title = "Family Dinner",
                        time = now + (3 * DAY),
                        location = "Mom's House"
                    )
                )
            )
        ),

        Conversation(
            id = "4",
            contactName = "Project Team",
            contactAvatar = "PT",
            messages = listOf(
                Message(
                    id = "m4",
                    senderId = "team",
                    senderName = "Alex",
                    content = "URGENT: Server is down. Need everyone online ASAP!",
                    timestamp = now - (15 * 60000),
                    isFromUser = false,
                    priority = Priority.URGENT,
                    tone = Tone.URGENT,
                    intent = Intent.REQUEST
                )
            ),
            lastMessage = Message(
                id = "m4",
                senderId = "team",
                senderName = "Alex",
                content = "URGENT: Server is down. Need everyone online ASAP!",
                timestamp = now - (15 * 60000),
                isFromUser = false,
                priority = Priority.URGENT,
                tone = Tone.URGENT,
                intent = Intent.REQUEST
            ),
            unreadCount = 3,
            isPinned = true,
            aiSummary = "🚨 CRITICAL: Server outage - immediate attention required",
            suggestedReplies = listOf(
                "On it! Logging in now.",
                "I'm available. What's the status?",
                "Checking the logs now. Will update in 5 mins."
            )
        ),

        Conversation(
            id = "5",
            contactName = "Jamie Rodriguez",
            contactAvatar = "JR",
            messages = listOf(
                Message(
                    id = "m5",
                    senderId = "jamie",
                    senderName = "Jamie Rodriguez",
                    content = "Thanks so much for your help yesterday! Really appreciate it 😊",
                    timestamp = now - (20 * HOUR),
                    isFromUser = false,
                    priority = Priority.LOW,
                    tone = Tone.FRIENDLY,
                    intent = Intent.PRAISE
                )
            ),
            lastMessage = Message(
                id = "m5",
                senderId = "jamie",
                senderName = "Jamie Rodriguez",
                content = "Thanks so much for your help yesterday! Really appreciate it 😊",
                timestamp = now - (20 * HOUR),
                isFromUser = false,
                priority = Priority.LOW,
                tone = Tone.FRIENDLY,
                intent = Intent.PRAISE
            ),
            unreadCount = 0,
            aiSummary = "Jamie expressed gratitude for your assistance",
            suggestedReplies = listOf(
                "Happy to help anytime!",
                "No problem at all!",
                "Glad I could help! Let me know if you need anything else."
            )
        )
    )

    // Sample conversation details with full history
    fun getConversationDetails(conversationId: String): Conversation? {
        return when (conversationId) {
            "1" -> conversations[0].copy(
                messages = listOf(
                    Message(
                        id = "m1_1",
                        senderId = "sarah_chen",
                        senderName = "Sarah Chen",
                        content = "Hi! How are you doing?",
                        timestamp = now - (2 * DAY),
                        isFromUser = false,
                        tone = Tone.FRIENDLY,
                        intent = Intent.GREETING
                    ),
                    Message(
                        id = "m1_2",
                        senderId = "user",
                        senderName = "You",
                        content = "Hey Sarah! Doing great, thanks. How about you?",
                        timestamp = now - (2 * DAY) + HOUR,
                        isFromUser = true,
                        tone = Tone.FRIENDLY,
                        intent = Intent.GREETING
                    ),
                    Message(
                        id = "m1_3",
                        senderId = "sarah_chen",
                        senderName = "Sarah Chen",
                        content = "Good! Just wanted to touch base about the project. Can we schedule a meeting soon?",
                        timestamp = now - (2 * DAY) + (2 * HOUR),
                        isFromUser = false,
                        tone = Tone.CASUAL,
                        intent = Intent.SCHEDULING
                    ),
                    Message(
                        id = "m1_4",
                        senderId = "user",
                        senderName = "You",
                        content = "Sure! How about tomorrow afternoon?",
                        timestamp = now - (2 * DAY) + (3 * HOUR),
                        isFromUser = true,
                        tone = Tone.CASUAL,
                        intent = Intent.SCHEDULING
                    ),
                    Message(
                        id = "m1_5",
                        senderId = "sarah_chen",
                        senderName = "Sarah Chen",
                        content = "Hey! Are we still on for the project meeting tomorrow at 2 PM?",
                        timestamp = now - (2 * HOUR),
                        isFromUser = false,
                        priority = Priority.HIGH,
                        tone = Tone.FRIENDLY,
                        intent = Intent.QUESTION
                    )
                )
            )
            else -> conversations.find { it.id == conversationId }
        }
    }
}