package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.util.Log
import com.cautious5.crisis_coach.model.services.whisper.AudioRecorder
import com.cautious5.crisis_coach.model.services.whisper.AudioRecorderListener
import com.cautious5.crisis_coach.model.services.whisper.Whisper
import com.cautious5.crisis_coach.model.services.whisper.WhisperListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Service for offline speech recognition using the consolidated Whisper engine.
 */
class SpeechService(private val context: Context) {

    companion object {
        private const val TAG = "SpeechService"
        private const val MAX_RECOGNITION_TIME_MS = 30000L // 30 seconds

        private const val WHISPER_MODEL_PATH = "models/whisper-tiny-en.tflite"
        private const val WHISPER_VOCAB_PATH = "models/filters_vocab_en.bin"
    }

    // State management
    private val _recognitionState = MutableStateFlow(RecognitionState.IDLE)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _currentLanguage = MutableStateFlow("en-US") // Internal state for language
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Flow for real-time partial results
    private val _partialResultsFlow = MutableStateFlow("")
    val partialResultsFlow: StateFlow<String> = _partialResultsFlow.asStateFlow()

    private var whisper: Whisper? = null
    private var audioRecorder: AudioRecorder? = null
    private var resultChannel: Channel<SpeechResult>? = null

    suspend fun initialize(): InitializationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing SpeechService with Whisper...")
        try {
            whisper = Whisper(context).apply {
                setListener(createWhisperListener())
                // Restore the call to loadModel with the vocab path.
                // Set multilingual to false, as we're using the English-only model.
                loadModel(WHISPER_MODEL_PATH, WHISPER_VOCAB_PATH, multilingual = false)
            }
            audioRecorder = AudioRecorder(context).apply {
                setListener(createRecorderListener())
            }
            Log.i(TAG, "SpeechService initialized successfully.")
            InitializationResult.Success
        } catch (e: Exception) {
            val error = "Failed to initialize SpeechService: ${e.message}"
            Log.e(TAG, error, e)
            InitializationResult.Error(error, e)
        }
    }

    /**
     * Sets the language for speech recognition.
     */
    fun setLanguage(languageCode: String) {
        _currentLanguage.value = languageCode
        Log.d(TAG, "Speech recognition language set to: $languageCode")
    }

    suspend fun startRecognition(prompt: String = "Speak now"): SpeechResult = withContext(Dispatchers.Main) {
        if (_recognitionState.value != RecognitionState.IDLE) {
            Log.w(TAG, "Recognition already in progress. Stopping previous session.")
            stopRecognition()
            delay(100)
        }

        Log.d(TAG, "Starting speech recognition for language: ${_currentLanguage.value}...")
        _recognitionState.value = RecognitionState.LISTENING
        _partialResultsFlow.value = prompt

        val whisperInstance = whisper ?: return@withContext SpeechResult.Error("Whisper not initialized")
        val recorderInstance = audioRecorder ?: return@withContext SpeechResult.Error("Recorder not initialized")

        try {
            resultChannel = Channel(capacity = 1)
            recorderInstance.start()

            val result = withTimeoutOrNull(MAX_RECOGNITION_TIME_MS) {
                resultChannel?.receive()
            } ?: run {
                Log.w(TAG, "Speech recognition timed out after $MAX_RECOGNITION_TIME_MS ms.")
                SpeechResult.Error("Speech recognition timed out.")
            }

            cleanup()
            return@withContext result

        } catch (e: Exception) {
            val error = "Speech recognition failed: ${e.message}"
            Log.e(TAG, error, e)
            cleanup()
            return@withContext SpeechResult.Error(error, e)
        }
    }


    /**
     * Stops ongoing speech recognition
     */
    fun stopRecognition() {
        Log.d(TAG, "Stopping speech recognition requested.")
        audioRecorder?.stop()
    }

    /**
     * Cancels the ongoing recognition session immediately.
     */
    fun cancelRecognition() {
        Log.d(TAG, "Cancelling speech recognition.")
        if (_recognitionState.value == RecognitionState.IDLE) return

        resultChannel?.trySend(SpeechResult.Cancelled)
        cleanup()
    }

    private fun cleanup() {
        audioRecorder?.stop()
        whisper?.stop()
        resultChannel?.close()
        resultChannel = null
        if (_recognitionState.value != RecognitionState.IDLE) {
            _recognitionState.value = RecognitionState.IDLE
        }
        if (_partialResultsFlow.value.isNotEmpty()) {
            _partialResultsFlow.value = ""
        }
        Log.d(TAG, "Recognition session cleaned up.")
    }

    fun release() {
        Log.d(TAG, "Releasing SpeechService resources.")
        cleanup()
        whisper?.release()
        audioRecorder?.release()
        whisper = null
        audioRecorder = null
    }

    private fun createWhisperListener(): WhisperListener {
        return object : WhisperListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Whisper update: $message")
                _partialResultsFlow.value = message
                if (message.contains("Processing", ignoreCase = true)) {
                    _recognitionState.value = RecognitionState.PROCESSING
                }
            }

            override fun onResultReceived(result: String) {
                Log.i(TAG, "Final Whisper result received.")
                val speechResult = if (result.isNotBlank()) {
                    val recognitionResult = RecognitionResult(text = result.trim(), confidence = 0.9f)
                    SpeechResult.Success(listOf(recognitionResult))
                } else {
                    SpeechResult.Error("No speech recognized.")
                }
                resultChannel?.trySend(speechResult)
            }

            override fun onError(error: String) {
                Log.e(TAG, "Whisper error: $error")
                _recognitionState.value = RecognitionState.ERROR
                resultChannel?.trySend(SpeechResult.Error(error))
            }
        }
    }

    private fun createRecorderListener(): AudioRecorderListener {
        return object : AudioRecorderListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Recorder update: $message")
                when {
                    message.equals("Recording started", ignoreCase = true) -> {
                        _recognitionState.value = RecognitionState.SPEAKING
                        _partialResultsFlow.value = "Listening..."
                    }
                    message.equals("Recording stopped", ignoreCase = true) -> {
                        whisper?.start()
                    }
                }
            }

            override fun onDataReceived(samples: FloatArray) {
                whisper?.writeBuffer(samples)
            }

            override fun onError(error: String) {
                Log.e(TAG, "Recorder error: $error")
                _recognitionState.value = RecognitionState.ERROR
                resultChannel?.trySend(SpeechResult.Error(error))
            }
        }
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