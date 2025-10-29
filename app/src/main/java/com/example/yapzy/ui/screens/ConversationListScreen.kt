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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.yapzy.data.MessageCache
import com.example.yapzy.models.Conversation
import com.example.yapzy.phone.SMSManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val smsManager = remember { SMSManager(context) }
    val messageCache = remember { MessageCache(context) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }

    // Load from cache immediately, then refresh in background
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Load from cache first
            val cached = messageCache.getConversations()
            if (cached != null) {
                conversations = cached
                isLoading = false
            } else {
                isLoading = true
            }

            // Refresh from source in background
            isRefreshing = true
            loadConversations(
                smsManager = smsManager,
                messageCache = messageCache
            ) { loadedConversations, error ->
                conversations = loadedConversations
                errorMessage = error
                isLoading = false
                isRefreshing = false
            }
        }
    }

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
                title = { Text("Messages") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                loadConversations(
                                    smsManager = smsManager,
                                    messageCache = messageCache
                                ) { loadedConversations, error ->
                                    conversations = loadedConversations
                                    errorMessage = error
                                    isRefreshing = false
                                }
                            }
                        },
                        enabled = !isRefreshing
                    ) {
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search messages") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )

            // Error message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when {
                isLoading && conversations.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Loading messages...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                filteredConversations.isEmpty() -> {
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
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredConversations, key = { it.id }) { conversation ->
                            ConversationItem(
                                conversation = conversation,
                                onClick = {
                                    onConversationClick(conversation.id)
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadConversations(
    smsManager: SMSManager,
    messageCache: MessageCache,
    onResult: (List<Conversation>, String?) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val conversations = smsManager.getConversations()
        messageCache.saveConversations(conversations)
        withContext(Dispatchers.Main) {
            onResult(conversations, null)
        }
    } catch (e: SecurityException) {
        withContext(Dispatchers.Main) {
            onResult(emptyList(), "Permission denied. Please grant SMS permissions.")
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onResult(emptyList(), "Error loading messages: ${e.message}")
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar with photo loading support
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (conversation.photoUri != null && conversation.photoUri.isNotEmpty()) {
                // Load actual contact photo
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(conversation.photoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Contact photo for ${conversation.contactName}",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        // Show loading indicator while image loads
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    error = {
                        // Show initials if image fails to load
                        Text(
                            text = conversation.contactAvatar,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            } else {
                // Show initials when no photo available
                Text(
                    text = conversation.contactAvatar,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
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
                Text(
                    text = conversation.lastMessage.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = conversation.getPreviewText(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (conversation.unreadCount > 0)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal
            )
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