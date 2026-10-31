package com.example.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun ask(
        provider: String,
        history: List<ChatMessage>,
        memory: String,
        openAiKey: String,
        claudeKey: String,
        openAiModel: String,
        claudeModel: String
    ): String = withContext(Dispatchers.IO) {
        require(history.isNotEmpty()) { "Write a message first." }
        val system = """
            You are JARVIS, a capable personal AI assistant for an Android phone.
            Be helpful, accurate, calm, concise, and use polished British English when natural.
            Do not claim to control phone features unless the app actually performs the action.
            Explain uncertainty honestly. Keep responses readable on a phone.
            User-provided personal memory (may be empty): ${memory.take(4000)}
        """.trimIndent()

        val isClaude = provider == "Claude"
        val key = if (isClaude) claudeKey else openAiKey
        require(key.isNotBlank()) {
            if (isClaude) "Add your Anthropic API key in Settings." else "Add your OpenAI API key in Settings."
        }

        val body = if (isClaude) {
            val messages = JSONArray()
            history.takeLast(24).forEach { msg ->
                if (msg.role == "user" || msg.role == "assistant") {
                    messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            }
            JSONObject()
                .put("model", claudeModel.ifBlank { "claude-3-5-haiku-20241022" })
                .put("max_tokens", 1800)
                .put("system", system)
                .put("messages", messages)
        } else {
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            history.takeLast(24).forEach { msg ->
                if (msg.role == "user" || msg.role == "assistant") {
                    messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            }
            JSONObject()
                .put("model", openAiModel.ifBlank { "gpt-4o-mini" })
                .put("messages", messages)
                .put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(if (isClaude) "https://api.anthropic.com/v1/messages"
                 else "https://api.openai.com/v1/chat/completions")
            .addHeader(if (isClaude) "x-api-key" else "Authorization",
                if (isClaude) key else "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .apply { if (isClaude) addHeader("anthropic-version", "2023-06-01") }
            .post(body.toString().toRequestBody(jsonType))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    val error = JSONObject(raw).optJSONObject("error")
                    error?.optString("message") ?: raw.take(500)
                }.getOrDefault(raw.take(500))
                throw IllegalStateException("Provider returned ${response.code}: $detail")
            }
            val obj = JSONObject(raw)
            val answer = if (isClaude) {
                obj.optJSONArray("content")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("text") }
                        .filter { it.isNotBlank() }.joinToString("\n")
                }.orEmpty()
            } else {
                obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?.optString("content").orEmpty()
            }
            answer.ifBlank { "I received an empty response. Please try again." }
        }
    }
}
