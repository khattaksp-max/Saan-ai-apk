package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.action.PhoneActionHandler
import com.example.audio.AudioRecorderManager
import com.example.audio.GeminiAudioPlayer
import com.example.audio.WavUtils
import com.example.api.GeminiVoiceRepository
import com.example.data.MemoryRepository
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.GeminiVoice
import com.example.model.UserPreferences
import com.example.model.VoiceState
import com.example.service.SanaVoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SanaUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val isContinuousActive: Boolean = false,
    val liveAmplitude: Float = 0f,
    val lastUserQuery: String = "",
    val lastAssistantResponse: String = "",
    val activeActionNotice: String? = null,
    val errorMessage: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
)

class SanaViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SanaViewModel"
    }

    private val repository = GeminiVoiceRepository()
    private val memoryRepository = MemoryRepository(application)

    private val _uiState = MutableStateFlow(
        SanaUiState(
            preferences = memoryRepository.loadPreferences(),
            messages = memoryRepository.loadChatHistory()
        )
    )
    val uiState: StateFlow<SanaUiState> = _uiState.asStateFlow()

    private var audioRecorder: AudioRecorderManager? = null
    private var audioPlayer: GeminiAudioPlayer? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SanaVoiceService.ACTION_STOP_BROADCAST) {
                stopContinuousVoice()
            }
        }
    }

    init {
        initAudioPlayer()
        registerStopReceiver()
    }

    private fun registerStopReceiver() {
        val filter = IntentFilter(SanaVoiceService.ACTION_STOP_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                stopReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            getApplication<Application>().registerReceiver(stopReceiver, filter)
        }
    }

    private fun initAudioPlayer() {
        audioPlayer = GeminiAudioPlayer(
            context = getApplication(),
            onPlaybackStarted = {
                _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
            },
            onPlaybackProgress = { amplitude ->
                _uiState.update { it.copy(liveAmplitude = amplitude) }
            },
            onPlaybackCompleted = {
                handlePlaybackCompleted()
            },
            onError = { errMsg ->
                Log.e(TAG, "Audio player error: $errMsg")
                handlePlaybackCompleted()
            }
        )
    }

    private fun initAudioRecorder(): Boolean {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.ERROR,
                    errorMessage = "Microphone permission is required for voice conversation with SANA."
                )
            }
            return false
        }

        if (audioRecorder == null) {
            audioRecorder = AudioRecorderManager(
                onAmplitudeChanged = { amp ->
                    if (_uiState.value.voiceState == VoiceState.LISTENING) {
                        _uiState.update { it.copy(liveAmplitude = amp) }
                    }
                },
                onSpeechStarted = {
                    // Speech initiated by user
                    _uiState.update { it.copy(errorMessage = null) }
                },
                onSpeechEnded = { pcmBytes ->
                    handleUserSpeechCompleted(pcmBytes)
                },
                onError = { err ->
                    _uiState.update {
                        it.copy(
                            voiceState = VoiceState.ERROR,
                            errorMessage = err,
                            isContinuousActive = false
                        )
                    }
                    audioRecorder?.stopListening()
                }
            )
        }
        return true
    }

    /**
     * ONE-CLICK CONTINUOUS VOICE CONVERSATION ENTRY POINT.
     * Tapping this once launches the complete conversational loop!
     */
    fun startContinuousVoice() {
        if (!initAudioRecorder()) return

        _uiState.update {
            it.copy(
                isContinuousActive = true,
                voiceState = VoiceState.LISTENING,
                errorMessage = null,
                activeActionNotice = null
            )
        }

        // Start Foreground Service so microphone stays protected
        try {
            SanaVoiceService.startService(getApplication())
        } catch (e: Exception) {
            Log.w(TAG, "Service start notice: ${e.message}")
        }

        audioRecorder?.startListening()
    }

    /**
     * Stop button entry point: cleanly halts loop, releases microphone and playback.
     */
    fun stopContinuousVoice() {
        _uiState.update {
            it.copy(
                isContinuousActive = false,
                voiceState = VoiceState.IDLE,
                liveAmplitude = 0f
            )
        }

        audioRecorder?.stopListening()
        audioPlayer?.stop()

        try {
            SanaVoiceService.stopService(getApplication())
        } catch (_: Exception) {}
    }

    private fun handleUserSpeechCompleted(pcmBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.PROCESSING,
                    liveAmplitude = 0f
                )
            }

            // Convert raw PCM to 16kHz WAV format
            val wavAudio = WavUtils.pcmToWav(pcmBytes, sampleRate = 16000)

            // Add placeholder in message log
            val userMsg = ChatMessage(
                isUser = true,
                text = "Voice message",
                audioBytes = wavAudio
            )
            addMessage(userMsg)

            sendToGemini(userAudioBytes = wavAudio, userPrompt = null)
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(isUser = true, text = text.trim())
        addMessage(userMsg)

        _uiState.update {
            it.copy(
                voiceState = VoiceState.PROCESSING,
                errorMessage = null,
                lastUserQuery = text.trim()
            )
        }

        viewModelScope.launch {
            sendToGemini(userAudioBytes = null, userPrompt = text.trim())
        }
    }

    private suspend fun sendToGemini(userAudioBytes: ByteArray?, userPrompt: String?) {
        val historyTurns = _uiState.value.messages.takeLast(6).map {
            Pair(if (it.isUser) "user" else "model", it.text)
        }

        val result = repository.generateVoiceContent(
            userPrompt = userPrompt,
            userAudioBytes = userAudioBytes,
            conversationHistory = historyTurns,
            userPreferences = _uiState.value.preferences,
            manualKey = _uiState.value.preferences.manualApiKey.takeIf { it.isNotBlank() }
        )

        result.fold(
            onSuccess = { response ->
                handleGeminiSuccess(response)
            },
            onFailure = { error ->
                Log.e(TAG, "Gemini call failed: ${error.message}", error)
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.ERROR,
                        errorMessage = error.localizedMessage ?: "Failed to receive response from Gemini."
                    )
                }
                // If in continuous mode, pause briefly then retry or allow user to speak
                if (_uiState.value.isContinuousActive) {
                    delay(3000)
                    if (_uiState.value.isContinuousActive && _uiState.value.voiceState == VoiceState.ERROR) {
                        _uiState.update { it.copy(voiceState = VoiceState.LISTENING, errorMessage = null) }
                        audioRecorder?.resumeListening()
                    }
                }
            }
        )
    }

    private fun handleGeminiSuccess(response: com.example.api.GeminiVoiceResponse) {
        viewModelScope.launch(Dispatchers.Main) {
            val rawText = response.text

            // Check for phone actions (WhatsApp, YouTube, Camera, Settings, etc.)
            val detectedAction = PhoneActionHandler.extractAction(rawText)
            var actionResultText: String? = null
            var actionSuccess: Boolean? = null

            if (detectedAction != null) {
                val actionResult = PhoneActionHandler.execute(getApplication(), detectedAction)
                actionResultText = actionResult.message
                actionSuccess = actionResult.success
                _uiState.update { it.copy(activeActionNotice = actionResult.message) }
            }

            val cleanedText = PhoneActionHandler.cleanResponseText(rawText)

            val sanaMsg = ChatMessage(
                isUser = false,
                text = cleanedText.ifBlank { "Voice response" },
                audioBytes = response.audioBytes,
                audioMimeType = response.audioMimeType,
                actionExecuted = detectedAction,
                actionSuccess = actionSuccess
            )
            addMessage(sanaMsg)

            _uiState.update {
                it.copy(
                    lastAssistantResponse = cleanedText,
                    errorMessage = null
                )
            }

            // Real Gemini Native Audio output
            if (response.audioBytes != null && response.audioBytes.isNotEmpty()) {
                // SANA is speaking: pause mic so SANA never hears herself
                audioRecorder?.pauseListening()
                audioPlayer?.playAudio(response.audioBytes, response.audioMimeType)
            } else {
                // If response had no audio, transition safely
                handlePlaybackCompleted()
            }
        }
    }

    private fun handlePlaybackCompleted() {
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(liveAmplitude = 0f) }

            if (_uiState.value.isContinuousActive) {
                // Echo clearance buffer (350ms) to ensure speaker audio has dissipated completely
                delay(350)
                if (_uiState.value.isContinuousActive) {
                    _uiState.update {
                        it.copy(
                            voiceState = VoiceState.LISTENING,
                            activeActionNotice = null
                        )
                    }
                    audioRecorder?.resumeListening()
                }
            } else {
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.IDLE,
                        activeActionNotice = null
                    )
                }
            }
        }
    }

    fun replayMessageAudio(message: ChatMessage) {
        val bytes = message.audioBytes ?: return
        audioRecorder?.pauseListening()
        audioPlayer?.playAudio(bytes, message.audioMimeType)
    }

    fun retryLastMessage() {
        val lastUserMsg = _uiState.value.messages.lastOrNull { it.isUser }
        if (lastUserMsg != null) {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.PROCESSING,
                    errorMessage = null
                )
            }
            viewModelScope.launch {
                sendToGemini(userAudioBytes = lastUserMsg.audioBytes, userPrompt = lastUserMsg.text.takeIf { it != "Voice message" })
            }
        } else {
            startContinuousVoice()
        }
    }

    private fun addMessage(msg: ChatMessage) {
        _uiState.update { current ->
            val updated = current.messages + msg
            memoryRepository.saveChatHistory(updated)
            current.copy(messages = updated)
        }
    }

    fun clearChatHistory() {
        memoryRepository.clearChatHistory()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun setAssistantMode(mode: AssistantMode) {
        val updated = _uiState.value.preferences.copy(assistantMode = mode)
        memoryRepository.savePreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun setGeminiVoice(voice: GeminiVoice) {
        val updated = _uiState.value.preferences.copy(geminiVoice = voice)
        memoryRepository.savePreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun updatePreferences(
        userName: String,
        preferredLanguage: String,
        customInstructions: String,
        manualApiKey: String
    ) {
        val updated = _uiState.value.preferences.copy(
            userName = userName.trim(),
            preferredLanguage = preferredLanguage,
            customInstructions = customInstructions.trim(),
            manualApiKey = manualApiKey.trim()
        )
        memoryRepository.savePreferences(updated)
        _uiState.update { it.copy(preferences = updated, errorMessage = null) }
    }

    fun addMemoryNote(note: String) {
        if (note.isBlank()) return
        val currentNotes = _uiState.value.preferences.memoryNotes
        val updatedNotes = currentNotes + note.trim()
        val updated = _uiState.value.preferences.copy(memoryNotes = updatedNotes)
        memoryRepository.savePreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun removeMemoryNote(index: Int) {
        val currentNotes = _uiState.value.preferences.memoryNotes.toMutableList()
        if (index in currentNotes.indices) {
            currentNotes.removeAt(index)
            val updated = _uiState.value.preferences.copy(memoryNotes = currentNotes)
            memoryRepository.savePreferences(updated)
            _uiState.update { it.copy(preferences = updated) }
        }
    }

    fun clearMemory() {
        memoryRepository.clearMemory()
        _uiState.update {
            it.copy(
                preferences = it.preferences.copy(
                    memoryNotes = emptyList(),
                    customInstructions = ""
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
        audioRecorder?.stopListening()
        audioPlayer?.stop()
        try {
            SanaVoiceService.stopService(getApplication())
        } catch (_: Exception) {}
    }
}
