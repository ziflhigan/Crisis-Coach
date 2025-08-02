package com.cautious5.crisis_coach.model.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureTimeMillis

/**
 * Main interface for Gemma model operations using MediaPipe LLM Inference API
 * Handles model initialization, text generation, and multimodal inference
 */
@SuppressLint("StaticFieldLeak")
class GemmaModelManager private constructor(
    private val context: Context,
    val modelLoader: ModelLoader
) {
    companion object {
        private const val TAG = "GemmaModelManager"

        @Volatile
        private var INSTANCE: GemmaModelManager? = null

        fun getInstance(ctx: Context): GemmaModelManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GemmaModelManager(
                    ctx.applicationContext,
                    ModelLoader(ctx.applicationContext)
                ).also { INSTANCE = it }
            }
    }

    // State management
    private val _modelState = MutableStateFlow(ModelState.UNINITIALIZED)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(ModelPerformanceMetrics())
    val performanceMetrics: StateFlow<ModelPerformanceMetrics> = _performanceMetrics.asStateFlow()

    private val _currentConfig = MutableStateFlow<ModelConfig?>(null)
    val currentConfig: StateFlow<ModelConfig?> = _currentConfig.asStateFlow()

    private val _loadProgress = MutableStateFlow(0f)
    val loadProgress: StateFlow<Float> = _loadProgress.asStateFlow()

    // Thread safety
    private val inferenceDebugMutex = Mutex()

    // MediaPipe LLM components
    private var llmInference: LlmInference? = null
    private var textSession: LlmInferenceSession? = null
    private var visionSession: LlmInferenceSession? = null

    private var textSessionConfig: ModelConfig? = null
    private var visionSessionConfig: ModelConfig? = null

    /**
     * Initializes the model with the specified configuration using MediaPipe
     */
    suspend fun initializeModel(config: ModelConfig): InitializationResult =
        withContext(Dispatchers.IO) {
            _modelState.value = ModelState.LOADING
            _loadProgress.value = 0f

            return@withContext try {
                val initTime = measureTimeMillis {
                    // Phase 1: File preparation (0% to 80%)
                    val modelPath = modelLoader.prepareModelFile(config.variant) { progress ->
                        _loadProgress.value = progress * 0.8f  // Scale to 80% max
                    }

                    if (modelPath != null) {
                        // Small progress bump to show transition
                        _loadProgress.value = 0.85f

                        // Phase 2: Model initialization (85% to 100%)
                        initializeMediaPipeModel(modelPath, config)

                        // Complete
                        _currentConfig.value = config
                        _loadProgress.value = 1.0f
                        _modelState.value = ModelState.READY
                    } else {
                        throw IOException("Failed to find or extract model file: ${config.variant.fileName}")
                    }
                }
                Log.i(TAG, "Model initialized in $initTime ms")
                _performanceMetrics.value = _performanceMetrics.value.copy(initializationTimeMs = initTime)
                InitializationResult.Success
            } catch (e: Exception) {
                _modelState.value = ModelState.ERROR
                _loadProgress.value = 0f
                Log.e(TAG, "Model init failed: ${e.message}", e)
                InitializationResult.Error("Model init failed: ${e.message}", e)
            }
        }

    /**
     * Generates streaming text response from text input
     */
    fun generateText(
        prompt: String,
        maxTokens: Int? = null,
        temperature: Float? = null
    ): Flow<GenerationResult> = flow {
        if (_modelState.value != ModelState.READY) {
            emit(GenerationResult.Error("Model not ready. Current state: ${_modelState.value}"))
            return@flow
        }

        if (prompt.isBlank()) {
            emit(GenerationResult.Error("Prompt cannot be empty"))
            return@flow
        }

        Log.d(TAG, "Generating text for prompt: ${prompt.take(50)}...")

        inferenceDebugMutex.withLock {
            _modelState.value = ModelState.BUSY

            try {
                val startTime = System.currentTimeMillis()
                var fullResponse = ""
                var tokenCount = 0

                // Recreate session if config changes
                val currentModelConfig = _currentConfig.value
                if (textSession == null || currentModelConfig != textSessionConfig) {
                    Log.d(TAG, "Creating new text session. Reason: New session or config change.")
                    textSession?.close() // Close old session if it exists
                    textSession = createTextSession(currentModelConfig)
                    textSessionConfig = currentModelConfig // Store the config used
                }

                // Add query and generate streaming response
                textSession?.addQueryChunk(prompt)

                val result = suspendCancellableCoroutine<GenerationResult> { continuation ->
                    var isCompleted = false

                    textSession?.generateResponseAsync { partialResult, done ->
                        try {
                            if (!isCompleted) {
                                fullResponse += partialResult
                                tokenCount++

                                if (done) {
                                    isCompleted = true
                                    val inferenceTime = System.currentTimeMillis() - startTime

                                    // Update performance metrics
                                    _performanceMetrics.value =
                                        _performanceMetrics.value.withNewInference(inferenceTime)
                                    _modelState.value = ModelState.READY

                                    continuation.resume(
                                        GenerationResult.Success(
                                            text = fullResponse,
                                            inferenceTimeMs = inferenceTime,
                                            tokensGenerated = tokenCount
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            if (!isCompleted) {
                                isCompleted = true
                                _modelState.value = ModelState.READY
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                }

                emit(result)

            } catch (e: Exception) {
                Log.e(TAG, "Text generation failed: ${e.message}", e)
                _modelState.value = ModelState.READY
                emit(GenerationResult.Error("Text generation failed: ${e.message}", e))
            }
        }
    }

    /**
     * Generates text response from image and text input (multimodal)
     */
    suspend fun generateFromImage(
        image: Bitmap,
        prompt: String = "Describe what you see and provide relevant advice."
    ): GenerationResult = withContext(Dispatchers.IO) {
        if (_modelState.value != ModelState.READY) {
            return@withContext GenerationResult.Error("Model not ready. Current state: ${_modelState.value}")
        }

        Log.d(TAG, "Generating response for image analysis: $prompt")

        return@withContext inferenceDebugMutex.withLock {
            _modelState.value = ModelState.BUSY
            try {
                val startTime = System.currentTimeMillis()

                // Recreate session only if the configuration has changed
                val currentModelConfig = _currentConfig.value
                if (visionSession == null || currentModelConfig != visionSessionConfig) {
                    Log.d(TAG, "Creating new vision session. Reason: New session or config change.")
                    visionSession?.close()
                    visionSession = createVisionSession(currentModelConfig)
                    visionSessionConfig = currentModelConfig
                }

                // The image passed in should already be preprocessed
                val mpImage = BitmapImageBuilder(image).build()

                visionSession?.addQueryChunk(prompt)
                visionSession?.addImage(mpImage)
                val response = visionSession?.generateResponse()

                val inferenceTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Image analysis completed in ${inferenceTime}ms")

                _performanceMetrics.value =
                    _performanceMetrics.value.withNewInference(inferenceTime)
                _modelState.value = ModelState.READY

                if (response != null) {
                    GenerationResult.Success(
                        text = response,
                        inferenceTimeMs = inferenceTime,
                        tokensGenerated = response.split(" ").size
                    )
                } else {
                    GenerationResult.Error("No response from vision session")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image analysis failed: ${e.message}", e)
                _modelState.value = ModelState.READY
                GenerationResult.Error("Image analysis failed: ${e.message}", e)
            }
        }
    }

    /**
     * Releases model resources
     */
    suspend fun releaseModel(): Unit = withContext(Dispatchers.IO) {
        Log.d(TAG, "Releasing model resources")

        try {
            textSession?.close()
            visionSession?.close()
            llmInference?.close()

            textSession = null
            visionSession = null
            llmInference = null

            textSessionConfig = null
            visionSessionConfig = null

            _modelState.value = ModelState.UNINITIALIZED
            _currentConfig.value = null

            // Reset performance metrics
            _performanceMetrics.value = ModelPerformanceMetrics()

            Log.d(TAG, "Model resources released successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing model resources: ${e.message}", e)
        }
    }

    /**
     * Applies new generation parameters to the model without full re-initialization
     * Recreates sessions for sampling params and engine for token limit changes
     */
    suspend fun applyGenerationParams(params: GenerationParams): Unit = withContext(Dispatchers.IO) {
        val currentConfig = _currentConfig.value ?: return@withContext
        Log.d(TAG, "Applying generation params: temp=${params.temperature}, topK=${params.topK}, topP=${params.topP}, maxTokens=${params.maxOutputTokens}")

        val updatedConfig = currentConfig.copy(
            temperature = params.temperature,
            topK = params.topK,
            topP = params.topP,
            maxOutputTokens = params.maxOutputTokens
        )

        try {
            // Check if we need to recreate the engine (for max tokens change)
            val needsEngineRecreation = params.maxOutputTokens != currentConfig.maxOutputTokens

            if (needsEngineRecreation) {
                Log.d(TAG, "Max tokens changed, need to recreate engine")
                _modelState.value = ModelState.LOADING
                _loadProgress.value = 0.1f

                // Close everything
                textSession?.close()
                visionSession?.close()
                llmInference?.close()

                textSession = null
                visionSession = null
                llmInference = null
                textSessionConfig = null
                visionSessionConfig = null

                _loadProgress.value = 0.3f

                // Recreate the engine with new config
                val modelPath = modelLoader.getInternalModelPath(updatedConfig.variant)
                initializeMediaPipeModel(modelPath, updatedConfig)

                _loadProgress.value = 0.9f
            } else {
                Log.d(TAG, "Only sampling params changed, recreating sessions only")
                // Just close sessions, they'll be recreated on next use with new params
                textSession?.close()
                visionSession?.close()
                textSession = null
                visionSession = null
                textSessionConfig = null
                visionSessionConfig = null
            }

            // Update the config
            _currentConfig.value = updatedConfig
            _modelState.value = ModelState.READY
            _loadProgress.value = 1.0f

            Log.i(TAG, "Generation parameters applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying generation parameters: ${e.message}", e)
            _modelState.value = ModelState.ERROR
            _loadProgress.value = 0f
            throw e
        }
    }

    /**
     * Gets current memory usage estimation
     */
    fun getMemoryUsage(): Long {
        return _currentConfig.value?.variant?.approximateRamUsageMB ?: 0L
    }

    /**
     * Checks if model is ready for inference
     */
    fun isReady(): Boolean = _modelState.value == ModelState.READY

    // Private helper methods

    /**
     * Initializes the MediaPipe LLM Inference engine
     */
    private suspend fun initializeMediaPipeModel(modelPath: String, config: ModelConfig) =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Initializing MediaPipe LLM Inference with path: $modelPath")
            Log.d(TAG, "Hardware preference: ${config.hardwarePreference}")
            Log.d(TAG, "Max Tokens set to: ${config.maxOutputTokens}")

            val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(config.maxOutputTokens)
                .setMaxNumImages(1) // Add this line for vision capabilities

            when (config.hardwarePreference) {
                HardwarePreference.GPU_PREFERRED -> {
                    Log.d(TAG, "Configuring for GPU acceleration")
                    optionsBuilder.setPreferredBackend(LlmInference.Backend.GPU)
                }

                HardwarePreference.CPU_ONLY -> {
                    Log.d(TAG, "Configuring for CPU only")
                    optionsBuilder.setPreferredBackend(LlmInference.Backend.CPU)
                }

                HardwarePreference.NNAPI -> {
                    Log.d(
                        TAG,
                        "NNAPI requested, but not supported by LLM Inference API. Using CPU."
                    )
                    optionsBuilder.setPreferredBackend(LlmInference.Backend.CPU)
                }

                HardwarePreference.AUTO -> {
                    Log.d(TAG, "Using automatic hardware selection (default)")
                    // Don't set backend, let MediaPipe decide
                }
            }

            llmInference = LlmInference.createFromOptions(context, optionsBuilder.build())
            Log.i(
                TAG,
                "MediaPipe LLM Inference engine initialized successfully with ${config.hardwarePreference}"
            )
        }

    /**
     * Creates a text-only session
     */
    private fun createTextSession(config: ModelConfig?): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .apply {
                config?.let {
                    Log.d(
                        TAG,
                        "Applying session params: Temp=${it.temperature}, TopK=${it.topK}, TopP=${it.topP}"
                    )
                    setTemperature(it.temperature)
                    setTopK(it.topK)
                    setTopP(it.topP)
                }
            }
            .build()
        return LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
    }

    /**
     * Creates a vision-enabled session
     */
    private fun createVisionSession(config: ModelConfig?): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .apply {
                config?.let {
                    setTemperature(it.temperature)
                    setTopK(it.topK)
                    setTopP(it.topP)
                }
                // Enable vision modality
                setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
            }
            .build()

        return LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
    }
}

/**
 * Result classes for model operations
 */
sealed class InitializationResult {
    data object Success : InitializationResult()
    data class Error(val message: String, val cause: Throwable? = null) : InitializationResult()
}

sealed class GenerationResult {
    data class Success(
        val text: String,
        val inferenceTimeMs: Long,
        val tokensGenerated: Int
    ) : GenerationResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : GenerationResult()
}