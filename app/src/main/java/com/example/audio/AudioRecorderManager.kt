package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioRecorderManager(
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onSpeechStarted: () -> Unit,
    private val onSpeechEnded: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorderManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SPEECH_RMS_THRESHOLD = 500.0 // RMS threshold to trigger speech
        private const val SILENCE_RMS_THRESHOLD = 380.0 // RMS threshold considered silence
        private const val SILENCE_TIMEOUT_MS = 1100L // 1.1s of silence triggers end-of-speech
        private const val PRE_SPEECH_BUFFER_CHUNKS = 5 // ~320ms buffer
        private const val MAX_SPEECH_DURATION_MS = 25000L // 25 seconds safety limit
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isRecording = false

    @Volatile
    private var isPaused = false

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) {
            isPaused = false
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, 2048 * 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_RECOGNITION fails on some devices
                val fallbackRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (fallbackRecord.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Failed to initialize microphone hardware.")
                    fallbackRecord.release()
                    return
                }
                audioRecord = fallbackRecord
            } else {
                audioRecord = record
            }

            // Apply acoustic echo canceler and noise suppressor if available
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio effects init warning: ${e.message}")
                }
            }

            audioRecord?.startRecording()
            isRecording = true
            isPaused = false

            recordingJob = scope.launch {
                runRecordLoop()
            }
        } catch (e: SecurityException) {
            onError("Microphone permission denied.")
        } catch (e: Exception) {
            onError("Microphone error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun runRecordLoop() {
        val chunkShorts = 1024
        val shortBuffer = ShortArray(chunkShorts)
        val byteBuffer = ByteBuffer.allocate(chunkShorts * 2).order(ByteOrder.LITTLE_ENDIAN)

        val preSpeechQueue = ArrayDeque<ByteArray>()
        val speechStream = ByteArrayOutputStream()

        var isSpeechActive = false
        var speechStartTime = 0L
        var silenceStartTime: Long? = null

        while (scope.isActive && isRecording) {
            if (isPaused) {
                // Paused during SANA speaking to prevent self-listening
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {}
                continue
            }

            val readCount = audioRecord?.read(shortBuffer, 0, chunkShorts) ?: -1
            if (readCount <= 0) {
                continue
            }

            // Compute RMS amplitude
            var sumSquare = 0.0
            for (i in 0 until readCount) {
                val s = shortBuffer[i].toDouble()
                sumSquare += s * s
            }
            val rms = sqrt(sumSquare / readCount)
            val normalizedAmp = (rms / 3000.0).coerceIn(0.0, 1.0).toFloat()
            onAmplitudeChanged(normalizedAmp)

            // Convert shorts to bytes
            byteBuffer.clear()
            for (i in 0 until readCount) {
                byteBuffer.putShort(shortBuffer[i])
            }
            val chunkBytes = byteBuffer.array().copyOf(readCount * 2)

            val now = System.currentTimeMillis()

            if (!isSpeechActive) {
                // Keep rolling pre-speech buffer
                preSpeechQueue.addLast(chunkBytes)
                if (preSpeechQueue.size > PRE_SPEECH_BUFFER_CHUNKS) {
                    preSpeechQueue.removeFirst()
                }

                if (rms >= SPEECH_RMS_THRESHOLD) {
                    // Speech detected!
                    isSpeechActive = true
                    speechStartTime = now
                    silenceStartTime = null
                    speechStream.reset()

                    // Prepend pre-speech buffer
                    for (preChunk in preSpeechQueue) {
                        speechStream.write(preChunk)
                    }
                    speechStream.write(chunkBytes)
                    preSpeechQueue.clear()

                    onSpeechStarted()
                }
            } else {
                // Speech is currently active
                speechStream.write(chunkBytes)

                val speechDuration = now - speechStartTime

                if (rms < SILENCE_RMS_THRESHOLD) {
                    if (silenceStartTime == null) {
                        silenceStartTime = now
                    } else if (now - silenceStartTime >= SILENCE_TIMEOUT_MS) {
                        // End of speech triggered after continuous silence!
                        val recordedBytes = speechStream.toByteArray()
                        isSpeechActive = false
                        silenceStartTime = null
                        speechStream.reset()

                        // Temporarily pause recording while sending & waiting
                        isPaused = true
                        onSpeechEnded(recordedBytes)
                    }
                } else {
                    // Reset silence timer on active voice
                    silenceStartTime = null
                }

                // Safety maximum duration cap
                if (speechDuration >= MAX_SPEECH_DURATION_MS) {
                    val recordedBytes = speechStream.toByteArray()
                    isSpeechActive = false
                    silenceStartTime = null
                    speechStream.reset()
                    isPaused = true
                    onSpeechEnded(recordedBytes)
                }
            }
        }
    }

    /**
     * Pauses microphone processing while SANA is speaking to prevent self-listening.
     */
    fun pauseListening() {
        isPaused = true
    }

    /**
     * Resumes microphone listening when SANA finishes speaking.
     */
    fun resumeListening() {
        isPaused = false
    }

    /**
     * Stops and releases microphone resources completely.
     */
    fun stopListening() {
        isRecording = false
        isPaused = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}
