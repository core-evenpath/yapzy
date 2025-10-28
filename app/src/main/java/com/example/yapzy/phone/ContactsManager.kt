package com.example.yapzy.phone

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)

/**
 * Manager class for accessing and managing device contacts
 */
class ContactsManager(private val context: Context) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Retrieve all contacts from the device
     */
    fun getAllContacts(): List<Contact> {
        if (!hasContactsPermission()) {
            return emptyList()
        }

        val contacts = mutableListOf<Contact>()
        val contentResolver: ContentResolver = context.contentResolver

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex)
                val photoUri = it.getString(photoIndex)
                val isStarred = it.getInt(starredIndex) == 1

                contacts.add(
                    Contact(
                        id = id,
                        name = name,
                        phoneNumber = number,
                        photoUri = photoUri,
                        isFavorite = isStarred
                    )
                )
            }
        }

        return contacts
    }

    /**
     * Get favorite/starred contacts
     */
    fun getFavoriteContacts(): List<Contact> {
        return getAllContacts().filter { it.isFavorite }
    }

    /**
     * Search contacts by name or number
     */
    fun searchContacts(query: String): List<Contact> {
        return getAllContacts().filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.phoneNumber.contains(query, ignoreCase = true)
        }
    }

    /**
     * Get contact by phone number
     */
    fun getContactByNumber(phoneNumber: String): Contact? {
        if (!hasContactsPermission()) {
            return null
        }

        val contentResolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
        val selectionArgs = arrayOf(phoneNumber)

        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED
            ),
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                return Contact(
                    id = it.getString(idIndex),
                    name = it.getString(nameIndex),
                    phoneNumber = it.getString(numberIndex),
                    photoUri = it.getString(photoIndex),
                    isFavorite = it.getInt(starredIndex) == 1
                )
            }
        }

        return null
    }

    companion object {
        const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
        const val PERMISSION_WRITE_CONTACTS = Manifest.permission.WRITE_CONTACTS
    }
}
