package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.util.Log
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.GenerationResult
import com.cautious5.crisis_coach.utils.PromptUtils
import com.cautious5.crisis_coach.utils.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Comprehensive translation service that combines speech recognition,
 * AI-powered translation, and text-to-speech synthesis
 */
class TranslationService(
    private val context: Context,
    private val gemmaModelManager: GemmaModelManager,
    val speechService: SpeechService,
    val textToSpeechService: TextToSpeechService
) {

    companion object {
        private const val TAG = "TranslationService"
        private const val MAX_TRANSLATION_RETRIES = 2
        private const val PRONUNCIATION_GUIDE_REQUEST = true
    }

    // State management
    private val _translationState = MutableStateFlow(TranslationState.IDLE)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    private val _sourceLanguage = MutableStateFlow("en-US")
    val sourceLanguage: StateFlow<String> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow("es-ES")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    /**
     * Initializes all translation components
     */
    suspend fun initialize(): TranslationInitResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing TranslationService")

        try {
            // Initialize speech recognition
            val speechInit = speechService.initialize()
            if (speechInit is InitializationResult.Error) {
                return@withContext TranslationInitResult.Error("Speech service init failed: ${speechInit.message}")
            }

            // Initialize text-to-speech
            val ttsInit = textToSpeechService.initialize()
            if (ttsInit is TTSInitializationResult.Error) {
                return@withContext TranslationInitResult.Error("TTS service init failed: ${ttsInit.message}")
            }

            // Verify Gemma model is ready
            if (!gemmaModelManager.isReady()) {
                return@withContext TranslationInitResult.Error("Gemma model not ready")
            }

            Log.i(TAG, "TranslationService initialized successfully")
            TranslationInitResult.Success

        } catch (e: Exception) {
            val error = "Translation service initialization failed: ${e.message}"
            Log.e(TAG, error, e)
            TranslationInitResult.Error(error, e)
        }
    }

    /**
     * Performs complete voice-to-voice translation
     * Listens to speech, translates it, and speaks the result
     */
    suspend fun translateVoiceToVoice(
        prompt: String = "Please speak now"
    ): VoiceTranslationResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting voice-to-voice translation (${_sourceLanguage.value} -> ${_targetLanguage.value})")
        _translationState.value = TranslationState.LISTENING

        try {
            speechService.setLanguage(_sourceLanguage.value)
            // Step 1: Capture speech input
            when (val speechResult = speechService.startRecognition(prompt)) {
                is SpeechResult.Success -> {
                    val spokenText = speechResult.results.firstOrNull()?.text
                    if (spokenText.isNullOrBlank()) {
                        return@withContext VoiceTranslationResult.Error("No speech detected")
                    }

                    Log.d(TAG, "Captured speech: '$spokenText'")

                    // Step 2: Translate the text
                    val translationResult = translateText(
                        text = spokenText,
                        sourceLanguage = _sourceLanguage.value,
                        targetLanguage = _targetLanguage.value,
                        includePronunciation = PRONUNCIATION_GUIDE_REQUEST
                    )

                    when (translationResult) {
                        is TextTranslationResult.Success -> {
                            // Step 3: Speak the translation
                            val speakResult = textToSpeechService.speak(
                                text = translationResult.translatedText,
                                languageCode = _targetLanguage.value
                            )

                            _translationState.value = TranslationState.IDLE

                            VoiceTranslationResult.Success(
                                originalText = spokenText,
                                translatedText = translationResult.translatedText,
                                pronunciationGuide = translationResult.pronunciationGuide,
                                confidence = speechResult.results.firstOrNull()?.confidence ?: 0f,
                                speechSynthesisSuccess = speakResult is SpeechSynthesisResult.Success
                            )
                        }
                        is TextTranslationResult.Error -> {
                            _translationState.value = TranslationState.IDLE
                            VoiceTranslationResult.Error("Translation failed: ${translationResult.message}")
                        }
                    }
                }
                is SpeechResult.Error -> {
                    _translationState.value = TranslationState.IDLE
                    VoiceTranslationResult.Error("Speech recognition failed: ${speechResult.message}")
                }
                is SpeechResult.Cancelled -> {
                    _translationState.value = TranslationState.IDLE
                    VoiceTranslationResult.Cancelled
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Voice translation failed: ${e.message}", e)
            _translationState.value = TranslationState.IDLE
            VoiceTranslationResult.Error("Voice translation failed: ${e.message}", e)
        }
    }

    /**
     * Translates text using the Gemma model with full streaming support
     * Returns a Flow that emits translation progress
     */
    fun translateTextStreaming(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        includePronunciation: Boolean = false
    ): Flow<TextTranslationResult> = flow {
        if (text.isBlank()) {
            emit(TextTranslationResult.Error("Text cannot be empty"))
            return@flow
        }

        Log.d(TAG, "Starting streaming translation: '${text.take(50)}...' ($sourceLanguage -> $targetLanguage)")
        _translationState.value = TranslationState.TRANSLATING

        try {
            val prompt = PromptUtils.buildTranslationPrompt(text, sourceLanguage, targetLanguage, includePronunciation)
            var lastError: String? = null
            var successfulResult: TextTranslationResult.Success? = null

            // Using gemmaModelManager.generateTextWithRealtimeUpdates for a better streaming experience
            gemmaModelManager.generateTextWithRealtimeUpdates(prompt).collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        val parsed = ResponseParser.parseTranslationResponse(result.text, includePronunciation)
                        val parsedResult = TextTranslationResult.Success(
                            translatedText = parsed.translatedText,
                            pronunciationGuide = parsed.pronunciationGuide,
                            isComplete = false // It's progress
                        )
                        emit(parsedResult)
                        successfulResult = parsedResult.copy(isComplete = true) // Store final version
                    }
                    is GenerationResult.Error -> {
                        lastError = result.message
                        emit(TextTranslationResult.Error("Translation failed: ${result.message}"))
                    }
                }
            }

            if (successfulResult != null) {
                emit(successfulResult!!) // Emit the final, complete result
            } else {
                emit(TextTranslationResult.Error("Translation failed: $lastError"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Translation error: ${e.message}", e)
            emit(TextTranslationResult.Error("Translation error: ${e.message}", e))
        } finally {
            _translationState.value = TranslationState.IDLE
        }
    }

    /**
     * Non-streaming version that returns the final result
     * (for backwards compatibility)
     */
    private suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        includePronunciation: Boolean = false
    ): TextTranslationResult {
        return translateTextStreaming(text, sourceLanguage, targetLanguage, includePronunciation)
            .filter { it is TextTranslationResult.Success && it.isComplete || it is TextTranslationResult.Error }
            .firstOrNull() ?: TextTranslationResult.Error("No translation result received")
    }

    /**
     * Speaks translated text with pronunciation guide
     */
    suspend fun speakTranslation(
        translatedText: String,
        pronunciationGuide: String? = null,
        speakGuide: Boolean = false
    ): SpeechOutputResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Speaking translation: '${translatedText.take(50)}...'")

        try {
            // Speak the translated text
            val mainSpeakResult = textToSpeechService.speak(
                text = translatedText,
                languageCode = _targetLanguage.value
            )

            // Optionally speak pronunciation guide
            val guideResult = if (speakGuide && !pronunciationGuide.isNullOrBlank()) {
                textToSpeechService.speak(
                    text = "Pronunciation guide: $pronunciationGuide",
                    languageCode = _sourceLanguage.value,
                    queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
                )
            } else {
                SpeechSynthesisResult.Success
            }

            SpeechOutputResult.Success(
                mainSpeechSuccess = mainSpeakResult is SpeechSynthesisResult.Success,
                guideSpeechSuccess = guideResult is SpeechSynthesisResult.Success
            )

        } catch (e: Exception) {
            Log.e(TAG, "Speech output failed: ${e.message}", e)
            SpeechOutputResult.Error("Speech output failed: ${e.message}", e)
        }
    }

    /**
     * Updates translation language settings
     */
    fun setLanguages(sourceLanguage: String, targetLanguage: String) {
        Log.d(TAG, "Setting languages: $sourceLanguage -> $targetLanguage")
        _sourceLanguage.value = sourceLanguage
        _targetLanguage.value = targetLanguage
        // Also update the speech service's language setting
        speechService.setLanguage(sourceLanguage)
    }

    /**
     * Gets list of supported languages for translation
     */
    fun getSupportedLanguages(): List<TranslationLanguage> {
        // This list can be loaded from a config file or be dynamic in the future
        return listOf(
            TranslationLanguage("en-US", "English (US)", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("en-GB", "English (UK)", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("es-ES", "Spanish", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("fr-FR", "French", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("de-DE", "German", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("it-IT", "Italian", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("pt-BR", "Portuguese (Brazil)", supportsTranslation = true, supportsSpeech = true),
            TranslationLanguage("ru-RU", "Russian", supportsTranslation = true, supportsSpeech = false),
            TranslationLanguage("ja-JP", "Japanese", supportsTranslation = true, supportsSpeech = false),
            TranslationLanguage("ko-KR", "Korean", supportsTranslation = true, supportsSpeech = false),
            TranslationLanguage("zh-CN", "Chinese (Simplified)", supportsTranslation = true, supportsSpeech = false),
            TranslationLanguage("ar", "Arabic", supportsTranslation = true, supportsSpeech = false),
            TranslationLanguage("hi-IN", "Hindi", supportsTranslation = true, supportsSpeech = false)
        )
    }

    /**
     * Stops the listening phase of voice recognition gracefully.
     * This will stop the audio recorder and trigger the transcription process.
     */
    fun stopListening() {
        Log.d(TAG, "Stopping the listening phase to begin transcription.")
        speechService.stopRecognition()
    }

    /**
     * Cancels ongoing translation operation
     */
    fun cancelTranslation() {
        Log.d(TAG, "Cancelling translation")

        try {
            speechService.cancelRecognition()
            gemmaModelManager.cancelGeneration()
            textToSpeechService.stop()
            _translationState.value = TranslationState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling translation: ${e.message}", e)
        }
    }
}

/**
 * Translation state enumeration
 */
enum class TranslationState {
    IDLE,
    LISTENING,
    TRANSLATING,
    SPEAKING,
    ERROR
}

/**
 * Result classes for translation operations
 */
sealed class TranslationInitResult {
    data object Success : TranslationInitResult()
    data class Error(val message: String, val cause: Throwable? = null) : TranslationInitResult()
}

sealed class VoiceTranslationResult {
    data class Success(
        val originalText: String,
        val translatedText: String,
        val pronunciationGuide: String?,
        val confidence: Float,
        val speechSynthesisSuccess: Boolean
    ) : VoiceTranslationResult()

    data class Error(val message: String, val cause: Throwable? = null) : VoiceTranslationResult()
    data object Cancelled : VoiceTranslationResult()
}

sealed class TextTranslationResult {
    data class Success(
        val translatedText: String,
        val pronunciationGuide: String?,
        val isComplete: Boolean = true
    ) : TextTranslationResult()

    data class Error(val message: String, val cause: Throwable? = null) : TextTranslationResult()
}

sealed class SpeechOutputResult {
    data class Success(
        val mainSpeechSuccess: Boolean,
        val guideSpeechSuccess: Boolean
    ) : SpeechOutputResult()

    data class Error(val message: String, val cause: Throwable? = null) : SpeechOutputResult()
}

/**
 * Data classes for translation information
 */
data class TranslationLanguage(
    val code: String,
    val displayName: String,
    val supportsTranslation: Boolean,
    val supportsSpeech: Boolean
) {
    val locale: Locale get() = Locale.forLanguageTag(code)
}