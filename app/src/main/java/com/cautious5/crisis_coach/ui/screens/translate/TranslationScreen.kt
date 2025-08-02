package com.cautious5.crisis_coach.ui.screens.translate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.services.TranslationLanguage
import com.cautious5.crisis_coach.ui.components.CardActionButton
import com.cautious5.crisis_coach.ui.components.CenteredVoiceInput
import com.cautious5.crisis_coach.ui.components.EmptyState
import com.cautious5.crisis_coach.ui.components.ErrorCard
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.components.SectionHeader
import com.cautious5.crisis_coach.ui.components.SuccessCard
import com.cautious5.crisis_coach.utils.LocalPermissionManager

/**
 * Modernized Translation screen for Crisis Coach.
 * Focuses on a clean, intuitive, and action-oriented UI.
 */
@Composable
fun TranslateScreen(
    viewModel: TranslateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionManager = LocalPermissionManager.current

    TranslateScreenContent(
        uiState = uiState,
        onInputTextChanged = viewModel::updateInputText,
        onSourceLanguageChanged = viewModel::setSourceLanguage,
        onTargetLanguageChanged = viewModel::setTargetLanguage,
        onSwapLanguages = viewModel::swapLanguages,
        onStartVoiceInput = {
            if (permissionManager.hasMicrophonePermissions()) {
                viewModel.startVoiceTranslation()
            } else {
                permissionManager.requestMicrophonePermissions()
            }
        },
        onStopVoiceInput = viewModel::stopVoiceTranslation,
        onTranslateClicked = viewModel::onTranslateClicked,
        onPlayTranslation = viewModel::playTranslation,
        onTogglePronunciationGuide = viewModel::togglePronunciationGuide,
        onClearError = viewModel::clearError,
        onClearTranslation = viewModel::clearTranslation
    )
}

@Composable
private fun TranslateScreenContent(
    uiState: TranslateViewModel.TranslateUiState,
    onInputTextChanged: (String) -> Unit,
    onSourceLanguageChanged: (String) -> Unit,
    onTargetLanguageChanged: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onTranslateClicked: () -> Unit,
    onPlayTranslation: () -> Unit,
    onTogglePronunciationGuide: () -> Unit,
    onClearError: () -> Unit,
    onClearTranslation: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Language Selection
        LanguageSelectionSection(
            sourceLanguage = uiState.sourceLanguage,
            targetLanguage = uiState.targetLanguage,
            availableLanguages = uiState.availableLanguages,
            onSourceLanguageChanged = onSourceLanguageChanged,
            onTargetLanguageChanged = onTargetLanguageChanged,
            onSwapLanguages = onSwapLanguages
        )

        // Output / Result
        OutputSection(
            uiState = uiState,
            onPlayTranslation = onPlayTranslation,
            onTogglePronunciationGuide = onTogglePronunciationGuide,
            onClearError = onClearError
        )

        // Input Section
        InputSection(
            uiState = uiState,
            onInputTextChanged = onInputTextChanged,
            onStartVoiceInput = onStartVoiceInput,
            onStopVoiceInput = onStopVoiceInput,
            onTranslateClicked = onTranslateClicked
        )

        // Clear All Button
        AnimatedVisibility(
            visible = uiState.inputText.isNotBlank() || uiState.outputText.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedButton(
                onClick = onClearTranslation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Session")
            }
        }
    }
}

@Composable
private fun LanguageSelectionSection(
    sourceLanguage: String,
    targetLanguage: String,
    availableLanguages: List<TranslationLanguage>,
    onSourceLanguageChanged: (String) -> Unit,
    onTargetLanguageChanged: (String) -> Unit,
    onSwapLanguages: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = "Select Languages")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguageDropdown(
                modifier = Modifier.weight(1f),
                selectedLanguage = sourceLanguage,
                availableLanguages = availableLanguages,
                onLanguageSelected = onSourceLanguageChanged,
                label = "From"
            )

            IconButton(
                onClick = onSwapLanguages,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.SwapHoriz, "Swap languages")
            }

            LanguageDropdown(
                modifier = Modifier.weight(1f),
                selectedLanguage = targetLanguage,
                availableLanguages = availableLanguages,
                onLanguageSelected = onTargetLanguageChanged,
                label = "To"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    modifier: Modifier = Modifier,
    selectedLanguage: String,
    availableLanguages: List<TranslationLanguage>,
    onLanguageSelected: (String) -> Unit,
    label: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLanguageInfo = availableLanguages.find { it.code == selectedLanguage }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            value = selectedLanguageInfo?.displayName ?: selectedLanguage,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableLanguages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                    },
                    leadingIcon = if (language.supportsSpeech) {
                        { Icon(Icons.Default.Mic, "Supports speech", tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun OutputSection(
    uiState: TranslateViewModel.TranslateUiState,
    onPlayTranslation: () -> Unit,
    onTogglePronunciationGuide: () -> Unit,
    onClearError: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = "Result")

        if (!uiState.error.isNullOrBlank()) {
            // Corrected ErrorCard implementation
            ErrorCard(title = "Translation Error") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CardActionButton(text = "Dismiss", onClick = onClearError)
                    }
                }
            }
        } else if (uiState.outputText.isNotBlank()) {
            SuccessCard(title = "Translation") {
                Text(
                    text = uiState.outputText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(
                    visible = uiState.showPronunciationGuide && !uiState.pronunciationGuide.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PronunciationGuide(guide = uiState.pronunciationGuide ?: "")
                }

                ResultActions(
                    uiState = uiState,
                    onPlayTranslation = onPlayTranslation,
                    onTogglePronunciationGuide = onTogglePronunciationGuide
                )
            }
        } else {
            EmptyState(
                icon = Icons.Default.Info,
                title = "Awaiting Input",
                subtitle = "Use voice or text input below to start translating. The result will appear here."
            )
        }
    }
}


@Composable
private fun PronunciationGuide(guide: String) {
    ResultCard(
        title = "Pronunciation Guide",
        modifier = Modifier.fillMaxWidth(),
        surfaceTint = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = guide,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultActions(
    uiState: TranslateViewModel.TranslateUiState,
    onPlayTranslation: () -> Unit,
    onTogglePronunciationGuide: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        if (uiState.canPlayTranslation) {
            TextButton(
                onClick = onPlayTranslation,
                enabled = !uiState.isSpeaking && uiState.isTTSReady
            ) {
                Icon(
                    if (uiState.isSpeaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = "Play translation",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isSpeaking) "Playing" else "Play")
            }
        }

        if (!uiState.pronunciationGuide.isNullOrBlank()) {
            TextButton(onClick = onTogglePronunciationGuide) {
                Icon(
                    if (uiState.showPronunciationGuide) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle pronunciation guide",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.showPronunciationGuide) "Hide Guide" else "Show Guide")
            }
        }
    }
}

@Composable
private fun InputSection(
    uiState: TranslateViewModel.TranslateUiState,
    onInputTextChanged: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onTranslateClicked: () -> Unit
) {
    val canUseMicrophone = remember(uiState.sourceLanguage, uiState.availableLanguages) {
        uiState.availableLanguages.find { it.code == uiState.sourceLanguage }?.supportsSpeech ?: false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("Input")

        // Voice Input
        CenteredVoiceInput(
            isListening = uiState.isListening,
            onStartListening = onStartVoiceInput,
            onStopListening = onStopVoiceInput,
            enabled = canUseMicrophone
        )

        Text(
            text = "OR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Text Input
        OutlinedTextField(
            value = uiState.inputText,
            onValueChange = onInputTextChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type text to translate") },
            minLines = 3,
            maxLines = 5,
            enabled = !uiState.isListening
        )

        // Translate Button for text input
        Button(
            onClick = onTranslateClicked,
            enabled = uiState.inputText.isNotBlank() && !uiState.isTranslating && !uiState.isListening,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isTranslating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Translating...")
            } else {
                Icon(Icons.Default.Translate, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Translate Text")
            }
        }
    }
}