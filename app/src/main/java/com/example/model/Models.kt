package com.example.model

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

enum class AssistantMode(val title: String, val description: String) {
    ASSISTANT("Assistant", "Helpful, efficient, capable, and structured for daily productivity."),
    FRIEND("Friend", "Warm, casual, witty, cheerful, and great for conversational banter."),
    COMPANION("Companion", "Empathetic, attentive, deep listener, emotionally attuned and caring."),
    ROMANTIC("Romantic", "Sweet, gentle, poetic, and heartwarming with respectful warmth.")
}

enum class GeminiVoice(val voiceName: String, val displayName: String, val description: String) {
    KORE("Kore", "Kore", "Warm, natural, and expressive tone"),
    AOEDE("Aoede", "Aoede", "Clear, luminous, and melodic tone")
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val audioBytes: ByteArray? = null,
    val audioMimeType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val actionExecuted: String? = null,
    val actionSuccess: Boolean? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChatMessage
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class UserPreferences(
    val userName: String = "",
    val preferredLanguage: String = "Auto (English / Urdu)",
    val assistantMode: AssistantMode = AssistantMode.ASSISTANT,
    val geminiVoice: GeminiVoice = GeminiVoice.KORE,
    val customInstructions: String = "",
    val memoryNotes: List<String> = emptyList(),
    val manualApiKey: String = ""
)
