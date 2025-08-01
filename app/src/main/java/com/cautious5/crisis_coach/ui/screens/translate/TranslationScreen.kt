package com.cautious5.crisis_coach.ui.screens.translate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.services.TranslationLanguage
import com.cautious5.crisis_coach.ui.components.VoiceInputButton
import com.cautious5.crisis_coach.ui.theme.CrisisCoachTheme
import com.cautious5.crisis_coach.ui.theme.EmergencyTextStyles
import com.cautious5.crisis_coach.ui.theme.highConfidence
import com.cautious5.crisis_coach.ui.theme.lowConfidence
import com.cautious5.crisis_coach.ui.theme.mediumConfidence
import com.cautious5.crisis_coach.ui.theme.playButton
import com.cautious5.crisis_coach.ui.theme.sourceLanguage
import com.cautious5.crisis_coach.ui.theme.targetLanguage
import com.cautious5.crisis_coach.ui.theme.translationSurface
import com.cautious5.crisis_coach.utils.LocalPermissionManager

/**
 * Translation screen for Crisis Coach app
 * Provides voice and text translation with pronunciation guides
 */

@Composable
fun TranslateScreen(
    viewModel: TranslateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Permission handling
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
        onClearTranslation = viewModel::clearTranslation,
        canUseMicrophone = viewModel.languageSupportsSpeech(uiState.sourceLanguage)
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
    onClearTranslation: () -> Unit,
    canUseMicrophone: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        TranslateHeader()

        // Language Selectors
        LanguageSelectionSection(
            sourceLanguage = uiState.sourceLanguage,
            targetLanguage = uiState.targetLanguage,
            availableLanguages = uiState.availableLanguages,
            onSourceLanguageChanged = onSourceLanguageChanged,
            onTargetLanguageChanged = onTargetLanguageChanged,
            onSwapLanguages = onSwapLanguages
        )

        // Input Section
        InputSection(
            inputText = uiState.inputText,
            onInputTextChanged = onInputTextChanged,
            isListening = uiState.isListening,
            isTranslating = uiState.isTranslating,
            onStartVoiceInput = onStartVoiceInput,
            onStopVoiceInput = onStopVoiceInput,
            canUseMicrophone = canUseMicrophone,
            onTranslateClicked = onTranslateClicked
        )

        // Output Section - Only show if there's content or error
        if (uiState.outputText.isNotBlank() || uiState.error != null) {
            OutputSection(
                outputText = uiState.outputText,
                pronunciationGuide = uiState.pronunciationGuide,
                showPronunciationGuide = uiState.showPronunciationGuide,
                canPlayTranslation = uiState.canPlayTranslation,
                isSpeaking = uiState.isSpeaking,
                confidence = uiState.confidence,
                error = uiState.error,
                onPlayTranslation = onPlayTranslation,
                onTogglePronunciationGuide = onTogglePronunciationGuide,
                onClearError = onClearError,
                isTTSReady = uiState.isTTSReady
            )
        }

        // Action Buttons
        ActionButtonsSection(
            onClearTranslation = onClearTranslation,
            hasContent = uiState.inputText.isNotBlank() || uiState.outputText.isNotBlank()
        )
    }
}

@Composable
private fun TranslateHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.translationSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Translation",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Live Translation",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Voice and text translation for emergency communication",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Languages",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source Language Dropdown
                LanguageDropdown(
                    modifier = Modifier.weight(1f),
                    selectedLanguage = sourceLanguage,
                    availableLanguages = availableLanguages,
                    onLanguageSelected = onSourceLanguageChanged,
                    label = "From",
                    color = MaterialTheme.colorScheme.sourceLanguage
                )

                // Swap Button
                SwapLanguagesButton(onClick = onSwapLanguages)

                // Target Language Dropdown
                LanguageDropdown(
                    modifier = Modifier.weight(1f),
                    selectedLanguage = targetLanguage,
                    availableLanguages = availableLanguages,
                    onLanguageSelected = onTargetLanguageChanged,
                    label = "To",
                    color = MaterialTheme.colorScheme.targetLanguage
                )
            }
        }
    }
}

@Composable
private fun SwapLanguagesButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = "Swap languages",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
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
    color: Color
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                focusedLabelColor = color
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!language.supportsSpeech) {
                                Text(
                                    text = "Text only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                    },
                    leadingIcon = if (language.supportsSpeech) {
                        {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Supports speech",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun InputSection(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    isListening: Boolean,
    isTranslating: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    canUseMicrophone: Boolean,
    onTranslateClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Input",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            // Voice Input Section
            VoiceInputSection(
                isListening = isListening,
                canUseMicrophone = canUseMicrophone,
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput
            )

            // Text Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Or type here...") },
                placeholder = { Text("Enter text to translate") },
                minLines = 3,
                maxLines = 5,
                textStyle = EmergencyTextStyles.InputText
            )

            // Translate Button with integrated status
            TranslateButton(
                onClick = onTranslateClicked,
                enabled = inputText.isNotBlank() && !isTranslating,
                isTranslating = isTranslating
            )
        }
    }
}

@Composable
private fun VoiceInputSection(
    isListening: Boolean,
    canUseMicrophone: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VoiceInputButton(
            isListening = isListening,
            onStartListening = onStartVoiceInput,
            onStopListening = onStopVoiceInput,
            enabled = canUseMicrophone,
            modifier = Modifier.size(64.dp)
        )

        if (!canUseMicrophone) {
            Text(
                text = "Voice input not supported for this language",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else if (isListening) {
            Text(
                text = "Listening... Speak now",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TranslateButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isTranslating: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isTranslating) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translating...")
        } else {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translate")
        }
    }
}

@Composable
private fun OutputSection(
    outputText: String,
    pronunciationGuide: String?,
    showPronunciationGuide: Boolean,
    canPlayTranslation: Boolean,
    isSpeaking: Boolean,
    confidence: Float,
    error: String?,
    onPlayTranslation: () -> Unit,
    onTogglePronunciationGuide: () -> Unit,
    onClearError: () -> Unit,
    isTTSReady: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with confidence
            OutputHeader(confidence = confidence)

            if (error != null) {
                ErrorCard(
                    error = error,
                    onDismiss = onClearError
                )
            } else {
                // Translation Output
                TranslationOutput(
                    outputText = outputText,
                    pronunciationGuide = pronunciationGuide,
                    showPronunciationGuide = showPronunciationGuide,
                    canPlayTranslation = canPlayTranslation,
                    isSpeaking = isSpeaking,
                    onPlayTranslation = onPlayTranslation,
                    onTogglePronunciationGuide = onTogglePronunciationGuide,
                    isTTSReady = isTTSReady
                )
            }
        }
    }
}

@Composable
private fun OutputHeader(confidence: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Translation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        if (confidence > 0f) {
            ConfidenceBadge(confidence = confidence)
        }
    }
}

@Composable
private fun TranslationOutput(
    outputText: String,
    pronunciationGuide: String?,
    showPronunciationGuide: Boolean,
    canPlayTranslation: Boolean,
    isSpeaking: Boolean,
    onPlayTranslation: () -> Unit,
    onTogglePronunciationGuide: () -> Unit,
    isTTSReady: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main translation text
            Text(
                text = outputText,
                style = EmergencyTextStyles.TranslationText,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Pronunciation Guide
            if (showPronunciationGuide && !pronunciationGuide.isNullOrBlank()) {
                HorizontalDivider()
                PronunciationGuide(pronunciationGuide = pronunciationGuide)
            }

            // Action Buttons
            TranslationActions(
                canPlayTranslation = canPlayTranslation,
                isSpeaking = isSpeaking,
                showPronunciationGuide = showPronunciationGuide,
                onPlayTranslation = onPlayTranslation,
                onTogglePronunciationGuide = onTogglePronunciationGuide,
                isTTSReady = isTTSReady
            )
        }
    }
}

@Composable
private fun PronunciationGuide(pronunciationGuide: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Pronunciation Guide:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = pronunciationGuide,
            style = EmergencyTextStyles.PronunciationGuide,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TranslationActions(
    canPlayTranslation: Boolean,
    isSpeaking: Boolean,
    showPronunciationGuide: Boolean,
    onPlayTranslation: () -> Unit,
    onTogglePronunciationGuide: () -> Unit,
    isTTSReady: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play Translation Button
        if (canPlayTranslation) {
            Button(
                onClick = onPlayTranslation,
                enabled = !isSpeaking && isTTSReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.playButton
                )
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = "Play translation",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isSpeaking) "Playing" else "Play")
            }
        }

        // Toggle Pronunciation Guide
        TextButton(
            onClick = onTogglePronunciationGuide
        ) {
            Icon(
                imageVector = if (showPronunciationGuide) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = "Toggle pronunciation guide",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (showPronunciationGuide) "Hide Guide" else "Show Guide")
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.highConfidence
        confidence >= 0.6f -> MaterialTheme.colorScheme.mediumConfidence
        else -> MaterialTheme.colorScheme.lowConfidence
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ErrorCard(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = error,
                style = EmergencyTextStyles.ErrorText,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    onClearTranslation: () -> Unit,
    hasContent: Boolean
) {
    if (hasContent) {
        OutlinedButton(
            onClick = onClearTranslation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear All")
        }
    }
}

// Preview Parameter Provider for cleaner previews
class TranslateUiStateProvider : PreviewParameterProvider<TranslateViewModel.TranslateUiState> {
    override val values = sequenceOf(
        // Basic state
        TranslateViewModel.TranslateUiState(
            sourceLanguage = "en",
            targetLanguage = "ar",
            availableLanguages = getSampleLanguages()
        ),
        // With translation
        TranslateViewModel.TranslateUiState(
            sourceLanguage = "en",
            targetLanguage = "ar",
            inputText = "I need help",
            outputText = "أحتاج مساعدة",
            pronunciationGuide = "[ana ahtaju musaeda]",
            confidence = 0.95f,
            availableLanguages = getSampleLanguages()
        ),
        // Error state
        TranslateViewModel.TranslateUiState(
            sourceLanguage = "en",
            targetLanguage = "ar",
            inputText = "Help me",
            error = "Translation failed. Please try again.",
            availableLanguages = getSampleLanguages()
        ),
        // Listening state
        TranslateViewModel.TranslateUiState(
            sourceLanguage = "en",
            targetLanguage = "fr",
            isListening = true,
            availableLanguages = getSampleLanguages()
        )
    )

    private fun getSampleLanguages() = listOf(
        TranslationLanguage(
            "en", "English", true,
            supportsSpeech = true
        ),
        TranslationLanguage(
            "ar", "Arabic", true,
            supportsSpeech = true
        ),
        TranslationLanguage(
            "fr", "French", true,
            supportsSpeech = true
        ),
        TranslationLanguage(
            "es", "Spanish", true,
            supportsSpeech = true
        ),
        TranslationLanguage(
            "de", "German", false,
            supportsSpeech = true
        )
    )
}

@Preview(
    name = "Translation Screen",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun TranslateScreenPreview(
    @PreviewParameter(TranslateUiStateProvider::class) uiState: TranslateViewModel.TranslateUiState
) {
    CrisisCoachTheme {
        Surface {
            TranslateScreenContent(
                uiState = uiState,
                onInputTextChanged = {},
                onSourceLanguageChanged = {},
                onTargetLanguageChanged = {},
                onSwapLanguages = {},
                onStartVoiceInput = {},
                onStopVoiceInput = {},
                onTranslateClicked = {},
                onPlayTranslation = {},
                onTogglePronunciationGuide = {},
                onClearError = {},
                onClearTranslation = {},
                canUseMicrophone = true
            )
        }
    }
}

@Preview(
    name = "Translation Screen - Dark",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun TranslateScreenDarkPreview() {
    CrisisCoachTheme(darkTheme = true) {
        Surface {
            TranslateScreenContent(
                uiState = TranslateViewModel.TranslateUiState(
                    sourceLanguage = "en",
                    targetLanguage = "es",
                    inputText = "Where is the hospital?",
                    outputText = "¿Dónde está el hospital?",
                    availableLanguages = listOf(
                        TranslationLanguage(
                            "en", "English", true,
                            supportsSpeech = true
                        ),
                        TranslationLanguage(
                            "es", "Spanish", true,
                            supportsSpeech = true
                        )
                    )
                ),
                onInputTextChanged = {},
                onSourceLanguageChanged = {},
                onTargetLanguageChanged = {},
                onSwapLanguages = {},
                onStartVoiceInput = {},
                onStopVoiceInput = {},
                onTranslateClicked = {},
                onPlayTranslation = {},
                onTogglePronunciationGuide = {},
                onClearError = {},
                onClearTranslation = {},
                canUseMicrophone = true
            )
        }
    }
}