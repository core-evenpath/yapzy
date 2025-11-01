package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

class PhoneManager(private val context: Context) {

    companion object {
        private const val TAG = "PhoneManager"
    }

    /**
     * Check if CALL_PHONE permission is granted
     */
    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Make a phone call to the given number
     */
    fun makeCall(phoneNumber: String) {
        if (!hasCallPermission()) {
            Log.e(TAG, "CALL_PHONE permission not granted")
            throw SecurityException("CALL_PHONE permission required")
        }

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${phoneNumber.trim()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Initiating call to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            throw e
        }
    }

    /**
     * Open dialer with pre-filled number
     */
    fun openDialer(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phoneNumber.trim()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opening dialer with $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening dialer", e)
            throw e
        }
    }

    /**
     * Format phone number for display
     */
    fun formatPhoneNumberForDisplay(phoneNumber: String): String {
        return try {
            val countryCode = Locale.getDefault().country
            PhoneNumberUtils.formatNumber(phoneNumber, countryCode) ?: phoneNumber
        } catch (e: Exception) {
            Log.w(TAG, "Error formatting phone number", e)
            phoneNumber
        }
    }

    /**
     * Clean phone number (remove non-digit characters)
     */
    fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }

    /**
     * Check if a phone number is valid
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = cleanPhoneNumber(phoneNumber)
        return cleaned.length >= 10
    }
}