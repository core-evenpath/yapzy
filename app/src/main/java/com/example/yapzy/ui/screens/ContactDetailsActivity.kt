// app/src/main/java/com/example/yapzy/ui/screens/ContactDetailsActivity.kt
package com.example.yapzy.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.yapzy.phone.Contact
import com.example.yapzy.phone.ContactsManager
import com.example.yapzy.phone.PhoneManager
import com.example.yapzy.ui.theme.YapzyTheme
import com.google.gson.Gson

class ContactDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactJson = intent.getStringExtra("contact_json") ?: run {
            finish()
            return
        }

        val contact = Gson().fromJson(contactJson, Contact::class.java)
        val contactsManager = ContactsManager(this)
        val phoneManager = PhoneManager(this)

        setContent {
            YapzyTheme {
                ContactDetailsScreen(
                    contact = contact,
                    onBackClick = { finish() },
                    onCallClick = {
                        try {
                            phoneManager.makeCall(contact.phoneNumber)
                            finish()
                        } catch (e: Exception) {
                            // Handle error
                        }
                    },
                    onMessageClick = { finish() }
                )
            }
        }
    }
}