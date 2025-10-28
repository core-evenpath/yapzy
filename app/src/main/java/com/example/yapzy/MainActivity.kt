package com.example.yapzy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.yapzy.navigation.AppNavigation
import com.example.yapzy.phone.PermissionHandler
import com.example.yapzy.ui.screens.PermissionScreen
import com.example.yapzy.ui.theme.YapzyTheme

class MainActivity : ComponentActivity() {

    private var hasPermissions = false
    private var initialPhoneNumber: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasPermissions = allGranted

        if (allGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            recreate()
        } else {
            val deniedCount = permissions.filter { !it.value }.size
            Toast.makeText(
                this,
                "Some permissions were denied: $deniedCount permissions",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate called")

        try {
            enableEdgeToEdge()

            initialPhoneNumber = handleIntent(intent)
            hasPermissions = PermissionHandler.hasAllPermissions(this)

            Log.d("MainActivity", "hasPermissions: $hasPermissions")

            setContent {
                YapzyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (hasPermissions) {
                            val navController = rememberNavController()
                            AppNavigation(
                                navController = navController,
                                initialPhoneNumber = initialPhoneNumber
                            )
                        } else {
                            PermissionScreen(
                                onRequestPermissions = {
                                    permissionLauncher.launch(PermissionHandler.ALL_PERMISSIONS)
                                }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val phoneNumber = handleIntent(intent)
        if (phoneNumber != null) {
            initialPhoneNumber = phoneNumber
            recreate()
        }
    }

    private fun handleIntent(intent: Intent?): String? {
        val action = intent?.action
        val data = intent?.data

        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            data?.let { uri ->
                if (uri.scheme == "tel") {
                    return uri.schemeSpecificPart
                }
            }
        }
        return null
    }
}