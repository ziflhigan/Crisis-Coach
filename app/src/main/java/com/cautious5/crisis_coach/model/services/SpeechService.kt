package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service for offline speech recognition using Android's SpeechRecognizer
 * Prioritizes offline recognition for reliability in disaster scenarios
 */
class SpeechService(private val context: Context) {

    companion object {
        private const val TAG = "SpeechService"
        private const val DEFAULT_LANGUAGE = "en-US"
        private const val MAX_RECOGNITION_TIME_MS = 30000 // 30 seconds
    }

    // State management
    private val _recognitionState = MutableStateFlow(RecognitionState.IDLE)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _currentLanguage = MutableStateFlow(DEFAULT_LANGUAGE)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Speech recognition components
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionListener: RecognitionListener? = null
    private var resultChannel: Channel<SpeechResult>? = null

    // Configuration
    private var enableOfflineMode = true
    private var enablePartialResults = true
    private var maxResults = 5

    /**
     * Initializes the speech recognition service
     */
    fun initialize(): InitializationResult {
        Log.d(TAG, "Initializing SpeechService")

        try {
            // Check if speech recognition is available
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                val error = "Speech recognition not available on this device"
                Log.e(TAG, error)
                return InitializationResult.Error(error)
            }

            // Create speech recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            if (speechRecognizer == null) {
                val error = "Failed to create SpeechRecognizer instance"
                Log.e(TAG, error)
                return InitializationResult.Error(error)
            }

            Log.i(TAG, "SpeechService initialized successfully")
            return InitializationResult.Success

        } catch (e: Exception) {
            val error = "Failed to initialize SpeechService: ${e.message}"
            Log.e(TAG, error, e)
            return InitializationResult.Error(error, e)
        }
    }

    /**
     * Starts speech recognition for the specified language
     */
    suspend fun startRecognition(
        languageCode: String = _currentLanguage.value,
        prompt: String = "Speak now"
    ): SpeechResult {

        if (_recognitionState.value != RecognitionState.IDLE) {
            return SpeechResult.Error("Speech recognition already in progress")
        }

        Log.d(TAG, "Starting speech recognition (language: $languageCode)")
        _recognitionState.value = RecognitionState.LISTENING
        _currentLanguage.value = languageCode

        try {
            // Create result channel
            resultChannel = Channel<SpeechResult>(capacity = 1)

            // Create recognition intent
            val intent = createRecognitionIntent(languageCode, prompt)

            // Set up recognition listener
            recognitionListener = createRecognitionListener()
            speechRecognizer?.setRecognitionListener(recognitionListener)

            // Start listening
            speechRecognizer?.startListening(intent)

            // Wait for result
            val result = resultChannel!!.receive()

            Log.d(TAG, "Speech recognition completed: ${result.javaClass.simpleName}")
            return result

        } catch (e: Exception) {
            val error = "Speech recognition failed: ${e.message}"
            Log.e(TAG, error, e)
            _recognitionState.value = RecognitionState.ERROR
            return SpeechResult.Error(error, e)
        } finally {
            cleanup()
        }
    }

    /**
     * Stops ongoing speech recognition
     */
    fun stopRecognition() {
        Log.d(TAG, "Stopping speech recognition")

        try {
            speechRecognizer?.stopListening()
            _recognitionState.value = RecognitionState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition: ${e.message}", e)
        }
    }

    /**
     * Cancels ongoing speech recognition
     */
    fun cancelRecognition() {
        Log.d(TAG, "Cancelling speech recognition")

        try {
            speechRecognizer?.cancel()
            _recognitionState.value = RecognitionState.IDLE
            resultChannel?.trySend(SpeechResult.Cancelled)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition: ${e.message}", e)
        }
    }

    /**
     * Checks if offline speech recognition is available for the language
     */
    fun isOfflineAvailable(languageCode: String): Boolean {
        // This would require checking system settings for installed language packs
        // For now, return true for common languages
        val supportedOfflineLanguages = setOf("en-US", "en-GB", "es-ES", "fr-FR", "de-DE")
        return supportedOfflineLanguages.contains(languageCode)
    }

    /**
     * Gets list of available languages for speech recognition
     */
    fun getAvailableLanguages(): List<SpeechLanguage> {
        return listOf(
            SpeechLanguage("en-US", "English (US)", isOfflineAvailable("en-US")),
            SpeechLanguage("en-GB", "English (UK)", isOfflineAvailable("en-GB")),
            SpeechLanguage("es-ES", "Spanish", isOfflineAvailable("es-ES")),
            SpeechLanguage("fr-FR", "French", isOfflineAvailable("fr-FR")),
            SpeechLanguage("de-DE", "German", isOfflineAvailable("de-DE")),
            SpeechLanguage("ar", "Arabic", false),
            SpeechLanguage("zh-CN", "Chinese (Simplified)", false),
            SpeechLanguage("pt-BR", "Portuguese (Brazil)", false),
            SpeechLanguage("ru-RU", "Russian", false),
            SpeechLanguage("ja-JP", "Japanese", false)
        )
    }

    /**
     * Updates speech recognition configuration
     */
    fun updateConfiguration(
        offlineMode: Boolean = enableOfflineMode,
        partialResults: Boolean = enablePartialResults,
        maxResults: Int = this.maxResults
    ) {
        Log.d(TAG, "Updating configuration: offline=$offlineMode, partial=$partialResults, maxResults=$maxResults")

        this.enableOfflineMode = offlineMode
        this.enablePartialResults = partialResults
        this.maxResults = maxResults
    }

    /**
     * Gets current configuration
     */
    fun getConfiguration(): SpeechConfiguration {
        return SpeechConfiguration(
            offlineMode = enableOfflineMode,
            partialResults = enablePartialResults,
            maxResults = maxResults,
            currentLanguage = _currentLanguage.value
        )
    }

    /**
     * Releases speech recognition resources
     */
    fun release() {
        Log.d(TAG, "Releasing SpeechService resources")

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            recognitionListener = null
            resultChannel?.close()
            resultChannel = null

            _recognitionState.value = RecognitionState.IDLE

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SpeechService resources: ${e.message}", e)
        }
    }

    // Private helper methods

    /**
     * Creates recognition intent with appropriate settings
     */
    private fun createRecognitionIntent(languageCode: String, prompt: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Basic configuration
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)

            // Offline mode preference
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, enableOfflineMode)

            // Results configuration
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, enablePartialResults)

            // User interface
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            // Timeout configuration
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
        }
    }

    /**
     * Creates recognition listener for handling speech events
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                _recognitionState.value = RecognitionState.READY
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech detected")
                _recognitionState.value = RecognitionState.SPEAKING
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Update volume level for UI feedback if needed
                // Not logging to avoid spam
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "Audio buffer received: ${buffer?.size} bytes")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech detected")
                _recognitionState.value = RecognitionState.PROCESSING
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e(TAG, "Speech recognition error: $errorMessage (code: $error)")

                _recognitionState.value = RecognitionState.ERROR
                resultChannel?.trySend(SpeechResult.Error(errorMessage))
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG, "Speech recognition results received")

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (!matches.isNullOrEmpty()) {
                    val recognitionResults = matches.mapIndexed { index, text ->
                        RecognitionResult(
                            text = text,
                            confidence = confidence?.getOrNull(index) ?: 0f
                        )
                    }

                    Log.d(TAG, "Recognition successful: ${matches[0]} (confidence: ${confidence?.getOrNull(0)})")
                    _recognitionState.value = RecognitionState.IDLE
                    resultChannel?.trySend(SpeechResult.Success(recognitionResults))
                } else {
                    Log.w(TAG, "No recognition results")
                    _recognitionState.value = RecognitionState.IDLE
                    resultChannel?.trySend(SpeechResult.Error("No speech recognized"))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!enablePartialResults) return

                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.d(TAG, "Partial result: ${matches[0]}")
                    // Could emit partial results via a separate flow if needed
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "Speech event: type=$eventType")
            }
        }
    }

    /**
     * Converts error code to human-readable message
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
            else -> "Unknown error (code: $errorCode)"
        }
    }

    /**
     * Cleanup resources after recognition
     */
    private fun cleanup() {
        recognitionListener = null
        resultChannel?.close()
        resultChannel = null
    }
}

/**
 * Speech recognition states
 */
enum class RecognitionState {
    IDLE,
    LISTENING,
    READY,
    SPEAKING,
    PROCESSING,
    ERROR
}

/**
 * Result classes for speech operations
 */
sealed class SpeechResult {
    data class Success(val results: List<RecognitionResult>) : SpeechResult()
    data class Error(val message: String, val cause: Throwable? = null) : SpeechResult()
    data object Cancelled : SpeechResult()
}

sealed class InitializationResult {
    data object Success : InitializationResult()
    data class Error(val message: String, val cause: Throwable? = null) : InitializationResult()
}

/**
 * Individual recognition result with confidence score
 */
data class RecognitionResult(
    val text: String,
    val confidence: Float
) {
    val isHighConfidence: Boolean get() = confidence >= 0.8f
    val isMediumConfidence: Boolean get() = confidence >= 0.5f
}

/**
 * Available language information
 */
data class SpeechLanguage(
    val code: String,
    val displayName: String,
    val supportsOffline: Boolean
)

/**
 * Speech service configuration
 */
data class SpeechConfiguration(
    val offlineMode: Boolean,
    val partialResults: Boolean,
    val maxResults: Int,
    val currentLanguage: String
)