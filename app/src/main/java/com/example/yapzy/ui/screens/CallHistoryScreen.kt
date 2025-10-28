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
import com.example.yapzy.phone.CallLogManager
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.phone.PhoneManager
import java.text.SimpleDateFormat
import java.util.*

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
    val timestamp: Long,
    val duration: Int,
    val location: String? = null
) {
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis()
        val daysDiff = ((now - timestamp) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            daysDiff == 0 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            daysDiff == 1 -> "Yesterday"
            daysDiff < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
            else -> {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = timestamp
                val nowCalendar = Calendar.getInstance()
                nowCalendar.timeInMillis = now

                if (calendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR)) {
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
                } else {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
                }
            }
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
        val daysDiff = ((System.currentTimeMillis() - timestamp) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            daysDiff == 0 -> "Today"
            daysDiff == 1 -> "Yesterday"
            daysDiff < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
            else -> SimpleDateFormat("MMMM dd", Locale.getDefault()).format(Date(timestamp))
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
    val callLogManager = remember { CallLogManager(context) }

    var selectedFilter by remember { mutableStateOf(CallFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val allCalls = remember(refreshTrigger) {
        callLogManager.getCallLogs(limit = 200)
    }

    val filteredCalls = remember(selectedFilter, allCalls, searchQuery) {
        var calls = when (selectedFilter) {
            CallFilter.ALL -> allCalls
            CallFilter.MISSED -> allCalls.filter { it.callType == CallType.MISSED }
            CallFilter.CONTACTS -> allCalls.filter { it.contactName != null }
            CallFilter.NON_SPAM -> allCalls
            CallFilter.SPAM -> emptyList()
        }

        if (searchQuery.isNotEmpty()) {
            calls = calls.filter {
                it.contactName?.contains(searchQuery, ignoreCase = true) == true ||
                        it.phoneNumber.contains(searchQuery, ignoreCase = true)
            }
        }

        calls
    }

    val favoriteContacts = remember(refreshTrigger) {
        contactsManager.getFavoriteContacts().take(10).map {
            FavoriteContact(
                id = it.id,
                name = it.name,
                initials = it.name.split(" ").mapNotNull { word -> word.firstOrNull() }.take(2).joinToString(""),
                phoneNumber = it.phoneNumber
            )
        }
    }

    val groupedCalls = remember(filteredCalls) {
        filteredCalls.groupBy { it.getTimeSection() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            isActive = isSearchActive,
            onActiveChange = { isSearchActive = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        FilterTabRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (selectedFilter == CallFilter.ALL && !isSearchActive && favoriteContacts.isNotEmpty()) {
                    item {
                        FavoritesSection(
                            favorites = favoriteContacts,
                            onFavoriteClick = { contact ->
                                phoneManager.makeCall(contact.phoneNumber)
                            }
                        )
                    }
                }

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

                if (filteredCalls.isEmpty()) {
                    item {
                        EmptyCallHistoryState(filter = selectedFilter)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Pull down to refresh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
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
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
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
        divider = {}
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

        Column(modifier = Modifier.weight(1f)) {
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
                        val duration = call.getFormattedDuration()
                        if (duration.isNotEmpty()) {
                            append(" • $duration")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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