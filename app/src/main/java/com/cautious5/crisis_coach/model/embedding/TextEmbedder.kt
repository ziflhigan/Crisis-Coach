package com.cautious5.crisis_coach.model.embedding

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder as MediaPipeTextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wrapper for MediaPipe TextEmbedder providing text embedding functionality
 * Converts text into vector representations for semantic search
 */
@SuppressLint("StaticFieldLeak")
class TextEmbedder private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "TextEmbedder"
        private const val DEFAULT_MODEL_NAME = "text_embedder.tflite"
        private const val ASSETS_MODEL_PATH = "models/$DEFAULT_MODEL_NAME"

        @Volatile
        private var INSTANCE: TextEmbedder? = null

        fun getInstance(context: Context): TextEmbedder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TextEmbedder(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var mediaPipeEmbedder: MediaPipeTextEmbedder? = null
    private val initializationMutex = Mutex()
    private var isInitialized = false
    private var initializationError: String? = null

    // Configuration
    private var embeddingDimensions = 512 // Default, will be updated after initialization
    private var maxTextLength = 1000 // Maximum characters per embedding request

    /**
     * Initializes the MediaPipe TextEmbedder
     */
    private suspend fun initialize(): EmbedderResult<Unit> = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            if (isInitialized) {
                return@withContext EmbedderResult.Success(Unit)
            }

            if (initializationError != null) {
                return@withContext EmbedderResult.Error(initializationError!!)
            }

            Log.d(TAG, "Initializing MediaPipe TextEmbedder")

            try {
                // Configure base options
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(ASSETS_MODEL_PATH)
                    .build()

                // Configure embedder options
                val options = com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .build()

                // Create the embedder
                mediaPipeEmbedder = MediaPipeTextEmbedder.createFromOptions(context, options)

                // Get embedding dimensions from the model
                val testEmbedding = mediaPipeEmbedder!!.embed("test").embeddingResult().embeddings()[0]
                embeddingDimensions = testEmbedding.floatEmbedding().size

                isInitialized = true

                Log.i(TAG, "TextEmbedder initialized successfully (dimensions: $embeddingDimensions)")
                EmbedderResult.Success(Unit)

            } catch (e: Exception) {
                val errorMsg = "Failed to initialize TextEmbedder: ${e.message}"
                Log.e(TAG, errorMsg, e)
                initializationError = errorMsg
                EmbedderResult.Error(errorMsg, e)
            }
        }
    }

    /**
     * Generates embedding vector for the given text
     */
    suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text provided for embedding")
            return@withContext floatArrayOf()
        }

        // Ensure embedder is initialized
        if (!isInitialized) {
            when (val initResult = initialize()) {
                is EmbedderResult.Error -> {
                    Log.e(TAG, "Cannot embed text: ${initResult.message}")
                    return@withContext floatArrayOf()
                }
                is EmbedderResult.Success -> {
                    // Continue with embedding
                }
            }
        }

        try {
            // Truncate text if too long
            val processedText = if (text.length > maxTextLength) {
                Log.w(TAG, "Text truncated from ${text.length} to $maxTextLength characters")
                text.take(maxTextLength)
            } else {
                text
            }

            // Generate embedding
            val embeddingResult = mediaPipeEmbedder!!.embed(processedText)
            val embedding = embeddingResult.embeddingResult().embeddings()[0]

            // Convert to FloatArray
            val floatEmbedding = embedding.floatEmbedding()

            Log.d(TAG, "Generated embedding for text (${processedText.length} chars): ${floatEmbedding.size} dimensions")

            floatEmbedding

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding for text: ${e.message}", e)
            floatArrayOf()
        }
    }

    /**
     * Generates embeddings for multiple texts in batch
     */
    private suspend fun embedTextBatch(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext emptyList()
        }

        Log.d(TAG, "Generating embeddings for ${texts.size} texts")

        val embeddings = mutableListOf<FloatArray>()

        for ((index, text) in texts.withIndex()) {
            try {
                val embedding = embedText(text)
                embeddings.add(embedding)

                // Log progress for large batches
                if (texts.size > 10 && (index + 1) % 10 == 0) {
                    Log.d(TAG, "Generated embeddings for ${index + 1}/${texts.size} texts")
                }

            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate embedding for text $index: ${e.message}")
                embeddings.add(floatArrayOf()) // Add empty array to maintain index alignment
            }
        }

        Log.d(TAG, "Completed batch embedding: ${embeddings.count { it.isNotEmpty() }}/${texts.size} successful")
        embeddings
    }

    /**
     * Calculates similarity between two text embeddings
     */
    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.isEmpty() || embedding2.isEmpty() || embedding1.size != embedding2.size) {
            return 0f
        }

        return calculateCosineSimilarity(embedding1, embedding2)
    }

    /**
     * Finds the most similar text from a list of candidates
     */
    suspend fun findMostSimilar(
        queryText: String,
        candidates: List<String>,
        threshold: Float = 0.5f
    ): SimilarityResult? = withContext(Dispatchers.IO) {

        if (candidates.isEmpty()) {
            return@withContext null
        }

        Log.d(TAG, "Finding most similar text among ${candidates.size} candidates")

        try {
            // Generate query embedding
            val queryEmbedding = embedText(queryText)
            if (queryEmbedding.isEmpty()) {
                return@withContext null
            }

            // Generate candidate embeddings
            val candidateEmbeddings = embedTextBatch(candidates)

            // Find best match
            var bestIndex = -1
            var bestSimilarity = 0f

            candidateEmbeddings.forEachIndexed { index, embedding ->
                if (embedding.isNotEmpty()) {
                    val similarity = calculateSimilarity(queryEmbedding, embedding)
                    if (similarity > bestSimilarity && similarity >= threshold) {
                        bestSimilarity = similarity
                        bestIndex = index
                    }
                }
            }

            if (bestIndex >= 0) {
                SimilarityResult(
                    text = candidates[bestIndex],
                    similarity = bestSimilarity,
                    index = bestIndex
                )
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to find similar text: ${e.message}", e)
            null
        }
    }

    /**
     * Validates if an embedding is properly formed
     */
    fun isValidEmbedding(embedding: FloatArray): Boolean {
        return embedding.isNotEmpty() &&
                embedding.size == embeddingDimensions &&
                embedding.all { !it.isNaN() && it.isFinite() }
    }

    /**
     * Gets the embedding dimensions
     */
    fun getEmbeddingDimensions(): Int = embeddingDimensions

    /**
     * Gets embedder status information
     */
    fun getStatus(): EmbedderStatus {
        return EmbedderStatus(
            isInitialized = isInitialized,
            embeddingDimensions = embeddingDimensions,
            maxTextLength = maxTextLength,
            error = initializationError
        )
    }

    /**
     * Releases embedder resources
     */
    suspend fun release(): Unit = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            try {
                mediaPipeEmbedder?.close()
                mediaPipeEmbedder = null
                isInitialized = false
                initializationError = null

                Log.d(TAG, "TextEmbedder resources released")

            } catch (e: Exception) {
                Log.e(TAG, "Error releasing TextEmbedder resources: ${e.message}", e)
            }
        }
    }

    // Private helper methods

    /**
     * Calculates cosine similarity between two vectors
     */
    private fun calculateCosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.size != vector2.size) return 0f

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            norm1 += vector1[i] * vector1[i]
            norm2 += vector2[i] * vector2[i]
        }

        val magnitude = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (magnitude > 0f) dotProduct / magnitude else 0f
    }
}

/**
 * Result wrapper for embedder operations
 */
sealed class EmbedderResult<T> {
    data class Success<T>(val data: T) : EmbedderResult<T>()
    data class Error<T>(val message: String, val cause: Throwable? = null) : EmbedderResult<T>()
}

/**
 * Result for text similarity operations
 */
data class SimilarityResult(
    val text: String,
    val similarity: Float,
    val index: Int
)

/**
 * Status information for the embedder
 */
data class EmbedderStatus(
    val isInitialized: Boolean,
    val embeddingDimensions: Int,
    val maxTextLength: Int,
    val error: String?
)