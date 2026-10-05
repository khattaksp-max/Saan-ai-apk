package com.example.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AssistantMode
import com.example.model.GeminiVoice
import com.example.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiVoiceResponse(
    val text: String,
    val audioBytes: ByteArray?,
    val audioMimeType: String?
)

class GeminiVoiceRepository {
    companion object {
        private const val TAG = "GeminiVoiceRepository"
        // Primary native audio model as specified in guidelines
        private const val MODEL_NATIVE_AUDIO = "gemini-2.5-flash-native-audio-preview-12-2025"
        // Secondary fallback models for maximum reliability across regions/keys
        private const val MODEL_TTS = "gemini-2.5-flash-preview-tts"
        private const val MODEL_FLASH = "gemini-2.5-flash"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateVoiceContent(
        userPrompt: String? = null,
        userAudioBytes: ByteArray? = null,
        conversationHistory: List<Pair<String, String>>, // role ("user"/"model") to text
        userPreferences: UserPreferences,
        manualKey: String? = null
    ): Result<GeminiVoiceResponse> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey(manualKey)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini AI is not configured yet. Please add the required API configuration in the AI Studio Secrets panel or Settings.")
            )
        }

        // Try primary native audio model first, then fallback models if needed
        val modelsToTry = listOf(MODEL_NATIVE_AUDIO, MODEL_TTS, MODEL_FLASH)
        var lastError: Exception? = null

        for (model in modelsToTry) {
            try {
                val response = callGeminiApi(
                    model = model,
                    apiKey = apiKey,
                    userPrompt = userPrompt,
                    userAudioBytes = userAudioBytes,
                    conversationHistory = conversationHistory,
                    userPreferences = userPreferences
                )
                return@withContext Result.success(response)
            } catch (e: Exception) {
                Log.w(TAG, "Model $model returned error: ${e.message}. Trying fallback if available.")
                lastError = e
                // If it's a quota (429) or invalid key (403), no need to retry models
                val msg = e.message ?: ""
                if (msg.contains("API_KEY_INVALID") || msg.contains("403") || msg.contains("429") || msg.contains("Quota")) {
                    break
                }
            }
        }

        Result.failure(lastError ?: Exception("Unknown error communicating with Gemini API."))
    }

    private fun resolveApiKey(manualKey: String?): String {
        if (!manualKey.isNullOrBlank()) return manualKey.trim()
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }
        return buildKey.trim()
    }

    private fun callGeminiApi(
        model: String,
        apiKey: String,
        userPrompt: String?,
        userAudioBytes: ByteArray?,
        conversationHistory: List<Pair<String, String>>,
        userPreferences: UserPreferences
    ): GeminiVoiceResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val rootJson = JSONObject()

        // 1. System Instruction
        val systemInstructionJson = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().put("text", buildSystemPrompt(userPreferences)))
        systemInstructionJson.put("parts", sysParts)
        rootJson.put("systemInstruction", systemInstructionJson)

        // 2. Contents
        val contentsArray = JSONArray()

        // Past conversation context (last 6 turns for optimal latency and audio reasoning)
        val recentTurns = conversationHistory.takeLast(6)
        for ((role, text) in recentTurns) {
            val turnObj = JSONObject()
            turnObj.put("role", if (role == "user") "user" else "model")
            val parts = JSONArray()
            parts.put(JSONObject().put("text", text))
            turnObj.put("parts", parts)
            contentsArray.put(turnObj)
        }

        // Current user turn
        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()

        if (userAudioBytes != null && userAudioBytes.isNotEmpty()) {
            val audioObj = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", "audio/wav")
            inlineData.put("data", Base64.encodeToString(userAudioBytes, Base64.NO_WRAP))
            audioObj.put("inlineData", inlineData)
            currentParts.put(audioObj)
        }

        if (!userPrompt.isNullOrBlank()) {
            val textObj = JSONObject()
            textObj.put("text", userPrompt)
            currentParts.put(textObj)
        }

        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)
        rootJson.put("contents", contentsArray)

        // 3. GenerationConfig with Native Audio & SpeechConfig
        val genConfig = JSONObject()
        val modalities = JSONArray()
        modalities.put("TEXT")
        modalities.put("AUDIO")
        genConfig.put("responseModalities", modalities)

        val voiceName = userPreferences.geminiVoice.voiceName
        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", voiceName)
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        genConfig.put("speechConfig", speechConfig)

        rootJson.put("generationConfig", genConfig)

        val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val httpResponse = httpClient.newCall(request).execute()
        val responseBodyStr = httpResponse.body?.string() ?: ""

        if (!httpResponse.isSuccessful) {
            val errorMsg = parseErrorMessage(httpResponse.code, responseBodyStr)
            throw RuntimeException(errorMsg)
        }

        return parseGeminiResponse(responseBodyStr)
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: ""
            when {
                code == 400 -> "Bad Request: $message"
                code == 403 || message.contains("API_KEY_INVALID") -> "Invalid Gemini API Key. Please verify your API key."
                code == 429 || message.contains("RESOURCE_EXHAUSTED") -> "Gemini API rate limit or quota exceeded. Please try again in a moment."
                code == 404 -> "Model not available in current configuration."
                else -> "Gemini Error ($code): $message"
            }
        } catch (_: Exception) {
            "Network error ($code): ${body.take(120)}"
        }
    }

    private fun parseGeminiResponse(body: String): GeminiVoiceResponse {
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw RuntimeException("No response received from Gemini.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        var responseText = ""
        var audioBytes: ByteArray? = null
        var audioMimeType: String? = null

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                if (part.has("text")) {
                    val t = part.optString("text", "")
                    if (t.isNotBlank()) {
                        responseText = if (responseText.isBlank()) t else "$responseText\n$t"
                    }
                }

                if (part.has("inlineData")) {
                    val inline = part.getJSONObject("inlineData")
                    val mime = inline.optString("mimeType", "audio/wav")
                    val b64 = inline.optString("data", "")
                    if (b64.isNotBlank()) {
                        try {
                            audioBytes = Base64.decode(b64, Base64.DEFAULT)
                            audioMimeType = mime
                        } catch (e: Exception) {
                            Log.e(TAG, "Base64 decode audio error: ${e.message}")
                        }
                    }
                }
            }
        }

        if (responseText.isBlank() && audioBytes == null) {
            throw RuntimeException("Gemini returned an empty response.")
        }

        return GeminiVoiceResponse(
            text = responseText.trim(),
            audioBytes = audioBytes,
            audioMimeType = audioMimeType
        )
    }

    private fun buildSystemPrompt(userPreferences: UserPreferences): String {
        val modeDesc = when (userPreferences.assistantMode) {
            AssistantMode.ASSISTANT -> "You are in Assistant Mode: Be efficient, highly organized, direct, polite, and helpful with daily tasks, device control, questions, and ideas."
            AssistantMode.FRIEND -> "You are in Friend Mode: Be warm, playful, witty, cheerful, and talk like a close trusted companion."
            AssistantMode.COMPANION -> "You are in Companion Mode: Be deeply empathetic, attentive, comforting, thoughtful, and emotionally supportive."
            AssistantMode.ROMANTIC -> "You are in Romantic Mode: Be gentle, poetic, heartwarming, sweet, and caring. Remain strictly respectful, tasteful, and non-explicit."
        }

        val userContext = buildString {
            if (userPreferences.userName.isNotBlank()) {
                append("The user's name is ${userPreferences.userName}. ")
            }
            if (userPreferences.memoryNotes.isNotEmpty()) {
                append("User Memory & Stored Facts:\n")
                userPreferences.memoryNotes.forEach { note ->
                    append("- $note\n")
                }
            }
            if (userPreferences.customInstructions.isNotBlank()) {
                append("Custom User Instructions: ${userPreferences.customInstructions}\n")
            }
        }

        return """
            You are SANA, a state-of-the-art intelligent AI voice assistant and personal companion.
            
            Key Personality & Conversational Style:
            - You speak with real Gemini Native Audio directly to the user.
            - Keep your responses natural, expressive, concise, and conversational (1-3 sentences), perfectly tailored for spoken dialogue.
            - $modeDesc
            
            Language Support:
            - Fully support English, Urdu (اردو), and Roman Urdu.
            - Automatically detect the user's language and respond in the EXACT same language and script/style used by the user.
            - For Roman Urdu queries (e.g. "WhatsApp kholo", "Kaisi ho?"), reply in Roman Urdu (e.g. "Ji, main bilkul theek hoon! WhatsApp khol rahi hoon.").
            - For Urdu script queries (e.g. "کیسی ہو؟"), reply in Urdu script (e.g. "میں بالکل ٹھیک ہوں، آپ کی کیا مدد کر سکتی ہوں؟").
            - For English queries, reply in fluent, natural English.
            
            Android Phone Control & App Launching:
            - If the user asks you to open an app, use device control, or launch a supported application (WhatsApp, YouTube, Camera, Settings, Phone/Dialer, Browser):
              1. PREPEND your response with the exact tag:
                 - [ACTION:OPEN_WHATSAPP] for WhatsApp
                 - [ACTION:OPEN_YOUTUBE] for YouTube
                 - [ACTION:OPEN_CAMERA] for Camera
                 - [ACTION:OPEN_SETTINGS] for System Settings
                 - [ACTION:OPEN_DIALER] for Phone
                 - [ACTION:OPEN_BROWSER] for Browser
              2. Immediately follow with a natural, friendly confirmation in the user's language.
              Examples:
              User: "SANA, open WhatsApp" -> "[ACTION:OPEN_WHATSAPP] Sure, I'm opening WhatsApp for you."
              User: "WhatsApp kholo" -> "[ACTION:OPEN_WHATSAPP] Ji, WhatsApp khol rahi hoon."
              User: "واٹس ایپ کھولو" -> "[ACTION:OPEN_WHATSAPP] جی، میں واٹس ایپ کھول رہی ہوں۔"
              User: "Open Camera" -> "[ACTION:OPEN_CAMERA] Opening your camera right away."
              User: "Open YouTube" -> "[ACTION:OPEN_YOUTUBE] Sure! Opening YouTube."
            
            $userContext
        """.trimIndent()
    }
}
