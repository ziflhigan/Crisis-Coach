package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.GenerationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Service for analyzing images using Gemma's multimodal capabilities
 * Provides medical triage and structural damage assessment functionality
 */
class ImageAnalysisService(
    private val context: Context,
    private val gemmaModelManager: GemmaModelManager
) {

    companion object {
        private const val TAG = "ImageAnalysisService"
        private const val MAX_ANALYSIS_RETRIES = 2
        private const val MIN_IMAGE_SIZE = 64
        private const val MAX_IMAGE_SIZE = 1024
        private const val JPEG_QUALITY = 90
    }

    // State management
    private val _analysisState = MutableStateFlow(AnalysisState.IDLE)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _currentAnalysisType = MutableStateFlow(AnalysisType.GENERAL)
    val currentAnalysisType: StateFlow<AnalysisType> = _currentAnalysisType.asStateFlow()

    /**
     * Analyzes an image for medical triage purposes
     */
    suspend fun analyzeMedicalImage(
        image: Bitmap,
        specificQuestion: String? = null,
        patientContext: String? = null
    ): MedicalAnalysisResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting medical image analysis")
        _analysisState.value = AnalysisState.ANALYZING
        _currentAnalysisType.value = AnalysisType.MEDICAL

        try {
            // Validate and preprocess image
            val processedImage = preprocessImage(image)
            if (processedImage == null) {
                _analysisState.value = AnalysisState.IDLE
                return@withContext MedicalAnalysisResult.Error("Image preprocessing failed")
            }

            // Build medical analysis prompt
            val prompt = buildMedicalAnalysisPrompt(specificQuestion, patientContext)

            // Perform analysis with retries
            val analysisResult = performImageAnalysis(processedImage, prompt)

            _analysisState.value = AnalysisState.IDLE

            when (analysisResult) {
                is ImageAnalysisResult.Success -> {
                    val medicalAssessment = parseMedicalResponse(analysisResult.analysis)
                    MedicalAnalysisResult.Success(
                        assessment = medicalAssessment.assessment,
                        urgencyLevel = medicalAssessment.urgencyLevel,
                        recommendations = medicalAssessment.recommendations,
                        requiresProfessionalCare = medicalAssessment.requiresProfessionalCare,
                        confidenceLevel = analysisResult.confidence,
                        analysisTimeMs = analysisResult.analysisTimeMs
                    )
                }
                is ImageAnalysisResult.Error -> {
                    MedicalAnalysisResult.Error(analysisResult.message, analysisResult.cause)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Medical image analysis failed: ${e.message}", e)
            _analysisState.value = AnalysisState.IDLE
            MedicalAnalysisResult.Error("Medical analysis failed: ${e.message}", e)
        }
    }

    /**
     * Analyzes an image for structural damage assessment
     */
    suspend fun analyzeStructuralImage(
        image: Bitmap,
        structureType: StructureType = StructureType.UNKNOWN,
        specificConcerns: String? = null
    ): StructuralAnalysisResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting structural image analysis for: $structureType")
        _analysisState.value = AnalysisState.ANALYZING
        _currentAnalysisType.value = AnalysisType.STRUCTURAL

        try {
            // Validate and preprocess image
            val processedImage = preprocessImage(image)
            if (processedImage == null) {
                _analysisState.value = AnalysisState.IDLE
                return@withContext StructuralAnalysisResult.Error("Image preprocessing failed")
            }

            // Build structural analysis prompt
            val prompt = buildStructuralAnalysisPrompt(structureType, specificConcerns)

            // Perform analysis
            val analysisResult = performImageAnalysis(processedImage, prompt)

            _analysisState.value = AnalysisState.IDLE

            when (analysisResult) {
                is ImageAnalysisResult.Success -> {
                    val structuralAssessment = parseStructuralResponse(analysisResult.analysis, structureType)
                    StructuralAnalysisResult.Success(
                        structureType = structureType,
                        damageLevel = structuralAssessment.damageLevel,
                        safetyStatus = structuralAssessment.safetyStatus,
                        identifiedIssues = structuralAssessment.identifiedIssues,
                        immediateActions = structuralAssessment.immediateActions,
                        confidenceLevel = analysisResult.confidence,
                        analysisTimeMs = analysisResult.analysisTimeMs
                    )
                }
                is ImageAnalysisResult.Error -> {
                    StructuralAnalysisResult.Error(analysisResult.message, analysisResult.cause)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Structural image analysis failed: ${e.message}", e)
            _analysisState.value = AnalysisState.IDLE
            StructuralAnalysisResult.Error("Structural analysis failed: ${e.message}", e)
        }
    }

    /**
     * Performs general image analysis
     */
    suspend fun analyzeGeneralImage(
        image: Bitmap,
        question: String = "What do you see in this image that might be relevant for emergency response?"
    ): GeneralAnalysisResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting general image analysis")
        _analysisState.value = AnalysisState.ANALYZING
        _currentAnalysisType.value = AnalysisType.GENERAL

        try {
            // Validate and preprocess image
            val processedImage = preprocessImage(image)
            if (processedImage == null) {
                _analysisState.value = AnalysisState.IDLE
                return@withContext GeneralAnalysisResult.Error("Image preprocessing failed")
            }

            // Build general analysis prompt
            val prompt = buildGeneralAnalysisPrompt(question)

            // Perform analysis
            val analysisResult = performImageAnalysis(processedImage, prompt)

            _analysisState.value = AnalysisState.IDLE

            when (analysisResult) {
                is ImageAnalysisResult.Success -> {
                    GeneralAnalysisResult.Success(
                        description = analysisResult.analysis,
                        keyObservations = extractKeyObservations(analysisResult.analysis),
                        suggestedActions = extractSuggestedActions(analysisResult.analysis),
                        confidenceLevel = analysisResult.confidence,
                        analysisTimeMs = analysisResult.analysisTimeMs
                    )
                }
                is ImageAnalysisResult.Error -> {
                    GeneralAnalysisResult.Error(analysisResult.message, analysisResult.cause)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "General image analysis failed: ${e.message}", e)
            _analysisState.value = AnalysisState.IDLE
            GeneralAnalysisResult.Error("General analysis failed: ${e.message}", e)
        }
    }

    /**
     * Cancels ongoing image analysis
     */
    fun cancelAnalysis() {
        Log.d(TAG, "Cancelling image analysis")
        _analysisState.value = AnalysisState.IDLE
    }

    // Private helper methods

    /**
     * Preprocesses image for analysis (resize, optimize)
     */
    private fun preprocessImage(image: Bitmap): Bitmap? {
        return try {
            Log.d(TAG, "Preprocessing image: ${image.width}x${image.height}")

            // Check minimum size
            if (image.width < MIN_IMAGE_SIZE || image.height < MIN_IMAGE_SIZE) {
                Log.w(TAG, "Image too small: ${image.width}x${image.height}")
                return null
            }

            // Calculate target size maintaining aspect ratio
            val aspectRatio = image.width.toFloat() / image.height.toFloat()
            val targetSize = MAX_IMAGE_SIZE

            val (targetWidth, targetHeight) = if (aspectRatio > 1) {
                // Landscape
                targetSize to (targetSize / aspectRatio).toInt()
            } else {
                // Portrait or square
                (targetSize * aspectRatio).toInt() to targetSize
            }

            // Resize if necessary
            val processedImage = if (image.width > targetSize || image.height > targetSize) {
                Log.d(TAG, "Resizing image to: ${targetWidth}x${targetHeight}")
                Bitmap.createScaledBitmap(image, targetWidth, targetHeight, true)
            } else {
                image
            }

            // Ensure RGB_565 or ARGB_8888 format for model compatibility
            val finalImage = if (processedImage.config != Bitmap.Config.ARGB_8888 &&
                processedImage.config != Bitmap.Config.RGB_565) {
                processedImage.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                processedImage
            }

            Log.d(TAG, "Image preprocessed: ${finalImage.width}x${finalImage.height}, config: ${finalImage.config}")
            finalImage

        } catch (e: Exception) {
            Log.e(TAG, "Image preprocessing failed: ${e.message}", e)
            null
        }
    }

    /**
     * Performs the actual image analysis using Gemma model
     */
    private suspend fun performImageAnalysis(
        image: Bitmap,
        prompt: String
    ): ImageAnalysisResult {

        var lastError: String? = null
        repeat(MAX_ANALYSIS_RETRIES) { attempt ->
            Log.d(TAG, "Image analysis attempt ${attempt + 1}/$MAX_ANALYSIS_RETRIES")

            when (val result = gemmaModelManager.generateFromImage(image, prompt)) {
                is GenerationResult.Success -> {
                    Log.d(TAG, "Image analysis successful")
                    return ImageAnalysisResult.Success(
                        analysis = result.text,
                        confidence = calculateConfidence(result.text),
                        analysisTimeMs = result.inferenceTimeMs
                    )
                }
                is GenerationResult.Error -> {
                    lastError = result.message
                    Log.w(TAG, "Analysis attempt failed: ${result.message}")
                }
            }
        }

        return ImageAnalysisResult.Error("Analysis failed after $MAX_ANALYSIS_RETRIES attempts: $lastError")
    }

    /**
     * Builds medical analysis prompt
     */
    private fun buildMedicalAnalysisPrompt(specificQuestion: String?, patientContext: String?): String {
        val basePrompt = """
            You are an experienced field medic AI assistant analyzing an image for emergency medical triage.
            
            Please analyze this image and provide:
            1. A clear assessment of what you observe
            2. The urgency level (Critical, High, Medium, Low)
            3. Immediate care recommendations
            4. Whether professional medical care is required
            
            Focus on visible injuries, symptoms, or medical conditions.
        """.trimIndent()

        val contextSection = if (!patientContext.isNullOrBlank()) {
            "\n\nPatient context: $patientContext"
        } else ""

        val questionSection = if (!specificQuestion.isNullOrBlank()) {
            "\n\nSpecific question: $specificQuestion"
        } else ""

        return "$basePrompt$contextSection$questionSection\n\nProvide your analysis:"
    }

    /**
     * Builds structural analysis prompt
     */
    private fun buildStructuralAnalysisPrompt(structureType: StructureType, specificConcerns: String?): String {
        val structureContext = when (structureType) {
            StructureType.BUILDING -> "building or residential structure"
            StructureType.BRIDGE -> "bridge or crossing structure"
            StructureType.ROAD -> "road, pathway, or transportation infrastructure"
            StructureType.UTILITY -> "utility infrastructure (power lines, pipes, etc.)"
            StructureType.VEHICLE -> "vehicle or transportation equipment"
            StructureType.UNKNOWN -> "structure or infrastructure"
        }

        val basePrompt = """
            You are a structural engineer AI assistant analyzing damage to a $structureContext.
            
            Please analyze this image and provide:
            1. Assessment of visible damage or structural issues
            2. Safety status (Safe, Caution, Unsafe, Critical)
            3. Identified problems or concerns
            4. Immediate actions required
            
            Focus on structural integrity, safety hazards, and immediate risks.
        """.trimIndent()

        val concernsSection = if (!specificConcerns.isNullOrBlank()) {
            "\n\nSpecific concerns: $specificConcerns"
        } else ""

        return "$basePrompt$concernsSection\n\nProvide your structural assessment:"
    }

    /**
     * Builds general analysis prompt
     */
    private fun buildGeneralAnalysisPrompt(question: String): String {
        return """
            You are an emergency response AI assistant analyzing an image.
            
            Question: $question
            
            Please provide a detailed description focusing on elements relevant to emergency response,
            safety concerns, and any actionable observations.
            
            Your analysis:
        """.trimIndent()
    }

    /**
     * Parses medical analysis response
     */
    private fun parseMedicalResponse(response: String): MedicalAssessment {
        val urgencyLevel = when {
            response.contains("critical", true) -> UrgencyLevel.CRITICAL
            response.contains("high", true) && response.contains("urgency", true) -> UrgencyLevel.HIGH
            response.contains("medium", true) || response.contains("moderate", true) -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }

        val requiresProfessional = response.contains("professional", true) ||
                response.contains("hospital", true) ||
                response.contains("doctor", true) ||
                urgencyLevel in listOf(UrgencyLevel.CRITICAL, UrgencyLevel.HIGH)

        val recommendations = extractRecommendations(response)

        return MedicalAssessment(
            assessment = response,
            urgencyLevel = urgencyLevel,
            recommendations = recommendations,
            requiresProfessionalCare = requiresProfessional
        )
    }

    /**
     * Parses structural analysis response
     */
    private fun parseStructuralResponse(response: String, structureType: StructureType): StructuralAssessment {
        val safetyStatus = when {
            response.contains("critical", true) || response.contains("collapse", true) -> SafetyStatus.CRITICAL
            response.contains("unsafe", true) || response.contains("dangerous", true) -> SafetyStatus.UNSAFE
            response.contains("caution", true) || response.contains("concern", true) -> SafetyStatus.CAUTION
            else -> SafetyStatus.SAFE
        }

        val damageLevel = when {
            response.contains("severe", true) || response.contains("major", true) -> DamageLevel.SEVERE
            response.contains("moderate", true) || response.contains("significant", true) -> DamageLevel.MODERATE
            response.contains("minor", true) || response.contains("light", true) -> DamageLevel.MINOR
            else -> DamageLevel.NONE
        }

        return StructuralAssessment(
            damageLevel = damageLevel,
            safetyStatus = safetyStatus,
            identifiedIssues = extractIssues(response),
            immediateActions = extractActions(response)
        )
    }

    /**
     * Calculates confidence level based on response characteristics
     */
    private fun calculateConfidence(response: String): Float {
        var confidence = 0.5f // Base confidence

        // Increase confidence for detailed responses
        if (response.length > 100) confidence += 0.2f

        // Increase for specific terminology
        if (response.contains(Regex("\\b(wound|injury|fracture|bleeding)\\b", RegexOption.IGNORE_CASE))) {
            confidence += 0.1f
        }

        // Decrease for uncertain language
        if (response.contains(Regex("\\b(maybe|possibly|unclear|uncertain)\\b", RegexOption.IGNORE_CASE))) {
            confidence -= 0.2f
        }

        return confidence.coerceIn(0.1f, 1.0f)
    }

    /**
     * Extracts key observations from analysis text
     */
    private fun extractKeyObservations(analysis: String): List<String> {
        // Simple extraction based on sentence structure
        return analysis.split(". ")
            .filter { it.trim().length > 10 }
            .take(5)
            .map { it.trim() }
    }

    /**
     * Extracts suggested actions from analysis text
     */
    private fun extractSuggestedActions(analysis: String): List<String> {
        val actionWords = listOf("should", "recommend", "suggest", "advise", "must", "need to")
        return analysis.split(". ")
            .filter { sentence ->
                actionWords.any { actionWord -> sentence.contains(actionWord, true) }
            }
            .take(3)
            .map { it.trim() }
    }

    /**
     * Extracts recommendations from medical response
     */
    private fun extractRecommendations(response: String): List<String> {
        return extractSuggestedActions(response)
    }

    /**
     * Extracts identified issues from structural response
     */
    private fun extractIssues(response: String): List<String> {
        val issueWords = listOf("crack", "damage", "broken", "collapsed", "unstable", "leak")
        return response.split(". ")
            .filter { sentence ->
                issueWords.any { issueWord -> sentence.contains(issueWord, true) }
            }
            .take(5)
            .map { it.trim() }
    }

    /**
     * Extracts immediate actions from structural response
     */
    private fun extractActions(response: String): List<String> {
        return extractSuggestedActions(response)
    }
}

/**
 * Enumerations for analysis types and levels
 */
enum class AnalysisState {
    IDLE,
    ANALYZING,
    ERROR
}

enum class AnalysisType {
    GENERAL,
    MEDICAL,
    STRUCTURAL
}

enum class UrgencyLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

enum class SafetyStatus {
    SAFE,
    CAUTION,
    UNSAFE,
    CRITICAL
}

enum class DamageLevel {
    NONE,
    MINOR,
    MODERATE,
    SEVERE
}

enum class StructureType {
    BUILDING,
    BRIDGE,
    ROAD,
    UTILITY,
    VEHICLE,
    UNKNOWN
}

/**
 * Result classes for image analysis operations
 */
sealed class ImageAnalysisResult {
    data class Success(
        val analysis: String,
        val confidence: Float,
        val analysisTimeMs: Long
    ) : ImageAnalysisResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ImageAnalysisResult()
}

sealed class MedicalAnalysisResult {
    data class Success(
        val assessment: String,
        val urgencyLevel: UrgencyLevel,
        val recommendations: List<String>,
        val requiresProfessionalCare: Boolean,
        val confidenceLevel: Float,
        val analysisTimeMs: Long
    ) : MedicalAnalysisResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : MedicalAnalysisResult()
}

sealed class StructuralAnalysisResult {
    data class Success(
        val structureType: StructureType,
        val damageLevel: DamageLevel,
        val safetyStatus: SafetyStatus,
        val identifiedIssues: List<String>,
        val immediateActions: List<String>,
        val confidenceLevel: Float,
        val analysisTimeMs: Long
    ) : StructuralAnalysisResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : StructuralAnalysisResult()
}

sealed class GeneralAnalysisResult {
    data class Success(
        val description: String,
        val keyObservations: List<String>,
        val suggestedActions: List<String>,
        val confidenceLevel: Float,
        val analysisTimeMs: Long
    ) : GeneralAnalysisResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : GeneralAnalysisResult()
}

/**
 * Data classes for analysis results
 */
private data class MedicalAssessment(
    val assessment: String,
    val urgencyLevel: UrgencyLevel,
    val recommendations: List<String>,
    val requiresProfessionalCare: Boolean
)

private data class StructuralAssessment(
    val damageLevel: DamageLevel,
    val safetyStatus: SafetyStatus,
    val identifiedIssues: List<String>,
    val immediateActions: List<String>
)