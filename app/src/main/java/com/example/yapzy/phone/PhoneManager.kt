package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Utility class for managing phone-related operations
 * Handles permissions, dialing, and phone number formatting
 */
class PhoneManager(private val context: Context) {

    /**
     * Check if the app has permission to make phone calls
     */
    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if the app has permission to read call log
     */
    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if the app has permission to read contacts
     */
    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Make a direct phone call (requires CALL_PHONE permission)
     * Falls back to system dialer if permission not granted
     */
    fun makeCall(phoneNumber: String) {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        
        if (formattedNumber.isEmpty()) {
            return
        }

        if (hasCallPermission()) {
            // Direct call with CALL_PHONE permission
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$formattedNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            // Fallback to system dialer
            openDialer(formattedNumber)
        }
    }

    /**
     * Open system dialer with pre-filled number (no permission required)
     */
    fun openDialer(phoneNumber: String) {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        
        if (formattedNumber.isEmpty()) {
            return
        }

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$formattedNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Check if device can make phone calls
     */
    fun canMakeCalls(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    /**
     * Format phone number for calling
     * Removes spaces, dashes, and parentheses
     */
    fun formatPhoneNumber(number: String): String {
        return number.replace(Regex("[\\s\\-()]"), "")
    }

    /**
     * Format phone number for display
     * Example: +1 (555) 123-4567
     */
    fun formatPhoneNumberForDisplay(number: String): String {
        val cleaned = formatPhoneNumber(number)
        
        return when {
            cleaned.startsWith("+1") && cleaned.length == 12 -> {
                // US number with country code
                "+1 (${cleaned.substring(2, 5)}) ${cleaned.substring(5, 8)}-${cleaned.substring(8)}"
            }
            cleaned.length == 10 -> {
                // US number without country code
                "(${cleaned.substring(0, 3)}) ${cleaned.substring(3, 6)}-${cleaned.substring(6)}"
            }
            cleaned.startsWith("+") -> {
                // International number
                cleaned
            }
            else -> number
        }
    }

    /**
     * Get default dialer package name
     */
    fun getDefaultDialerPackage(): String? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage
    }

    companion object {
        const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE
        const val PERMISSION_READ_CALL_LOG = Manifest.permission.READ_CALL_LOG
        const val PERMISSION_WRITE_CALL_LOG = Manifest.permission.WRITE_CALL_LOG
        const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
    }
}
