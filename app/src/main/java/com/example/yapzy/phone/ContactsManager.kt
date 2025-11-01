package com.example.yapzy.phone

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri? = null,
    val isFavorite: Boolean = false
)

class ContactsManager(private val context: Context) {

    companion object {
        private const val TAG = "ContactsManager"
    }

    fun getContactByNumber(phoneNumber: String): Contact? {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            val projection = arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            )

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)

                    val id = if (idIndex >= 0) it.getString(idIndex) else phoneNumber
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else phoneNumber
                    val photoUriString = if (photoIndex >= 0) it.getString(photoIndex) else null
                    val photoUri = photoUriString?.let { uriStr -> Uri.parse(uriStr) }

                    return Contact(
                        id = id,
                        name = name,
                        phoneNumber = phoneNumber,
                        photoUri = photoUri,
                        isFavorite = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact by number: $phoneNumber", e)
        }

        return null
    }

    fun getAllContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val id = if (idIndex >= 0) it.getString(idIndex) else ""
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                    val photoUriString = if (photoIndex >= 0) it.getString(photoIndex) else null
                    val photoUri = photoUriString?.let { uriStr -> Uri.parse(uriStr) }

                    if (number.isNotEmpty()) {
                        contacts.add(
                            Contact(
                                id = id,
                                name = name,
                                phoneNumber = number,
                                photoUri = photoUri,
                                isFavorite = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all contacts", e)
        }

        return contacts
    }

    fun getFavoriteContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            val selection = "${ContactsContract.CommonDataKinds.Phone.STARRED} = ?"
            val selectionArgs = arrayOf("1")

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val id = if (idIndex >= 0) it.getString(idIndex) else ""
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                    val photoUriString = if (photoIndex >= 0) it.getString(photoIndex) else null
                    val photoUri = photoUriString?.let { uriStr -> Uri.parse(uriStr) }

                    if (number.isNotEmpty()) {
                        contacts.add(
                            Contact(
                                id = id,
                                name = name,
                                phoneNumber = number,
                                photoUri = photoUri,
                                isFavorite = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorite contacts", e)
        }

        return contacts
    }
}