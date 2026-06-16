# Build notes — fork of soupslurpr/Transcribro (voice + translate keyboard)

Foundation builds clean on macOS arm64. Design spec: `~/Dev/agora-voice-keyboard/synthesis.md`.

## Build recipe
1. **Submodule:** clone with `--recurse-submodules`, or run `git submodule update --init --recursive`.
   The `whisper.cpp` submodule MUST be populated or the native build fails.
2. **JDK 17, not 25** — Gradle 8.14.3 won't run on JDK 25.
   `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`.
3. **NDK:** `ndkVersion` bumped 27.2.12479018 → 28.2.13676358 (`app/build.gradle.kts`) to match installed NDK.
4. Build: `JAVA_HOME=<17> ./gradlew -p . assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
   First build ~6 min.

## Architecture (validated — see synthesis.md)
- **IME:** Transcribro fork — focused voice/translate keyboard you switch TO (no QWERTY; keep Gboard for typing).
- **ASR (hybrid):** **Groq Whisper large-v3-turbo** online (free tier ~2,000 req/day) / **on-device ACFT-small.en**
  (whisper.cpp; FUTO's MIT-licensed Adaptive-Context-Fine-Tuned small — robust at the low audio-context a keyboard
  runs at) offline + private.
- **Translate (hybrid):** Gemini 3.1 Flash-Lite online / TranslateGemma-4B on-device offline.
- **Register:** 2-tone knob (Casual / Normal) + male gender (ครับ) in the prompt.
- **Privacy:** cloud for normal; on-device lane for sensitive. Engine selector = the privacy lever.

## Done — plan fully implemented, builds green (debug APK 105 MB)
- [x] arm64-only ABI (−24 MB x86_64); bundled tiny.en deleted (−42 MB; assets now just the 1.8 MB VAD)
- [x] **ASR router** — `WhisperRepository.transcribeAudio(data, groqApiKey?)`: online Groq → else on-device
      whisper.cpp; key+flag read from DataStore in `MainRecognitionService` and passed through
- [x] On-device whisper loads from the **downloaded** model (`createContextFromFile(ModelDownloader.modelFile)`)
- [x] `model/ModelDownloader.kt` — atomic streamed download; whisper (ACFT-small) + LLM (.task) entries
- [x] `recognitionservice/GroqTranscriber.kt` — online ASR; `GeminiTranslator.kt` — online translate
- [x] **Translate-before-commit** in `VoiceInput.onPartialResults` — online Gemini, else commit raw English
      (offline translation dropped for v1 — Kerr 2026-06-16; MediaPipe removed)
- [x] Keyboard **control row**: Tone (Casual/Normal) + Language (EN / →TH / →ES) toggles, bound to prefs
- [x] Settings: Groq key + Gemini key fields + online/local toggle; first-run model-download card in StartScreen
- [x] Preferences: groqApiKey, useOnlineAsr, modelDownloaded, geminiApiKey, targetLanguage, tone (+ String setPreference)
- [x] Light rebrand: applicationId `dev.kerr.voicetranslate`, app name "Voice Translate" (package/namespace unchanged)

## Residual — runtime/human gates (cannot be code-completed here)
- **API keys** (free): Groq (console.groq.com) + Gemini (AI Studio) — enter in Settings. `gemini-3.1-flash-lite`
  endpoint confirmed valid (403 = needs key, not 404).
- **Model URLs to verify before download works:**
  - whisper ACFT-small ggml `MODEL_URL` in `ModelDownloader.kt` — my guess didn't resolve on HF; find the real
    futo-org ggml URL (github.com/futo-org/whisper-acft / voice-input-models).
  - (offline *translation* dropped for v1 — Kerr 2026-06-16; translation is online Gemini only, MediaPipe removed.
    To add it later: implement llama.cpp + TranslateGemma-4B GGUF, sharing one ggml via the repo's GGML_HOME
    mechanism. Offline *ASR* via on-device whisper is unaffected and stays.)
- **Phone:** install APK, enable the keyboard, grant mic, download the whisper model; then test ASR (online/offline)
  + translate (→TH/→ES, tone toggle).
- **Release build** (`assembleRelease`, minify already configured) for a much smaller APK than the 105 MB debug.
