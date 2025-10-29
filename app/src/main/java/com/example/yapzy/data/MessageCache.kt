package com.example.yapzy.data

import android.content.Context
import android.content.SharedPreferences
import com.example.yapzy.models.Conversation
import com.example.yapzy.models.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MessageCache(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("message_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    // In-memory cache for faster access
    private val conversationsCache = mutableMapOf<String, List<Conversation>>()
    private val messagesCache = mutableMapOf<String, List<Message>>()

    fun saveConversations(conversations: List<Conversation>) {
        conversationsCache["all"] = conversations
        prefs.edit().putString("conversations", gson.toJson(conversations)).apply()
        prefs.edit().putLong("conversations_timestamp", System.currentTimeMillis()).apply()
    }

    fun getConversations(): List<Conversation>? {
        // Try in-memory first
        conversationsCache["all"]?.let { return it }

        // Then try SharedPreferences
        val json = prefs.getString("conversations", null) ?: return null
        return try {
            val type = object : TypeToken<List<Conversation>>() {}.type
            val conversations: List<Conversation> = gson.fromJson(json, type)
            conversationsCache["all"] = conversations
            conversations
        } catch (e: Exception) {
            null
        }
    }

    fun saveMessages(conversationId: String, messages: List<Message>) {
        messagesCache[conversationId] = messages
        prefs.edit().putString("messages_$conversationId", gson.toJson(messages)).apply()
        prefs.edit().putLong("messages_${conversationId}_timestamp", System.currentTimeMillis()).apply()
    }

    fun getMessages(conversationId: String): List<Message>? {
        // Try in-memory first
        messagesCache[conversationId]?.let { return it }

        // Then try SharedPreferences
        val json = prefs.getString("messages_$conversationId", null) ?: return null
        return try {
            val type = object : TypeToken<List<Message>>() {}.type
            val messages: List<Message> = gson.fromJson(json, type)
            messagesCache[conversationId] = messages
            messages
        } catch (e: Exception) {
            null
        }
    }

    fun getCacheAge(key: String): Long {
        val timestamp = prefs.getLong("${key}_timestamp", 0)
        return System.currentTimeMillis() - timestamp
    }

    fun clearCache() {
        conversationsCache.clear()
        messagesCache.clear()
        prefs.edit().clear().apply()
    }

    fun clearConversationCache(conversationId: String) {
        messagesCache.remove(conversationId)
        prefs.edit().remove("messages_$conversationId").apply()
        prefs.edit().remove("messages_${conversationId}_timestamp").apply()
    }
}