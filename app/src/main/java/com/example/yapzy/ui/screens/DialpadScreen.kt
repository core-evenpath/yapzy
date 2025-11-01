package com.example.yapzy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.yapzy.phone.PhoneManager
import kotlinx.coroutines.launch

private const val TAG = "DialpadScreen"

@Composable
fun DialpadScreen(
    initialNumber: String = "",
    onNavigateToCallHistory: () -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf(initialNumber) }
    val context = LocalContext.current
    var phoneManager by remember { mutableStateOf<PhoneManager?>(null) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Properly manage PhoneManager lifecycle
    DisposableEffect(context) {
        val manager = PhoneManager(context)
        phoneManager = manager

        onDispose {
            phoneManager = null
        }
    }

    // Check if we have call permission - make it reactive
    val hasCallPermission by remember {
        derivedStateOf {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    IconButton(onClick = { errorMessage = null }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Phone number display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (phoneNumber.isEmpty()) "Enter number" else phoneNumber,
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 32.sp,
                color = if (phoneNumber.isEmpty())
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.weight(1f))

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
                DialpadButton(
                    number = "1",
                    letters = "",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "1"
                    }
                )
                DialpadButton(
                    number = "2",
                    letters = "ABC",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "2"
                    }
                )
                DialpadButton(
                    number = "3",
                    letters = "DEF",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "3"
                    }
                )
            }

            // Row 2: 4, 5, 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DialpadButton(
                    number = "4",
                    letters = "GHI",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "4"
                    }
                )
                DialpadButton(
                    number = "5",
                    letters = "JKL",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "5"
                    }
                )
                DialpadButton(
                    number = "6",
                    letters = "MNO",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "6"
                    }
                )
            }

            // Row 3: 7, 8, 9
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DialpadButton(
                    number = "7",
                    letters = "PQRS",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "7"
                    }
                )
                DialpadButton(
                    number = "8",
                    letters = "TUV",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "8"
                    }
                )
                DialpadButton(
                    number = "9",
                    letters = "WXYZ",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "9"
                    }
                )
            }

            // Row 4: *, 0, #
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DialpadButton(
                    number = "*",
                    letters = "",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "*"
                    }
                )
                DialpadButton(
                    number = "0",
                    letters = "+",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "0"
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        phoneNumber += "+"
                    }
                )
                DialpadButton(
                    number = "#",
                    letters = "",
                    onDigitClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber += "#"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backspace button
            IconButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        phoneNumber = phoneNumber.dropLast(1)
                        errorMessage = null
                    }
                },
                modifier = Modifier.size(64.dp),
                enabled = phoneNumber.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = "Delete",
                    modifier = Modifier.size(32.dp),
                    tint = if (phoneNumber.isNotEmpty())
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Call button
            FloatingActionButton(
                onClick = {
                    if (phoneNumber.isNotEmpty() && phoneManager != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        errorMessage = null

                        scope.launch {
                            try {
                                Log.d(TAG, "Initiating call to: $phoneNumber")
                                // PhoneManager.makeCall will handle launching InCallActivity
                                phoneManager?.makeCall(phoneNumber)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error making call from dialpad", e)
                                errorMessage = "Failed to make call: ${e.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.size(72.dp),
                containerColor = if (phoneNumber.isNotEmpty() && hasCallPermission)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = if (phoneNumber.isNotEmpty() && hasCallPermission) 6.dp else 0.dp
                )
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Call",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Add contact button
            IconButton(
                onClick = { /* TODO: Open contacts */ },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = "Add Contact",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DialpadButton(
    number: String,
    letters: String,
    onDigitClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary
                ),
                onClick = onDigitClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}