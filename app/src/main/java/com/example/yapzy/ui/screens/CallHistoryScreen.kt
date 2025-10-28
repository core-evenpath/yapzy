package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.phone.PhoneManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class CallType {
    INCOMING, OUTGOING, MISSED
}

enum class CallFilter {
    ALL, MISSED, CONTACTS, NON_SPAM, SPAM
}

data class CallLogEntry(
    val id: String,
    val phoneNumber: String,
    val contactName: String?,
    val callType: CallType,
    val timestamp: LocalDateTime,
    val duration: Int, // in seconds
    val location: String? = null
) {
    fun getFormattedTime(): String {
        val now = LocalDateTime.now()
        val daysDiff = ChronoUnit.DAYS.between(timestamp.toLocalDate(), now.toLocalDate())

        return when {
            daysDiff == 0L -> timestamp.format(DateTimeFormatter.ofPattern("h:mm a"))
            daysDiff == 1L -> "Yesterday"
            daysDiff < 7L -> timestamp.format(DateTimeFormatter.ofPattern("EEEE"))
            timestamp.year == now.year -> timestamp.format(DateTimeFormatter.ofPattern("MMM dd"))
            else -> timestamp.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        }
    }

    fun getFormattedDuration(): String {
        if (duration == 0) return ""
        val minutes = duration / 60
        val seconds = duration % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }

    fun getCallTypeText(): String {
        return when (callType) {
            CallType.INCOMING -> "Incoming"
            CallType.OUTGOING -> "Outgoing"
            CallType.MISSED -> "Missed"
        }
    }

    fun getTimeSection(): String {
        val now = LocalDateTime.now()
        val daysDiff = ChronoUnit.DAYS.between(timestamp.toLocalDate(), now.toLocalDate())

        return when {
            daysDiff == 0L -> "Today"
            daysDiff == 1L -> "Yesterday"
            daysDiff < 7L -> timestamp.format(DateTimeFormatter.ofPattern("EEEE"))
            else -> timestamp.format(DateTimeFormatter.ofPattern("MMMM dd"))
        }
    }
}

data class FavoriteContact(
    val id: String,
    val name: String,
    val initials: String,
    val phoneNumber: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onCallClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val phoneManager = remember { PhoneManager(context) }
    val contactsManager = remember { ContactsManager(context) }

    var selectedFilter by remember { mutableStateOf(CallFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Sample call history data
    val allCalls = remember { CallHistoryData.sampleCalls }

    // Filter calls based on selected filter
    val filteredCalls = remember(selectedFilter, allCalls) {
        when (selectedFilter) {
            CallFilter.ALL -> allCalls
            CallFilter.MISSED -> allCalls.filter { it.callType == CallType.MISSED }
            CallFilter.CONTACTS -> allCalls.filter { it.contactName != null }
            CallFilter.NON_SPAM -> allCalls // Filter out spam
            CallFilter.SPAM -> emptyList() // Show spam calls
        }
    }

    // Get favorite contacts
    val favoriteContacts = remember {
        FavoriteContactsData.favorites
    }

    // Group calls by time section
    val groupedCalls = remember(filteredCalls) {
        filteredCalls.groupBy { it.getTimeSection() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            isActive = isSearchActive,
            onActiveChange = { isSearchActive = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Filter Tabs
        FilterTabRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Favorites Section (only show in ALL filter)
            if (selectedFilter == CallFilter.ALL && !isSearchActive) {
                item {
                    FavoritesSection(
                        favorites = favoriteContacts,
                        onFavoriteClick = { contact ->
                            phoneManager.makeCall(contact.phoneNumber)
                        }
                    )
                }
            }

            // Grouped Call History
            groupedCalls.forEach { (section, calls) ->
                item {
                    Text(
                        text = section,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(calls) { call ->
                    CallHistoryItem(
                        call = call,
                        onClick = { onCallClick(call.phoneNumber) },
                        onCallClick = { phoneManager.makeCall(call.phoneNumber) }
                    )
                }
            }

            // Empty state
            if (filteredCalls.isEmpty()) {
                item {
                    EmptyCallHistoryState(filter = selectedFilter)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onActiveChange(false) },
        active = isActive,
        onActiveChange = onActiveChange,
        modifier = modifier,
        placeholder = { Text("Search contacts") },
        leadingIcon = {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        },
        trailingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        shape = RoundedCornerShape(28.dp),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        // Search results would go here
        Text(
            "Search results...",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun FilterTabRow(
    selectedFilter: CallFilter,
    onFilterSelected: (CallFilter) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedFilter.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp,
        divider = {} // Remove default divider
    ) {
        CallFilter.values().forEach { filter ->
            Tab(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                text = {
                    Text(
                        text = filter.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
fun FavoritesSection(
    favorites: List<FavoriteContact>,
    onFavoriteClick: (FavoriteContact) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { /* View all favorites */ }) {
                Text("View all", color = MaterialTheme.colorScheme.primary)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(favorites) { favorite ->
                FavoriteContactItem(
                    contact = favorite,
                    onClick = { onFavoriteClick(favorite) }
                )
            }
        }
    }
}

@Composable
fun FavoriteContactItem(
    contact: FavoriteContact,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(70.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CallHistoryItem(
    call: CallLogEntry,
    onClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar/Call Type Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    when (call.callType) {
                        CallType.INCOMING -> Color(0xFFE8F5E9)
                        CallType.OUTGOING -> Color(0xFFE3F2FD)
                        CallType.MISSED -> Color(0xFFFFEBEE)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (call.contactName != null) {
                Text(
                    text = call.contactName.first().toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (call.callType) {
                        CallType.INCOMING -> Color(0xFF2E7D32)
                        CallType.OUTGOING -> Color(0xFF1565C0)
                        CallType.MISSED -> Color(0xFFC62828)
                    }
                )
            } else {
                Icon(
                    when (call.callType) {
                        CallType.INCOMING -> Icons.Default.CallReceived
                        CallType.OUTGOING -> Icons.Default.CallMade
                        CallType.MISSED -> Icons.Default.CallMissed
                    },
                    contentDescription = call.callType.name,
                    tint = when (call.callType) {
                        CallType.INCOMING -> Color(0xFF4CAF50)
                        CallType.OUTGOING -> Color(0xFF2196F3)
                        CallType.MISSED -> Color(0xFFF44336)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Contact info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = call.contactName ?: call.phoneNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (call.callType == CallType.MISSED) FontWeight.Bold else FontWeight.Normal,
                color = if (call.callType == CallType.MISSED)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (call.callType) {
                        CallType.INCOMING -> Icons.Outlined.CallReceived
                        CallType.OUTGOING -> Icons.Outlined.CallMade
                        CallType.MISSED -> Icons.Outlined.CallMissed
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = buildString {
                        if (call.contactName != null) {
                            append("Mobile")
                        } else {
                            append(call.getCallTypeText())
                        }
                        if (call.location != null) {
                            append(" • ${call.location}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Time and call action
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = call.getFormattedTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = "Call",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyCallHistoryState(filter: CallFilter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                when (filter) {
                    CallFilter.MISSED -> Icons.Outlined.CallMissed
                    CallFilter.SPAM -> Icons.Outlined.Block
                    else -> Icons.Outlined.Phone
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = when (filter) {
                    CallFilter.MISSED -> "No missed calls"
                    CallFilter.SPAM -> "No spam calls"
                    CallFilter.CONTACTS -> "No calls from contacts"
                    else -> "No call history"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

object CallHistoryData {
    private val now = LocalDateTime.now()

    val sampleCalls = listOf(
        CallLogEntry(
            id = "1",
            phoneNumber = "+1 (555) 123-4567",
            contactName = "Michelle So",
            callType = CallType.INCOMING,
            timestamp = now.minusHours(2),
            duration = 325
        ),
        CallLogEntry(
            id = "2",
            phoneNumber = "+1 (555) 987-6543",
            contactName = "Stella Han",
            callType = CallType.OUTGOING,
            timestamp = now.minusHours(5),
            duration = 1842
        ),
        CallLogEntry(
            id = "3",
            phoneNumber = "(555) 483-5843",
            contactName = null,
            callType = CallType.OUTGOING,
            timestamp = now.minusDays(1).minusHours(5),
            duration = 420
        ),
        CallLogEntry(
            id = "4",
            phoneNumber = "+1 (555) 246-8135",
            contactName = "Monica",
            callType = CallType.INCOMING,
            timestamp = now.minusDays(1).minusHours(17).minusMinutes(30),
            duration = 156
        ),
        CallLogEntry(
            id = "5",
            phoneNumber = "+1 (800) 123-4567",
            contactName = "World Shipping",
            callType = CallType.INCOMING,
            timestamp = now.minusDays(1).minusHours(16).minusMinutes(30),
            duration = 2145,
            location = "San Francisco"
        ),
        CallLogEntry(
            id = "6",
            phoneNumber = "+1 (555) 321-7654",
            contactName = null,
            callType = CallType.MISSED,
            timestamp = now.minusDays(3),
            duration = 0
        ),
        CallLogEntry(
            id = "7",
            phoneNumber = "+1 (555) 111-2222",
            contactName = "Alex Kim",
            callType = CallType.OUTGOING,
            timestamp = now.minusDays(4),
            duration = 456
        )
    )
}

object FavoriteContactsData {
    val favorites = listOf(
        FavoriteContact(
            id = "1",
            name = "Alessia",
            initials = "A",
            phoneNumber = "+1 (555) 111-1111"
        ),
        FavoriteContact(
            id = "2",
            name = "Andrew",
            initials = "A",
            phoneNumber = "+1 (555) 222-2222"
        ),
        FavoriteContact(
            id = "3",
            name = "Michelle",
            initials = "M",
            phoneNumber = "+1 (555) 333-3333"
        ),
        FavoriteContact(
            id = "4",
            name = "Ben",
            initials = "B",
            phoneNumber = "+1 (555) 444-4444"
        ),
        FavoriteContact(
            id = "5",
            name = "Takara",
            initials = "T",
            phoneNumber = "+1 (555) 555-5555"
        )
    )
}