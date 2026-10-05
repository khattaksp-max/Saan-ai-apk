package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.GeminiVoice
import com.example.model.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

class MemoryRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "sana_memory_prefs"
        private const val KEY_PREFERENCES = "user_preferences"
        private const val KEY_CHAT_HISTORY = "chat_history"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPreferences(): UserPreferences {
        val jsonStr = prefs.getString(KEY_PREFERENCES, null) ?: return UserPreferences()
        return try {
            val obj = JSONObject(jsonStr)
            val modeStr = obj.optString("assistantMode", AssistantMode.ASSISTANT.name)
            val voiceStr = obj.optString("geminiVoice", GeminiVoice.KORE.name)
            val notesArr = obj.optJSONArray("memoryNotes") ?: JSONArray()
            val notes = mutableListOf<String>()
            for (i in 0 until notesArr.length()) {
                notes.add(notesArr.getString(i))
            }

            UserPreferences(
                userName = obj.optString("userName", ""),
                preferredLanguage = obj.optString("preferredLanguage", "Auto (English / Urdu)"),
                assistantMode = try { AssistantMode.valueOf(modeStr) } catch (_: Exception) { AssistantMode.ASSISTANT },
                geminiVoice = try { GeminiVoice.valueOf(voiceStr) } catch (_: Exception) { GeminiVoice.KORE },
                customInstructions = obj.optString("customInstructions", ""),
                memoryNotes = notes,
                manualApiKey = obj.optString("manualApiKey", "")
            )
        } catch (_: Exception) {
            UserPreferences()
        }
    }

    fun savePreferences(preferences: UserPreferences) {
        try {
            val obj = JSONObject().apply {
                put("userName", preferences.userName)
                put("preferredLanguage", preferences.preferredLanguage)
                put("assistantMode", preferences.assistantMode.name)
                put("geminiVoice", preferences.geminiVoice.name)
                put("customInstructions", preferences.customInstructions)
                put("manualApiKey", preferences.manualApiKey)

                val notesArr = JSONArray()
                preferences.memoryNotes.forEach { notesArr.put(it) }
                put("memoryNotes", notesArr)
            }
            prefs.edit().putString(KEY_PREFERENCES, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    fun loadChatHistory(): List<ChatMessage> {
        val jsonStr = prefs.getString(KEY_CHAT_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        isUser = item.optBoolean("isUser", false),
                        text = item.optString("text", ""),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        actionExecuted = item.optString("actionExecuted", "").takeIf { it.isNotBlank() },
                        actionSuccess = if (item.has("actionSuccess")) item.getBoolean("actionSuccess") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChatHistory(history: List<ChatMessage>) {
        try {
            // Keep last 50 messages to prevent unbounded storage
            val trimmed = history.takeLast(50)
            val arr = JSONArray()
            for (msg in trimmed) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("isUser", msg.isUser)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    msg.actionExecuted?.let { put("actionExecuted", it) }
                    msg.actionSuccess?.let { put("actionSuccess", it) }
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_CHAT_HISTORY, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun clearChatHistory() {
        prefs.edit().remove(KEY_CHAT_HISTORY).apply()
    }

    fun clearMemory() {
        val current = loadPreferences()
        val cleared = current.copy(
            memoryNotes = emptyList(),
            customInstructions = ""
        )
        savePreferences(cleared)
    }
}
