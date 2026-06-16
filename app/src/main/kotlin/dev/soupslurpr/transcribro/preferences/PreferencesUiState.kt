package dev.soupslurpr.transcribro.preferences

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.soupslurpr.transcribro.BuildConfig

/** Preference pairs, the first is the preference key, and the second is the default value. */
data class PreferencesUiState(
    /** Pitch black background. */
    val pitchBlackBackground: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("PITCH_BLACK_BACKGROUND")),
        mutableStateOf(false)
    ),

    /** Whether the user has accepted the privacy policy and license. */
    val acceptedPrivacyPolicyAndLicense: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("ACCEPTED_PRIVACY_POLICY_AND_LICENSE_V0.3.0")),
        mutableStateOf(false)
    ),

    /** Whether to automatically switch to the previous input method when the keyboard is done transcribing */
    val autoSwitchToPreviousInputMethod: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("AUTO_SWITCH_TO_PREVIOUS_INPUT_METHOD")),
        mutableStateOf(false)
    ),

    /** Whether to automatically stop recognition when speech stops being detected. */
    val autoStopRecognition: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("AUTO_STOP_RECOGNITION")),
        mutableStateOf(false)
    ),

    /** Whether to automatically start recognition when switching from another input method. */
    val autoStartRecognition: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("AUTO_START_RECOGNITION")),
        mutableStateOf(true)
    ),

    /** Whether to automatically send transcription when speech stops being detected. */
    val autoSendTranscription: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("AUTO_SEND_TRANSCRIPTION")),
        mutableStateOf(false)
    ),

    // --- Voice + translate keyboard additions ---

    /** Groq API key for online ASR (Whisper large-v3-turbo). Blank = none. Pre-filled for Kerr's build. */
    val groqApiKey: Pair<Preferences.Key<String>, MutableState<String>> = Pair(
        (stringPreferencesKey("GROQ_API_KEY")),
        mutableStateOf(BuildConfig.GROQ_API_KEY)
    ),

    /** Use online ASR (Groq) when a key is set and the network is available; else on-device whisper. */
    val useOnlineAsr: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("USE_ONLINE_ASR")),
        mutableStateOf(true)
    ),

    /** Whether the on-device whisper model has been downloaded. */
    val modelDownloaded: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("MODEL_DOWNLOADED")),
        mutableStateOf(false)
    ),

    /** Gemini API key for online translation. Blank = none. Pre-filled for Kerr's build. */
    val geminiApiKey: Pair<Preferences.Key<String>, MutableState<String>> = Pair(
        (stringPreferencesKey("GEMINI_API_KEY")),
        mutableStateOf(BuildConfig.GEMINI_API_KEY)
    ),

    /** Whether the keyboard translates dictation (to [targetLanguage]) instead of committing English. */
    val translateEnabled: Pair<Preferences.Key<Boolean>, MutableState<Boolean>> = Pair(
        (booleanPreferencesKey("TRANSLATE_ENABLED")),
        mutableStateOf(false)
    ),

    /** Translation target language code (see Languages), e.g. "th", "es", "ja". Set in Settings. */
    val targetLanguage: Pair<Preferences.Key<String>, MutableState<String>> = Pair(
        (stringPreferencesKey("TARGET_LANGUAGE")),
        mutableStateOf("th")
    ),

    /** Translation tone: "Casual" or "Normal". */
    val tone: Pair<Preferences.Key<String>, MutableState<String>> = Pair(
        (stringPreferencesKey("TONE")),
        mutableStateOf("Normal")
    )
)