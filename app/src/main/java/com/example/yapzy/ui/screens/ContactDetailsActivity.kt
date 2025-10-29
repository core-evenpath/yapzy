package com.example.yapzy.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.yapzy.phone.Contact
import com.example.yapzy.ui.theme.YapzyTheme
import com.google.gson.Gson

class ContactDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactJson = intent.getStringExtra("contact_json") ?: run {
            finish()
            return
        }

        val gson = Gson()
        val contact = gson.fromJson(contactJson, Contact::class.java)

        setContent {
            YapzyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContactDetailsScreen(
                        contact = contact,
                        onBackClick = { finish() },
                        onCallClick = { finish() },
                        onMessageClick = { finish() }
                    )
                }
            }
        }
    }
}