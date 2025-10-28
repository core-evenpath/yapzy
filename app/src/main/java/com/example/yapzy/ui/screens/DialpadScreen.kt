package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.phone.PhoneManager

@Composable
fun DialpadScreen(
    initialNumber: String = "",
    onNavigateToCallHistory: () -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf(initialNumber) }
    val context = LocalContext.current
    val phoneManager = remember { PhoneManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Phone number display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
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
                    MaterialTheme.colorScheme.onSurface
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
                DialpadButton("1", "", onDigitClick = { phoneNumber += "1" })
                DialpadButton("2", "ABC", onDigitClick = { phoneNumber += "2" })
                DialpadButton("3", "DEF", onDigitClick = { phoneNumber += "3" })
            }

            // Row 2: 4, 5, 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DialpadButton("4", "GHI", onDigitClick = { phoneNumber += "4" })
                DialpadButton("5", "JKL", onDigitClick = { phoneNumber += "5" })
                DialpadButton("6", "MNO", onDigitClick = { phoneNumber += "6" })
            }

            // Row 3: 7, 8, 9
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DialpadButton("7", "PQRS", onDigitClick = { phoneNumber += "7" })
                DialpadButton("8", "TUV", onDigitClick = { phoneNumber += "8" })
                DialpadButton("9", "WXYZ", onDigitClick = { phoneNumber += "9" })
            }

            // Row 4: *, 0, #
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DialpadButton("*", "", onDigitClick = { phoneNumber += "*" })
                DialpadButton(
                    "0", 
                    "+", 
                    onDigitClick = { phoneNumber += "0" },
                    onLongClick = { phoneNumber += "+" }
                )
                DialpadButton("#", "", onDigitClick = { phoneNumber += "#" })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Call button and backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backspace button
            IconButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        phoneNumber = phoneNumber.dropLast(1)
                    }
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Call button
            FloatingActionButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        phoneManager.makeCall(phoneNumber)
                    }
                },
                modifier = Modifier.size(72.dp),
                containerColor = if (phoneNumber.isEmpty()) 
                    MaterialTheme.colorScheme.surfaceVariant
                else 
                    Color(0xFF4CAF50),
                contentColor = Color.White
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Call",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Contact button placeholder
            IconButton(
                onClick = { /* TODO: Open contacts */ },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Contacts,
                    contentDescription = "Contacts",
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
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(
                if (isPressed)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable {
                isPressed = true
                onDigitClick()
                isPressed = false
            },
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
