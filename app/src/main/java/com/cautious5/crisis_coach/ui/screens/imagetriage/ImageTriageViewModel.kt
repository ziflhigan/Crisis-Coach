package com.cautious5.crisis_coach.ui.screens.imagetriage

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.cautious5.crisis_coach.CrisisCoachApplication
import com.cautious5.crisis_coach.model.services.*
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
    }

    // Services
    private val imageAnalysisService: ImageAnalysisService by lazy {
        (getApplication<CrisisCoachApplication>()).imageAnalysisService
    }

    /**
     * Analysis type options
     */
    enum class AnalysisTypeOption(val value: String, val displayName: String) {
        MEDICAL(AnalysisTypes.MEDICAL, "Medical"),
        STRUCTURAL(AnalysisTypes.STRUCTURAL, "Structural"),
        GENERAL(AnalysisTypes.GENERAL, "General")
    }

    /**
     * UI state for image triage screen
     */
    data class ImageTriageUiState(
        val selectedImage: Bitmap? = null,
        val imageUri: Uri? = null,
        val analysisType: AnalysisTypeOption = AnalysisTypeOption.MEDICAL,
        val customQuestion: String = "",
        val isAnalyzing: Boolean = false,
        val analysisState: AnalysisState = AnalysisState.IDLE,
        val analysisResult: AnalysisResult? = null,
        val error: String? = null,
        val showImagePicker: Boolean = false,
        val showCameraCapture: Boolean = false,
        val hasImage: Boolean = false,
        val analysisProgress: Float = 0f
    )

    /**
     * Analysis result wrapper for UI
     */
    sealed class AnalysisResult {
        data class Medical(
            val assessment: String,
            val urgencyLevel: ResponseParser.UrgencyLevel,
            val recommendations: List<String>,
            val requiresProfessionalCare: Boolean,
            val confidenceLevel: Float,
            val analysisTimeMs: Long
        ) : AnalysisResult()

        data class Structural(
            val assessment: String,
            val safetyStatus: ResponseParser.SafetyStatus,
            val identifiedIssues: List<String>,
            val immediateActions: List<String>,
            val confidenceLevel: Float,
            val analysisTimeMs: Long
        ) : AnalysisResult()

        data class General(
            val description: String,
            val keyObservations: List<String>,
            val suggestedActions: List<String>,
            val confidence: Float,
            val analysisTimeMs: Long
        ) : AnalysisResult()
    }

    // State flows
    private val _uiState = MutableStateFlow(ImageTriageUiState())
    val uiState: StateFlow<ImageTriageUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "ImageTriageViewModel initialized")
        initialize()
    }

    /**
     * Initialize the ViewModel
     */
    private fun initialize() {
        // Observe analysis service state
        viewModelScope.launch {
            imageAnalysisService.analysisState.collect { state ->
                Log.d(TAG, "Analysis service state changed: $state")
                updateAnalysisState(state)
            }
        }
    }

    /**
     * Update UI state based on analysis service state
     */
    private fun updateAnalysisState(state: AnalysisState) {
        _uiState.value = _uiState.value.copy(
            analysisState = state,
            isAnalyzing = state == AnalysisState.ANALYZING,
            analysisProgress = when (state) {
                AnalysisState.IDLE -> 0f
                AnalysisState.ANALYZING -> 0.5f // Indeterminate progress
                AnalysisState.ERROR -> 0f
            }
        )
    }

    /**
     * Set selected image from camera or gallery
     */
    fun setSelectedImage(bitmap: Bitmap, uri: Uri? = null) {
        Log.d(TAG, "Setting selected image: ${bitmap.width}x${bitmap.height}")

        try {
            // Validate image
            when (val validationResult = ImageUtils.validateImage(bitmap)) {
                is ImageUtils.ValidationResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedImage = bitmap,
                        imageUri = uri,
                        hasImage = true,
                        analysisResult = null, // Clear previous result
                        error = null
                    )
                    Log.d(TAG, "Image set successfully")
                }
                is ImageUtils.ValidationResult.Error -> {
                    Log.e(TAG, "Image validation failed: ${validationResult.message}")
                    setError("Invalid image: ${validationResult.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting image", e)
            setError("Failed to process image: ${e.message}")
        }
    }

    /**
     * Load image from URI
     */
    fun loadImageFromUri(uri: Uri) {
        Log.d(TAG, "Loading image from URI: $uri")

        viewModelScope.launch {
            try {
                val result = ImageUtils.loadAndPreprocessImage(getApplication(), uri)
                if (result.isSuccess) {
                    result.getOrNull()?.let { bitmap ->
                        setSelectedImage(bitmap, uri)
                    } ?: setError("Failed to retrieve image bitmap.")
                } else {
                    Log.e(TAG, "Failed to load image from URI", result.exceptionOrNull())
                    setError("Failed to load image: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image from URI", e)
                setError("Error loading image: ${e.message}")
            }
        }
    }

    /**
     * Set analysis type
     */
    fun setAnalysisType(type: AnalysisTypeOption) {
        Log.d(TAG, "Setting analysis type: ${type.displayName}")
        _uiState.value = _uiState.value.copy(
            analysisType = type,
            analysisResult = null // Clear previous result when changing type
        )
    }

    /**
     * Update custom question
     */
    fun updateCustomQuestion(question: String) {
        _uiState.value = _uiState.value.copy(customQuestion = question)
    }

    /**
     * Start image analysis
     */
    fun analyzeImage() {
        val currentState = _uiState.value
        val image = currentState.selectedImage

        if (image == null) {
            Log.w(TAG, "No image selected for analysis")
            setError("Please select an image first")
            return
        }

        if (currentState.isAnalyzing) {
            Log.w(TAG, "Analysis already in progress")
            return
        }

        Log.d(TAG, "Starting image analysis: ${currentState.analysisType.displayName}")
        clearError()

        viewModelScope.launch {
            try {
                when (currentState.analysisType) {
                    AnalysisTypeOption.MEDICAL -> analyzeMedicalImage(image, currentState.customQuestion)
                    AnalysisTypeOption.STRUCTURAL -> analyzeStructuralImage(image, currentState.customQuestion)
                    AnalysisTypeOption.GENERAL -> analyzeGeneralImage(image, currentState.customQuestion)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed with exception", e)
                setError("Analysis failed: ${e.message}")
            }
        }
    }

    /**
     * Analyze medical image
     */
    private suspend fun analyzeMedicalImage(image: Bitmap, customQuestion: String?) {
        Log.d(TAG, "Performing medical analysis")

        when (val result = imageAnalysisService.analyzeMedicalImage(
            image = image,
            specificQuestion = customQuestion?.takeIf { it.isNotBlank() },
            patientContext = null
        )) {
            is MedicalAnalysisResult.Success -> {
                Log.d(TAG, "Medical analysis completed successfully")
                _uiState.value = _uiState.value.copy(
                    analysisResult = AnalysisResult.Medical(
                        assessment = result.assessment,
                        urgencyLevel = result.urgencyLevel,
                        recommendations = result.recommendations,
                        requiresProfessionalCare = result.requiresProfessionalCare,
                        confidenceLevel = result.confidenceLevel,
                        analysisTimeMs = result.analysisTimeMs
                    )
                )
            }
            is MedicalAnalysisResult.Error -> {
                Log.e(TAG, "Medical analysis failed: ${result.message}")
                setError("Medical analysis failed: ${result.message}")
            }
        }
    }

    /**
     * Analyze structural image
     */
    private suspend fun analyzeStructuralImage(image: Bitmap, customQuestion: String?) {
        Log.d(TAG, "Performing structural analysis")

        when (val result = imageAnalysisService.analyzeStructuralImage(
            image = image,
            structureType = StructureType.UNKNOWN, // Let AI determine the type
            specificConcerns = customQuestion?.takeIf { it.isNotBlank() }
        )) {
            is StructuralAnalysisResult.Success -> {
                Log.d(TAG, "Structural analysis completed successfully")
                _uiState.value = _uiState.value.copy(
                    analysisResult = AnalysisResult.Structural(
                        assessment = result.structureType.displayName + " analysis completed",
                        safetyStatus = result.safetyStatus,
                        identifiedIssues = result.identifiedIssues,
                        immediateActions = result.immediateActions,
                        confidenceLevel = result.confidenceLevel,
                        analysisTimeMs = result.analysisTimeMs
                    )
                )
            }
            is StructuralAnalysisResult.Error -> {
                Log.e(TAG, "Structural analysis failed: ${result.message}")
                setError("Structural analysis failed: ${result.message}")
            }
        }
    }

    /**
     * Analyze general image
     */
    private suspend fun analyzeGeneralImage(image: Bitmap, customQuestion: String) {
        Log.d(TAG, "Performing general analysis")

        val question = customQuestion.ifBlank {
            "Describe what you see in this image and provide any relevant safety or emergency advice."
        }

        when (val result = imageAnalysisService.analyzeGeneralImage(image = image, question = question)) {
            is GeneralAnalysisResult.Success -> {
                Log.d(TAG, "General analysis completed successfully")
                _uiState.value = _uiState.value.copy(
                    analysisResult = AnalysisResult.General(
                        description = result.description,
                        keyObservations = result.keyObservations,
                        suggestedActions = result.suggestedActions,
                        confidence = result.confidenceLevel,
                        analysisTimeMs = result.analysisTimeMs
                    )
                )
            }
            is GeneralAnalysisResult.Error -> {
                Log.e(TAG, "General analysis failed: ${result.message}")
                setError("General analysis failed: ${result.message}")
            }
        }
    }

    /**
     * Clear selected image and results
     */
    fun clearImage() {
        Log.d(TAG, "Clearing selected image")
        _uiState.value = _uiState.value.copy(
            selectedImage = null,
            imageUri = null,
            hasImage = false,
            analysisResult = null,
            error = null,
            customQuestion = ""
        )
    }

    /**
     * Cancel ongoing analysis
     */
    fun cancelAnalysis() {
        Log.d(TAG, "Cancelling analysis")
        imageAnalysisService.cancelAnalysis()
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            analysisProgress = 0f
        )
    }

    /**
     * Show image picker dialog
     */
    fun showImagePicker() {
        _uiState.value = _uiState.value.copy(showImagePicker = true)
    }

    /**
     * Hide image picker dialog
     */
    fun hideImagePicker() {
        _uiState.value = _uiState.value.copy(showImagePicker = false)
    }

    /**
     * Show camera capture
     */
    fun showCameraCapture() {
        _uiState.value = _uiState.value.copy(showCameraCapture = true)
    }

    /**
     * Hide camera capture
     */
    fun hideCameraCapture() {
        _uiState.value = _uiState.value.copy(showCameraCapture = false)
    }

    /**
     * Get urgency color for medical results
     */
    fun getUrgencyColor(urgencyLevel: ResponseParser.UrgencyLevel): androidx.compose.ui.graphics.Color {
        return when (urgencyLevel) {
            ResponseParser.UrgencyLevel.CRITICAL -> androidx.compose.ui.graphics.Color.Red
            ResponseParser.UrgencyLevel.HIGH -> androidx.compose.ui.graphics.Color(0xFFFF6B00) // Orange
            ResponseParser.UrgencyLevel.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFFFCC00) // Yellow
            ResponseParser.UrgencyLevel.LOW -> androidx.compose.ui.graphics.Color.Green
            ResponseParser.UrgencyLevel.UNKNOWN -> androidx.compose.ui.graphics.Color.Gray
        }
    }

    /**
     * Get safety color for structural results
     */
    fun getSafetyColor(safetyStatus: ResponseParser.SafetyStatus): androidx.compose.ui.graphics.Color {
        return when (safetyStatus) {
            ResponseParser.SafetyStatus.CRITICAL -> androidx.compose.ui.graphics.Color.Red
            ResponseParser.SafetyStatus.UNSAFE -> androidx.compose.ui.graphics.Color(0xFFFF6B00) // Orange
            ResponseParser.SafetyStatus.CAUTION -> androidx.compose.ui.graphics.Color(0xFFFFCC00) // Yellow
            ResponseParser.SafetyStatus.SAFE -> androidx.compose.ui.graphics.Color.Green
            ResponseParser.SafetyStatus.UNKNOWN -> androidx.compose.ui.graphics.Color.Gray
        }
    }

    /**
     * Get urgency/safety level display text
     */
    fun getLevelDisplayText(level: Any): String {
        return when (level) {
            is ResponseParser.UrgencyLevel -> level.name.lowercase().replaceFirstChar { it.uppercase() }
            is ResponseParser.SafetyStatus -> level.name.lowercase().replaceFirstChar { it.uppercase() }
            else -> "Unknown"
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
     * Get estimated memory usage of current image
     */
    fun getImageMemoryUsage(): String {
        val image = _uiState.value.selectedImage ?: return "0 MB"
        val memoryBytes = ImageUtils.estimateBitmapMemory(image)
        val memoryMB = memoryBytes / (1024 * 1024)
        return "$memoryMB MB"
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ImageTriageViewModel cleared")

        // Clean up image resources
        _uiState.value.selectedImage?.let { bitmap ->
            ImageUtils.recycleBitmapIfNeeded(bitmap)
        }
    }
}