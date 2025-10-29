
package com.example.yapzy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.phone.CallManager
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.ui.theme.YapzyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class InCallActivity : ComponentActivity() {

    private var call: Call? = null
    private var callCallback: Call.Callback? = null
    private var callStartTime: Long = 0
    private var callDuration: Int = 0

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            // Contact picked - could be used for conference calling
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        call = CallManager.currentCall
        callStartTime = System.currentTimeMillis()

        if (call == null) {
            finish()
            return
        }

        setContent {
            YapzyTheme {
                InCallScreen(
                    call = call!!,
                    context = this,
                    onAcceptCall = {
                        call?.answer(0) // 0 = AUDIO
                    },
                    onDeclineCall = {
                        call?.reject(false, null)
                        showPostCallScreen(declined = true)
                        finish()
                    },
                    onEndCall = {
                        call?.disconnect()
                        showPostCallScreen(declined = false)
                        finish()
                    },
                    onMuteToggle = { muted ->
                        CallManager.setMuted(muted)
                    },
                    onSpeakerToggle = { speaker ->
                        CallManager.setSpeaker(speaker)
                    },
                    onOpenContacts = {
                        try {
                            contactPickerLauncher.launch(null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onDurationUpdate = { duration ->
                        callDuration = duration
                    }
                )
            }
        }

        callCallback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    showPostCallScreen(declined = false)
                    finish()
                }
            }
        }
        call?.registerCallback(callCallback!!)
    }

    private fun showPostCallScreen(declined: Boolean) {
        val phoneNumber = call?.details?.handle?.schemeSpecificPart ?: return
        val callType = when {
            declined -> "Declined"
            call?.details?.callDirection == Call.Details.DIRECTION_INCOMING -> "Incoming"
            else -> "Outgoing"
        }

        val intent = PostCallActivity.createIntent(
            context = this,
            phoneNumber = phoneNumber,
            duration = callDuration,
            callType = callType,
            timestamp = callStartTime
        )
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        callCallback?.let { callback ->
            call?.unregisterCallback(callback)
        }
    }
}

@Composable
fun InCallScreen(
    call: Call,
    context: Context,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onEndCall: () -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit,
    onOpenContacts: () -> Unit,
    onDurationUpdate: (Int) -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0) }
    var callState by remember { mutableStateOf(call.state) }
    var showDialpad by remember { mutableStateOf(false) }
    var showAddCallDialog by remember { mutableStateOf(false) }
    var showVideoNotSupported by remember { mutableStateOf(false) }

    val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
    val contactsManager = remember { ContactsManager(context) }
    val contact = remember { contactsManager.getContactByNumber(phoneNumber) }
    val displayName = contact?.name ?: phoneNumber
    val initials = if (contact != null) {
        contact.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")
    } else {
        phoneNumber.take(2)
    }

    val stateText = when (callState) {
        Call.STATE_DIALING -> "Calling..."
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_ACTIVE -> formatDuration(callDuration)
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_CONNECTING -> "Connecting..."
        else -> "Call"
    }

    val isIncoming = callState == Call.STATE_RINGING
    val isActive = callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING

    // Timer for active calls
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (isActive) {
                delay(1000)
                callDuration++
                onDurationUpdate(callDuration)
            }
        }
    }

    // Listen for call state changes
    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                callState = state
            }
        }
        call.registerCallback(callback)
        onDispose {
            call.unregisterCallback(callback)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isIncoming) Color(0xFF1B5E20) else Color(0xFF2C3E50)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section with contact info
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(if (isIncoming) Color(0xFF2E7D32) else Color(0xFF34495E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = displayName,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stateText,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    if (contact == null && phoneNumber != "Unknown") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = phoneNumber,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Call controls
                if (isIncoming) {
                    // Incoming call controls
                    IncomingCallControls(
                        onAccept = onAcceptCall,
                        onDecline = onDeclineCall
                    )
                } else {
                    // Active call controls
                    ActiveCallControls(
                        isMuted = isMuted,
                        isSpeaker = isSpeaker,
                        onMuteToggle = {
                            isMuted = !isMuted
                            onMuteToggle(isMuted)
                        },
                        onSpeakerToggle = {
                            isSpeaker = !isSpeaker
                            onSpeakerToggle(isSpeaker)
                        },
                        onDialpadClick = {
                            showDialpad = true
                        },
                        onAddCallClick = {
                            showAddCallDialog = true
                        },
                        onVideoClick = {
                            showVideoNotSupported = true
                        },
                        onContactsClick = {
                            onOpenContacts()
                        },
                        onEndCall = onEndCall
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Dialpad Overlay
        AnimatedVisibility(
            visible = showDialpad,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            DialpadOverlay(
                onDismiss = { showDialpad = false },
                onDigitClick = { digit ->
                    // Send DTMF tone
                    call.playDtmfTone(digit[0])
                    call.stopDtmfTone()
                }
            )
        }
    }

    // Add Call Dialog
    if (showAddCallDialog) {
        AlertDialog(
            onDismissRequest = { showAddCallDialog = false },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            title = { Text("Add Call") },
            text = { Text("Conference calling is not yet implemented. You can add this contact to a conference call in a future update.") },
            confirmButton = {
                TextButton(onClick = { showAddCallDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Video Not Supported Dialog
    if (showVideoNotSupported) {
        AlertDialog(
            onDismissRequest = { showVideoNotSupported = false },
            icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
            title = { Text("Video Calling") },
            text = { Text("Video calling is not available for this call. Please use a video calling app like WhatsApp, FaceTime, or Google Meet.") },
            confirmButton = {
                TextButton(onClick = { showVideoNotSupported = false }) {
                    Text("OK")
                }
            }
        )
    }
}


@Composable
fun DialpadOverlay(
    onDismiss: () -> Unit,
    onDigitClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF2C3E50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Dialpad",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dialpad grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Row 1: 1, 2, 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DialpadButton("1", "", onDigitClick)
                    DialpadButton("2", "ABC", onDigitClick)
                    DialpadButton("3", "DEF", onDigitClick)
                }

                // Row 2: 4, 5, 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DialpadButton("4", "GHI", onDigitClick)
                    DialpadButton("5", "JKL", onDigitClick)
                    DialpadButton("6", "MNO", onDigitClick)
                }

                // Row 3: 7, 8, 9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DialpadButton("7", "PQRS", onDigitClick)
                    DialpadButton("8", "TUV", onDigitClick)
                    DialpadButton("9", "WXYZ", onDigitClick)
                }

                // Row 4: *, 0, #
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DialpadButton("*", "", onDigitClick)
                    DialpadButton("0", "+", onDigitClick)
                    DialpadButton("#", "", onDigitClick)
                }
            }
        }
    }
}

@Composable
fun DialpadButton(
    number: String,
    letters: String,
    onClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color(0xFF34495E))
            .clickable { onClick(number) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun IncomingCallControls(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text(
            text = "Incoming call",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Decline button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = onDecline,
                    modifier = Modifier.size(72.dp),
                    containerColor = Color(0xFFE74C3C),
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Decline",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            // Accept button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = onAccept,
                    modifier = Modifier.size(72.dp),
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Accept",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Accept",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun ActiveCallControls(
    isMuted: Boolean,
    isSpeaker: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onDialpadClick: () -> Unit,
    onAddCallClick: () -> Unit,
    onVideoClick: () -> Unit,
    onContactsClick: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            CallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = "mute",
                isActive = isMuted,
                onClick = onMuteToggle
            )

            CallControlButton(
                icon = Icons.Default.Dialpad,
                label = "keypad",
                onClick = onDialpadClick
            )

            CallControlButton(
                icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label = "speaker",
                isActive = isSpeaker,
                onClick = onSpeakerToggle
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            CallControlButton(
                icon = Icons.Default.Add,
                label = "add call",
                onClick = onAddCallClick
            )

            CallControlButton(
                icon = Icons.Default.Videocam,
                label = "video",
                onClick = onVideoClick
            )

            CallControlButton(
                icon = Icons.Default.Contacts,
                label = "contacts",
                onClick = onContactsClick
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FloatingActionButton(
            onClick = onEndCall,
            modifier = Modifier.size(72.dp),
            containerColor = Color(0xFFE74C3C),
            contentColor = Color.White
        ) {
            Icon(
                Icons.Default.CallEnd,
                contentDescription = "End call",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            containerColor = if (isActive) Color(0xFF3498DB) else Color(0xFF34495E),
            contentColor = Color.White
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}