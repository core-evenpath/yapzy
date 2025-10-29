package com.example.yapzy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.yapzy.phone.CallManager
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.ui.theme.YapzyTheme
import android.telecom.Call
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class InCallActivity : ComponentActivity() {

    private var call: Call? = null
    private var callCallback: Call.Callback? = null
    private var callStartTime: Long = 0
    private var callDuration: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            // Contact picked - could be used for conference calling
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acquire proximity wake lock to automatically turn off screen when phone is near ear
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Yapzy::InCallWakeLock"
        )
        wakeLock?.acquire(10*60*1000L) // 10 minutes max

        // Set window flags for incoming calls to show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Keep screen on when not in proximity sensor range
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
                        call?.answer(0)
                    },
                    onDeclineCall = {
                        call?.reject(false, null)
                        showPostCallScreen(declined = true)
                        finish()
                    },
                    onEndCall = {
                        call?.disconnect()
                        finish()
                    },
                    onMuteToggle = { muted ->
                        CallManager.setMuted(muted)
                    },
                    onSpeakerToggle = { speaker ->
                        CallManager.setSpeaker(speaker)
                    },
                    onOpenContacts = {
                        contactPickerLauncher.launch(null)
                    },
                    onDurationUpdate = { duration ->
                        callDuration = duration
                    }
                )
            }
        }

        // Listen for call state changes
        callCallback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                when (state) {
                    Call.STATE_DISCONNECTED -> {
                        finish()
                    }
                }
            }
        }

        call?.registerCallback(callCallback!!)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle returning to the activity from notification
        setIntent(intent)
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

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
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
    var showMessageDialog by remember { mutableStateOf(false) }

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
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isIncoming) {
                            listOf(
                                Color(0xFF1B5E20),
                                Color(0xFF2E7D32),
                                Color(0xFF388E3C)
                            )
                        } else {
                            listOf(
                                Color(0xFF1A237E),
                                Color(0xFF283593),
                                Color(0xFF3949AB)
                            )
                        }
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - Call type indicator
            if (isIncoming) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "Incoming Call",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Middle section with contact info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Avatar with real photo or animated initials
                if (isIncoming) {
                    AnimatedContactAvatar(
                        contact = contact,
                        initials = initials,
                        contactsManager = contactsManager
                    )
                } else {
                    StaticContactAvatar(
                        contact = contact,
                        initials = initials,
                        contactsManager = contactsManager
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Contact name
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Call state/duration
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Bottom controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Control buttons (mute, speaker, etc.) - only for active calls
                if (!isIncoming) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        // Mute button
                        CallControlButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "Mute",
                            isActive = isMuted,
                            onClick = {
                                isMuted = !isMuted
                                onMuteToggle(isMuted)
                            }
                        )

                        // Dialpad button
                        CallControlButton(
                            icon = Icons.Default.Dialpad,
                            label = "Dialpad",
                            onClick = { showDialpad = true }
                        )

                        // Speaker button
                        CallControlButton(
                            icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            label = "Speaker",
                            isActive = isSpeaker,
                            onClick = {
                                isSpeaker = !isSpeaker
                                onSpeakerToggle(isSpeaker)
                            }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        // Video button (disabled for now)
                        CallControlButton(
                            icon = Icons.Default.Videocam,
                            label = "Video",
                            onClick = { showVideoNotSupported = true }
                        )

                        // Add call button
                        CallControlButton(
                            icon = Icons.Default.PersonAdd,
                            label = "Add",
                            onClick = { showAddCallDialog = true }
                        )

                        // Message button
                        CallControlButton(
                            icon = Icons.AutoMirrored.Filled.Message,
                            label = "Message",
                            onClick = { showMessageDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Answer/Decline or End call button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isIncoming) {
                        // Decline button
                        FloatingActionButton(
                            onClick = onDeclineCall,
                            containerColor = Color.Red,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Accept button
                        FloatingActionButton(
                            onClick = onAcceptCall,
                            containerColor = Color.Green,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        // End call button
                        FloatingActionButton(
                            onClick = onEndCall,
                            containerColor = Color.Red,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showDialpad) {
        DialpadDialog(
            onDismiss = { showDialpad = false },
            onDigitClick = { digit ->
                // Send DTMF tone
                call.playDtmfTone(digit.first())
            }
        )
    }

    if (showMessageDialog) {
        QuickMessageDialog(
            phoneNumber = phoneNumber,
            onDismiss = { showMessageDialog = false },
            onMessageSent = { showMessageDialog = false }
        )
    }

    if (showAddCallDialog) {
        AlertDialog(
            onDismissRequest = { showAddCallDialog = false },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            title = { Text("Add Call") },
            text = { Text("Conference calling is not yet available.") },
            confirmButton = {
                TextButton(onClick = { showAddCallDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showVideoNotSupported) {
        AlertDialog(
            onDismissRequest = { showVideoNotSupported = false },
            icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
            title = { Text("Video Calling") },
            text = { Text("Video calling is not available for this call.") },
            confirmButton = {
                TextButton(onClick = { showVideoNotSupported = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun AnimatedContactAvatar(
    contact: com.example.yapzy.phone.Contact?,
    initials: String,
    contactsManager: ContactsManager
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_scale"
    )

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (contact?.photoUri != null && contact.photoUri.isNotEmpty()) {
            // Show contact photo with proper error handling
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(contact.photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Contact photo",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    // Show initials on error
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            )
        } else {
            // Show initials
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun StaticContactAvatar(
    contact: com.example.yapzy.phone.Contact?,
    initials: String,
    contactsManager: ContactsManager
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (contact?.photoUri != null && contact.photoUri.isNotEmpty()) {
            // Show contact photo with proper error handling
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(contact.photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Contact photo",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    // Show initials on error
                    Text(
                        text = initials,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            )
        } else {
            // Show initials
            Text(
                text = initials,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun QuickMessageDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onMessageSent: () -> Unit
) {
    val quickReplies = listOf(
        "Can't talk now. Call you later.",
        "I'm busy right now.",
        "I'll call you back.",
        "What's up?",
        "Send me a text message."
    )
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
        title = { Text("Quick Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send a quick message:")
                quickReplies.forEach { message ->
                    OutlinedButton(
                        onClick = {
                            try {
                                val smsManager = SmsManager.getDefault()
                                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                                onMessageSent()
                            } catch (e: Exception) {
                                // Handle error
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(message, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DialpadDialog(
    onDismiss: () -> Unit,
    onDigitClick: (String) -> Unit
) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dialpad") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                digits.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { digit ->
                            OutlinedButton(
                                onClick = { onDigitClick(digit) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}