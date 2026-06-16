package dev.soupslurpr.transcribro.ui.voiceinput

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.SpeechRecognizer.createSpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration.getKeyRepeatDelay
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.soupslurpr.transcribro.R
import dev.soupslurpr.transcribro.dataStore
import dev.soupslurpr.transcribro.preferences.PreferencesViewModel
import dev.soupslurpr.transcribro.recognitionservice.MainRecognitionService
import dev.soupslurpr.transcribro.recognitionservice.GeminiTranslator
import dev.soupslurpr.transcribro.recognitionservice.Languages
import dev.soupslurpr.transcribro.ui.reusablecomposables.ScreenLazyColumn
import dev.soupslurpr.transcribro.ui.reusablecomposables.longPressableKey
import dev.soupslurpr.transcribro.ui.theme.TranscribroTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private var speechRecognizer: MutableState<SpeechRecognizer?> = mutableStateOf(null)

private var isRecognizing by mutableStateOf(false)

private var showInsufficientPermissionsError by mutableStateOf(false)

private var isSpeaking by mutableStateOf(false)

// Live UI state for the redesigned VoiceKeyboard panel, driven by the RecognitionListener.
private var previewText by mutableStateOf("")
private val waveLevels = mutableStateListOf<Float>()
private var micPulse by mutableStateOf(0f)

// Total characters committed during the current/last dictation session — lets the delete key's
// drag-up gesture undo the whole last input. Reset at each session start.
private var lastInputLength = 0

class VoiceInput : InputMethodService() {
    private val voiceInputLifecycleOwner = VoiceInputLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        voiceInputLifecycleOwner.onCreate()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreateInputView(): View {
        voiceInputLifecycleOwner.attachToDecorView(window?.window?.decorView)

        val view = ComposeView(this)

        view.setContent {
            val context = LocalContext.current

            val audioManager = context.getSystemService(AudioManager::class.java)

            val startedRecognitionMediaPlayer = MediaPlayer.create(context, R.raw.started_recognition)

            val stoppedRecognitionMediaPlayer = MediaPlayer.create(context, R.raw.stopped_recognition)

            val preferencesViewModel: PreferencesViewModel = viewModel(
                factory = PreferencesViewModel.PreferencesViewModelFactory(dataStore)
            )

            val preferencesUiState by preferencesViewModel.uiState.collectAsState()

            val acceptedPrivacyPolicyAndLicense = preferencesUiState.acceptedPrivacyPolicyAndLicense.second.value

            val autoStopRecognition by preferencesUiState.autoStopRecognition.second

            val autoStartRecognition by preferencesUiState.autoStartRecognition.second

            val snackbarHostState = remember { SnackbarHostState() }

            val snackbarCoroutine = rememberCoroutineScope()

            var snackbarJob: Job? by remember {
                mutableStateOf(null)
            }

            fun cancelSnackbarJobAndLaunch(
                message: String,
                actionLabel: String? = null,
                withDismissAction: Boolean = false,
                duration: SnackbarDuration =
                    if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite
            ) {
                snackbarJob?.cancel()
                snackbarJob = snackbarCoroutine.launch {
                    snackbarHostState.showSnackbar(
                        message,
                        actionLabel,
                        withDismissAction,
                        duration
                    )
                }
            }

            val maxHeight = if (LocalConfiguration.current.orientation == ORIENTATION_PORTRAIT) {
                LocalConfiguration.current.screenHeightDp.dp * 0.45f
            } else {
                LocalConfiguration.current.screenHeightDp.dp * 0.65f
            }

            TranscribroTheme(preferencesViewModel = preferencesViewModel) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                            if (!acceptedPrivacyPolicyAndLicense) {
                                ScreenLazyColumn {
                                    item {
                                        Text("Please accept the privacy policy and license first!")
                                    }
                                    item {
                                        Button(
                                            onClick = {
                                                startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName))
                                            }
                                        ) {
                                            Text("Open Transcribro")
                                        }
                                    }
                                }
                            } else if (showInsufficientPermissionsError) {
                                ScreenLazyColumn {
                                    item {
                                        Text(
                                            "Please grant \"Allow only while using the app\" microphone permission in " +
                                                    "settings to continue."
                                        )
                                    }
                                    item {
                                        Button(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )

                                                intent.addCategory(Intent.CATEGORY_DEFAULT)

                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                                startActivity(intent)

                                                showInsufficientPermissionsError = false
                                            }
                                        ) {
                                            Text("Open App Settings")
                                        }
                                    }
                                }
                            } else {
                                LaunchedEffect(true) {
                                    if (!isRecognizing) {
                                        if (speechRecognizer.value == null) {
                                            speechRecognizer.value = createSpeechRecognizer(
                                                applicationContext,
                                                ComponentName(applicationContext, MainRecognitionService::class.java)
                                            )

                                            speechRecognizer.value!!.setRecognitionListener(object :
                                                RecognitionListener {
                                                override fun onReadyForSpeech(params: Bundle?) {
                                                    isRecognizing = true
                                                    previewText = ""
                                                    waveLevels.clear()
                                                    micPulse = 0f
                                                    lastInputLength = 0

                                                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                                                        startedRecognitionMediaPlayer.start()
                                                    }
                                                }

                                                override fun onBeginningOfSpeech() {
                                                    isSpeaking = true
                                                }

                                                override fun onRmsChanged(rmsdB: Float) {
                                                    // Drive the mic pulse + waveform. rmsdB is roughly -2..10+.
                                                    val level = ((rmsdB + 2f) / 12f).coerceIn(0.06f, 1f)
                                                    micPulse = level
                                                    waveLevels.add(0, level)
                                                    while (waveLevels.size > 20) {
                                                        waveLevels.removeAt(waveLevels.size - 1)
                                                    }
                                                }

                                                override fun onBufferReceived(buffer: ByteArray?) {
//                TODO("Not yet implemented")
                                                }

                                                override fun onEndOfSpeech() {
                                                    isSpeaking = false
                                                }

                                                override fun onError(error: Int) {
                                                    when (error) {
                                                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                                            showInsufficientPermissionsError = true
                                                        }

                                                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                                                            cancelSnackbarJobAndLaunch(
                                                                "Recognition is finishing, please wait or cancel.",
                                                                withDismissAction = true,
                                                                duration = SnackbarDuration.Short
                                                            )
                                                        }

                                                        else -> {
//                                                            println(error)
                                                        }
                                                    }
                                                }

                                                override fun onResults(results: Bundle?) {
                                                    isRecognizing = false
                                                    micPulse = 0f
                                                    waveLevels.clear()
                                                    previewText = ""

                                                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                                                        stoppedRecognitionMediaPlayer.start()
                                                    }

                                                    if (preferencesUiState.autoSwitchToPreviousInputMethod.second.value) {
                                                        this@VoiceInput.switchToPreviousInputMethod()
                                                    }
                                                }

                                                override fun onPartialResults(partialResults: Bundle?) {
                                                    currentInputConnection.also { ic: InputConnection ->
                                                        if (partialResults != null) {
                                                            val transcription = partialResults.getStringArrayList(
                                                                SpeechRecognizer.RESULTS_RECOGNITION
                                                            )?.get(0) ?: ""

                                                            if (transcription.isNotEmpty()) {
                                                                previewText = transcription.trim()
                                                                // Translation mode: translate the segment, then commit
                                                                // (online Gemini; on-device TranslateGemma offline, B6).
                                                                val translateEnabled =
                                                                    preferencesUiState.translateEnabled.second.value
                                                                val targetLanguage =
                                                                    preferencesUiState.targetLanguage.second.value
                                                                if (translateEnabled) {
                                                                    val geminiKey =
                                                                        preferencesUiState.geminiApiKey.second.value
                                                                    val toneValue =
                                                                        preferencesUiState.tone.second.value
                                                                    val before = ic.getTextBeforeCursor(1, 0)
                                                                    val needsLeadingSpace =
                                                                        !(before == "" || before == "\n" || before == " ")
                                                                    val autoSend =
                                                                        preferencesUiState.autoSendTranscription.second.value
                                                                    snackbarCoroutine.launch {
                                                                        val trimmed = transcription.trim()
                                                                        // Online Gemini translation; if it fails
                                                                        // or there's no key, commit raw English.
                                                                        val translated = run {
                                                                            if (geminiKey.isNotBlank()) {
                                                                                try {
                                                                                    return@run GeminiTranslator.translate(
                                                                                        trimmed, targetLanguage, toneValue, geminiKey,
                                                                                        preferencesUiState.inputLanguage.second.value,
                                                                                    )
                                                                                } catch (_: Exception) {
                                                                                }
                                                                            }
                                                                            trimmed
                                                                        }
                                                                        val toCommit =
                                                                            (if (needsLeadingSpace) " " else "") + translated
                                                                        ic.commitText(toCommit, 1)
                                                                        lastInputLength += toCommit.length
                                                                        if (autoSend) {
                                                                            ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                                                                        }
                                                                    }
                                                                    return@also
                                                                }

                                                                var textToCommit = if ((ic.getTextBeforeCursor(
                                                                        2,
                                                                        0
                                                                    ) == "") || (ic.getTextBeforeCursor(1, 0) == "\n")
                                                                    || (ic.getTextBeforeCursor(1, 0) == " ")
                                                                ) {
                                                                    transcription.removePrefix(" ")
                                                                } else {
                                                                    transcription
                                                                }

                                                                val selectedText = ic.getSelectedText(0)

                                                                val readdUppercase =
                                                                    textToCommit.filter { it.isLetter() }.firstOrNull {
                                                                        !it.isUpperCase()
                                                                    } == null

                                                                if (!selectedText.isNullOrEmpty()) {
                                                                    val removeSuffixPoint =
                                                                        textToCommit.trim().toList().withIndex()
                                                                            .reversed().firstOrNull {
                                                                                it.value.isLetterOrDigit()
                                                                            }

                                                                    if (removeSuffixPoint != null) {
                                                                        textToCommit = textToCommit.trim()
                                                                            .substring(0..removeSuffixPoint.index)
                                                                    }

                                                                    val removePrefixPoint =
                                                                        textToCommit.trim().toList().withIndex()
                                                                            .firstOrNull {
                                                                                it.value.isLetterOrDigit()
                                                                            }

                                                                    if (removePrefixPoint != null) {
                                                                        textToCommit = textToCommit.trim()
                                                                            .substring(removePrefixPoint.index..textToCommit.trim().lastIndex)
                                                                    }

                                                                    val selectedEndOfWordPunctuation =
                                                                        selectedText.trim().toList().withIndex()
                                                                            .reversed().firstOrNull {
                                                                                it.value.isLetterOrDigit()
                                                                            }

                                                                    val firstSelectedNonWhitespaceCharacter =
                                                                        selectedText.trim().firstOrNull()

                                                                    if (firstSelectedNonWhitespaceCharacter != null) {
                                                                        textToCommit =
                                                                            if (textToCommit.firstOrNull { !it.isUpperCase() } == null) {
                                                                                textToCommit
                                                                            } else if (firstSelectedNonWhitespaceCharacter.isUpperCase()) {
                                                                                textToCommit[0].uppercaseChar() + textToCommit.substring(
                                                                                    1..textToCommit.lastIndex
                                                                                )
                                                                            } else if (firstSelectedNonWhitespaceCharacter.isLowerCase()) {
                                                                                textToCommit[0].lowercaseChar() + textToCommit.substring(
                                                                                    1..textToCommit.lastIndex
                                                                                )
                                                                            } else if (selectedText.firstOrNull { it.isLetterOrDigit() || it.isWhitespace() } == null) {
                                                                                textToCommit[0].lowercaseChar() + textToCommit.substring(
                                                                                    1..textToCommit.lastIndex
                                                                                )
                                                                            } else {
                                                                                textToCommit
                                                                            }
                                                                    }

                                                                    if (selectedEndOfWordPunctuation != null) {
                                                                        textToCommit += selectedText.trim()
                                                                            .substring(selectedEndOfWordPunctuation.index + 1..selectedText.trim().lastIndex)
                                                                    }

                                                                    if (selectedText.firstOrNull { it.isLetterOrDigit() } == null) {
                                                                        textToCommit = " $textToCommit"

                                                                        if (textToCommit.last().isLetterOrDigit()) {
                                                                            textToCommit = "$textToCommit$selectedText"
                                                                        }
                                                                    }
                                                                } else {
                                                                    val twoCharactersBeforeCursor =
                                                                        ic.getTextBeforeCursor(2, 0)
                                                                    val firstLetterOrDigit = textToCommit.withIndex()
                                                                        .firstOrNull { it.value.isLetterOrDigit() }

                                                                    if ((twoCharactersBeforeCursor != null) && (twoCharactersBeforeCursor.firstOrNull { it.isLetterOrDigit() } != null)) {
                                                                        if (twoCharactersBeforeCursor[0].isLetterOrDigit() && (twoCharactersBeforeCursor[1].isLetterOrDigit() || twoCharactersBeforeCursor[1].isWhitespace())) {
                                                                            if (firstLetterOrDigit != null) {
                                                                                val chars = textToCommit.toCharArray()

                                                                                chars[firstLetterOrDigit.index] =
                                                                                    firstLetterOrDigit.value.lowercaseChar()

                                                                                textToCommit = chars.concatToString()
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                if (readdUppercase) {
                                                                    textToCommit = textToCommit.uppercase()
                                                                }

                                                                ic.commitText(
                                                                    textToCommit,
                                                                    1
                                                                )
                                                                lastInputLength += textToCommit.length

                                                                if (preferencesUiState.autoSendTranscription.second.value) {
                                                                    ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                override fun onEvent(eventType: Int, params: Bundle?) {
//            TODO("Not yet implemented")
                                                }
                                            })

                                            if (autoStartRecognition) {
                                                speechRecognizer.value!!.startListening(
                                                    getStartListeningIntent(
                                                        autoStopRecognition
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                VoiceKeyboard(
                                    state = VoiceKeyboardState(
                                        listening = isRecognizing,
                                        translateOn = preferencesUiState.translateEnabled.second.value,
                                        targetLang = Languages.shortLabel(
                                            preferencesUiState.targetLanguage.second.value
                                        ),
                                        tone = preferencesUiState.tone.second.value,
                                        previewText = previewText,
                                        levels = waveLevels.toList().ifEmpty { List(20) { 0.1f } },
                                        pulse = if (isRecognizing) micPulse else 0f,
                                    ),
                                    onMicClick = {
                                        if (isRecognizing) {
                                            speechRecognizer.value?.stopListening()
                                        } else {
                                            speechRecognizer.value?.startListening(
                                                getStartListeningIntent(autoStopRecognition)
                                            )
                                        }
                                    },
                                    onLangChip = {
                                        val next = !preferencesUiState.translateEnabled.second.value
                                        preferencesUiState.translateEnabled.second.value = next
                                        preferencesViewModel.setPreference(
                                            preferencesUiState.translateEnabled.first, next
                                        )
                                    },
                                    onToneChip = {
                                        val next =
                                            if (preferencesUiState.tone.second.value == "Casual") "Normal" else "Casual"
                                        preferencesUiState.tone.second.value = next
                                        preferencesViewModel.setPreference(
                                            preferencesUiState.tone.first, next
                                        )
                                    },
                                    onOverflow = {
                                        speechRecognizer.value?.cancel()
                                        isRecognizing = false
                                        startActivity(
                                            context.packageManager
                                                .getLaunchIntentForPackage(context.packageName)!!
                                                .apply { action = Intent.ACTION_APPLICATION_PREFERENCES }
                                        )
                                    },
                                    onDeleteLast = {
                                        val sel = currentInputConnection.getSelectedText(0)
                                        if (sel.isNullOrEmpty()) {
                                            currentInputConnection.deleteSurroundingText(1, 0)
                                        } else {
                                            currentInputConnection.commitText("", 1)
                                        }
                                    },
                                    onUndoLast = {
                                        if (lastInputLength > 0) {
                                            currentInputConnection.deleteSurroundingText(lastInputLength, 0)
                                            lastInputLength = 0
                                        }
                                    },
                                    onNewLine = {
                                        currentInputConnection.commitText("\n", 1)
                                    },
                                    onSwitchKeyboard = {
                                        speechRecognizer.value?.cancel()
                                        isRecognizing = false
                                        switchToPreviousInputMethod()
                                    },
                                )
                            }
                            SnackbarHost(
                                snackbarHostState,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                    }
                }
            }
        }

        return view
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        voiceInputLifecycleOwner.onResume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        voiceInputLifecycleOwner.onPause()
    }

    override fun onFinishInput() {
        speechRecognizer.value?.cancel()
        isRecognizing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputLifecycleOwner.onDestroy()

        speechRecognizer.value?.cancel()
        speechRecognizer.value?.destroy()
        speechRecognizer.value = null
    }

    private fun getStartListeningIntent(longForm: Boolean): Intent {
        return Intent().apply {
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(MainRecognitionService.EXTRA_AUTO_STOP, longForm)
        }
    }
}

class VoiceInputLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }

    fun attachToDecorView(decorView: View?) {
        if (decorView == null) return

        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }
}
