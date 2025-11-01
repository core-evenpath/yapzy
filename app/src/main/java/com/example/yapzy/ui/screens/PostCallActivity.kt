package com.example.yapzy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.phone.Contact
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.phone.PhoneManager
import com.example.yapzy.ui.theme.YapzyTheme
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PostCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PostCallActivity"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CALL_DURATION = "call_duration"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_CALL_TIMESTAMP = "call_timestamp"

        fun createIntent(
            context: Context,
            phoneNumber: String,
            duration: Int,
            callType: String,
            timestamp: Long
        ): Intent {
            return Intent(context, PostCallActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALL_DURATION, duration)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_CALL_TIMESTAMP, timestamp)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    // Activity-level managers
    private lateinit var contactsManager: ContactsManager
    private lateinit var phoneManager: PhoneManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        try {
            // Initialize managers at activity level
            contactsManager = ContactsManager(this)
            phoneManager = PhoneManager(this)

            val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
            val duration = intent.getIntExtra(EXTRA_CALL_DURATION, 0)
            val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "outgoing"
            val timestamp = intent.getLongExtra(EXTRA_CALL_TIMESTAMP, System.currentTimeMillis())

            setContent {
                YapzyTheme {
                    PostCallScreen(
                        phoneNumber = phoneNumber,
                        duration = duration,
                        callType = callType,
                        timestamp = timestamp,
                        contactsManager = contactsManager,
                        phoneManager = phoneManager,
                        onDismiss = { finish() },
                        onViewProfile = { contact ->
                            viewContactProfile(contact, phoneNumber)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }

    private fun viewContactProfile(contact: Contact?, phoneNumber: String) {
        try {
            val contactToShow = contact ?: Contact(
                id = phoneNumber,
                name = phoneNumber,
                phoneNumber = phoneNumber,
                isFavorite = false
            )

            val intent = Intent(this, ContactDetailsActivity::class.java).apply {
                val gson = Gson()
                val contactJson = gson.toJson(contactToShow)
                putExtra("contact_json", contactJson)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error viewing contact profile", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallScreen(
    phoneNumber: String,
    duration: Int,
    callType: String,
    timestamp: Long,
    contactsManager: ContactsManager,
    phoneManager: PhoneManager,
    onDismiss: () -> Unit,
    onViewProfile: (Contact?) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Load contact once
    val contact = remember(phoneNumber) {
        try {
            contactsManager.getContactByNumber(phoneNumber)
        } catch (e: Exception) {
            Log.e("PostCallScreen", "Error loading contact", e)
            null
        }
    }

    val displayName = contact?.name ?: phoneNumber
    val initials = if (contact != null) {
        contact.name.split(" ")
            .mapNotNull { it.firstOrNull() }
            .take(2)
            .joinToString("")
            .uppercase()
    } else {
        phoneNumber.take(2)
    }

    var notes by remember { mutableStateOf("") }
    var showNotesDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val timeAgo = remember(timestamp) {
        val diff = System.currentTimeMillis() - timestamp
        when {
            diff < 60000 -> "less than 1m ago"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }

    val formattedDuration = remember(duration) {
        if (duration > 0) {
            val minutes = duration / 60
            val seconds = duration % 60
            if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        } else {
            "Not answered"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Call ended indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Call ended $timeAgo",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Name
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Call details
                    Text(
                        text = "$callType • $formattedDuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // View Profile Button
                    Button(
                        onClick = { onViewProfile(contact) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("View profile")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phone number and location
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = phoneNumber,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (contact == null) {
                            Text(
                                text = "Unknown location",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PostCallActionButton(
                            icon = Icons.Default.Phone,
                            label = "CALL",
                            onClick = {
                                scope.launch {
                                    try {
                                        phoneManager.makeCall(phoneNumber)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Log.e("PostCallScreen", "Error making call", e)
                                        errorMessage = "Failed to make call"
                                    }
                                }
                            }
                        )

                        PostCallActionButton(
                            icon = Icons.Default.Message,
                            label = "MESSAGE",
                            onClick = {
                                // TODO: Navigate to messages
                                onDismiss()
                            }
                        )

                        PostCallActionButton(
                            icon = Icons.Default.Edit,
                            label = "NOTES",
                            onClick = {
                                showNotesDialog = true
                            }
                        )
                    }

                    // Error message
                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Notes Dialog
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            icon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            },
            title = {
                Text("Call Notes")
            },
            text = {
                Column {
                    Text(
                        "Add notes about this call with $displayName",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text("What did you discuss?") },
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotesDialog = false
                        errorMessage = "Notes saved successfully"
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PostCallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}