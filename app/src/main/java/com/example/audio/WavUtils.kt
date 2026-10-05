package com.example.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtils {

    /**
     * Checks if data starts with RIFF header (standard WAV file).
     */
    fun isWav(data: ByteArray): Boolean {
        if (data.size < 12) return false
        return data[0] == 'R'.code.toByte() &&
               data[1] == 'I'.code.toByte() &&
               data[2] == 'F'.code.toByte() &&
               data[3] == 'F'.code.toByte() &&
               data[8] == 'W'.code.toByte() &&
               data[9] == 'A'.code.toByte() &&
               data[10] == 'V'.code.toByte() &&
               data[11] == 'E'.code.toByte()
    }

    /**
     * Checks if data starts with ID3 header or MP3 sync frame.
     */
    fun isMp3(data: ByteArray): Boolean {
        if (data.size < 3) return false
        // ID3v2 tag
        if (data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
            return true
        }
        // MPEG audio frame sync 11111111 111...
        return (data[0].toInt() and 0xFF) == 0xFF && ((data[1].toInt() and 0xE0) == 0xE0)
    }

    /**
     * Adds a canonical 44-byte WAV header to 16-bit Mono PCM byte data.
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            // RIFF chunk
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())
            putInt(totalDataLen)
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())

            // fmt sub-chunk
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat: 1 = PCM
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample)

            // data sub-chunk
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())
            putInt(totalAudioLen)
        }.array()

        val out = ByteArrayOutputStream(44 + totalAudioLen)
        out.write(header)
        out.write(pcmData)
        return out.toByteArray()
    }

    /**
     * Ensures any audio payload from Gemini is in a container format playable by MediaPlayer.
     * If already WAV or MP3, returns as-is. If raw PCM, prepends WAV header.
     */
    fun ensurePlayableAudio(
        data: ByteArray,
        mimeType: String? = null
    ): Pair<ByteArray, String> {
        if (isWav(data)) {
            return Pair(data, "audio/wav")
        }
        if (isMp3(data)) {
            return Pair(data, "audio/mp3")
        }

        // Determine sample rate if specified in mimeType like "audio/pcm;rate=24000"
        var sampleRate = 24000
        if (mimeType != null && mimeType.contains("rate=")) {
            val rateStr = mimeType.substringAfter("rate=").substringBefore(";").substringBefore(" ")
            rateStr.toIntOrNull()?.let { sampleRate = it }
        }

        val wavData = pcmToWav(data, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
        return Pair(wavData, "audio/wav")
    }
}
