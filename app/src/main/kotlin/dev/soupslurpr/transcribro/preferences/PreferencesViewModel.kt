package dev.soupslurpr.transcribro.preferences

import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PreferencesViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    /**
     * Settings state
     */
    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            populatePreferencesFromDatastore()
        }
    }

    /**
     * Populate the values of the preferences from the Preferences DataStore.
     * This function is only called from this ViewModel's init
     */
    private suspend fun populatePreferencesFromDatastore() {
        dataStore.data.map { preferences ->
            _uiState.update { currentState ->
                currentState.copy(
                    pitchBlackBackground = Pair(
                        uiState.value.pitchBlackBackground.first,
                        mutableStateOf(
                            preferences[uiState.value.pitchBlackBackground.first] ?: uiState.value
                                .pitchBlackBackground.second.value
                        )
                    ),
                    acceptedPrivacyPolicyAndLicense = Pair(
                        uiState.value.acceptedPrivacyPolicyAndLicense.first,
                        mutableStateOf(
                            preferences[uiState.value.acceptedPrivacyPolicyAndLicense.first] ?: uiState.value
                                .acceptedPrivacyPolicyAndLicense.second.value
                        )
                    ),
                    autoSwitchToPreviousInputMethod = Pair(
                        uiState.value.autoSwitchToPreviousInputMethod.first,
                        mutableStateOf(
                            preferences[uiState.value.autoSwitchToPreviousInputMethod
                                .first] ?: uiState.value
                                .autoSwitchToPreviousInputMethod.second.value
                        )
                    ),
                    autoStopRecognition = Pair(
                        uiState.value.autoStopRecognition.first,
                        mutableStateOf(
                            preferences[uiState.value.autoStopRecognition
                                .first] ?: uiState.value
                                .autoStopRecognition.second.value
                        )
                    ),
                    autoStartRecognition = Pair(
                        uiState.value.autoStartRecognition.first,
                        mutableStateOf(
                            preferences[uiState.value.autoStartRecognition
                                .first] ?: uiState.value
                                .autoStartRecognition.second.value
                        )
                    ),
                    autoSendTranscription = Pair(
                        uiState.value.autoSendTranscription.first,
                        mutableStateOf(
                            preferences[uiState.value.autoSendTranscription
                                .first] ?: uiState.value
                                .autoSendTranscription.second.value
                        )
                    ),
                    groqApiKey = Pair(
                        uiState.value.groqApiKey.first,
                        mutableStateOf(
                            preferences[uiState.value.groqApiKey.first]
                                ?: uiState.value.groqApiKey.second.value
                        )
                    ),
                    useOnlineAsr = Pair(
                        uiState.value.useOnlineAsr.first,
                        mutableStateOf(
                            preferences[uiState.value.useOnlineAsr.first]
                                ?: uiState.value.useOnlineAsr.second.value
                        )
                    ),
                    modelDownloaded = Pair(
                        uiState.value.modelDownloaded.first,
                        mutableStateOf(
                            preferences[uiState.value.modelDownloaded.first]
                                ?: uiState.value.modelDownloaded.second.value
                        )
                    ),
                    geminiApiKey = Pair(
                        uiState.value.geminiApiKey.first,
                        mutableStateOf(
                            preferences[uiState.value.geminiApiKey.first]
                                ?: uiState.value.geminiApiKey.second.value
                        )
                    ),
                    translateEnabled = Pair(
                        uiState.value.translateEnabled.first,
                        mutableStateOf(
                            preferences[uiState.value.translateEnabled.first]
                                ?: uiState.value.translateEnabled.second.value
                        )
                    ),
                    targetLanguage = Pair(
                        uiState.value.targetLanguage.first,
                        mutableStateOf(
                            preferences[uiState.value.targetLanguage.first]
                                ?: uiState.value.targetLanguage.second.value
                        )
                    ),
                    tone = Pair(
                        uiState.value.tone.first,
                        mutableStateOf(
                            preferences[uiState.value.tone.first]
                                ?: uiState.value.tone.second.value
                        )
                    ),
                    inputLanguage = Pair(
                        uiState.value.inputLanguage.first,
                        mutableStateOf(
                            preferences[uiState.value.inputLanguage.first]
                                ?: uiState.value.inputLanguage.second.value
                        )
                    )
                )
            }
        }.collect()
    }

    /**
     * Set a preference to a value and save to Preferences DataStore
     */
    fun setPreference(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[key] = value
            }
        }
    }

    /** String preference overload (API keys, target language, tone). */
    fun setPreference(key: Preferences.Key<String>, value: String) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[key] = value
            }
        }
    }

    class PreferencesViewModelFactory(private val dataStore: DataStore<Preferences>) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PreferencesViewModel(dataStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class $modelClass")
        }
    }
}