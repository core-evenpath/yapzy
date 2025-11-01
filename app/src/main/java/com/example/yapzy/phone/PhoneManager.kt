package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat

class PhoneManager(private val context: Context) {

    companion object {
        private const val TAG = "PhoneManager"
    }

    private val telecomManager: TelecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    fun makeCall(phoneNumber: String) {
        try {
            Log.d(TAG, "Making call to: $phoneNumber")

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "CALL_PHONE permission not granted")
                throw SecurityException("CALL_PHONE permission not granted")
            }

            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$phoneNumber")
            callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            context.startActivity(callIntent)
            Log.d(TAG, "Call intent started successfully")

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchInCallActivity()
            }, 500)

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception making call", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            throw e
        }
    }

    private fun launchInCallActivity() {
        try {
            val inCallIntent = Intent()
            inCallIntent.setClassName(
                context.packageName,
                "com.example.yapzy.ui.screens.InCallActivity"
            )
            inCallIntent.putExtra("OUTGOING_CALL", true)
            inCallIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            context.startActivity(inCallIntent)
            Log.d(TAG, "InCallActivity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching InCallActivity", e)
        }
    }

    fun getDefaultDialerPackage(): String? {
        return try {
            telecomManager.defaultDialerPackage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default dialer package", e)
            null
        }
    }

    fun canMakeCalls(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun formatPhoneNumberForDisplay(phoneNumber: String): String {
        // Remove all non-digit characters
        val digitsOnly = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Format based on length
        return when {
            digitsOnly.startsWith("+1") && digitsOnly.length == 12 -> {
                // US number: +1 (XXX) XXX-XXXX
                "+1 (${digitsOnly.substring(2, 5)}) ${digitsOnly.substring(5, 8)}-${digitsOnly.substring(8)}"
            }
            digitsOnly.length == 10 -> {
                // US number without country code: (XXX) XXX-XXXX
                "(${digitsOnly.substring(0, 3)}) ${digitsOnly.substring(3, 6)}-${digitsOnly.substring(6)}"
            }
            digitsOnly.length == 11 && digitsOnly.startsWith("1") -> {
                // US number with 1 prefix: 1 (XXX) XXX-XXXX
                "1 (${digitsOnly.substring(1, 4)}) ${digitsOnly.substring(4, 7)}-${digitsOnly.substring(7)}"
            }
            else -> phoneNumber // Return as-is if doesn't match expected format
        }
    }
}