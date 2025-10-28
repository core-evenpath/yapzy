package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

class PhoneManager(private val context: Context) {

    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun makeCall(phoneNumber: String) {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        
        if (formattedNumber.isEmpty()) {
            return
        }

        if (hasCallPermission()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$formattedNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            openDialer(formattedNumber)
        }
    }

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

    fun canMakeCalls(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    fun formatPhoneNumber(number: String): String {
        return number.replace(Regex("[\\s\\-()]"), "")
    }

    fun formatPhoneNumberForDisplay(number: String): String {
        val cleaned = formatPhoneNumber(number)
        
        return when {
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
    }

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
