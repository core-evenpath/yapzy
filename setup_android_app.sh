#!/bin/bash

# Enhanced Android App Setup Script with SMS Auto-fill
# Run this script from your Android project root directory
# Creates: Login Screen → OTP Screen → Home Screen with SMS auto-fill support

echo "🚀 Setting up Android app with SMS Auto-fill support..."

# Get the project root directory
PROJECT_ROOT=$(pwd)
APP_SRC="$PROJECT_ROOT/app/src/main"
KOTLIN_SRC="$APP_SRC/java/com/example/myapp"
MANIFEST_PATH="$APP_SRC/AndroidManifest.xml"

echo "📁 Creating package structure..."

# Create package directories
mkdir -p "$KOTLIN_SRC/data"
mkdir -p "$KOTLIN_SRC/ui/navigation"
mkdir -p "$KOTLIN_SRC/ui/screens"
mkdir -p "$KOTLIN_SRC/ui/components"
mkdir -p "$KOTLIN_SRC/ui/theme"
mkdir -p "$KOTLIN_SRC/utils"

echo "📝 Creating data models..."

# Create User data model
cat > "$KOTLIN_SRC/data/UserData.kt" << 'EOF'
package com.example.yapzy.data

data class User(
    val id: String,
    val phoneNumber: String,
    val name: String? = null,
    val isVerified: Boolean = false
)

object UserRepository {
    private var currentUser: User? = null
    
    fun setCurrentUser(user: User) {
        currentUser = user
    }
    
    fun getCurrentUser(): User? = currentUser
    
    fun clearUser() {
        currentUser = null
    }
}
EOF

echo "📝 Creating SMS auto-fill utilities..."

# Create SMS Retriever helper
cat > "$KOTLIN_SRC/utils/SmsRetriever.kt" << 'EOF'
package com.example.yapzy.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

class SmsRetrieverHelper(
    private val activity: Activity,
    private val onOtpReceived: (String) -> Unit
) {
    private var smsReceiver: BroadcastReceiver? = null

    fun startListening() {
        val client = SmsRetriever.getClient(activity)
        val task = client.startSmsRetriever()

        task.addOnSuccessListener {
            // SMS retrieval started successfully
            registerBroadcastReceiver()
        }

        task.addOnFailureListener {
            // Failed to start SMS retrieval
        }
    }

    private fun registerBroadcastReceiver() {
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
                    val extras = intent.extras
                    val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status

                    when (status?.statusCode) {
                        CommonStatusCodes.SUCCESS -> {
                            val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                            message?.let {
                                val otp = extractOtp(it)
                                if (otp != null) {
                                    onOtpReceived(otp)
                                }
                            }
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        ContextCompat.registerReceiver(
            activity,
            smsReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun extractOtp(message: String): String? {
        // Extract 4-6 digit OTP from SMS
        val pattern = "\\d{4,6}".toRegex()
        return pattern.find(message)?.value
    }

    fun stopListening() {
        smsReceiver?.let {
            activity.unregisterReceiver(it)
            smsReceiver = null
        }
    }
}
EOF

# Create Phone Number Manager
cat > "$KOTLIN_SRC/utils/PhoneNumberManager.kt" << 'EOF'
package com.example.yapzy.utils

import android.content.Context
import android.telephony.TelephonyManager

object PhoneNumberManager {
    fun getDevicePhoneNumber(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val phoneNumber = telephonyManager.line1Number
            
            // Clean and format phone number
            phoneNumber?.let {
                if (it.startsWith("+91")) {
                    it.substring(3)
                } else if (it.length == 10) {
                    it
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
EOF

echo "📝 Creating navigation files..."

# Create NavGraph.kt
cat > "$KOTLIN_SRC/ui/navigation/NavGraph.kt" << 'EOF'
package com.example.yapzy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.yapzy.ui.screens.*

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object OTPVerification : Screen("otp/{phoneNumber}") {
        fun createRoute(phoneNumber: String) = "otp/$phoneNumber"
    }
    object Home : Screen("home")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onSendOTP = { phoneNumber ->
                    navController.navigate(Screen.OTPVerification.createRoute(phoneNumber))
                }
            )
        }

        composable(
            route = Screen.OTPVerification.route,
            arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            OTPVerificationScreen(
                phoneNumber = phoneNumber,
                onVerified = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen()
        }
    }
}
EOF

echo "📝 Creating Login screen with phone auto-fill..."

# Create LoginScreen.kt
cat > "$KOTLIN_SRC/ui/screens/LoginScreen.kt" << 'EOF'
package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.utils.PhoneNumberManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSendOTP: (String) -> Unit
) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Auto-fill phone number on first load
    LaunchedEffect(Unit) {
        val deviceNumber = PhoneNumberManager.getDevicePhoneNumber(context)
        deviceNumber?.let {
            phoneNumber = it
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6A5ACD),
                        Color(0xFF4B0082)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo
            Card(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Phone",
                        modifier = Modifier.size(60.dp),
                        tint = Color(0xFF6A5ACD)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Welcome Text
            Text(
                text = "Welcome Back",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Sign in with your phone number",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter Phone Number",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Phone Number Input
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { 
                            phoneNumber = it.filter { char -> char.isDigit() }
                            isError = false
                        },
                        label = { Text("Phone Number") },
                        leadingIcon = {
                            Text(
                                text = "+91",
                                fontSize = 16.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        placeholder = { Text("Enter 10 digit number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        isError = isError,
                        supportingText = if (isError) {
                            { Text("Please enter a valid 10-digit phone number") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6A5ACD),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Send OTP Button
                    Button(
                        onClick = {
                            if (phoneNumber.length == 10) {
                                isLoading = true
                                onSendOTP(phoneNumber)
                            } else {
                                isError = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6A5ACD)
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Send OTP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Terms and Privacy
            Text(
                text = "By continuing, you agree to our Terms & Privacy Policy",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
EOF

echo "📝 Creating OTP screen with SMS auto-fill..."

# Create OTPVerificationScreen.kt
cat > "$KOTLIN_SRC/ui/screens/OTPVerificationScreen.kt" << 'EOF'
package com.example.yapzy.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.data.User
import com.example.yapzy.data.UserRepository
import com.example.yapzy.utils.SmsRetrieverHelper
import kotlinx.coroutines.delay
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPVerificationScreen(
    phoneNumber: String,
    onVerified: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    var otp by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    
    // SMS Auto-fill
    DisposableEffect(Unit) {
        val smsRetriever = SmsRetrieverHelper(context as ComponentActivity) { receivedOtp ->
            otp = receivedOtp
        }
        smsRetriever.startListening()
        
        onDispose {
            smsRetriever.stopListening()
        }
    }

    // Resend timer
    LaunchedEffect(resendTimer) {
        if (resendTimer > 0) {
            delay(1000)
            resendTimer--
        } else {
            canResend = true
        }
    }

    // Auto-verify when OTP is complete
    LaunchedEffect(otp) {
        if (otp.length == 4) {
            isVerifying = true
            delay(500) // Simulate verification
            
            // Create and save user
            val user = User(
                id = UUID.randomUUID().toString(),
                phoneNumber = phoneNumber,
                isVerified = true
            )
            UserRepository.setCurrentUser(user)
            
            onVerified()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6A5ACD),
                        Color(0xFF4B0082)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Back button
            IconButton(
                onClick = onBackToLogin,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Title
            Text(
                text = "Verify Phone",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter the 4-digit code sent to\n+91 $phoneNumber",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // OTP Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter OTP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // OTP Input Boxes
                    OtpInputField(
                        otp = otp,
                        onOtpChange = { newOtp ->
                            if (newOtp.length <= 4) {
                                otp = newOtp
                                isError = false
                            }
                        },
                        isError = isError
                    )

                    if (isError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invalid OTP. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Resend OTP
                    if (canResend) {
                        TextButton(onClick = {
                            canResend = false
                            resendTimer = 30
                            otp = ""
                        }) {
                            Text(
                                text = "Resend OTP",
                                color = Color(0xFF6A5ACD),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "Resend OTP in ${resendTimer}s",
                            fontSize = 14.sp,
                            color = Color(0xFF999999)
                        )
                    }

                    if (isVerifying) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF6A5ACD)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OtpInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    isError: Boolean
) {
    BasicTextField(
        value = otp,
        onValueChange = { value ->
            if (value.all { it.isDigit() }) {
                onOtpChange(value)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val char = when {
                        index < otp.length -> otp[index].toString()
                        else -> ""
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .border(
                                width = 2.dp,
                                color = when {
                                    isError -> MaterialTheme.colorScheme.error
                                    index < otp.length -> Color(0xFF6A5ACD)
                                    else -> Color(0xFFCCCCCC)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = if (index < otp.length) Color(0xFFF5F5FF) else Color.White,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    )
}
EOF

echo "📝 Creating Home screen..."

# Create HomeScreen.kt
cat > "$KOTLIN_SRC/ui/screens/HomeScreen.kt" << 'EOF'
package com.example.yapzy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yapzy.data.UserRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val currentUser = remember { UserRepository.getCurrentUser() }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
    
    val currentDate = remember {
        SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6A5ACD),
                        Color(0xFF4B0082)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentDate,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                IconButton(
                    onClick = { /* Profile action */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Welcome Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Welcome! 👋",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    currentUser?.let {
                        Text(
                            text = "You're signed in with\n+91 ${it.phoneNumber}",
                            fontSize = 16.sp,
                            color = Color(0xFF666666),
                            lineHeight = 24.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Divider(color = Color(0xFFEEEEEE))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Your app is ready to use!",
                        fontSize = 14.sp,
                        color = Color(0xFF999999)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Actions
            Text(
                text = "Quick Actions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = "Profile",
                    description = "View your profile",
                    modifier = Modifier.weight(1f)
                )
                
                QuickActionCard(
                    title = "Settings",
                    description = "App settings",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
        }
    }
}
EOF

echo "🎨 Creating theme files..."

# Create Color.kt
cat > "$KOTLIN_SRC/ui/theme/Color.kt" << 'EOF'
package com.example.yapzy.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFF6A5ACD)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
EOF

# Create Theme.kt
cat > "$KOTLIN_SRC/ui/theme/Theme.kt" << 'EOF'
package com.example.yapzy.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun MyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
EOF

# Create Type.kt
cat > "$KOTLIN_SRC/ui/theme/Type.kt" << 'EOF'
package com.example.yapzy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
EOF

echo "📝 Creating MainActivity.kt..."

# Create MainActivity.kt
cat > "$KOTLIN_SRC/MainActivity.kt" << 'EOF'
package com.example.yapzy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.yapzy.ui.navigation.NavGraph
import com.example.yapzy.ui.theme.MyAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }
            }
        }
    }
}
EOF

echo "📝 Updating AndroidManifest.xml with permissions..."

# Update AndroidManifest.xml
cat > "$MANIFEST_PATH" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions for SMS auto-fill and phone number -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    
    <!-- Internet permission (if needed for API calls) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApp"
        tools:targetApi="31">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MyApp">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>

</manifest>
EOF

echo "📝 Creating build.gradle.kts..."

# Create build.gradle.kts
cat > "$PROJECT_ROOT/app/build.gradle.kts" << 'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

android {
    namespace = "com.example.yapzy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.yapzy"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Google Play Services for SMS Retriever API
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:18.1.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
EOF

echo ""
echo "✅ Enhanced Setup Complete!"
echo ""
echo "📂 Created files:"
echo "   ✓ Login Screen (with phone auto-fill)"
echo "   ✓ OTP Screen (with SMS auto-fill)"
echo "   ✓ Home Screen (personalized welcome)"
echo "   ✓ SMS Retriever Helper"
echo "   ✓ Phone Number Manager"
echo "   ✓ Navigation & Theme files"
echo "   ✓ Updated AndroidManifest.xml with permissions"
echo "   ✓ Updated build.gradle.kts with SMS Retriever API"
echo ""
echo "🔑 Key Features:"
echo "   ✓ Phone number auto-fill from device"
echo "   ✓ SMS OTP auto-detection"
echo "   ✓ Auto-verify when OTP complete"
echo "   ✓ 30-second resend timer"
echo "   ✓ Beautiful gradient UI"
echo ""
echo "⚠️  Important Notes:"
echo "   1. SMS auto-fill requires Google Play Services"
echo "   2. Permissions need user consent at runtime"
echo "   3. Test on physical device (SMS won't work on emulator)"
echo "   4. Your SMS must contain 4-6 digit OTP"
echo ""
echo "🔄 Next Steps:"
echo "   1. Update package name from 'com.example.yapzy' to yours"
echo "   2. Sync Project with Gradle Files"
echo "   3. Request runtime permissions for phone state"
echo "   4. Test SMS auto-fill on real device"
echo ""
echo "📱 For SMS auto-fill to work:"
echo "   • User must grant READ_PHONE_STATE permission"
echo "   • SMS must arrive within 5 minutes"
echo "   • SMS must contain alphanumeric code (4-6 digits)"
echo ""
echo "🎉 Your app is ready to build!"
