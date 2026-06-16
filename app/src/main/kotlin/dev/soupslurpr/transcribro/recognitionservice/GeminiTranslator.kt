package dev.soupslurpr.transcribro.recognitionservice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online translation via Gemini 3.1 Flash-Lite (free tier). On-device TranslateGemma is the offline
 * counterpart (same prompt). Sends text off-device, so route sensitive content to on-device instead.
 *
 * NOTE: verify the model id "gemini-3.1-flash-lite" against the live API before relying on it.
 */
object GeminiTranslator {
    private const val MODEL = "gemini-3.1-flash-lite"

    private fun endpoint(apiKey: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

    /**
     * Translate [text] (English) into the language with code [targetLanguage] (see [Languages]) in
     * the given [tone] ("Casual" or "Normal"), as a male speaker. Returns [text] unchanged if blank.
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        tone: String,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        val langName = Languages.englishName(targetLanguage)
        val prompt = buildTranslatePrompt(text, langName, targetLanguage, tone)

        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }

        val conn = (URL(endpoint(apiKey)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("Gemini translation failed: HTTP ${conn.responseCode} $err")
            }
            val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            resp.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } finally {
            conn.disconnect()
        }
    }

    /** Shared prompt builder — also used by the on-device TranslateGemma path. */
    fun buildTranslatePrompt(text: String, langName: String, targetLanguage: String, tone: String): String {
        val toneDesc = if (tone == "Casual") {
            "casual, friendly tone, like texting a close friend"
        } else {
            "standard polite-neutral tone, natural and safe to send to anyone (not slangy, not stiff)"
        }
        return buildString {
            append("Translate the following English message into $langName. ")
            append("The speaker is MALE — use the correct masculine and politeness forms for $langName")
            if (targetLanguage == "th") append(" (use ผม and ครับ where natural)")
            append(". Tone: $toneDesc. Preserve numbers, times, and names exactly. ")
            append("Output ONLY the $langName translation, no explanation, no romanization.\n\n")
            append(text)
        }
    }
}
