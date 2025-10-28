package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yapzy.models.Conversation
import com.example.yapzy.models.Priority
import com.example.yapzy.phone.SMSManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit
) {
    val context = LocalContext.current
    val smsManager = remember { SMSManager(context) }
    
    var showAISummaries by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Load real conversations
    val conversations = remember(refreshTrigger) {
        smsManager.getConversations()
    }

    // Filter conversations based on search
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isEmpty()) {
            conversations
        } else {
            conversations.filter {
                it.contactName.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showAISummaries = !showAISummaries }) {
                        Icon(
                            imageVector = if (showAISummaries) Icons.Default.Psychology else Icons.Default.PsychologyAlt,
                            contentDescription = "Toggle AI Summaries",
                            tint = if (showAISummaries) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* New message */ },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Edit, "New Message")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // AI Insights Banner
            if (showAISummaries && filteredConversations.isNotEmpty()) {
                AIInsightsBanner(
                    unreadCount = filteredConversations.sumOf { it.unreadCount },
                    urgentCount = filteredConversations.count { it.lastMessage.priority == Priority.URGENT }
                )
            }
            
            if (filteredConversations.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Message,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "No messages yet" else "No messages found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Start a new conversation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredConversations) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            showAISummary = showAISummaries,
                            onClick = { onConversationClick(conversation.id) }
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    
                    // Refresh hint
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pull down to refresh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AIInsightsBanner(unreadCount: Int, urgentCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "AI Insights",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$unreadCount unread • $urgentCount urgent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (urgentCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text("$urgentCount", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    showAISummary: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar with priority indicator
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.contactAvatar,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Priority badge
            if (conversation.lastMessage.priority == Priority.URGENT) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            } else if (conversation.lastMessage.priority == Priority.HIGH) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF9800))
                )
            }
        }
        
        // Message content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (conversation.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = conversation.lastMessage.getFormattedTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            // AI Summary or message preview
            if (showAISummary && conversation.aiSummary != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = conversation.aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = conversation.getPreviewText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation.unreadCount > 0) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Contextual indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tone indicator
                ToneChip(conversation.lastMessage.tone.name)
                
                // Intent indicator
                IntentChip(conversation.lastMessage.intent.name)
            }
        }
        
        // Unread badge
        if (conversation.unreadCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    conversation.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun ToneChip(tone: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = when (tone) {
            "URGENT" -> MaterialTheme.colorScheme.errorContainer
            "FRIENDLY" -> Color(0xFFE8F5E9)
            "FORMAL" -> Color(0xFFE3F2FD)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = tone,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = when (tone) {
                "URGENT" -> MaterialTheme.colorScheme.onErrorContainer
                "FRIENDLY" -> Color(0xFF2E7D32)
                "FORMAL" -> Color(0xFF1565C0)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun IntentChip(intent: String) {
    val icon = when (intent) {
        "QUESTION" -> Icons.Default.Help
        "REQUEST" -> Icons.Default.Assignment
        "SCHEDULING" -> Icons.Default.Schedule
        "URGENT" -> Icons.Default.Warning
        else -> Icons.Default.Info
    }
    
    Icon(
        icon,
        contentDescription = intent,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
