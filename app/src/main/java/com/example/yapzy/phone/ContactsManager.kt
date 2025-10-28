package com.example.yapzy.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Data class representing a contact
 */
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val isFavorite: Boolean = false
)

/**
 * Manager class for handling contacts operations
 */
class ContactsManager(private val context: Context) {

    companion object {
        private const val TAG = "ContactsManager"
    }

    /**
     * Check if the app has permission to read contacts
     */
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

    /**
     * Get a contact by phone number
     */
    fun getContactByNumber(phoneNumber: String?): Contact? {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null
        }

        if (!hasContactsPermission()) {
            Log.w(TAG, "No contacts permission")
            return null
        }

        val cleanedNumber = try {
            phoneNumber.replace(Regex("[\\s\\-()\\+]"), "")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning phone number", e)
            phoneNumber
        }

        if (cleanedNumber.isEmpty()) {
            return null
        }

        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null")
                return null
            }

            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

            if (idIndex < 0 || nameIndex < 0 || numberIndex < 0 || starredIndex < 0) {
                Log.w(TAG, "Invalid column indices")
                return null
            }

            while (cursor.moveToNext()) {
                try {
                    val number = cursor.getString(numberIndex)
                    if (number == null) continue

                    val numberCleaned = number.replace(Regex("[\\s\\-()\\+]"), "")

                    // Check if the numbers match (comparing last 10 digits for flexibility)
                    if (numberCleaned.isNotEmpty() && cleanedNumber.isNotEmpty()) {
                        val minLength = minOf(10, minOf(numberCleaned.length, cleanedNumber.length))
                        if (numberCleaned.takeLast(minLength) == cleanedNumber.takeLast(minLength)) {
                            val id = cursor.getString(idIndex) ?: continue
                            val name = cursor.getString(nameIndex) ?: "Unknown"
                            val starred = try {
                                cursor.getInt(starredIndex) == 1
                            } catch (e: Exception) {
                                false
                            }

                            return Contact(
                                id = id,
                                name = name,
                                phoneNumber = phoneNumber,
                                isFavorite = starred
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing contact row", e)
                    continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact by number", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * Get all contacts from the device
     */
    fun getAllContacts(): List<Contact> {
        if (!hasContactsPermission()) {
            Log.w(TAG, "No contacts permission")
            return emptyList()
        }

        val contactsMap = mutableMapOf<String, Contact>()
        var cursor: Cursor? = null

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null")
                return emptyList()
            }

            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

            if (idIndex < 0 || nameIndex < 0 || numberIndex < 0 || starredIndex < 0) {
                Log.w(TAG, "Invalid column indices")
                return emptyList()
            }

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val number = cursor.getString(numberIndex) ?: ""
                    val starred = try {
                        cursor.getInt(starredIndex) == 1
                    } catch (e: Exception) {
                        false
                    }

                    // Avoid duplicates - keep only one number per contact
                    if (!contactsMap.containsKey(id) && number.isNotEmpty()) {
                        contactsMap[id] = Contact(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            isFavorite = starred
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing contact row", e)
                    continue
                }
            }

            Log.d(TAG, "Retrieved ${contactsMap.size} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all contacts", e)
        } finally {
            cursor?.close()
        }

        return contactsMap.values.toList()
    }

    /**
     * Get favorite/starred contacts
     */
    fun getFavoriteContacts(): List<Contact> {
        if (!hasContactsPermission()) {
            Log.w(TAG, "No contacts permission")
            return emptyList()
        }

        val contactsMap = mutableMapOf<String, Contact>()
        var cursor: Cursor? = null

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            val selection = "${ContactsContract.CommonDataKinds.Phone.STARRED} = ?"
            val selectionArgs = arrayOf("1")

            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null")
                return emptyList()
            }

            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            if (idIndex < 0 || nameIndex < 0 || numberIndex < 0) {
                Log.w(TAG, "Invalid column indices")
                return emptyList()
            }

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val number = cursor.getString(numberIndex) ?: ""

                    // Avoid duplicates
                    if (!contactsMap.containsKey(id) && number.isNotEmpty()) {
                        contactsMap[id] = Contact(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            isFavorite = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing favorite contact row", e)
                    continue
                }
            }

            Log.d(TAG, "Retrieved ${contactsMap.size} favorite contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorite contacts", e)
        } finally {
            cursor?.close()
        }

        return contactsMap.values.toList()
    }

    /**
     * Search contacts by name or phone number
     */
    fun searchContacts(query: String): List<Contact> {
        if (query.isEmpty()) {
            return emptyList()
        }

        if (!hasContactsPermission()) {
            Log.w(TAG, "No contacts permission")
            return emptyList()
        }

        return try {
            getAllContacts().filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                        contact.phoneNumber.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts", e)
            emptyList()
        }
    }
}