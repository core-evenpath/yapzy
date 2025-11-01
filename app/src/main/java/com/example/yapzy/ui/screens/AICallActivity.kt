package com.example.yapzy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.ai.*
import com.example.yapzy.ui.theme.YapzyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AICallActivity : ComponentActivity() {

    private val viewModel: AICallViewModel by viewModels()
    private var aiCallManager: AICallManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_TYPE = "caller_type"

        fun createIntent(
            context: Context,
            phoneNumber: String,
            callerName: String? = null,
            callerType: String = "UNKNOWN"
        ): Intent {
            return Intent(context, AICallActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_TYPE, callerType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWindowFlags()

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        val callerTypeStr = intent.getStringExtra(EXTRA_CALLER_TYPE) ?: "UNKNOWN"

        val callerType = when (callerTypeStr) {
            "CONTACT" -> CallerType.CONTACT
            "SPAM_LIKELY" -> CallerType.SPAM_LIKELY
            else -> CallerType.UNKNOWN
        }

        viewModel.setCallerInfo(CallerInfo(
            name = callerName,
            number = phoneNumber,
            type = callerType
        ))

        aiCallManager = AICallManager(
            context = this,
            onTranscriptUpdate = { transcript ->
                viewModel.setTranscript(transcript)
            },
            onStateChange = { state ->
                viewModel.setCallState(state)
            },
            onError = { error ->
                viewModel.setError(error)
            }
        )

        setContent {
            YapzyTheme {
                AICallScreen(
                    viewModel = viewModel,
                    aiCallManager = aiCallManager!!,
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun setupWindowFlags() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Yapzy::AICallWakeLock"
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
    }

    override fun onDestroy() {
        super.onDestroy()
        aiCallManager?.cleanup()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
}

@Composable
fun AICallScreen(
    viewModel: AICallViewModel,
    aiCallManager: AICallManager,
    onDismiss: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val aiMessage by viewModel.aiMessage.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaker by viewModel.isSpeaker.collectAsState()
    val callerInfo by viewModel.callerInfo.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Call duration timer
    LaunchedEffect(callState) {
        if (callState == AICallState.ACTIVE || callState == AICallState.AI_TAKEOVER || callState == AICallState.AI_CALL) {
            while (isActive) {
                delay(1000)
                viewModel.setCallDuration(callDuration + 1)
            }
        }
    }

    // Show error messages
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            delay(3000)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (callState) {
            AICallState.INCOMING -> IncomingCallScreen(
                callerInfo = callerInfo,
                onAnswer = {
                    viewModel.setCallState(AICallState.ACTIVE)
                },
                onDecline = {
                    viewModel.setCallState(AICallState.DECLINED)
                    onDismiss()
                },
                onAICall = {
                    callerInfo?.let { info ->
                        aiCallManager.startAICall(info.number, info.name)
                    }
                },
                onAIMessage = {
                    callerInfo?.let { info ->
                        aiCallManager.composeAIMessage(info.number, info.name)
                    }
                }
            )

            AICallState.ACTIVE -> ActiveCallScreen(
                callerInfo = callerInfo,
                duration = callDuration,
                isMuted = isMuted,
                isSpeaker = isSpeaker,
                onMuteToggle = {
                    viewModel.toggleMute()
                    aiCallManager.setMuted(!isMuted)
                },
                onSpeakerToggle = {
                    viewModel.toggleSpeaker()
                    aiCallManager.setSpeaker(!isSpeaker)
                },
                onHandToAI = {
                    callerInfo?.let { info ->
                        aiCallManager.handCallToAI(info.number, info.name)
                    }
                },
                onEndCall = {
                    aiCallManager.endCall()
                    onDismiss()
                }
            )

            AICallState.CONNECTING -> ConnectingScreen(callerInfo)

            AICallState.AI_CALL, AICallState.AI_TAKEOVER -> AICallInProgressScreen(
                callerInfo = callerInfo,
                transcript = transcript,
                isFromTakeover = callState == AICallState.AI_TAKEOVER,
                duration = callDuration,
                onTakeOver = {
                    aiCallManager.takeBackCall()
                },
                onEndCall = {
                    aiCallManager.endCall()
                    onDismiss()
                }
            )

            AICallState.AI_MESSAGE -> AIMessageComposingScreen(
                callerInfo = callerInfo,
                message = aiMessage,
                isTyping = isTyping,
                onSendMessage = {
                    callerInfo?.let { info ->
                        aiCallManager.sendSMS(info.number, aiMessage)
                    }
                    viewModel.setCallState(AICallState.DECLINED)
                    onDismiss()
                },
                onBack = {
                    viewModel.setCallState(AICallState.INCOMING)
                }
            )

            AICallState.DECLINED, AICallState.ENDED -> {
                LaunchedEffect(Unit) {
                    onDismiss()
                }
            }

            else -> {}
        }

        // Error Snackbar
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    callerInfo: CallerInfo?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAICall: () -> Unit,
    onAIMessage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF3B82F6)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(40.dp))

            // Caller info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerInfo?.name?.take(2)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = callerInfo?.name ?: callerInfo?.number ?: "Unknown",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(8.dp))

                if (callerInfo?.name != null) {
                    Text(
                        text = callerInfo.number,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                if (callerInfo?.type == CallerType.SPAM_LIKELY) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Red.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Likely Spam",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Primary actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color.Red,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = onAnswer,
                        containerColor = Color.Green,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // AI Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onAICall,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            brush = SolidColor(Color.White)
                        )
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI Answer")
                    }

                    OutlinedButton(
                        onClick = onAIMessage,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            brush = SolidColor(Color.White)
                        )
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI Message")
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    callerInfo: CallerInfo?,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHandToAI: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF3B82F6)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerInfo?.name?.take(2)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = callerInfo?.name ?: callerInfo?.number ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = formatCallDuration(duration),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Call controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AICallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        isActive = isMuted,
                        onClick = onMuteToggle
                    )

                    AICallControlButton(
                        icon = Icons.Default.VolumeUp,
                        label = "Speaker",
                        isActive = isSpeaker,
                        onClick = onSpeakerToggle
                    )
                }

                // Hand to AI button
                Button(
                    onClick = onHandToAI,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hand to AI")
                }

                // End call button
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = Color.Red,
                    modifier = Modifier.size(70.dp)
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

@Composable
fun ConnectingScreen(callerInfo: CallerInfo?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF3B82F6)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Connecting...",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
    }
}

@Composable
fun AICallInProgressScreen(
    callerInfo: CallerInfo?,
    transcript: List<TranscriptItem>,
    isFromTakeover: Boolean,
    duration: Int,
    onTakeOver: () -> Unit,
    onEndCall: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "AI Handling Call",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = callerInfo?.name ?: callerInfo?.number ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = formatCallDuration(duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            // Transcript
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transcript) { item ->
                    TranscriptItemView(item)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isFromTakeover) {
                    Button(
                        onClick = onTakeOver,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Over")
                    }
                }

                Button(
                    onClick = onEndCall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("End Call")
                }
            }
        }
    }
}

@Composable
fun AIMessageComposingScreen(
    callerInfo: CallerInfo?,
    message: String,
    isTyping: Boolean,
    onSendMessage: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "AI Composing Message",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(Modifier.height(24.dp))

            // Caller info
            Text(
                text = "To: ${callerInfo?.name ?: callerInfo?.number ?: "Unknown"}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            // Message
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (isTyping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("AI is composing message...")
                    } else {
                        Text(
                            text = message.ifEmpty { "No message composed yet" },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Send button
            Button(
                onClick = onSendMessage,
                modifier = Modifier.fillMaxWidth(),
                enabled = message.isNotEmpty() && !isTyping
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send Message")
            }
        }
    }
}

@Composable
fun TranscriptItemView(item: TranscriptItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.speaker == Speaker.CALLER)
            Arrangement.Start
        else
            Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (item.speaker == Speaker.CALLER)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = when (item.speaker) {
                        Speaker.CALLER -> "Caller"
                        Speaker.AI -> "AI"
                        Speaker.USER -> "You"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.speaker == Speaker.CALLER)
                        MaterialTheme.colorScheme.primary
                    else
                        Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.speaker == Speaker.CALLER)
                        MaterialTheme.colorScheme.onSurface
                    else
                        Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.speaker == Speaker.CALLER)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AICallControlButton(
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
            onClick = onClick,
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(60.dp)
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
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

private fun formatCallDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}