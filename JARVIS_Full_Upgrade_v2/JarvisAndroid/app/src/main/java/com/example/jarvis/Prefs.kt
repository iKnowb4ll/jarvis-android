package com.example.jarvis

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val content: String, val time: Long = System.currentTimeMillis())

class Prefs(context: Context) {
    private val master = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val p = EncryptedSharedPreferences.create(
        context, "jarvis_secure", master,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var openAiKey: String
        get() = p.getString("openai", "") ?: ""
        set(v) = p.edit().putString("openai", v.trim()).apply()
    var claudeKey: String
        get() = p.getString("claude", "") ?: ""
        set(v) = p.edit().putString("claude", v.trim()).apply()
    var provider: String
        get() = p.getString("provider", "OpenAI") ?: "OpenAI"
        set(v) = p.edit().putString("provider", v).apply()
    var openAiModel: String
        get() = p.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) = p.edit().putString("openai_model", v.trim()).apply()
    var claudeModel: String
        get() = p.getString("claude_model", "claude-3-5-haiku-20241022") ?: "claude-3-5-haiku-20241022"
        set(v) = p.edit().putString("claude_model", v.trim()).apply()
    var memory: String
        get() = p.getString("memory", "") ?: ""
        set(v) = p.edit().putString("memory", v.take(4000)).apply()
    var voiceEnabled: Boolean
        get() = p.getBoolean("voice_enabled", true)
        set(v) = p.edit().putBoolean("voice_enabled", v).apply()

    fun loadHistory(): List<ChatMessage> = runCatching {
        val array = JSONArray(p.getString("history", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ChatMessage(o.optString("role"), o.optString("content"), o.optLong("time"))
        }
    }.getOrDefault(emptyList())

    fun saveHistory(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(80).forEach {
            array.put(JSONObject().put("role", it.role).put("content", it.content).put("time", it.time))
        }
        p.edit().putString("history", array.toString()).apply()
    }

    fun clearHistory() = p.edit().remove("history").apply()
}
