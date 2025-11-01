package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.yapzy.ui.screens.InCallActivity

class PhoneManager(private val context: Context) {

    companion object {
        private const val TAG = "PhoneManager"
        const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE
        const val PERMISSION_READ_CALL_LOG = Manifest.permission.READ_CALL_LOG
        const val PERMISSION_WRITE_CALL_LOG = Manifest.permission.WRITE_CALL_LOG
        const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
    }

    fun hasCallPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking call permission", e)
            false
        }
    }

    fun hasCallLogPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking call log permission", e)
            false
        }
    }

    fun hasContactsPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contacts permission", e)
            false
        }
    }

    fun makeCall(phoneNumber: String) {
        try {
            val formattedNumber = formatPhoneNumber(phoneNumber)

            if (formattedNumber.isEmpty()) {
                Log.w(TAG, "Cannot make call: empty phone number")
                showError("Invalid phone number")
                return
            }

            Log.d(TAG, "Attempting to call: $formattedNumber")

            if (hasCallPermission()) {
                try {
                    // First, launch our InCallActivity immediately
                    val inCallIntent = Intent(context, InCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("OUTGOING_CALL", true)
                        putExtra("PHONE_NUMBER", formattedNumber)
                    }
                    context.startActivity(inCallIntent)

                    // Then initiate the actual call
                    // Small delay to ensure our activity is launched first
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val callIntent = Intent(Intent.ACTION_CALL).apply {
                                data = Uri.parse("tel:$formattedNumber")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(callIntent)
                            Log.d(TAG, "Call initiated successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error initiating call after activity launch", e)
                        }
                    }, 100)

                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException when trying to call - permission issue", e)
                    showError("Call permission denied")
                    openDialer(formattedNumber)
                } catch (e: Exception) {
                    Log.e(TAG, "Error making call", e)
                    showError("Failed to make call: ${e.message}")
                    openDialer(formattedNumber)
                }
            } else {
                Log.w(TAG, "No CALL_PHONE permission, opening dialer instead")
                openDialer(formattedNumber)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in makeCall", e)
            showError("Failed to initiate call: ${e.message}")
        }
    }

    fun openDialer(phoneNumber: String) {
        try {
            val formattedNumber = formatPhoneNumber(phoneNumber)

            if (formattedNumber.isEmpty()) {
                Log.w(TAG, "Cannot open dialer: empty phone number")
                showError("Invalid phone number")
                return
            }

            Log.d(TAG, "Opening dialer with: $formattedNumber")

            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$formattedNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Dialer opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening dialer", e)
            showError("Failed to open dialer: ${e.message}")
        }
    }

    fun canMakeCalls(): Boolean {
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking telephony feature", e)
            false
        }
    }

    fun formatPhoneNumber(number: String): String {
        return try {
            number.replace(Regex("[\\s\\-()]"), "")
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting phone number", e)
            number
        }
    }

    fun formatPhoneNumberForDisplay(number: String): String {
        return try {
            val cleaned = formatPhoneNumber(number)

            when {
                cleaned.startsWith("+1") && cleaned.length == 12 -> {
                    "+1 (${cleaned.substring(2, 5)}) ${cleaned.substring(5, 8)}-${cleaned.substring(8)}"
                }
                cleaned.length == 10 -> {
                    "(${cleaned.substring(0, 3)}) ${cleaned.substring(3, 6)}-${cleaned.substring(6)}"
                }
                cleaned.startsWith("+") -> {
                    cleaned
                }
                else -> number
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting phone number for display", e)
            number
        }
    }

    fun getDefaultDialerPackage(): String? {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default dialer package", e)
            null
        }
    }

    private fun showError(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast", e)
        }
    }
}