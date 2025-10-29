package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.yapzy.phone.Contact
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.phone.PhoneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onContactClick: (Contact) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val contactsManager = remember { ContactsManager(context) }
    val phoneManager = remember { PhoneManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    // Load contacts on lifecycle start
    LaunchedEffect(lifecycleOwner, showFavoritesOnly) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loadContacts(contactsManager, showFavoritesOnly) { result, error ->
                contacts = result
                errorMessage = error
                isLoading = false
            }
        }
    }

    // Filter contacts based on search
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isEmpty()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Group contacts by first letter
    val groupedContacts = remember(filteredContacts) {
        filteredContacts.groupBy {
            it.name.firstOrNull()?.uppercaseChar() ?: '#'
        }.toSortedMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Contacts",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                        Icon(
                            imageVector = if (showFavoritesOnly) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = "Toggle Favorites",
                            tint = if (showFavoritesOnly)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                loadContacts(contactsManager, showFavoritesOnly) { result, error ->
                                    contacts = result
                                    errorMessage = error
                                    isLoading = false
                                }
                            }
                        }
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
                onClick = { /* Add new contact */ },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.PersonAdd, "Add Contact")
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
                placeholder = { Text("Search contacts") },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                isLoading -> {
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
                                "Loading contacts...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                filteredContacts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Contacts,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = if (searchQuery.isEmpty()) {
                                    if (showFavoritesOnly) "No favorite contacts" else "No contacts"
                                } else {
                                    "No contacts found"
                                },
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
                        groupedContacts.forEach { (letter, contactsInGroup) ->
                            item {
                                Text(
                                    text = letter.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            items(contactsInGroup, key = { it.id }) { contact ->
                                ContactItem(
                                    contact = contact,
                                    onCallClick = {
                                        scope.launch {
                                            try {
                                                phoneManager.makeCall(contact.phoneNumber)
                                            } catch (e: Exception) {
                                                errorMessage = "Failed to make call: ${e.message}"
                                            }
                                        }
                                    },
                                    onMessageClick = {
                                        // Navigate to messages - this would need navigation integration
                                    },
                                    onContactClick = {
                                        onContactClick(contact)
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
}

private suspend fun loadContacts(
    contactsManager: ContactsManager,
    favoritesOnly: Boolean,
    onResult: (List<Contact>, String?) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val contacts = if (favoritesOnly) {
            contactsManager.getFavoriteContacts()
        } else {
            contactsManager.getAllContacts()
        }
        withContext(Dispatchers.Main) {
            onResult(contacts, null)
        }
    } catch (e: SecurityException) {
        withContext(Dispatchers.Main) {
            onResult(emptyList(), "Permission denied. Please grant contacts permission.")
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onResult(emptyList(), "Error loading contacts: ${e.message}")
        }
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit,
    onContactClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContactClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar - show photo if available, otherwise show initials
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (contact.photoUri != null && contact.photoUri.isNotEmpty()) {
                // Display actual contact photo with proper error handling
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(contact.photoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Contact photo for ${contact.name}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    error = {
                        // Display initials on error
                        Text(
                            text = contact.name.split(" ")
                                .mapNotNull { it.firstOrNull() }
                                .take(2)
                                .joinToString("")
                                .uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                )
            } else {
                // Display initials when no photo URI
                Text(
                    text = contact.name.split(" ")
                        .mapNotNull { it.firstOrNull() }
                        .take(2)
                        .joinToString("")
                        .uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Contact Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (contact.isFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = contact.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onMessageClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Message,
                    contentDescription = "Message",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}