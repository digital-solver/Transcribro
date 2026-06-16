package dev.soupslurpr.transcribro.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soupslurpr.transcribro.R
import dev.soupslurpr.transcribro.dataStore
import dev.soupslurpr.transcribro.preferences.PreferencesViewModel
import dev.soupslurpr.transcribro.ui.reusablecomposables.ScreenLazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.soupslurpr.transcribro.recognitionservice.Languages

@Composable
fun SettingsStartScreen(
    onClickLicense: () -> Unit,
    onClickPrivacyPolicy: () -> Unit,
    onClickCredits: () -> Unit,
) {
    val preferencesViewModel: PreferencesViewModel = viewModel(
        factory = PreferencesViewModel.PreferencesViewModelFactory(LocalContext.current.dataStore)
    )

    val preferencesUiState by preferencesViewModel.uiState.collectAsState()

    val localUriHandler = LocalUriHandler.current

    ScreenLazyColumn(
        modifier = Modifier
            .fillMaxSize()
    ) {
        item {
            SettingsCategory(
                stringResource(R.string.theme)
            )
        }
        item {
            val preference = preferencesUiState.pitchBlackBackground
            SettingsSwitchItem(
                name = stringResource(id = R.string.pitch_black_background_setting_name),
                description = stringResource(id = R.string.pitch_black_background_setting_description),
                checked = preference.second.value,
                onCheckedChange = {
                    preferencesViewModel.setPreference(
                        preference.first,
                        it
                    )
                }
            )
        }
        item {
            SettingsCategory(
                stringResource(R.string.voice_input_keyboard_setting_category)
            )
        }
        item {
            val preference = preferencesUiState.autoSwitchToPreviousInputMethod
            SettingsSwitchItem(
                name = stringResource(id = R.string.auto_switch_to_previous_input_method_setting_name),
                description = stringResource(id = R.string.auto_switch_to_previous_input_method_setting_description),
                checked = preference.second.value,
                onCheckedChange = {
                    preferencesViewModel.setPreference(
                        preference.first,
                        it
                    )
                }
            )
        }
        item {
            val preference = preferencesUiState.autoStopRecognition
            SettingsSwitchItem(
                name = stringResource(id = R.string.auto_stop_recognition_setting_name),
                description = stringResource(id = R.string.auto_stop_recognition_setting_description),
                checked = preference.second.value,
                onCheckedChange = {
                    preferencesViewModel.setPreference(
                        preference.first,
                        it
                    )
                }
            )
        }
        item {
            val preference = preferencesUiState.autoStartRecognition
            SettingsSwitchItem(
                name = stringResource(id = R.string.auto_start_recognition_setting_name),
                description = stringResource(id = R.string.auto_start_recognition_setting_description),
                checked = preference.second.value,
                onCheckedChange = {
                    preferencesViewModel.setPreference(
                        preference.first,
                        it
                    )
                }
            )
        }
        item {
            val preference = preferencesUiState.autoSendTranscription
            SettingsSwitchItem(
                name = stringResource(id = R.string.auto_send_transcription_setting_name),
                description = stringResource(id = R.string.auto_send_transcription_setting_description),
                checked = preference.second.value,
                onCheckedChange = {
                    preferencesViewModel.setPreference(
                        preference.first,
                        it
                    )
                }
            )
        }
        item {
            SettingsCategory(
                stringResource(R.string.speech_recognition_setting_category)
            )
        }
        item {
            val preference = preferencesUiState.useOnlineAsr
            SettingsSwitchItem(
                name = stringResource(id = R.string.use_online_asr_setting_name),
                description = stringResource(id = R.string.use_online_asr_setting_description),
                checked = preference.second.value,
                onCheckedChange = {
                    preferencesViewModel.setPreference(preference.first, it)
                }
            )
        }
        item {
            val preference = preferencesUiState.groqApiKey
            SettingsTextFieldItem(
                name = stringResource(id = R.string.groq_api_key_setting_name),
                description = stringResource(id = R.string.groq_api_key_setting_description),
                value = preference.second.value,
                onValueChange = {
                    preference.second.value = it
                    preferencesViewModel.setPreference(preference.first, it)
                },
                isSecret = true,
            )
        }
        item {
            val preference = preferencesUiState.geminiApiKey
            SettingsTextFieldItem(
                name = stringResource(id = R.string.gemini_api_key_setting_name),
                description = stringResource(id = R.string.gemini_api_key_setting_description),
                value = preference.second.value,
                onValueChange = {
                    preference.second.value = it
                    preferencesViewModel.setPreference(preference.first, it)
                },
                isSecret = true,
            )
        }
        item {
            SettingsTranslationLanguageItem(
                currentCode = preferencesUiState.targetLanguage.second.value,
                onPick = { code ->
                    preferencesUiState.targetLanguage.second.value = code
                    preferencesViewModel.setPreference(
                        preferencesUiState.targetLanguage.first, code
                    )
                }
            )
        }
        item {
            SettingsCategory(
                stringResource(R.string.about_setting_category)
            )
        }
        item {
            SettingsIconItem(
                name = stringResource(id = R.string.view_source_code_setting_name),
                description = stringResource(id = R.string.view_source_code_setting_description),
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = {
                    localUriHandler.openUri("https://github.com/soupslurpr/Transcribro")
                }
            )
        }
        item {
            SettingsIconItem(
                name = stringResource(id = R.string.license_setting_name),
                description = stringResource(id = R.string.license_setting_description),
                icon = Icons.Filled.Info,
                onClick = onClickLicense
            )
        }
        item {
            SettingsIconItem(
                name = stringResource(id = R.string.privacy_policy_setting_name),
                description = stringResource(id = R.string.privacy_policy_setting_description),
                icon = Icons.Filled.Info,
                onClick = onClickPrivacyPolicy
            )
        }
        item {
            SettingsIconItem(
                name = stringResource(id = R.string.credits_setting_name),
                description = stringResource(id = R.string.credits_setting_description),
                icon = Icons.Filled.Info,
                onClick = onClickCredits
            )
        }
    }
}

@Composable
fun SettingsCategory(category: String) {
    Text(
        text = category,
        modifier = Modifier.padding(top = 8.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsSwitchItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = modifier
            .toggleable(
                value = checked,
                onValueChange = { onCheckedChange(it) }
            ),
        headlineContent = {
            Text(
                name,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = run {
            if (description != null) {
                { Text(description) }
            } else {
                null
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        }
    )
}

@Composable
fun SettingsTextFieldItem(
    name: String,
    description: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    isSecret: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(name, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Column {
                if (description != null) {
                    Text(description)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (isSecret && value.isNotEmpty()) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    )
}

@Composable
fun SettingsTranslationLanguageItem(
    currentCode: String,
    onPick: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val current = Languages.byCode(currentCode)
    val currentLabel = current?.let { "${it.englishName} (${it.nativeName})" } ?: currentCode

    SettingsIconItem(
        name = stringResource(R.string.translation_language_setting_name),
        description = stringResource(R.string.translation_language_setting_description, currentLabel),
        icon = Icons.Filled.Translate,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        LanguagePickerDialog(
            currentCode = currentCode,
            onPick = {
                onPick(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun LanguagePickerDialog(
    currentCode: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) {
            Languages.all
        } else {
            Languages.all.filter {
                it.englishName.contains(query, ignoreCase = true) ||
                    it.nativeName.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translation_language_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.translation_language_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .padding(top = 8.dp)
                ) {
                    items(filtered, key = { it.code }) { lang ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(lang.code) },
                            headlineContent = { Text(lang.englishName) },
                            supportingContent = { Text(lang.nativeName) },
                            trailingContent = if (lang.code == currentCode) {
                                { Icon(Icons.Filled.Check, contentDescription = "selected") }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SettingsIconItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier
            .clickable(
                onClick = onClick
            ),
        headlineContent = {
            Text(
                name,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = run {
            if (description != null) {
                { Text(description) }
            } else {
                null
            }
        },
        trailingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        }
    )
}