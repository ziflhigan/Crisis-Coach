package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.util.Log
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.GenerationResult
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
    private val speechService: SpeechService,
    private val textToSpeechService: TextToSpeechService
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
            // Step 1: Capture speech input
            val speechResult = speechService.startRecognition(_sourceLanguage.value, prompt)

            when (speechResult) {
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
    private fun translateTextStreaming(
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
            val prompt = buildTranslationPrompt(text, sourceLanguage, targetLanguage, includePronunciation)

            var lastError: String? = null
            var successfulResult: TextTranslationResult.Success? = null

            repeat(MAX_TRANSLATION_RETRIES) { attempt ->
                if (successfulResult != null) return@repeat // Exit early if we got a good result

                Log.d(TAG, "Translation attempt ${attempt + 1}/$MAX_TRANSLATION_RETRIES")

                try {
                    var accumulatedText = ""

                    gemmaModelManager.generateText(prompt).collect { result ->
                        when (result) {
                            is GenerationResult.Success -> {
                                accumulatedText = result.text

                                // Try to parse the current accumulated text
                                val parsedResult = parseTranslationResponse(accumulatedText, includePronunciation)

                                if (parsedResult != null) {
                                    // Emit the current progress
                                    emit(TextTranslationResult.Success(
                                        translatedText = parsedResult.translatedText,
                                        pronunciationGuide = parsedResult.pronunciationGuide,
                                        isComplete = false // Indicate this might be partial
                                    ))

                                    // Store the final result
                                    successfulResult = TextTranslationResult.Success(
                                        translatedText = parsedResult.translatedText,
                                        pronunciationGuide = parsedResult.pronunciationGuide,
                                        isComplete = true
                                    )
                                }
                            }
                            is GenerationResult.Error -> {
                                lastError = result.message
                                emit(TextTranslationResult.Error("Translation failed: ${result.message}"))
                            }
                        }
                    }

                    // If we got a successful result, emit the final version and break
                    successfulResult?.let {
                        emit(it)
                        Log.d(TAG, "Translation completed successfully")
                    }

                } catch (e: Exception) {
                    lastError = "Exception during translation: ${e.message}"
                    Log.w(TAG, "Translation attempt exception: ${e.message}", e)
                    emit(TextTranslationResult.Error("Translation attempt failed: ${e.message}"))
                }
            }

            // If no successful result after all retries
            if (successfulResult == null) {
                emit(TextTranslationResult.Error("Translation failed after $MAX_TRANSLATION_RETRIES attempts: $lastError"))
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
    }

    /**
     * Gets list of supported languages for translation
     */
    fun getSupportedLanguages(): List<TranslationLanguage> {
        return listOf(
            TranslationLanguage("en-US", "English (US)",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("en-GB", "English (UK)",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("es-ES", "Spanish",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("fr-FR", "French",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("de-DE", "German",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("it-IT", "Italian",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("pt-BR", "Portuguese (Brazil)",
                supportsTranslation = true,
                supportsSpeech = true
            ),
            TranslationLanguage("ru-RU", "Russian",
                supportsTranslation = true,
                supportsSpeech = false
            ),
            TranslationLanguage("ja-JP", "Japanese",
                supportsTranslation = true,
                supportsSpeech = false
            ),
            TranslationLanguage("ko-KR", "Korean",
                supportsTranslation = true,
                supportsSpeech = false
            ),
            TranslationLanguage("zh-CN", "Chinese (Simplified)",
                supportsTranslation = true,
                supportsSpeech = false
            ),
            TranslationLanguage("ar", "Arabic", supportsTranslation = true, supportsSpeech = false),
            TranslationLanguage("hi-IN", "Hindi",
                supportsTranslation = true,
                supportsSpeech = false
            )
        )
    }

    /**
     * Cancels ongoing translation operation
     */
    fun cancelTranslation() {
        Log.d(TAG, "Cancelling translation")

        try {
            speechService.cancelRecognition()
            textToSpeechService.stop()
            _translationState.value = TranslationState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling translation: ${e.message}", e)
        }
    }

    // Private helper methods

    /**
     * Builds the translation prompt for the Gemma model
     */
    private fun buildTranslationPrompt(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        includePronunciation: Boolean
    ): String {
        val sourceLangName = getLanguageName(sourceLanguage)
        val targetLangName = getLanguageName(targetLanguage)

        val basePrompt = """
            Translate the following $sourceLangName text to $targetLangName accurately and naturally:
            
            "$text"
        """.trimIndent()

        return if (includePronunciation) {
            """
            $basePrompt
            
            Please provide:
            1. The translation in $targetLangName
            2. A pronunciation guide in $sourceLangName script to help pronounce the $targetLangName translation
            
            Format your response as:
            Translation: [translated text]
            Pronunciation: [pronunciation guide]
            """.trimIndent()
        } else {
            "$basePrompt\n\nProvide only the translation:"
        }
    }

    /**
     * Parses the translation response from Gemma
     */
    private fun parseTranslationResponse(
        response: String,
        hasPronunciation: Boolean
    ): TextTranslationResult.Success? {

        return try {
            if (hasPronunciation) {
                // Parse structured response with pronunciation
                val translationMatch = Regex("Translation:\\s*(.+?)(?=\\n|Pronunciation:|$)", RegexOption.DOT_MATCHES_ALL)
                    .find(response)
                val pronunciationMatch = Regex("Pronunciation:\\s*(.+?)$", RegexOption.DOT_MATCHES_ALL)
                    .find(response)

                val translation = translationMatch?.groupValues?.get(1)?.trim()
                val pronunciation = pronunciationMatch?.groupValues?.get(1)?.trim()

                if (!translation.isNullOrBlank()) {
                    TextTranslationResult.Success(
                        translatedText = translation,
                        pronunciationGuide = pronunciation
                    )
                } else {
                    // Fallback: use entire response as translation
                    TextTranslationResult.Success(
                        translatedText = response.trim(),
                        pronunciationGuide = null
                    )
                }
            } else {
                // Simple translation without pronunciation
                TextTranslationResult.Success(
                    translatedText = response.trim(),
                    pronunciationGuide = null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse structured response, using raw text: ${e.message}")
            TextTranslationResult.Success(
                translatedText = response.trim(),
                pronunciationGuide = null
            )
        }
    }

    /**
     * Gets human-readable language name from language code
     */
    private fun getLanguageName(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "en-us", "en-gb", "en" -> "English"
            "es-es", "es" -> "Spanish"
            "fr-fr", "fr" -> "French"
            "de-de", "de" -> "German"
            "it-it", "it" -> "Italian"
            "pt-br", "pt" -> "Portuguese"
            "ru-ru", "ru" -> "Russian"
            "ja-jp", "ja" -> "Japanese"
            "ko-kr", "ko" -> "Korean"
            "zh-cn", "zh" -> "Chinese"
            "ar" -> "Arabic"
            "hi-in", "hi" -> "Hindi"
            else -> languageCode.uppercase()
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
        val isComplete: Boolean = true // New parameter for streaming support
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