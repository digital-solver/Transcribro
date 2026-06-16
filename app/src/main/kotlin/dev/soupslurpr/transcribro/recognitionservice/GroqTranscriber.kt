package dev.soupslurpr.transcribro.recognitionservice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online ASR via Groq's free-tier Whisper large-v3-turbo. Used when connected; on-device whisper.cpp
 * (ACFT-small) is the offline / private fallback. Mirrors the translation hybrid.
 *
 * Free tier ~2,000 audio requests/day, no card. NOTE: sends audio off-device — route anything
 * sensitive to the on-device path instead.
 */
object GroqTranscriber {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val MODEL = "whisper-large-v3-turbo"

    /**
     * Transcribe mono PCM audio via Groq.
     * @param samples normalized float PCM in [-1, 1] (what the VAD / whisper path already produces)
     * @param sampleRate Hz (16000)
     * @param apiKey user's Groq API key
     * @param language ISO code to bias recognition (null = autodetect)
     */
    suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        apiKey: String,
        language: String? = "en",
    ): String = withContext(Dispatchers.IO) {
        val wav = pcmFloatToWav(samples, sampleRate)
        val boundary = "----transcribro${System.nanoTime()}"
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.writeBytes("$value\r\n")
                }
                field("model", MODEL)
                field("response_format", "json")
                if (language != null) field("language", language)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
                out.writeBytes("Content-Type: audio/wav\r\n\r\n")
                out.write(wav)
                out.writeBytes("\r\n")
                out.writeBytes("--$boundary--\r\n")
            }
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("Groq transcription failed: HTTP ${conn.responseCode} $err")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("text").trim()
        } finally {
            conn.disconnect()
        }
    }

    /** Wrap normalized float PCM as a 16-bit mono WAV (what Groq expects). */
    private fun pcmFloatToWav(samples: FloatArray, sampleRate: Int): ByteArray {
        val pcm = ByteArrayOutputStream(samples.size * 2)
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
            pcm.write(v and 0xFF)
            pcm.write((v shr 8) and 0xFF)
        }
        val data = pcm.toByteArray()
        val out = ByteArrayOutputStream(44 + data.size)
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        val byteRate = sampleRate * 2 // mono, 16-bit
        out.write("RIFF".toByteArray()); le32(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1) // PCM, mono
        le32(sampleRate); le32(byteRate); le16(2); le16(16)
        out.write("data".toByteArray()); le32(data.size); out.write(data)
        return out.toByteArray()
    }
}
