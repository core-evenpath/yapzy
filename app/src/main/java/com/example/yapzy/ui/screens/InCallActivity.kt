package com.example.yapzy.ui.screens

import android.content.Context
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        call = CallManager.currentCall

        if (call == null) {
            finish()
            return
        }

        setContent {
            YapzyTheme {
                InCallScreen(
                    call = call!!,
                    context = this,
                    onEndCall = {
                        call?.disconnect()
                        finish()
                    },
                    onMuteToggle = { muted ->
                        CallManager.setMuted(muted)
                    },
                    onSpeakerToggle = { speaker ->
                        CallManager.setSpeaker(speaker)
                    }
                )
            }
        }

        // Monitor call state
        call?.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        call?.unregisterCallback(object : Call.Callback() {})
    }
}

@Composable
fun InCallScreen(
    call: Call,
    context: Context,
    onEndCall: () -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0) }
    var callState by remember { mutableStateOf(call.state) }

    // Get caller details
    val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
    val contactsManager = remember { ContactsManager(context) }
    val contact = remember { contactsManager.getContactByNumber(phoneNumber) }
    val displayName = contact?.name ?: phoneNumber
    val initials = if (contact != null) {
        contact.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")
    } else {
        phoneNumber.take(2)
    }

    // Call state text
    val stateText = when (callState) {
        Call.STATE_DIALING -> "Calling..."
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_ACTIVE -> formatDuration(callDuration)
        Call.STATE_HOLDING -> "On hold"
        else -> "Connecting..."
    }

    // Timer for active calls
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (isActive) {
                delay(1000)
                callDuration++
            }
        }
    }

    // Monitor call state changes
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF2C3E50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - Caller info
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34495E)),
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

                // Caller name
                Text(
                    text = displayName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Call state or duration
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

            // Middle section - Call controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // First row of controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Mute button
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = "mute",
                        isActive = isMuted,
                        onClick = {
                            isMuted = !isMuted
                            onMuteToggle(isMuted)
                        }
                    )

                    // Keypad button
                    CallControlButton(
                        icon = Icons.Default.Dialpad,
                        label = "keypad",
                        onClick = { /* TODO: Show keypad */ }
                    )

                    // Speaker button
                    CallControlButton(
                        icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        label = "speaker",
                        isActive = isSpeaker,
                        onClick = {
                            isSpeaker = !isSpeaker
                            onSpeakerToggle(isSpeaker)
                        }
                    )
                }

                // Second row of controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Add call button
                    CallControlButton(
                        icon = Icons.Default.Add,
                        label = "add call",
                        onClick = { /* TODO: Add call */ }
                    )

                    // Video call button (placeholder)
                    CallControlButton(
                        icon = Icons.Default.Videocam,
                        label = "FaceTime",
                        onClick = { /* Not available */ }
                    )

                    // Contacts button
                    CallControlButton(
                        icon = Icons.Default.Contacts,
                        label = "contacts",
                        onClick = { /* TODO: Show contacts */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom section - End call button
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

            Spacer(modifier = Modifier.height(32.dp))
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