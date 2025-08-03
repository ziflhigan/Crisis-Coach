package com.cautious5.crisis_coach.model.ai

import android.util.Log

/**
 * Configuration data class for Gemma model variants and hardware preferences
 * Defines supported model types, their characteristics, and runtime preferences
 */
data class ModelConfig(
    val variant: ModelVariant,
    val hardwarePreference: HardwarePreference,
    val modelPath: String,
    val maxOutputTokens: Int = 4096,
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f
) {
    companion object {
        private const val TAG = "ModelConfig"

        /**
         * Creates a default configuration based on device capabilities
         */
        fun createDefault(
            availableRamMB: Long,
            hasGpuAcceleration: Boolean,
            modelPath: String
        ): ModelConfig {
            val variant = if (availableRamMB >= 6144) { // 6GB or more
                Log.d(TAG, "High-end device detected, using E4B model")
                ModelVariant.GEMMA_3N_E4B
            } else {
                Log.d(TAG, "Mid-range device detected, using E2B model")
                ModelVariant.GEMMA_3N_E2B
            }

            val hardwarePreference = if (hasGpuAcceleration) {
                Log.d(TAG, "GPU acceleration available")
                HardwarePreference.GPU_PREFERRED
            } else {
                Log.d(TAG, "CPU-only execution")
                HardwarePreference.CPU_ONLY
            }

            return ModelConfig(
                variant = variant,
                hardwarePreference = hardwarePreference,
                modelPath = modelPath
            )
        }
    }
}

/**
 * Supported Gemma model variants with their characteristics
 */
enum class ModelVariant(
    val displayName: String,
    val effectiveParams: String,
    val approximateRamUsageMB: Long,
    val fileName: String,
    val huggingFaceRepo: String,
    val downloadFileName: String
) {
    GEMMA_3N_E2B(
        displayName = "Gemma 3n E2B (Standard)",
        effectiveParams = "2B",
        approximateRamUsageMB = 3100,
        fileName = "gemma-3n-E2B-it-int4.task",
        huggingFaceRepo = "google/gemma-3n-E2B-it-litert-preview",
        downloadFileName = "gemma-3n-E2B-it-int4.task"
    ),
    GEMMA_3N_E4B(
        displayName = "Gemma 3n E4B (High Quality)",
        effectiveParams = "4B",
        approximateRamUsageMB = 4400,
        fileName = "gemma-3n-E4B-it-int4.task",
        huggingFaceRepo = "google/gemma-3n-E4B-it-litert-preview",
        downloadFileName = "gemma-3n-E4B-it-int4.task"
    );

    fun getDownloadUrl(): String {
        return "https://huggingface.co/$huggingFaceRepo/resolve/main/$downloadFileName"
    }
}

/**
 * Hardware acceleration preferences for model execution
 */
enum class HardwarePreference(
    val displayName: String,
    val description: String
) {
    AUTO(
        displayName = "Auto",
        description = "Automatically select best available hardware"
    ),
    GPU_PREFERRED(
        displayName = "GPU",
        description = "Use GPU acceleration for faster inference"
    ),
    CPU_ONLY(
        displayName = "CPU",
        description = "Use CPU only (more compatible, uses less battery)"
    ),
    NNAPI(
        displayName = "NNAPI (Not Supported)",
        description = "Android Neural Networks API - not available for LLM tasks"
    )
}

/**
 * Model execution state tracking
 */
enum class ModelState {
    UNINITIALIZED,
    LOADING,
    READY,
    ERROR,
    BUSY
}

/**
 * Performance metrics for model operations
 */
data class ModelPerformanceMetrics(
    val initializationTimeMs: Long = 0,
    val lastInferenceTimeMs: Long = 0,
    val averageInferenceTimeMs: Long = 0,
    val totalInferences: Int = 0,
    val memoryUsageMB: Long = 0
) {
    /**
     * Updates metrics with a new inference time
     */
    fun withNewInference(inferenceTimeMs: Long): ModelPerformanceMetrics {
        val newTotal = totalInferences + 1
        val newAverage = if (totalInferences == 0) {
            inferenceTimeMs
        } else {
            ((averageInferenceTimeMs * totalInferences) + inferenceTimeMs) / newTotal
        }

        return copy(
            lastInferenceTimeMs = inferenceTimeMs,
            averageInferenceTimeMs = newAverage,
            totalInferences = newTotal
        )
    }
}

/**
 * Generation parameters for model inference
 */
data class GenerationParams(
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 4096
)