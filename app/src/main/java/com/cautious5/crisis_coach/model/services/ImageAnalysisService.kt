package com.cautious5.crisis_coach.model.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.GenerationResult
import com.cautious5.crisis_coach.utils.ImageUtils
import com.cautious5.crisis_coach.utils.PromptUtils
import com.cautious5.crisis_coach.utils.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Service for analyzing images using Gemma's multimodal capabilities.
 * This service is now a thin layer that coordinates model calls and parsing utilities.
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

    private val _analysisState = MutableStateFlow(AnalysisState.IDLE)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    suspend fun analyzeMedicalImage(
        image: Bitmap,
        specificQuestion: String? = null,
        patientContext: String? = null
    ): MedicalAnalysisResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting medical image analysis")
        _analysisState.value = AnalysisState.ANALYZING

        val processedImage = ImageUtils.preprocessBitmap(image)
        // Build prompt using centralized utility
        val prompt = PromptUtils.buildMedicalAnalysisPrompt(specificQuestion, patientContext)

        when (val analysisResult = performImageAnalysis(processedImage, prompt)) {
            is ImageAnalysisResult.Success -> {
                Log.d(TAG, "Parsing medical response.")
                // Parse response using centralized utility
                val assessment = analysisResult.analysis
                val urgency = ResponseParser.extractUrgencyLevel(assessment)
                val recommendations = ResponseParser.extractActionItems(assessment)

                _analysisState.value = AnalysisState.IDLE
                MedicalAnalysisResult.Success(
                    assessment = assessment,
                    urgencyLevel = urgency,
                    recommendations = recommendations,
                    requiresProfessionalCare = urgency in listOf(ResponseParser.UrgencyLevel.CRITICAL, ResponseParser.UrgencyLevel.HIGH),
                    confidenceLevel = analysisResult.confidence,
                    analysisTimeMs = analysisResult.analysisTimeMs
                )
            }
            is ImageAnalysisResult.Error -> {
                _analysisState.value = AnalysisState.IDLE
                MedicalAnalysisResult.Error(analysisResult.message, analysisResult.cause)
            }
        }
    }

    suspend fun analyzeStructuralImage(
        image: Bitmap,
        structureType: StructureType = StructureType.UNKNOWN,
        specificConcerns: String? = null
    ): StructuralAnalysisResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting structural image analysis for: $structureType")
        _analysisState.value = AnalysisState.ANALYZING

        val processedImage = ImageUtils.preprocessBitmap(image)
        // Build prompt using centralized utility
        val prompt = PromptUtils.buildStructuralAnalysisPrompt(structureType.displayName, specificConcerns)

        when (val analysisResult = performImageAnalysis(processedImage, prompt)) {
            is ImageAnalysisResult.Success -> {
                Log.d(TAG, "Parsing structural response.")
                // Parse response using centralized utility
                val assessment = analysisResult.analysis
                val safetyStatus = ResponseParser.extractSafetyStatus(assessment)
                val issues = ResponseParser.extractKeyFindings(assessment)
                val actions = ResponseParser.extractActionItems(assessment)

                _analysisState.value = AnalysisState.IDLE
                StructuralAnalysisResult.Success(
                    structureType = structureType,
                    damageLevel = DamageLevel.UNKNOWN, // Let parser handle this if needed, or simplify
                    safetyStatus = safetyStatus,
                    identifiedIssues = issues,
                    immediateActions = actions,
                    confidenceLevel = analysisResult.confidence,
                    analysisTimeMs = analysisResult.analysisTimeMs
                )
            }
            is ImageAnalysisResult.Error -> {
                _analysisState.value = AnalysisState.IDLE
                StructuralAnalysisResult.Error(analysisResult.message, analysisResult.cause)
            }
        }
    }

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
                        confidence = ResponseParser.extractConfidence(result.text), // Use parser
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

    fun cancelAnalysis() {
        Log.d(TAG, "Cancelling image analysis")
        _analysisState.value = AnalysisState.IDLE
    }
}


enum class AnalysisState { IDLE, ANALYZING, ERROR }
enum class AnalysisType { GENERAL, MEDICAL, STRUCTURAL }

typealias UrgencyLevel = ResponseParser.UrgencyLevel
typealias SafetyStatus = ResponseParser.SafetyStatus

enum class DamageLevel { NONE, MINOR, MODERATE, SEVERE, UNKNOWN }
enum class StructureType(val displayName: String) {
    BUILDING("Building"),
    BRIDGE("Bridge"),
    ROAD("Road"),
    UTILITY("Utility"),
    VEHICLE("Vehicle"),
    UNKNOWN("Unknown Structure")
}

sealed class ImageAnalysisResult {
    data class Success(val analysis: String, val confidence: Float, val analysisTimeMs: Long) : ImageAnalysisResult()
    data class Error(val message: String, val cause: Throwable? = null) : ImageAnalysisResult()
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

    data class Error(val message: String, val cause: Throwable? = null) : MedicalAnalysisResult()
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

    data class Error(val message: String, val cause: Throwable? = null) : StructuralAnalysisResult()
}