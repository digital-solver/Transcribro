package dev.soupslurpr.transcribro.recognitionservice.whisper

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.whispercpp.whisper.WhisperContext
import dev.soupslurpr.transcribro.recognitionservice.GroqTranscriber

class WhisperRepository(
    private val whisperLocalDataSource: WhisperLocalDataSource
) {

    private var whisperContext: MutableState<WhisperContext?> =
        mutableStateOf(null)

    private suspend fun loadWhisperContextIfNull() {
        if (whisperContext.value == null) {
            whisperContext.value = whisperLocalDataSource.getWhisperContext()
        }
    }

    /**
     * Transcribe a speech segment. If [groqApiKey] is non-blank, use online Groq Whisper
     * (large-v3-turbo); otherwise (or if the online call fails) fall back to on-device whisper.cpp.
     */
    suspend fun transcribeAudio(data: ShortArray, groqApiKey: String? = null): String {
        // assume we only have one channel
        val samples = FloatArray(data.size) { index ->
            (data[index] / 32767.0f).coerceIn(-1f..1f)
        }

        if (!groqApiKey.isNullOrBlank()) {
            try {
                // Groq wants the real (un-padded) audio.
                return GroqTranscriber.transcribe(samples, 16000, groqApiKey, "en").removeSuffix(" .")
            } catch (_: Exception) {
                // network/online failure → fall through to on-device
            }
        }

        return transcribeLocal(samples, data.size)
    }

    private suspend fun transcribeLocal(samples: FloatArray, originalSize: Int): String {
        loadWhisperContextIfNull()
        var buffer = samples
        if (originalSize < 32000) {
            val newBuffer = FloatArray(32000)
            for ((i, value) in samples.withIndex()) {
                newBuffer[i] = value
            }
            newBuffer.fill(0f, originalSize, newBuffer.size)
            buffer = newBuffer
        }

        val transcript = whisperContext.value?.transcribeData(buffer, ((originalSize / 16000f) * 1000f).toLong()) ?: ""
        return transcript.removeSuffix(" .") // remove hallucination
    }

    suspend fun release() {
        whisperContext.value?.release()
    }
}