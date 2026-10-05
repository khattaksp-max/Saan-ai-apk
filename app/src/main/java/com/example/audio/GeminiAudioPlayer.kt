package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class GeminiAudioPlayer(
    private val context: Context,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackProgress: (Float) -> Unit, // 0.0 to 1.0 simulated/sampled wave for visualizer
    private val onPlaybackCompleted: () -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "GeminiAudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var visualizerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile
    var isPlaying = false
        private set

    fun playAudio(audioBytes: ByteArray, mimeType: String? = null) {
        stop()

        if (audioBytes.isEmpty()) {
            onError("Received empty audio response.")
            onPlaybackCompleted()
            return
        }

        try {
            // Ensure audio has valid container header (convert raw PCM to WAV if needed)
            val (playableBytes, determinedMime) = WavUtils.ensurePlayableAudio(audioBytes, mimeType)

            // Write to safe cache file for MediaPlayer
            val cacheFile = File(context.cacheDir, "sana_speech_${System.currentTimeMillis()}.${if (determinedMime.contains("mp3")) "mp3" else "wav"}")
            FileOutputStream(cacheFile).use { fos ->
                fos.write(playableBytes)
                fos.flush()
            }

            requestAudioFocus()

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(cacheFile.absolutePath)
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        isPlaying = true
                        onPlaybackStarted()
                        startVisualizerLoop(mp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting playback: ${e.message}")
                        releasePlayback()
                        onError("Playback start error: ${e.localizedMessage}")
                    }
                }
                setOnCompletionListener {
                    cleanUpFile(cacheFile)
                    releasePlayback()
                    onPlaybackCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    cleanUpFile(cacheFile)
                    releasePlayback()
                    onError("Audio playback error ($what, $extra)")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio player: ${e.message}", e)
            releasePlayback()
            onError("Audio playback setup failed: ${e.localizedMessage}")
            onPlaybackCompleted()
        }
    }

    private fun startVisualizerLoop(player: MediaPlayer) {
        visualizerJob?.cancel()
        visualizerJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (isActive && isPlaying) {
                try {
                    if (player.isPlaying) {
                        // Generate rich dynamic speaking visualizer rhythm
                        phase += 0.35
                        val base = (kotlin.math.sin(phase) + 1.0) / 2.0
                        val harmonic = (kotlin.math.sin(phase * 2.3) + 1.0) / 4.0
                        val amplitude = (base * 0.7 + harmonic * 0.3).toFloat().coerceIn(0.15f, 1.0f)
                        onPlaybackProgress(amplitude)
                    }
                } catch (_: Exception) {
                    break
                }
                delay(60)
            }
            onPlaybackProgress(0f)
        }
    }

    private fun requestAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            stop()
                        }
                    }
                    .build()
                audioManager.requestAudioFocus(focusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            stop()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request warning: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest!!)
                focusRequest = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon warning: ${e.message}")
        }
    }

    private fun releasePlayback() {
        isPlaying = false
        visualizerJob?.cancel()
        visualizerJob = null
        abandonAudioFocus()

        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun cleanUpFile(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    fun stop() {
        releasePlayback()
    }
}
