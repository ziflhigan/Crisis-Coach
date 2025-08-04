package com.cautious5.crisis_coach.ui.screens.translate

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cautious5.crisis_coach.CrisisCoachApplication
import com.cautious5.crisis_coach.model.services.SpeechOutputResult
import com.cautious5.crisis_coach.model.services.TTSState
import com.cautious5.crisis_coach.model.services.TextTranslationResult
import com.cautious5.crisis_coach.model.services.TranslationInitResult
import com.cautious5.crisis_coach.model.services.TranslationLanguage
import com.cautious5.crisis_coach.model.services.TranslationService
import com.cautious5.crisis_coach.model.services.TranslationState
import com.cautious5.crisis_coach.model.services.VoiceTranslationResult
import com.cautious5.crisis_coach.utils.Constants.DEFAULT_SOURCE_LANGUAGE
import com.cautious5.crisis_coach.utils.Constants.DEFAULT_TARGET_LANGUAGE
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.Constants.TRANSLATION_MAX_TEXT_LENGTH
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for TranslateScreen
 * Manages translation state, coordinates with TranslationService,
 * and handles voice input/output functionality
 */
class TranslateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = LogTags.TRANSLATE_VM
    }

    // Services
    private val translationService: TranslationService by lazy {
        (getApplication<CrisisCoachApplication>()).translationService
    }

    private var lastVoiceTranslationTime = 0L
    private val VOICE_TRANSLATION_DEBOUNCE_MS = 1000L // 1 second debounce

    /**
     * UI state for translation screen
     */
    data class TranslateUiState(
        val sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE,
        val targetLanguage: String = DEFAULT_TARGET_LANGUAGE,
        val inputText: String = "",
        val outputText: String = "",
        val pronunciationGuide: String? = null,
        val isListening: Boolean = false,
        val isTranslating: Boolean = false,
        val isSpeaking: Boolean = false,
        val translationState: TranslationState = TranslationState.IDLE,
        val error: String? = null,
        val confidence: Float = 0f,
        val availableLanguages: List<TranslationLanguage> = emptyList(),
        val canPlayTranslation: Boolean = false,
        val showPronunciationGuide: Boolean = true,
        val isTTSReady: Boolean = false
    )

    // State flows
    private val _uiState = MutableStateFlow(TranslateUiState())
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    // Translation job for debouncing text input
    private var translationJob: Job? = null

    init {
        Log.d(TAG, "TranslateViewModel initialized")
        initialize()
    }

    /**
     * Initialize the ViewModel
     */
    private fun initialize() {
        viewModelScope.launch {
            try {
                // Initialize translation service
                when (val initResult = translationService.initialize()) {
                    is TranslationInitResult.Success -> {
                        Log.d(TAG, "Translation service initialized successfully")
                        loadSupportedLanguages()
                        observeTranslationServiceState()
                        observeSpeechResults() // Observe speech partial results
                        observeTTSState()
                    }
                    is TranslationInitResult.Error -> {
                        Log.e(TAG, "Failed to initialize translation service: ${initResult.message}")
                        setError("Translation service initialization failed: ${initResult.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TranslateViewModel", e)
                setError("Initialization failed: ${e.message}")
            }
        }
    }

    private fun observeTTSState() {
        viewModelScope.launch {
            // Wait a bit for TTS to initialize
            delay(1000)

            // Check TTS state
            val ttsState = translationService.textToSpeechService.ttsState.value
            _uiState.update {
                it.copy(isTTSReady = ttsState == TTSState.READY)
            }

            // Continue observing TTS state
            translationService.textToSpeechService.ttsState.collect { state ->
                _uiState.update {
                    it.copy(isTTSReady = state == TTSState.READY)
                }
            }
        }
    }

    private fun observeSpeechResults() {
        viewModelScope.launch {
            // Listen for partial results from the speech service for real-time transcription
            translationService.speechService.partialResultsFlow.collect { partialText ->
                if (_uiState.value.isListening) {
                    _uiState.update { it.copy(inputText = partialText) }
                }
            }
        }
    }

    /**
     * Load supported languages
     */
    private fun loadSupportedLanguages() {
        val languages = translationService.getSupportedLanguages()
        _uiState.value = _uiState.value.copy(availableLanguages = languages)
        Log.d(TAG, "Loaded ${languages.size} supported languages")
    }

    /**
     * Observe translation service state changes
     */
    private fun observeTranslationServiceState() {
        viewModelScope.launch {
            translationService.translationState.collect { state ->
                Log.d(TAG, "Translation service state changed: $state")
                updateTranslationState(state)
            }
        }

        viewModelScope.launch {
            translationService.sourceLanguage.collect { language ->
                _uiState.value = _uiState.value.copy(sourceLanguage = language)
            }
        }

        viewModelScope.launch {
            translationService.targetLanguage.collect { language ->
                _uiState.value = _uiState.value.copy(targetLanguage = language)
            }
        }
    }

    /**
     * Update UI state based on translation service state
     */
    private fun updateTranslationState(state: TranslationState) {
        _uiState.value = _uiState.value.copy(
            translationState = state,
            isListening = state == TranslationState.LISTENING,
            isTranslating = state == TranslationState.TRANSLATING,
            isSpeaking = state == TranslationState.SPEAKING
        )
    }

    /**
     * Start voice translation
     */
    fun startVoiceTranslation() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVoiceTranslationTime < VOICE_TRANSLATION_DEBOUNCE_MS) {
            Log.w(TAG, "Voice translation called too quickly, ignoring")
            return
        }
        lastVoiceTranslationTime = currentTime

        Log.d(TAG, "Starting voice translation")

        if (_uiState.value.isListening || _uiState.value.isTranslating) {
            Log.w(TAG, "Voice translation already in progress")
            return
        }

        clearError()

        viewModelScope.launch {
            try {
                when (val result = translationService.translateVoiceToVoice("Please speak now")) {
                    is VoiceTranslationResult.Success -> {
                        Log.d(TAG, "Voice translation successful")
                        _uiState.value = _uiState.value.copy(
                            inputText = result.originalText,
                            outputText = result.translatedText,
                            pronunciationGuide = result.pronunciationGuide,
                            confidence = result.confidence,
                            canPlayTranslation = result.speechSynthesisSuccess
                        )
                    }
                    is VoiceTranslationResult.Error -> {
                        Log.e(TAG, "Voice translation failed: ${result.message}")
                        setError(result.message)
                    }
                    is VoiceTranslationResult.Cancelled -> {
                        Log.d(TAG, "Voice translation cancelled")
                        clearTranslation()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice translation exception", e)
                setError("Voice translation failed: ${e.message}")
            }
        }
    }

    /**
     * Stop voice translation
     */
    /**
     * Stop voice translation
     */
    fun stopVoiceTranslation() {
        Log.d(TAG, "Stopping voice recording to begin translation")
        translationService.stopListening()
    }

    /**
     * Update input text and trigger translation with debouncing
     */
    fun updateInputText(text: String) {
        val currentText = if (text.length > TRANSLATION_MAX_TEXT_LENGTH) {
            text.take(TRANSLATION_MAX_TEXT_LENGTH)
        } else {
            text
        }

        // Just update the input text, don't trigger translation
        _uiState.update { it.copy(inputText = currentText) }

        // Clear output if input is empty
        if (currentText.isBlank()) {
            clearTranslation()
        }
    }

    fun onTranslateClicked() {
        val textToTranslate = _uiState.value.inputText
        if (textToTranslate.isBlank()) {
            return
        }

        // Cancel any existing translation job
        translationJob?.cancel()

        translationJob = viewModelScope.launch {
            translateText(textToTranslate)
        }
    }

    /**
     * Translate text input
     */
    private suspend fun translateText(text: String) {
        if (text.isBlank()) return

        Log.d(TAG, "Translating text: ${text.take(50)}...")
        clearError()

        try {
            translationService.translateTextStreaming(
                text = text,
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = _uiState.value.targetLanguage,
                includePronunciation = _uiState.value.showPronunciationGuide
            ).collect { result ->
                when (result) {
                    is TextTranslationResult.Success -> {
                        if (result.isComplete) Log.d(TAG, "Text translation completed")
                        _uiState.update {
                            it.copy(
                                outputText = result.translatedText,
                                pronunciationGuide = result.pronunciationGuide,
                                canPlayTranslation = result.translatedText.isNotBlank()
                            )
                        }
                    }
                    is TextTranslationResult.Error -> {
                        Log.e(TAG, "Text translation failed: ${result.message}")
                        setError(result.message)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text translation exception", e)
            setError("Translation failed: ${e.message}")
        }
    }

    /**
     * Play translation using text-to-speech
     */
    fun playTranslation() {
        val outputText = _uiState.value.outputText
        val pronunciationGuide = _uiState.value.pronunciationGuide

        if (outputText.isBlank()) {
            Log.w(TAG, "No translation to play")
            return
        }

        Log.d(TAG, "Playing translation")

        viewModelScope.launch {
            try {
                when (val result = translationService.speakTranslation(
                    translatedText = outputText,
                    pronunciationGuide = pronunciationGuide,
                    speakGuide = false // Only speak the translation, not the pronunciation guide
                )) {
                    is SpeechOutputResult.Success -> {
                        Log.d(TAG, "Translation played successfully")
                    }
                    is SpeechOutputResult.Error -> {
                        Log.e(TAG, "Failed to play translation: ${result.message}")
                        setError("Failed to play translation: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing translation", e)
                setError("Error playing translation: ${e.message}")
            }
        }
    }

    /**
     * Set source language
     */
    fun setSourceLanguage(languageCode: String) {
        Log.d(TAG, "Setting source language: $languageCode")
        translationService.setLanguages(languageCode, _uiState.value.targetLanguage)

        // Re-translate if there's existing input
        if (_uiState.value.inputText.isNotBlank()) {
            translationJob?.cancel()
            translationJob = viewModelScope.launch {
                translateText(_uiState.value.inputText)
            }
        }
    }

    /**
     * Set target language
     */
    fun setTargetLanguage(languageCode: String) {
        Log.d(TAG, "Setting target language: $languageCode")
        translationService.setLanguages(_uiState.value.sourceLanguage, languageCode)

        // Re-translate if there's existing input
        if (_uiState.value.inputText.isNotBlank()) {
            translationJob?.cancel()
            translationJob = viewModelScope.launch {
                translateText(_uiState.value.inputText)
            }
        }
    }

    /**
     * Swap source and target languages
     */
    fun swapLanguages() {
        Log.d(TAG, "Swapping languages")
        val currentSource = _uiState.value.sourceLanguage
        val currentTarget = _uiState.value.targetLanguage

        translationService.setLanguages(currentTarget, currentSource)

        // Swap input and output text
        val currentInput = _uiState.value.inputText
        val currentOutput = _uiState.value.outputText

        _uiState.value = _uiState.value.copy(
            inputText = currentOutput,
            outputText = currentInput,
            pronunciationGuide = null
        )

        // Re-translate with swapped text
        if (_uiState.value.inputText.isNotBlank()) {
            translationJob?.cancel()
            translationJob = viewModelScope.launch {
                translateText(_uiState.value.inputText)
            }
        }
    }

    /**
     * Toggle pronunciation guide visibility
     */
    fun togglePronunciationGuide() {
        val currentValue = _uiState.value.showPronunciationGuide
        Log.d(TAG, "Toggling pronunciation guide visibility to: ${!currentValue}")

        _uiState.update { it.copy(showPronunciationGuide = !currentValue) }
    }

    /**
     * Clear current translation
     */
    fun clearTranslation() {
        Log.d(TAG, "Clearing translation")
        translationJob?.cancel()
        _uiState.update {
            it.copy(
                outputText = "",
                pronunciationGuide = null,
                confidence = 0f,
                canPlayTranslation = false,
                isTranslating = false,
                translationState = TranslationState.IDLE
            )
        }
    }

    /**
     * Set error message
     */
    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Get language display name
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return _uiState.value.availableLanguages
            .find { it.code == languageCode }
            ?.displayName ?: languageCode
    }

    /**
     * Check if language supports speech recognition
     */
    fun languageSupportsSpeech(languageCode: String): Boolean {
        return _uiState.value.availableLanguages
            .find { it.code == languageCode }
            ?.supportsSpeech ?: false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "TranslateViewModel cleared")
        translationJob?.cancel()
        translationService.cancelTranslation()
    }
}