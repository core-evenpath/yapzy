package com.example.yapzy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen() {
    var showDialpad by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialpad = !showDialpad },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (showDialpad) Icons.Default.Close else Icons.Default.Dialpad,
                    contentDescription = if (showDialpad) "Close Dialpad" else "Open Dialpad"
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showDialpad) {
                DialpadScreen(
                    onNavigateToCallHistory = { showDialpad = false }
                )
            } else {
                CallHistoryScreen(
                    onCallClick = { phoneNumber ->
                        // Handle call
                    }
                )
            }
        }
    }
}
