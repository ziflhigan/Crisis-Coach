package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Service for text-to-speech functionality using Android's TextToSpeech API
 * Supports multiple languages and provides offline speech synthesis
 */
class TextToSpeechService(private val context: Context) {

    companion object {
        private const val TAG = "TextToSpeechService"
        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_COUNTRY = "US"
        private const val UTTERANCE_ID_PREFIX = "crisis_coach_tts_"
    }

    // State management
    private val _ttsState = MutableStateFlow(TTSState.UNINITIALIZED)
    val ttsState: StateFlow<TTSState> = _ttsState.asStateFlow()

    private val _currentLanguage = MutableStateFlow(Locale(DEFAULT_LANGUAGE, DEFAULT_COUNTRY))
    val currentLanguage: StateFlow<Locale> = _currentLanguage.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<TTSLanguage>>(emptyList())
    val availableLanguages: StateFlow<List<TTSLanguage>> = _availableLanguages.asStateFlow()

    // TTS components
    private var textToSpeech: TextToSpeech? = null
    private var speechResultChannel: Channel<SpeechSynthesisResult>? = null
    private var utteranceIdCounter = 0

    // Configuration
    private var speechRate = 1.0f
    private var pitch = 1.0f
    private var enableOfflineMode = true

    /**
     * Initializes the text-to-speech service
     */
    fun initialize(): TTSInitializationResult {
        Log.d(TAG, "Initializing TextToSpeechService")
        _ttsState.value = TTSState.INITIALIZING

        return try {
            textToSpeech = TextToSpeech(context) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        Log.i(TAG, "TextToSpeech initialized successfully")
                        onTTSInitialized()
                    }
                    TextToSpeech.ERROR -> {
                        Log.e(TAG, "TextToSpeech initialization failed")
                        _ttsState.value = TTSState.ERROR
                    }
                    else -> {
                        Log.w(TAG, "TextToSpeech initialization status: $status")
                        _ttsState.value = TTSState.ERROR
                    }
                }
            }

            TTSInitializationResult.Success

        } catch (e: Exception) {
            val error = "Failed to initialize TextToSpeech: ${e.message}"
            Log.e(TAG, error, e)
            _ttsState.value = TTSState.ERROR
            TTSInitializationResult.Error(error, e)
        }
    }

    /**
     * Speaks the given text with optional configuration
     */
    suspend fun speak(
        text: String,
        languageCode: String? = null,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ): SpeechSynthesisResult {

        if (text.isBlank()) {
            return SpeechSynthesisResult.Error("Text cannot be empty")
        }

        if (_ttsState.value != TTSState.READY) {
            return SpeechSynthesisResult.Error("TTS not ready. Current state: ${_ttsState.value}")
        }

        Log.d(TAG, "Speaking text: '${text.take(50)}...' (language: $languageCode)")

        try {
            // Set language if specified
            languageCode?.let { langCode ->
                val locale = parseLanguageCode(langCode)
                setLanguage(locale)
            }

            // Generate unique utterance ID
            val utteranceId = "${UTTERANCE_ID_PREFIX}${++utteranceIdCounter}"

            // Create result channel
            speechResultChannel = Channel<SpeechSynthesisResult>(capacity = 1)

            // Set up utterance progress listener
            textToSpeech?.setOnUtteranceProgressListener(createUtteranceProgressListener(utteranceId))

            // Start speaking
            val result = textToSpeech?.speak(text, queueMode, null, utteranceId)

            return when (result) {
                TextToSpeech.SUCCESS -> {
                    // Wait for completion
                    speechResultChannel!!.receive()
                }
                TextToSpeech.ERROR -> {
                    SpeechSynthesisResult.Error("TTS speak() returned error")
                }
                else -> {
                    SpeechSynthesisResult.Error("Unknown TTS error (code: $result)")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Speech synthesis failed: ${e.message}", e)
            return SpeechSynthesisResult.Error("Speech synthesis failed: ${e.message}", e)
        } finally {
            cleanupSpeechChannel()
        }
    }

    /**
     * Stops current speech synthesis
     */
    fun stop() {
        Log.d(TAG, "Stopping speech synthesis")

        try {
            textToSpeech?.stop()
            speechResultChannel?.trySend(SpeechSynthesisResult.Stopped)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS: ${e.message}", e)
        }
    }

    /**
     * Sets the speech language
     */
    fun setLanguage(locale: Locale): LanguageSetResult {
        Log.d(TAG, "Setting TTS language to: ${locale.displayName}")

        val tts = textToSpeech ?: return LanguageSetResult.Error("TTS not initialized")

        return try {
            val result = tts.setLanguage(locale)

            when (result) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    _currentLanguage.value = locale
                    Log.i(TAG, "Language set successfully: ${locale.displayName}")
                    LanguageSetResult.Success
                }
                TextToSpeech.LANG_MISSING_DATA -> {
                    val error = "Language data missing for: ${locale.displayName}"
                    Log.w(TAG, error)
                    LanguageSetResult.Error(error)
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    val error = "Language not supported: ${locale.displayName}"
                    Log.w(TAG, error)
                    LanguageSetResult.Error(error)
                }
                else -> {
                    val error = "Unknown language set result: $result"
                    Log.w(TAG, error)
                    LanguageSetResult.Error(error)
                }
            }
        } catch (e: Exception) {
            val error = "Failed to set language: ${e.message}"
            Log.e(TAG, error, e)
            LanguageSetResult.Error(error, e)
        }
    }

    /**
     * Updates TTS configuration
     */
    fun updateConfiguration(
        speechRate: Float = this.speechRate,
        pitch: Float = this.pitch,
        offlineMode: Boolean = this.enableOfflineMode
    ): ConfigurationResult {
        Log.d(TAG, "Updating TTS configuration: rate=$speechRate, pitch=$pitch, offline=$offlineMode")

        val tts = textToSpeech ?: return ConfigurationResult.Error("TTS not initialized")

        return try {
            // Set speech rate (0.1 to 3.0, 1.0 is normal)
            val rateResult = tts.setSpeechRate(speechRate.coerceIn(0.1f, 3.0f))
            if (rateResult == TextToSpeech.ERROR) {
                return ConfigurationResult.Error("Failed to set speech rate")
            }

            // Set pitch (0.1 to 2.0, 1.0 is normal)
            val pitchResult = tts.setPitch(pitch.coerceIn(0.1f, 2.0f))
            if (pitchResult == TextToSpeech.ERROR) {
                return ConfigurationResult.Error("Failed to set pitch")
            }

            // Update internal configuration
            this.speechRate = speechRate
            this.pitch = pitch
            this.enableOfflineMode = offlineMode

            Log.i(TAG, "TTS configuration updated successfully")
            ConfigurationResult.Success

        } catch (e: Exception) {
            val error = "Failed to update TTS configuration: ${e.message}"
            Log.e(TAG, error, e)
            ConfigurationResult.Error(error, e)
        }
    }

    /**
     * Gets current TTS configuration
     */
    fun getConfiguration(): TTSConfiguration {
        return TTSConfiguration(
            speechRate = speechRate,
            pitch = pitch,
            offlineMode = enableOfflineMode,
            currentLanguage = _currentLanguage.value
        )
    }

    /**
     * Checks if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    /**
     * Gets TTS engine information
     */
    fun getEngineInfo(): TTSEngineInfo {
        val tts = textToSpeech
        return TTSEngineInfo(
            engineName = tts?.defaultEngine ?: "Unknown",
            isInitialized = _ttsState.value == TTSState.READY,
            currentLanguage = _currentLanguage.value,
            availableLanguages = _availableLanguages.value.size
        )
    }

    /**
     * Releases TTS resources
     */
    fun release() {
        Log.d(TAG, "Releasing TextToSpeechService resources")

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null

            speechResultChannel?.close()
            speechResultChannel = null

            _ttsState.value = TTSState.UNINITIALIZED

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS resources: ${e.message}", e)
        }
    }

    // Private helper methods

    /**
     * Called when TTS is successfully initialized
     */
    private fun onTTSInitialized() {
        try {
            // Set default language
            val defaultLocale = Locale(DEFAULT_LANGUAGE, DEFAULT_COUNTRY)
            setLanguage(defaultLocale)

            // Discover available languages
            discoverAvailableLanguages()

            // Set default configuration
            updateConfiguration()

            _ttsState.value = TTSState.READY
            Log.i(TAG, "TTS ready with ${_availableLanguages.value.size} available languages")

        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS initialization: ${e.message}", e)
            _ttsState.value = TTSState.ERROR
        }
    }

    /**
     * Discovers available TTS languages
     */
    private fun discoverAvailableLanguages() {
        val tts = textToSpeech ?: return

        try {
            val commonLanguages = listOf(
                Locale("en", "US") to "English (US)",
                Locale("en", "GB") to "English (UK)",
                Locale("es", "ES") to "Spanish",
                Locale("fr", "FR") to "French",
                Locale("de", "DE") to "German",
                Locale("it", "IT") to "Italian",
                Locale("pt", "BR") to "Portuguese (Brazil)",
                Locale("ru", "RU") to "Russian",
                Locale("ja", "JP") to "Japanese",
                Locale("ko", "KR") to "Korean",
                Locale("zh", "CN") to "Chinese (Simplified)",
                Locale("ar") to "Arabic",
                Locale("hi", "IN") to "Hindi"
            )

            val availableLanguages = commonLanguages.mapNotNull { (locale, displayName) ->
                when (val availability = tts.isLanguageAvailable(locale)) {
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                        TTSLanguage(
                            locale = locale,
                            displayName = displayName,
                            availability = when (availability) {
                                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> LanguageAvailability.FULL
                                TextToSpeech.LANG_COUNTRY_AVAILABLE -> LanguageAvailability.COUNTRY
                                else -> LanguageAvailability.BASIC
                            }
                        )
                    }
                    else -> null
                }
            }

            _availableLanguages.value = availableLanguages
            Log.d(TAG, "Discovered ${availableLanguages.size} available TTS languages")

        } catch (e: Exception) {
            Log.e(TAG, "Error discovering available languages: ${e.message}", e)
        }
    }

    /**
     * Parses language code string to Locale
     */
    private fun parseLanguageCode(languageCode: String): Locale {
        return try {
            val parts = languageCode.split("-", "_")
            when (parts.size) {
                1 -> Locale(parts[0])
                2 -> Locale(parts[0], parts[1])
                else -> Locale(parts[0], parts[1], parts[2])
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse language code: $languageCode", e)
            Locale(DEFAULT_LANGUAGE, DEFAULT_COUNTRY)
        }
    }

    /**
     * Creates utterance progress listener for speech tracking
     */
    private fun createUtteranceProgressListener(expectedUtteranceId: String): UtteranceProgressListener {
        return object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                if (utteranceId == expectedUtteranceId) {
                    Log.d(TAG, "Speech started: $utteranceId")
                    _ttsState.value = TTSState.SPEAKING
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == expectedUtteranceId) {
                    Log.d(TAG, "Speech completed: $utteranceId")
                    _ttsState.value = TTSState.READY
                    speechResultChannel?.trySend(SpeechSynthesisResult.Success)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == expectedUtteranceId) {
                    Log.e(TAG, "Speech error: $utteranceId")
                    _ttsState.value = TTSState.READY
                    speechResultChannel?.trySend(SpeechSynthesisResult.Error("TTS utterance error"))
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == expectedUtteranceId) {
                    Log.d(TAG, "Speech stopped: $utteranceId (interrupted: $interrupted)")
                    _ttsState.value = TTSState.READY
                    speechResultChannel?.trySend(
                        if (interrupted) SpeechSynthesisResult.Stopped
                        else SpeechSynthesisResult.Success
                    )
                }
            }
        }
    }

    /**
     * Cleans up speech channel resources
     */
    private fun cleanupSpeechChannel() {
        speechResultChannel?.close()
        speechResultChannel = null
    }
}

/**
 * TTS state enumeration
 */
enum class TTSState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR
}

/**
 * Language availability levels
 */
enum class LanguageAvailability {
    BASIC,      // Language available
    COUNTRY,    // Language and country available
    FULL        // Language, country, and variant available
}

/**
 * Result classes for TTS operations
 */
sealed class TTSInitializationResult {
    data object Success : TTSInitializationResult()
    data class Error(val message: String, val cause: Throwable? = null) : TTSInitializationResult()
}

sealed class SpeechSynthesisResult {
    data object Success : SpeechSynthesisResult()
    data object Stopped : SpeechSynthesisResult()
    data class Error(val message: String, val cause: Throwable? = null) : SpeechSynthesisResult()
}

sealed class LanguageSetResult {
    data object Success : LanguageSetResult()
    data class Error(val message: String, val cause: Throwable? = null) : LanguageSetResult()
}

sealed class ConfigurationResult {
    data object Success : ConfigurationResult()
    data class Error(val message: String, val cause: Throwable? = null) : ConfigurationResult()
}

/**
 * Data classes for TTS information
 */
data class TTSLanguage(
    val locale: Locale,
    val displayName: String,
    val availability: LanguageAvailability
) {
    val languageCode: String get() = "${locale.language}-${locale.country}"
    val isFullySupported: Boolean get() = availability == LanguageAvailability.FULL
}

data class TTSConfiguration(
    val speechRate: Float,
    val pitch: Float,
    val offlineMode: Boolean,
    val currentLanguage: Locale
)

data class TTSEngineInfo(
    val engineName: String,
    val isInitialized: Boolean,
    val currentLanguage: Locale,
    val availableLanguages: Int
)