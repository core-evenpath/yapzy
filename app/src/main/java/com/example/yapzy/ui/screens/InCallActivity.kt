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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

        // Acquire wake lock to keep screen responsive
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
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

        // Keep screen on and ensure touch responsiveness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Disable proximity sensor effects on screen
        window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)

        // Ensure full brightness for better visibility and touch response
        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams

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

                Spacer(modifier = Modifier.height(32.dp))

                // Contact name
                Text(
                    text = displayName,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Call status
                Text(
                    text = stateText,
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Normal
                )

                // Phone number if not in contacts
                if (contact == null && phoneNumber != "Unknown") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = phoneNumber,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // Bottom section - Controls
            if (isIncoming) {
                ModernIncomingCallControls(
                    onAccept = onAcceptCall,
                    onDecline = onDeclineCall,
                    onMessage = { showMessageDialog = true }
                )
            } else {
                ModernActiveCallControls(
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
                    onDialpadClick = { showDialpad = true },
                    onAddCallClick = { showAddCallDialog = true },
                    onVideoClick = { showVideoNotSupported = true },
                    onContactsClick = { onOpenContacts() },
                    onEndCall = onEndCall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
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
                    call.playDtmfTone(digit[0])
                    call.stopDtmfTone()
                }
            )
        }
    }

    // Quick Message Dialog
    if (showMessageDialog) {
        QuickMessageDialog(
            phoneNumber = phoneNumber,
            onDismiss = { showMessageDialog = false },
            onMessageSent = {
                showMessageDialog = false
                // Optionally decline the call after sending message
                onDeclineCall()
            }
        )
    }

    // Other Dialogs
    if (showAddCallDialog) {
        AlertDialog(
            onDismissRequest = { showAddCallDialog = false },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            title = { Text("Add Call") },
            text = { Text("Conference calling is not yet implemented.") },
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
        if (contact?.photoUri != null) {
            // Show contact photo
            AsyncImage(
                model = contact.photoUri,
                contentDescription = "Contact photo",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
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
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (contact?.photoUri != null) {
            // Show contact photo
            AsyncImage(
                model = contact.photoUri,
                contentDescription = "Contact photo",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
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

    var customMessage by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Send quick message",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!showCustomInput) {
                    quickReplies.forEach { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sendSMS(phoneNumber, message)
                                    onMessageSent()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Custom message")
                    }
                } else {
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type your message") },
                        maxLines = 3
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showCustomInput) {
                TextButton(
                    onClick = {
                        if (customMessage.isNotBlank()) {
                            sendSMS(phoneNumber, customMessage)
                            onMessageSent()
                        } else {
                            errorMessage = "Please enter a message"
                        }
                    }
                ) {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun sendSMS(phoneNumber: String, message: String) {
    try {
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun ModernIncomingCallControls(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = onDecline,
                    modifier = Modifier.size(80.dp),
                    containerColor = Color(0xFFDC3545),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
                Text(
                    text = "Decline",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            // Message button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = onMessage,
                    modifier = Modifier.size(64.dp),
                    containerColor = Color.White.copy(alpha = 0.2f),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = "Message",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
                Text(
                    text = "Message",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Normal
                )
            }

            // Accept button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = onAccept,
                    modifier = Modifier.size(80.dp),
                    containerColor = Color(0xFF28A745),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Accept",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
                Text(
                    text = "Accept",
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ModernActiveCallControls(
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
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // First row of controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModernCallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = "Mute",
                isActive = isMuted,
                onClick = onMuteToggle
            )

            ModernCallControlButton(
                icon = Icons.Default.Dialpad,
                label = "Keypad",
                onClick = onDialpadClick
            )

            ModernCallControlButton(
                icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label = "Speaker",
                isActive = isSpeaker,
                onClick = onSpeakerToggle
            )
        }

        // Second row of controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModernCallControlButton(
                icon = Icons.Default.Add,
                label = "Add",
                onClick = onAddCallClick
            )

            ModernCallControlButton(
                icon = Icons.Default.Videocam,
                label = "Video",
                onClick = onVideoClick
            )

            ModernCallControlButton(
                icon = Icons.Default.Contacts,
                label = "Contacts",
                onClick = onContactsClick
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // End call button
        FloatingActionButton(
            onClick = onEndCall,
            modifier = Modifier.size(76.dp),
            containerColor = Color(0xFFDC3545),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                Icons.Default.CallEnd,
                contentDescription = "End call",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
fun ModernCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            color = if (isActive) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
            tonalElevation = if (isActive) 4.dp else 0.dp
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
        }
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Normal
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
        color = Color(0xFF1A237E)
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
                listOf(
                    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                    listOf("*" to "", "0" to "+", "#" to "")
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { (number, letters) ->
                            DialpadButton(number, letters, onDigitClick)
                        }
                    }
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
            .background(Color.White.copy(alpha = 0.15f))
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

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}