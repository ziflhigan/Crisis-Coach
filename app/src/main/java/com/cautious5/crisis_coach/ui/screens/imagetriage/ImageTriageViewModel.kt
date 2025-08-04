package com.cautious5.crisis_coach.ui.screens.imagetriage

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cautious5.crisis_coach.CrisisCoachApplication
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.GenerationResult
import com.cautious5.crisis_coach.model.services.ImageAnalysisService
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.Constants.AnalysisTypes
import com.cautious5.crisis_coach.utils.ImageUtils
import com.cautious5.crisis_coach.utils.ResponseParser

/**
 * ViewModel for ImageTriageScreen
 * Manages image analysis state, coordinates with ImageAnalysisService,
 * and handles image capture/selection functionality
 */
class ImageTriageViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = LogTags.IMAGE_TRIAGE_VM
        private const val PROGRESS_ANIMATION_DELAY = 50L
    }

    private val gemmaModelManager: GemmaModelManager by lazy {
        (getApplication<CrisisCoachApplication>()).gemmaModelManager
    }

    private val imageAnalysisService: ImageAnalysisService by lazy {
        ImageAnalysisService(getApplication(), gemmaModelManager)
    }

    /**
     * Analysis type options
     */
    enum class AnalysisTypeOption(
        val value: String,
        val displayName: String,
        val description: String,
        val examples: List<String>
    ) {
        MEDICAL(
            value = AnalysisTypes.MEDICAL,
            displayName = "Medical",
            description = "Analyze injuries, wounds, and medical conditions",
            examples = listOf("Cuts and wounds", "Burns", "Fractures", "Bleeding", "Swelling")
        ),
        STRUCTURAL(
            value = AnalysisTypes.STRUCTURAL,
            displayName = "Structural",
            description = "Assess building damage and structural integrity",
            examples = listOf("Cracks in walls", "Damaged bridges", "Collapsed roofs", "Foundation issues")
        ),
        GENERAL(
            value = AnalysisTypes.GENERAL,
            displayName = "General",
            description = "General safety assessment and observations",
            examples = listOf("Hazard identification", "Safety concerns", "Environmental risks")
        )
    }

    /**
     *  Progress tracking with substeps
     */
    sealed class AnalysisProgress(val message: String, val value: Float) {
        data object Idle : AnalysisProgress("Ready", 0f)
        data object InitializingAnalysis : AnalysisProgress("Initializing analysis...", 0.1f)
        data object PreprocessingImage : AnalysisProgress("Processing image...", 0.2f)
        data object PreparingPrompt : AnalysisProgress("Preparing analysis parameters...", 0.3f)
        data object ConnectingToModel : AnalysisProgress("Connecting to AI model...", 0.4f)
        data object WaitingForResponse : AnalysisProgress("Waiting for AI response...", 0.5f)
        data object StreamingResponse : AnalysisProgress("Analyzing...", 0.7f)
        data object FinalizingResults : AnalysisProgress("Finalizing results...", 0.9f)
        data object Complete : AnalysisProgress("Analysis complete", 1.0f)
    }

    /**
     * Analysis result wrapper
     */
    sealed class AnalysisResult {
        data class Medical(
            val assessment: String,
            val urgencyLevel: ResponseParser.UrgencyLevel,
            val requiresProfessionalCare: Boolean,
            val confidenceLevel: Float,
            val analysisTimeMs: Long,
        ) : AnalysisResult()

        data class Structural(
            val assessment: String,
            val safetyStatus: ResponseParser.SafetyStatus,
            val confidenceLevel: Float,
            val analysisTimeMs: Long,
            val structureType: String = "Unknown",
            val damageLevel: String = "Unknown"
        ) : AnalysisResult()

        data class General(
            val description: String,
            val confidence: Float,
            val analysisTimeMs: Long,
            val riskLevel: String = "Unknown",
        ) : AnalysisResult()
    }

    /**
     * UI state with better progress tracking
     */
    data class ImageTriageUiState(
        val selectedImage: Bitmap? = null,
        val imageUri: Uri? = null,
        val analysisType: AnalysisTypeOption = AnalysisTypeOption.MEDICAL,
        val customQuestion: String = "",
        val isAnalyzing: Boolean = false,
        val analysisResult: AnalysisResult? = null,
        val streamingAnalysis: String = "",
        val analysisProgress: AnalysisProgress = AnalysisProgress.Idle,
        val error: String? = null,
        val showImagePicker: Boolean = false,
        val confidence: Float = 0f,
        val analysisTimeMs: Long = 0L,
        val progressMessage: String = "",
        val isModelReady: Boolean = false,
    ) {
        val hasImage: Boolean get() = selectedImage != null
        val isBusy: Boolean get() = isAnalyzing
        val hasStreamingContent: Boolean get() = streamingAnalysis.isNotEmpty()
        val hasFinalResult: Boolean get() = !isAnalyzing && analysisResult != null
        val canAnalyze: Boolean get() = hasImage && !isAnalyzing && isModelReady
    }

    // State flows
    private val _uiState = MutableStateFlow(ImageTriageUiState())
    val uiState: StateFlow<ImageTriageUiState> = _uiState.asStateFlow()

    // Track if we've started receiving streaming content
    private val _streamingStarted = MutableStateFlow(false)

    init {
        Log.d(TAG, "ImageTriageViewModel initialized")
        initialize()
    }

    /**
     * Initialize the ViewModel with better state management
     */
    private fun initialize() {
        // Monitor model readiness
        viewModelScope.launch {
            gemmaModelManager.modelState.collect { modelState ->
                _uiState.update { it.copy(
                    isModelReady = modelState == com.cautious5.crisis_coach.model.ai.ModelState.READY
                )}
            }
        }

        viewModelScope.launch {
            gemmaModelManager.streamingText
                .collect { streamingText ->
                    val currentState = _uiState.value

                    if (currentState.isAnalyzing &&
                        gemmaModelManager.isStreaming.value &&
                        streamingText.isNotEmpty()) {

                        if (!_streamingStarted.value) {
                            _streamingStarted.value = true
                            updateProgress(AnalysisProgress.StreamingResponse)
                        }

                        _uiState.update { it.copy(streamingAnalysis = streamingText) }
                    }
                }
        }

        // Monitor streaming completion
        viewModelScope.launch {
            gemmaModelManager.isStreaming
                .collect { isStreaming ->
                    if (!isStreaming && _streamingStarted.value) {
                        _streamingStarted.value = false
                    }
                }
        }
    }

    /**
     * Image analysis
     */
    fun analyzeImage() {
        val currentState = _uiState.value
        val image = currentState.selectedImage

        if (image == null) {
            setError("Please select an image first")
            return
        }

        if (currentState.isAnalyzing) {
            Log.w(TAG, "Analysis already in progress")
            return
        }

        if (!currentState.isModelReady) {
            setError("AI model is not ready. Please wait...")
            return
        }

        Log.d(TAG, "Starting analysis: ${currentState.analysisType.displayName}")

        // Update UI state immediately on main thread
        _uiState.update { it.copy(
            isAnalyzing = true,
            streamingAnalysis = "",
            analysisResult = null,
            error = null
        )}

        // Launch coroutine on IO dispatcher for background work
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val analysisFlow = when (currentState.analysisType) {
                    AnalysisTypeOption.MEDICAL -> imageAnalysisService.analyzeMedicalImageStreaming(
                        image = image,
                        specificQuestion = currentState.customQuestion.takeIf { it.isNotBlank() }
                    )
                    AnalysisTypeOption.STRUCTURAL -> imageAnalysisService.analyzeStructuralImageStreaming(
                        image = image,
                        specificConcerns = currentState.customQuestion.takeIf { it.isNotBlank() }
                    )
                    AnalysisTypeOption.GENERAL -> imageAnalysisService.analyzeGeneralImageStreaming(
                        image = image,
                        question = currentState.customQuestion.ifBlank {
                            "Describe what you see in this image and provide any relevant safety or emergency advice."
                        }
                    )
                }

                // Collect results on IO thread, update UI state (StateFlow is thread-safe)
                analysisFlow.collectLatest { result ->
                    when (result) {
                        is GenerationResult.Success -> {
                            handleAnalysisSuccess(result, currentState.analysisType)
                        }
                        is GenerationResult.Error -> {
                            handleAnalysisError(result)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                handleAnalysisError(GenerationResult.Error("Analysis failed: ${e.message}", e))
            }
        }
    }

    /**
     * Handle successful analysis completion
     */
    private suspend fun handleAnalysisSuccess(
        result: GenerationResult.Success,
        analysisType: AnalysisTypeOption
    ) {
        updateProgressAnimated(AnalysisProgress.FinalizingResults)

        // Parse results
        val analysisResult = parseResultForAnalysisType(
            analysisType,
            result.text,
            result.inferenceTimeMs
        )

        // Small delay to show finalizing state
        delay(200)

        updateProgress(AnalysisProgress.Complete)

        // Update final state
        _uiState.update { it.copy(
            analysisResult = analysisResult,
            isAnalyzing = false,
            streamingAnalysis = "",
            confidence = ResponseParser.extractConfidence(result.text),
            analysisTimeMs = result.inferenceTimeMs
        )}
    }

    /**
     * Handle analysis errors
     */
    private fun handleAnalysisError(error: GenerationResult.Error) {
        _uiState.update { it.copy(
            isAnalyzing = false,
            streamingAnalysis = "",
            analysisProgress = AnalysisProgress.Idle,
            error = "Analysis failed: ${error.message}"
        )}
    }

    /**
     * Update progress with animation delay
     */
    private suspend fun updateProgressAnimated(progress: AnalysisProgress) {
        updateProgress(progress)
        delay(PROGRESS_ANIMATION_DELAY)
    }

    /**
     * Update progress state
     */
    private fun updateProgress(progress: AnalysisProgress) {
        _uiState.update { it.copy(
            analysisProgress = progress,
            progressMessage = progress.message
        )}
    }

    /**
     * Parse result based on analysis type
     */
    private fun parseResultForAnalysisType(
        analysisType: AnalysisTypeOption,
        resultText: String,
        inferenceTime: Long
    ): AnalysisResult {
        val confidence = ResponseParser.extractConfidence(resultText)

        return when (analysisType) {
            AnalysisTypeOption.MEDICAL -> {
                val urgency = ResponseParser.extractUrgencyLevel(resultText)

                AnalysisResult.Medical(
                    assessment = resultText,
                    urgencyLevel = urgency,
                    requiresProfessionalCare = urgency in listOf(
                        ResponseParser.UrgencyLevel.CRITICAL,
                        ResponseParser.UrgencyLevel.HIGH
                    ),
                    confidenceLevel = confidence,
                    analysisTimeMs = inferenceTime
                )
            }

            AnalysisTypeOption.STRUCTURAL -> {
                val safetyStatus = ResponseParser.extractSafetyStatus(resultText)

                AnalysisResult.Structural(
                    assessment = resultText,
                    safetyStatus = safetyStatus,
                    confidenceLevel = confidence,
                    analysisTimeMs = inferenceTime,
                    structureType = "Analyzed Structure",
                    damageLevel = safetyStatus.name
                )
            }

            AnalysisTypeOption.GENERAL -> {
                val riskLevel = ResponseParser.extractRiskLevel(resultText)

                AnalysisResult.General(
                    description = resultText,
                    confidence = confidence,
                    analysisTimeMs = inferenceTime,
                    riskLevel = riskLevel.name.replace('_', ' ')
                )
            }
        }
    }

    /**
     * Load image from URI with error handling
     */
    fun loadImageFromUri(uri: Uri) {
        Log.d(TAG, "Loading image from URI")

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    analysisProgress = AnalysisProgress.PreprocessingImage,
                    progressMessage = "Loading image..."
                )}

                val result = withContext(Dispatchers.IO) {
                    ImageUtils.loadAndPreprocessImage(
                        context = getApplication(),
                        uri = uri,
                        config = ImageUtils.PreprocessConfig(correctOrientation = true)
                    )
                }

                if (result.isSuccess) {
                    result.getOrNull()?.let { bitmap ->
                        setSelectedImage(bitmap, uri)
                    } ?: setError("Failed to process image.")
                } else {
                    setError("Failed to load image: ${result.exceptionOrNull()?.message}")
                }

                _uiState.update { it.copy(
                    analysisProgress = AnalysisProgress.Idle,
                    progressMessage = ""
                )}
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image", e)
                _uiState.update { it.copy(
                    analysisProgress = AnalysisProgress.Idle,
                    progressMessage = "",
                    error = "Error loading image: ${e.message}"
                )}
            }
        }
    }

    private fun setSelectedImage(bitmap: Bitmap, uri: Uri? = null) {
        when (val validationResult = ImageUtils.validateImage(bitmap)) {
            is ImageUtils.ValidationResult.Success -> {
                _uiState.update { it.copy(
                    selectedImage = bitmap,
                    imageUri = uri,
                    analysisResult = null,
                    streamingAnalysis = "",
                    error = null,
                    analysisProgress = AnalysisProgress.Idle,
                    progressMessage = ""
                )}
            }
            is ImageUtils.ValidationResult.Error -> {
                setError("Invalid image: ${validationResult.message}")
            }
        }
    }

    // UI action methods
    fun setAnalysisType(type: AnalysisTypeOption) {
        _uiState.update { it.copy(
            analysisType = type,
            analysisResult = null,
            streamingAnalysis = "",
            error = null
        )}
    }

    fun updateCustomQuestion(question: String) {
        _uiState.update { it.copy(customQuestion = question) }
    }

    fun clearImage() {
        _uiState.value.selectedImage?.let {
            ImageUtils.recycleBitmapIfNeeded(it)
        }

        _uiState.update { it.copy(
            selectedImage = null,
            imageUri = null,
            analysisResult = null,
            streamingAnalysis = "",
            error = null,
            customQuestion = "",
            analysisProgress = AnalysisProgress.Idle,
            progressMessage = ""
        )}
    }

    fun cancelAnalysis() {
        Log.d(TAG, "Cancelling analysis")
        gemmaModelManager.cancelGeneration()
        _streamingStarted.value = false

        _uiState.update { it.copy(
            isAnalyzing = false,
            analysisProgress = AnalysisProgress.Idle,
            streamingAnalysis = "",
            progressMessage = "Analysis cancelled"
        )}
    }

    fun showImagePicker() {
        _uiState.update { it.copy(showImagePicker = true) }
    }

    fun hideImagePicker() {
        _uiState.update { it.copy(showImagePicker = false) }
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(
            error = message,
            isAnalyzing = false,
            analysisProgress = AnalysisProgress.Idle
        )}
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ImageTriageViewModel cleared")
        _uiState.value.selectedImage?.let {
            ImageUtils.recycleBitmapIfNeeded(it)
        }
    }
}