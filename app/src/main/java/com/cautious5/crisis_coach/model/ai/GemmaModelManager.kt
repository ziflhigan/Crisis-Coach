package com.cautious5.crisis_coach.model.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.system.measureTimeMillis
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.annotation.SuppressLint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

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

        @Volatile private var INSTANCE: GemmaModelManager? = null

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
            return@withContext try {
                val initTime = measureTimeMillis {
                    when (val load = modelLoader.loadModel(config.variant)) {
                        is ModelLoader.LoadResult.Success -> {
                            initializeMediaPipeModel(load.modelPath, config)
                            _currentConfig.value = config
                            _modelState.value = ModelState.READY
                        }
                        is ModelLoader.LoadResult.Error ->
                            return@withContext InitializationResult.Error(load.message, load.cause)
                        is ModelLoader.LoadResult.Missing ->  // Add this case
                            return@withContext InitializationResult.Error(
                                "Model file not found: ${config.variant.fileName}. Please download the model first.",
                                null
                            )
                    }
                }
                Log.i(TAG, "Model initialized in $initTime ms")
                _performanceMetrics.value = _performanceMetrics.value.copy(initializationTimeMs = initTime)
                InitializationResult.Success
            } catch (e: Exception) {
                _modelState.value = ModelState.ERROR
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
                                    _performanceMetrics.value = _performanceMetrics.value.withNewInference(inferenceTime)
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
        prompt: String = "Describe what you see and provide relevant advice.",
        maxTokens: Int? = null
    ): GenerationResult = withContext(Dispatchers.IO) {

        if (_modelState.value != ModelState.READY) {
            return@withContext GenerationResult.Error("Model not ready. Current state: ${_modelState.value}")
        }

        Log.d(TAG, "Generating response for image analysis: $prompt")

        return@withContext inferenceDebugMutex.withLock {
            _modelState.value = ModelState.BUSY

            try {
                val startTime = System.currentTimeMillis()

                // Preprocess image
                val processedImage = preprocessImage(image)
                val mpImage = BitmapImageBuilder(processedImage).build()

                // Recreate session if config changes
                val currentModelConfig = _currentConfig.value
                if (visionSession == null || currentModelConfig != visionSessionConfig) {
                    Log.d(TAG, "Creating new vision session. Reason: New session or config change.")
                    visionSession?.close() // Close old session
                    visionSession = createVisionSession(currentModelConfig)
                    visionSessionConfig = currentModelConfig // Store the config used
                }

                // Add query and image
                visionSession?.addQueryChunk(prompt)
                visionSession?.addImage(mpImage)

                val inferenceTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Image analysis completed in ${inferenceTime}ms")

                // Generate response synchronously for images
                val response = visionSession?.generateResponse() ?: "Unable to analyze image"

                // Update performance metrics
                _performanceMetrics.value = _performanceMetrics.value.withNewInference(inferenceTime)
                _modelState.value = ModelState.READY

                GenerationResult.Success(
                    text = response,
                    inferenceTimeMs = inferenceTime,
                    tokensGenerated = response.split(" ").size
                )

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
    private suspend fun initializeMediaPipeModel(modelPath: String, config: ModelConfig) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing MediaPipe LLM Inference with path: $modelPath")
        Log.d(TAG, "Hardware preference: ${config.hardwarePreference}")

        // Configure LLM Inference options
        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(config.maxOutputTokens)
            .setMaxTopK(config.topK)
            .build()

        // Create LLM Inference instance
        llmInference = LlmInference.createFromOptions(context, inferenceOptions)

        Log.i(TAG, "MediaPipe LLM Inference initialized successfully")
        Log.i(TAG, "Hardware acceleration will be automatically optimized by MediaPipe")
    }

    /**
     * Creates a text-only session
     */
    private fun createTextSession(config: ModelConfig?): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .apply {
                config?.let {
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

    /**
     * Preprocesses image for model input
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Resize image to model's expected input size (typically 256x256 or 512x512)
        val targetSize = 256
        return if (bitmap.width != targetSize || bitmap.height != targetSize) {
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        } else {
            bitmap
        }
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