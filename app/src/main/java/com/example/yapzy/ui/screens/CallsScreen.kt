package com.example.yapzy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(initialPhoneNumber: String? = null) {
    var showDialpad by remember { mutableStateOf(initialPhoneNumber != null) }
    var phoneNumberForDialpad by remember { mutableStateOf(initialPhoneNumber ?: "") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showDialpad = !showDialpad
                    if (showDialpad) {
                        phoneNumberForDialpad = ""
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp
                )
            ) {
                Icon(
                    imageVector = if (showDialpad) Icons.Default.History else Icons.Default.Dialpad,
                    contentDescription = if (showDialpad) "Show Call History" else "Open Dialpad"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AnimatedContent(
                targetState = showDialpad,
                transitionSpec = {
                    fadeIn() + slideInHorizontally() togetherWith
                            fadeOut() + slideOutHorizontally()
                },
                label = "CallScreenTransition"
            ) { isDialpadShown ->
                if (isDialpadShown) {
                    DialpadScreen(
                        initialNumber = phoneNumberForDialpad,
                        onNavigateToCallHistory = {
                            showDialpad = false
                        }
                    )
                } else {
                    CallHistoryScreen(
                        onCallClick = { phoneNumber ->
                            phoneNumberForDialpad = phoneNumber
                            showDialpad = true
                        }
                    )
                }
            }
        }
    }
}