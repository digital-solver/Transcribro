package dev.soupslurpr.transcribro.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device model download (FUTO-style): the Whisper model is fetched to internal storage on first
 * run instead of being bundled in the APK. Keeps the APK small (~tens of MB) and lets us ship a
 * large, accurate model that could never be bundled.
 *
 * NOTE: verify [MODEL_URL] / quant against the whisper.cpp HF repo before first real download.
 */
object ModelDownloader {
    // DEFAULT: FUTO's ACFT-fine-tuned Whisper small.en (244M), q8_0 ggml (~250 MB), MIT-licensed.
    // ACFT (Adaptive Context Fine-Tuning) keeps accuracy at the LOW audio-context a keyboard runs at
    // for speed — where stock Whisper collapses (small.en 2.77% -> 79.7% WER; ACFT stays ~2.81%).
    // whisper.cpp-compatible via the -ac/--audio-context path (Transcribro already scales audioCtx).
    // This is what FUTO Voice Input ships. Replaces the bundled stock tiny.en (39M).
    // URL verified 2026-06-16 (302 -> dl.voiceinput.futo.org, 264,477,561 bytes); downloader follows redirects.
    const val MODEL_FILE = "small_en_acft_q8_0.bin"
    const val MODEL_URL = "https://voiceinput.futo.org/VoiceInput/small_en_acft_q8_0.bin"

    // Optional "maximum accuracy" alternative the modular downloader can offer instead: stock
    // large-v3-turbo q5_0 (~574 MB). Higher raw WER at full context, but heavier and NOT context-tuned
    // for short dictation — better only for hard audio at full context, at a real speed/size cost.
    // const val TURBO_FILE = "ggml-large-v3-turbo-q5_0.bin"
    // const val TURBO_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin"

    fun modelDir(context: Context): File =
        File(context.filesDir, "models/whisper").apply { mkdirs() }

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_FILE)

    fun isModelPresent(context: Context): Boolean =
        modelFile(context).let { it.exists() && it.length() > 0 }

    /**
     * Downloads the model if not already present. Streams to a `.part` file then atomically renames,
     * so an interrupted download never leaves a corrupt model in place.
     *
     * @param onProgress invoked with (bytesDownloaded, totalBytes); total is -1 if the server
     *   doesn't report a content length.
     * @return the ready-to-load model file.
     */
    suspend fun ensureModel(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        downloadTo(MODEL_URL, modelFile(context), onProgress)
    }

    /** Stream [url] to [target] atomically (via a .part file), reporting progress. */
    private suspend fun downloadTo(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0) return@withContext target
        val tmp = File(target.parentFile, "${target.name}.part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw IOException("Model download failed: HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        } finally {
            conn.disconnect()
        }
    }
}
