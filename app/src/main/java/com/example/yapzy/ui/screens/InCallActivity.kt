package com.example.yapzy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import com.example.yapzy.ui.components.*
class InCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InCallActivity"
    }

    private var call: Call? = null
    private var callCallback: Call.Callback? = null
    private var callStartTime: Long = 0
    private var callDuration: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        setupWindowFlags()

        val isOutgoingCall = intent.getBooleanExtra("OUTGOING_CALL", false)

        call = CallManager.currentCall
        callStartTime = System.currentTimeMillis()

        if (call == null && !isOutgoingCall) {
            finish()
            return
        }

        setContent {
            YapzyTheme {
                InCallUI(
                    call = call,
                    onAcceptCall = { call?.answer(0) },
                    onDeclineCall = {
                        call?.reject(false, null)
                        showPostCallScreen(true)
                        finish()
                    },
                    onEndCall = {
                        call?.disconnect()
                        finish()
                    },
                    onMuteToggle = { CallManager.setMuted(it) },
                    onSpeakerToggle = { CallManager.setSpeaker(it) },
                    onDurationUpdate = { callDuration = it }
                )
            }
        }

        callCallback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    runOnUiThread {
                        showPostCallScreen(false)
                        finish()
                    }
                }
            }
        }

        call?.registerCallback(callCallback!!)

        if (isOutgoingCall && call == null) {
            monitorForOutgoingCall()
        }
    }

    private fun monitorForOutgoingCall() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var attempts = 0
        val runnable = object : Runnable {
            override fun run() {
                call = CallManager.currentCall
                if (call != null) {
                    callCallback?.let { call?.registerCallback(it) }
                    recreate()
                } else if (attempts < 50) {
                    attempts++
                    handler.postDelayed(this, 100)
                } else {
                    finish()
                }
            }
        }
        handler.post(runnable)
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
        setIntent(intent)
        call = CallManager.currentCall
    }

    private fun showPostCallScreen(declined: Boolean) {
        try {
            val phoneNumber = call?.details?.handle?.schemeSpecificPart ?: return
            val callType = when {
                declined -> "Declined"
                call?.details?.callDirection == Call.Details.DIRECTION_INCOMING -> "Incoming"
                else -> "Outgoing"
            }
            val intent = PostCallActivity.createIntent(this, phoneNumber, callDuration, callType, callStartTime)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing post-call screen", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            callCallback?.let { call?.unregisterCallback(it) }
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}

@Composable
fun InCallUI(
    call: Call?,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onEndCall: () -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit,
    onDurationUpdate: (Int) -> Unit
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0) }
    var callState by remember { mutableStateOf(call?.state ?: Call.STATE_CONNECTING) }
    var showDialpad by remember { mutableStateOf(false) }

    val phoneNumber = call?.details?.handle?.schemeSpecificPart ?: "Unknown"
    val contactsManager = remember { ContactsManager(context) }
    val contact = remember { contactsManager.getContactByNumber(phoneNumber) }
    val displayName = contact?.name ?: phoneNumber
    val initials = contact?.name?.split(" ")?.mapNotNull { it.firstOrNull() }?.take(2)?.joinToString("") ?: phoneNumber.take(2)

    val stateText = when (callState) {
        Call.STATE_DIALING -> "Calling..."
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_ACTIVE -> String.format("%02d:%02d", callDuration / 60, callDuration % 60)
        else -> "Call"
    }

    val isIncoming = callState == Call.STATE_RINGING

    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING) {
            while (isActive) {
                delay(1000)
                callDuration++
                onDurationUpdate(callDuration)
            }
        }
    }

    DisposableEffect(call) {
        if (call != null) {
            val callback = object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    callState = state
                }
            }
            call.registerCallback(callback)
            onDispose { call.unregisterCallback(callback) }
        } else {
            onDispose { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
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
                Text(displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stateText, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
            }

            Spacer(modifier = Modifier.weight(0.5f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (!isIncoming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        CallButton(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            "Mute",
                            isMuted
                        ) { isMuted = !isMuted; onMuteToggle(isMuted) }
                        CallButton(Icons.Default.Dialpad, "Dialpad", false) { showDialpad = true }
                        CallButton(
                            if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            "Speaker",
                            isSpeaker
                        ) { isSpeaker = !isSpeaker; onSpeakerToggle(isSpeaker) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (isIncoming) {
                        FloatingActionButton(onClick = onDeclineCall, containerColor = Color.Red, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Default.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        FloatingActionButton(onClick = onAcceptCall, containerColor = Color.Green, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Default.Call, "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        FloatingActionButton(onClick = onEndCall, containerColor = Color.Red, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Default.CallEnd, "End", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDialpad) {
        AlertDialog(
            onDismissRequest = { showDialpad = false },
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
                                    onClick = { call?.playDtmfTone(digit[0]) },
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
            confirmButton = { TextButton(onClick = { showDialpad = false }) { Text("Close") } }
        )
    }
}

@Composable
fun AvatarDisplay(contact: com.example.yapzy.phone.Contact?, initials: String, animated: Boolean) {
    val context = LocalContext.current
    val scale = if (animated) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            1f, 1.1f,
            infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
            label = "scale"
        ).value
    } else 1f

    Box(
        modifier = Modifier.size(140.dp).scale(scale).clip(CircleShape).background(Color.White.copy(0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (contact?.photoUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(contact.photoUri).build(),
                contentDescription = null,
                modifier = Modifier.size(140.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = { Text(initials, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            )
        } else {
            Text(initials, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun CallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(0.3f),
            modifier = Modifier.size(56.dp).clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
    }
}