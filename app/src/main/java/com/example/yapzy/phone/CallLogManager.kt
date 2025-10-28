package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.yapzy.ui.screens.CallLogEntry
import com.example.yapzy.ui.screens.CallType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CallLogManager(private val context: Context) {

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCallLogs(limit: Int = 100): List<CallLogEntry> {
        if (!hasCallLogPermission()) {
            return emptyList()
        }

        val callLogs = mutableListOf<CallLogEntry>()
        val contactsManager = ContactsManager(context)

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_NAME
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = it.getString(idIndex)
                    val number = it.getString(numberIndex) ?: "Unknown"
                    val date = it.getLong(dateIndex)
                    val duration = it.getInt(durationIndex)
                    val type = it.getInt(typeIndex)
                    val cachedName = it.getString(nameIndex)

                    val callType = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        else -> CallType.MISSED
                    }

                    val contactName = cachedName ?: contactsManager.getContactByNumber(number)?.name

                    val dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(date),
                        ZoneId.systemDefault()
                    )

                    callLogs.add(
                        CallLogEntry(
                            id = id,
                            phoneNumber = number,
                            contactName = contactName,
                            callType = callType,
                            timestamp = dateTime,
                            duration = duration
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return callLogs
    }
}
