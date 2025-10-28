package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yapzy.data.SampleData
import com.example.yapzy.models.Message
import com.example.yapzy.models.Priority
import com.example.yapzy.models.Tone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBackClick: () -> Unit
) {
    val conversation = remember { SampleData.getConversationDetails(conversationId) }
    var messageText by remember { mutableStateOf("") }
    var showSmartReplies by remember { mutableStateOf(true) }
    var showContextPanel by remember { mutableStateOf(false) }
    var showAIAssist by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    if (conversation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Conversation not found")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                conversation.contactAvatar,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                conversation.contactName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (conversation.contextualInfo?.upcomingMeetings?.isNotEmpty() == true) {
                                Text(
                                    "Meeting in ${conversation.contextualInfo.upcomingMeetings[0].time}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showContextPanel = !showContextPanel }) {
                        Icon(
                            Icons.Default.Info,
                            "Context Info",
                            tint = if (showContextPanel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Context Panel
            if (showContextPanel) {
                ContextPanel(
                    contextInfo = conversation.contextualInfo,
                    onDismiss = { showContextPanel = false }
                )
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(conversation.messages) { message ->
                    MessageBubble(message)
                }
            }

            // Smart Replies
            if (showSmartReplies && conversation.suggestedReplies.isNotEmpty()) {
                SmartRepliesSection(
                    replies = conversation.suggestedReplies,
                    onReplyClick = { reply ->
                        messageText = reply
                        showSmartReplies = false
                    },
                    onDismiss = { showSmartReplies = false }
                )
            }

            // AI Writing Assistant
            if (showAIAssist && messageText.isNotEmpty()) {
                AIAssistPanel(
                    originalText = messageText,
                    onSuggestionClick = { suggestion ->
                        messageText = suggestion
                        showAIAssist = false
                    },
                    onDismiss = { showAIAssist = false }
                )
            }

            // Message Input
            MessageInputBar(
                message = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    // Handle send
                    messageText = ""
                    showSmartReplies = false
                    scope.launch {
                        listState.animateScrollToItem(conversation.messages.size)
                    }
                },
                onAIAssistClick = { showAIAssist = !showAIAssist },
                aiAssistActive = showAIAssist
            )
        }
    }

    // Bottom Sheet for additional options
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            ChatOptionsSheet(onDismiss = { showBottomSheet = false })
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                ),
                color = if (message.isFromUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (message.isFromUser) 0.dp else 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (message.isFromUser)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Message metadata (AI analysis)
            if (!message.isFromUser) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message.getFormattedTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Priority indicator
                    if (message.priority == Priority.URGENT || message.priority == Priority.HIGH) {
                        Badge(
                            containerColor = if (message.priority == Priority.URGENT)
                                MaterialTheme.colorScheme.error
                            else
                                Color(0xFFFF9800)
                        ) {
                            Text(
                                message.priority.name,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Tone chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = message.tone.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    message.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmartRepliesSection(
    replies: List<String>,
    onReplyClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Smart Replies",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(replies) { reply ->
                    SuggestionChip(
                        onClick = { onReplyClick(reply) },
                        label = { Text(reply) },
                        icon = {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AIAssistPanel(
    originalText: String,
    onSuggestionClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "AI Writing Assistant",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tone analysis
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Detected tone:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Badge {
                    Text("CASUAL", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Suggestions
            Text(
                "Suggestions:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            SuggestionOption(
                title = "More Professional",
                text = "Thank you for your inquiry. I would be pleased to assist you with this matter.",
                onClick = { onSuggestionClick(it) }
            )

            Spacer(Modifier.height(8.dp))

            SuggestionOption(
                title = "Friendlier",
                text = "Hey! Thanks for reaching out! I'd love to help you with that 😊",
                onClick = { onSuggestionClick(it) }
            )

            Spacer(Modifier.height(8.dp))

            SuggestionOption(
                title = "More Concise",
                text = "Happy to help with this!",
                onClick = { onSuggestionClick(it) }
            )
        }
    }
}

@Composable
fun SuggestionOption(
    title: String,
    text: String,
    onClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(text) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = "Use",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MessageInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAIAssistClick: () -> Unit,
    aiAssistActive: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onAIAssistClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (aiAssistActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        Color.Transparent
                )
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = "AI Assist",
                    tint = if (aiAssistActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                trailingIcon = {
                    Row {
                        IconButton(onClick = { /* Attach */ }) {
                            Icon(Icons.Default.AttachFile, "Attach")
                        }
                        IconButton(onClick = { /* Voice */ }) {
                            Icon(Icons.Default.Mic, "Voice")
                        }
                    }
                }
            )

            FloatingActionButton(
                onClick = onSendClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ContextPanel(
    contextInfo: com.example.yapzy.models.ContextualInfo?,
    onDismiss: () -> Unit
) {
    if (contextInfo == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Context & Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Close", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Upcoming meetings
            if (contextInfo.upcomingMeetings.isNotEmpty()) {
                ContextSection(
                    icon = Icons.Default.Event,
                    title = "Upcoming Meetings",
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    contextInfo.upcomingMeetings.forEach { meeting ->
                        Text(
                            "• ${meeting.title} - ${meeting.time}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Related emails
            if (contextInfo.relatedEmails.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ContextSection(
                    icon = Icons.Default.Email,
                    title = "Recent Emails",
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    contextInfo.relatedEmails.forEach { email ->
                        Text(
                            "• ${email.subject}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            email.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Shared projects
            if (contextInfo.sharedProjects.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ContextSection(
                    icon = Icons.Default.Folder,
                    title = "Shared Projects",
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        contextInfo.sharedProjects.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ContextSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun ChatOptionsSheet(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Message Options",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OptionItem(
            icon = Icons.Default.Summarize,
            title = "Summarize Conversation",
            description = "Get AI-powered summary of this chat"
        )

        OptionItem(
            icon = Icons.Default.Schedule,
            title = "Schedule Follow-up",
            description = "Set a reminder to follow up"
        )

        OptionItem(
            icon = Icons.Default.Announcement,
            title = "Draft Announcement",
            description = "Create a message for multiple recipients"
        )

        OptionItem(
            icon = Icons.Default.Analytics,
            title = "Conversation Insights",
            description = "View tone, sentiment, and engagement analysis"
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun OptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle option */ }
            .padding(vertical = 8.dp),
        color = Color.Transparent
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}