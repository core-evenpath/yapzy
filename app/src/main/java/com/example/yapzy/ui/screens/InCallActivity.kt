package com.example.yapzy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.yapzy.phone.CallManager
import com.example.yapzy.phone.Contact
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.ui.theme.YapzyTheme
import android.telecom.Call
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InCallActivity"
        private const val MAX_MONITOR_ATTEMPTS = 50
        private const val MONITOR_DELAY_MS = 100L
    }

    private var call: Call? = null
    private var callCallback: Call.Callback? = null
    private var callStartTime: Long = 0
    private var callDuration: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorHandler: Handler? = null
    private var monitorRunnable: Runnable? = null

    private var contactInfo: ContactInfo? = null
    private lateinit var contactsManager: ContactsManager

    private val callStateHolder = mutableStateOf(Call.STATE_CONNECTING)
    private val durationHolder = mutableIntStateOf(0)

    data class ContactInfo(
        val contact: Contact?,
        val displayName: String,
        val initials: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate - Starting InCallActivity")

        try {
            setupWindowFlags()

            contactsManager = ContactsManager(this)

            val isOutgoingCall = intent.getBooleanExtra("OUTGOING_CALL", false)
            call = CallManager.currentCall
            callStartTime = System.currentTimeMillis()

            Log.d(TAG, "onCreate - isOutgoing: $isOutgoingCall, hasCall: ${call != null}")

            if (call == null && !isOutgoingCall) {
                Log.w(TAG, "No active call and not outgoing, finishing")
                finish()
                return
            }

            loadContactInfo()
            setupCallCallback()

            if (isOutgoingCall && call == null) {
                startCallMonitoring()
            } else if (call != null) {
                callStateHolder.value = call?.state ?: Call.STATE_CONNECTING
            }

            setContent {
                YapzyTheme {
                    val callState by callStateHolder
                    val duration by durationHolder

                    InCallScreen(
                        callState = callState,
                        duration = duration,
                        contactInfo = contactInfo,
                        onAcceptCall = ::handleAcceptCall,
                        onDeclineCall = ::handleDeclineCall,
                        onEndCall = ::handleEndCall,
                        onMuteToggle = ::handleMuteToggle,
                        onSpeakerToggle = ::handleSpeakerToggle,
                        onPlayDtmf = ::handlePlayDtmf
                    )
                }
            }

            startDurationTimer()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }

    private fun loadContactInfo() {
        try {
            val phoneNumber = call?.details?.handle?.schemeSpecificPart ?: "Unknown"
            val contact = contactsManager.getContactByNumber(phoneNumber)
            val displayName = contact?.name ?: phoneNumber
            val initials = if (contact != null) {
                contact.name.split(" ")
                    .mapNotNull { it.firstOrNull() }
                    .take(2)
                    .joinToString("")
                    .uppercase()
            } else {
                phoneNumber.take(2).uppercase()
            }

            contactInfo = ContactInfo(contact, displayName, initials)
            Log.d(TAG, "Loaded contact info: $displayName")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contact info", e)
            contactInfo = ContactInfo(null, "Unknown", "UN")
        }
    }

    private fun setupCallCallback() {
        try {
            callCallback = object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    Log.d(TAG, "Call state changed to: $state")

                    runOnUiThread {
                        callStateHolder.value = state

                        if (state == Call.STATE_DISCONNECTED) {
                            Log.d(TAG, "Call disconnected")
                            handleCallDisconnected()
                        }
                    }
                }
            }

            call?.registerCallback(callCallback!!)
            Log.d(TAG, "Call callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up call callback", e)
        }
    }

    private fun startCallMonitoring() {
        Log.d(TAG, "Starting call monitoring")
        monitorHandler = Handler(Looper.getMainLooper())
        var attempts = 0

        monitorRunnable = object : Runnable {
            override fun run() {
                try {
                    call = CallManager.currentCall

                    if (call != null) {
                        Log.d(TAG, "Call found after $attempts attempts")
                        loadContactInfo()
                        setupCallCallback()
                        callStateHolder.value = call?.state ?: Call.STATE_CONNECTING
                        recreate()
                    } else if (attempts < MAX_MONITOR_ATTEMPTS) {
                        attempts++
                        monitorHandler?.postDelayed(this, MONITOR_DELAY_MS)
                    } else {
                        Log.e(TAG, "Call monitoring timeout")
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in call monitoring", e)
                    finish()
                }
            }
        }

        monitorHandler?.post(monitorRunnable!!)
    }

    private fun startDurationTimer() {
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)

                val state = callStateHolder.value
                if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING) {
                    callDuration++
                    durationHolder.intValue = callDuration
                }

                if (state == Call.STATE_DISCONNECTED) {
                    break
                }
            }
        }
    }

    private fun handleAcceptCall() {
        Log.d(TAG, "Accepting call")
        try {
            call?.answer(0)
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call", e)
        }
    }

    private fun handleDeclineCall() {
        Log.d(TAG, "Declining call")
        try {
            call?.reject(false, null)
            showPostCallScreen(true)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error declining call", e)
            finish()
        }
    }

    private fun handleEndCall() {
        Log.d(TAG, "Ending call")
        try {
            call?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            finish()
        }
    }

    private fun handleMuteToggle(muted: Boolean) {
        Log.d(TAG, "Toggle mute: $muted")
        try {
            CallManager.setMuted(muted)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute", e)
        }
    }

    private fun handleSpeakerToggle(speaker: Boolean) {
        Log.d(TAG, "Toggle speaker: $speaker")
        try {
            CallManager.setSpeaker(speaker)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker", e)
        }
    }

    private fun handlePlayDtmf(digit: Char) {
        Log.d(TAG, "Playing DTMF: $digit")
        try {
            call?.playDtmfTone(digit)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing DTMF", e)
        }
    }

    private fun handleCallDisconnected() {
        try {
            showPostCallScreen(false)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling call disconnect", e)
            finish()
        }
    }

    private fun setupWindowFlags() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Yapzy::InCallWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L)

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
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up window flags", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent")
        try {
            setIntent(intent)
            call = CallManager.currentCall
            if (call != null) {
                loadContactInfo()
                setupCallCallback()
                callStateHolder.value = call?.state ?: Call.STATE_CONNECTING
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNewIntent", e)
        }
    }

    private fun showPostCallScreen(declined: Boolean) {
        try {
            val phoneNumber = call?.details?.handle?.schemeSpecificPart
            if (phoneNumber != null) {
                val callType = when {
                    declined -> "Declined"
                    call?.details?.callDirection == Call.Details.DIRECTION_INCOMING -> "Incoming"
                    else -> "Outgoing"
                }

                val intent = PostCallActivity.createIntent(
                    this,
                    phoneNumber,
                    callDuration,
                    callType,
                    callStartTime
                )
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing post-call screen", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - Cleaning up resources")

        try {
            monitorRunnable?.let { monitorHandler?.removeCallbacks(it) }
            monitorHandler = null
            monitorRunnable = null

            callCallback?.let {
                call?.unregisterCallback(it)
                callCallback = null
            }

            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
                wakeLock = null
            }

            call = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
    }
}

@Composable
fun InCallScreen(
    callState: Int,
    duration: Int,
    contactInfo: InCallActivity.ContactInfo?,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onEndCall: () -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit,
    onPlayDtmf: (Char) -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var showDialpad by remember { mutableStateOf(false) }

    val displayName = contactInfo?.displayName ?: "Unknown"
    val initials = contactInfo?.initials ?: "UN"
    val contact = contactInfo?.contact

    val stateText = when (callState) {
        Call.STATE_DIALING -> "Calling..."
        Call.STATE_CONNECTING -> "Connecting..."
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_ACTIVE -> String.format("%02d:%02d", duration / 60, duration % 60)
        else -> "Call"
    }

    val isIncoming = callState == Call.STATE_RINGING

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF283593),
                            Color(0xFF3949AB)
                        )
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
            if (isIncoming) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        "Incoming Call",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AvatarDisplay(contact, initials, isIncoming)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (!isIncoming && callState == Call.STATE_ACTIVE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        CallControlButton(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            "Mute",
                            isMuted
                        ) {
                            isMuted = !isMuted
                            onMuteToggle(isMuted)
                        }
                        CallControlButton(
                            Icons.Default.Dialpad,
                            "Dialpad",
                            false
                        ) {
                            showDialpad = true
                        }
                        CallControlButton(
                            if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            "Speaker",
                            isSpeaker
                        ) {
                            isSpeaker = !isSpeaker
                            onSpeakerToggle(isSpeaker)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (isIncoming) {
                        FloatingActionButton(
                            onClick = onDeclineCall,
                            containerColor = Color.Red,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        FloatingActionButton(
                            onClick = onAcceptCall,
                            containerColor = Color.Green,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.Call,
                                "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick = onEndCall,
                            containerColor = Color.Red,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                "End",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialpad) {
        DialpadDialog(
            onDismiss = { showDialpad = false },
            onDigitClick = onPlayDtmf
        )
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(0.3f),
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    label,
                    tint = if (active) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(0.9f)
        )
    }
}

@Composable
fun AvatarDisplay(
    contact: Contact?,
    initials: String,
    animated: Boolean
) {
    val context = LocalContext.current
    val scale = if (animated) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        ).value
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (contact?.photoUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(contact.photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = {
                    Text(
                        initials,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            )
        } else {
            Text(
                initials,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun DialpadDialog(
    onDismiss: () -> Unit,
    onDigitClick: (Char) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dialpad") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { digit ->
                            OutlinedButton(
                                onClick = { onDigitClick(digit[0]) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Text(digit, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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