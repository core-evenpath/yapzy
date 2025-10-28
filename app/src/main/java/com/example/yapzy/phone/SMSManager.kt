package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.yapzy.models.*

class SMSManager(private val context: Context) {

    fun hasSMSPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getConversations(): List<Conversation> {
        if (!hasSMSPermission()) {
            return emptyList()
        }

        val conversations = mutableListOf<Conversation>()
        val contactsManager = ContactsManager(context)

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                val threadMap = mutableMapOf<String, Conversation>()

                while (it.moveToNext()) {
                    val threadId = it.getString(threadIdIndex)
                    val address = it.getString(addressIndex) ?: "Unknown"
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    val type = it.getInt(typeIndex)
                    val isRead = it.getInt(readIndex) == 1

                    if (!threadMap.containsKey(threadId)) {
                        val contact = contactsManager.getContactByNumber(address)
                        val contactName = contact?.name ?: address
                        val initials = if (contact != null) {
                            contact.name.split(" ").mapNotNull { word -> word.firstOrNull() }.take(2).joinToString("")
                        } else {
                            address.take(2)
                        }

                        val isFromUser = type == Telephony.Sms.MESSAGE_TYPE_SENT

                        val message = Message(
                            id = threadId,
                            senderId = address,
                            senderName = if (isFromUser) "You" else contactName,
                            content = body,
                            timestamp = date,
                            isFromUser = isFromUser,
                            priority = Priority.MEDIUM,
                            tone = Tone.NEUTRAL,
                            intent = Intent.INFORMATION
                        )

                        threadMap[threadId] = Conversation(
                            id = threadId,
                            contactName = contactName,
                            contactAvatar = initials,
                            messages = listOf(message),
                            lastMessage = message,
                            unreadCount = if (!isRead && !isFromUser) 1 else 0,
                            aiSummary = null,
                            suggestedReplies = listOf("Thanks!", "Okay", "Sure, sounds good!")
                        )
                    } else {
                        val existing = threadMap[threadId]!!
                        if (!isRead && type != Telephony.Sms.MESSAGE_TYPE_SENT) {
                            threadMap[threadId] = existing.copy(
                                unreadCount = existing.unreadCount + 1
                            )
                        }
                    }
                }

                conversations.addAll(threadMap.values)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return conversations.sortedByDescending { it.lastMessage.timestamp }
    }

    fun getMessagesForContact(threadId: String, limit: Int = 200): List<Message> {
        if (!hasSMSPermission()) {
            return emptyList()
        }

        val messages = mutableListOf<Message>()
        val contactsManager = ContactsManager(context)

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId),
                "${Telephony.Sms.DATE} ASC LIMIT $limit"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val address = it.getString(addressIndex) ?: "Unknown"
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    val type = it.getInt(typeIndex)

                    val contact = contactsManager.getContactByNumber(address)
                    val contactName = contact?.name ?: address

                    val isFromUser = type == Telephony.Sms.MESSAGE_TYPE_SENT

                    messages.add(
                        Message(
                            id = id,
                            senderId = address,
                            senderName = if (isFromUser) "You" else contactName,
                            content = body,
                            timestamp = date,
                            isFromUser = isFromUser,
                            priority = Priority.MEDIUM,
                            tone = Tone.NEUTRAL,
                            intent = Intent.INFORMATION
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return messages
    }

    fun sendSMS(phoneNumber: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}