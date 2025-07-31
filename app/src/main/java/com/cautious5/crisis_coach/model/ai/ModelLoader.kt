package com.cautious5.crisis_coach.model.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Handles model file discovery, validation, and loading operations
 * Supports loading from assets, external storage, and app-specific directories
 */
class ModelLoader(private val context: Context) {

    companion object {
        private const val TAG = "ModelLoader"
        private const val MODELS_DIRECTORY = "models"
        private const val ASSETS_MODELS_PATH = "models"

        // Model file validation constants
        private const val MIN_MODEL_SIZE_BYTES = 100 * 1024 * 1024 // 100MB minimum
        private const val MAX_MODEL_SIZE_BYTES = 8L * 1024 * 1024 * 1024 // 8GB maximum
    }

    /**
     * Result class for model loading operations
     */
    sealed class LoadResult {
        data class Success(val modelPath: String) : LoadResult()
        data class Error(val message: String, val cause: Throwable? = null) : LoadResult()
        data object Missing : LoadResult()
    }

    /**
     * Discovers and loads the specified model variant
     * Searches in multiple locations in priority order:
     * 1. App's internal files directory
     * 2. External storage (Downloads folder)
     * 3. Assets directory (copy to internal storage)
     */
    suspend fun loadModel(variant: ModelVariant): LoadResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading model variant: ${variant.displayName}")

        try {
            // First, check if model exists in app's internal storage
            val internalModelPath = getInternalModelPath(variant)
            if (isValidModelFile(internalModelPath)) {
                Log.d(TAG, "Found model in internal storage: $internalModelPath")
                return@withContext LoadResult.Success(internalModelPath)
            }

            // Check external storage (Downloads folder)
            val externalModelPath = getExternalModelPath(variant)
            if (isValidModelFile(externalModelPath)) {
                Log.d(TAG, "Found model in external storage: $externalModelPath")
                // Copy to internal storage for faster access
                val copiedPath = copyModelToInternal(externalModelPath, variant)
                return@withContext if (copiedPath != null) {
                    LoadResult.Success(copiedPath)
                } else {
                    LoadResult.Success(externalModelPath)
                }
            }

            // Try to extract from assets
            val assetModelPath = extractModelFromAssets(variant)
            if (assetModelPath != null) {
                Log.d(TAG, "Extracted model from assets: $assetModelPath")
                return@withContext LoadResult.Success(assetModelPath)
            }

            // Return Missing instead of Error
            Log.w(TAG, "Model file not found: ${variant.fileName}")
            return@withContext LoadResult.Missing

        } catch (e: Exception) {
            Log.e(TAG, "Error loading model: ${e.message}", e)
            return@withContext LoadResult.Error("Failed to load model: ${e.message}", e)
        }
    }

    /**
     * Gets the path for model in internal storage
     */
    private fun getInternalModelPath(variant: ModelVariant): String {
        val modelsDir = File(context.filesDir, MODELS_DIRECTORY)
        return File(modelsDir, variant.fileName).absolutePath
    }

    /**
     * Gets the path for model in external storage (Downloads)
     */
    private fun getExternalModelPath(variant: ModelVariant): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, variant.fileName).absolutePath
    }

    /**
     * Validates if a model file exists and has correct size
     */
    private fun isValidModelFile(filePath: String): Boolean {
        val file = File(filePath)

        if (!file.exists()) {
            Log.d(TAG, "Model file does not exist: $filePath")
            return false
        }

        val fileSize = file.length()
        if (fileSize < MIN_MODEL_SIZE_BYTES) {
            Log.w(TAG, "Model file too small: $fileSize bytes (minimum: $MIN_MODEL_SIZE_BYTES)")
            return false
        }

        if (fileSize > MAX_MODEL_SIZE_BYTES) {
            Log.w(TAG, "Model file too large: $fileSize bytes (maximum: $MAX_MODEL_SIZE_BYTES)")
            return false
        }

        if (!file.canRead()) {
            Log.w(TAG, "Cannot read model file: $filePath")
            return false
        }

        Log.d(TAG, "Valid model file found: $filePath (${fileSize / (1024 * 1024)} MB)")
        return true
    }

    /**
     * Copies model from external storage to internal storage for faster access
     */
    private suspend fun copyModelToInternal(sourcePath: String, variant: ModelVariant): String? {
        return try {
            Log.d(TAG, "Copying model to internal storage...")
            val sourceFile = File(sourcePath)
            val targetPath = getInternalModelPath(variant)
            val targetFile = File(targetPath)

            // Create parent directory if it doesn't exist
            targetFile.parentFile?.mkdirs()

            // Copy file
            withContext(Dispatchers.IO) {
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (isValidModelFile(targetPath)) {
                Log.d(TAG, "Model copied successfully to: $targetPath")
                targetPath
            } else {
                val sourceSize = sourceFile.length()
                val targetSize = targetFile.length()
                Log.e(TAG, "Copied model file is invalid. " +
                        "Source size: $sourceSize bytes, Target size: $targetSize bytes. Deleting.")
                targetFile.delete()
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy model to internal storage: ${e.message}", e)
            null
        }
    }

    /**
     * Extracts model from assets directory to internal storage
     */
    private suspend fun extractModelFromAssets(variant: ModelVariant): String? {
        return try {
            Log.d(TAG, "Extracting model from assets...")
            val assetPath = "$ASSETS_MODELS_PATH/${variant.fileName}"
            val targetPath = getInternalModelPath(variant)
            val targetFile = File(targetPath)

            // Check if asset exists
            context.assets.list(ASSETS_MODELS_PATH)?.let { assetFiles ->
                if (!assetFiles.contains(variant.fileName)) {
                    Log.d(TAG, "Model not found in assets: $assetPath")
                    return null
                }
            } ?: run {
                Log.d(TAG, "Assets models directory not found")
                return null
            }

            // Create parent directory
            targetFile.parentFile?.mkdirs()

            // Extract from assets
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (isValidModelFile(targetPath)) {
                Log.d(TAG, "Model extracted successfully from assets: $targetPath")
                targetPath
            } else {
                Log.e(TAG, "Extracted model file is invalid")
                targetFile.delete()
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract model from assets: ${e.message}", e)
            null
        }
    }

    /**
     * Checks available storage space for model operations
     */
    fun getAvailableStorageSpace(): Long {
        return try {
            val internalDir = context.filesDir
            val freeSpace = internalDir.freeSpace
            Log.d(TAG, "Available internal storage: ${freeSpace / (1024 * 1024)} MB")
            freeSpace
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check storage space: ${e.message}", e)
            0L
        }
    }

    /**
     * Cleans up old or invalid model files
     */
    suspend fun cleanupOldModels(): Unit = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.filesDir, MODELS_DIRECTORY)
            if (!modelsDir.exists()) return@withContext

            modelsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".task")) {
                    if (!isValidModelFile(file.absolutePath)) {
                        Log.d(TAG, "Deleting invalid model file: ${file.name}")
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during model cleanup: ${e.message}", e)
        }
    }

    /**
     * Gets information about all available models
     */
    fun getAvailableModels(): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()

        ModelVariant.entries.forEach { variant ->
            val internalPath = getInternalModelPath(variant)
            val externalPath = getExternalModelPath(variant)

            val location = when {
                isValidModelFile(internalPath) -> ModelLocation.INTERNAL
                isValidModelFile(externalPath) -> ModelLocation.EXTERNAL
                else -> null
            }

            models.add(
                ModelInfo(
                    variant = variant,
                    location = location,
                    path = when (location) {
                        ModelLocation.INTERNAL -> internalPath
                        ModelLocation.EXTERNAL -> externalPath
                        null -> null
                    },
                    sizeBytes = location?.let {
                        File(when(it) {
                            ModelLocation.INTERNAL -> internalPath
                            ModelLocation.EXTERNAL -> externalPath
                        }).length()
                    }
                )
            )
        }

        return models
    }
}

/**
 * Information about an available model
 */
data class ModelInfo(
    val variant: ModelVariant,
    val location: ModelLocation?,
    val path: String?,
    val sizeBytes: Long?
) {
    val isAvailable: Boolean get() = location != null && path != null
    val sizeMB: Long? get() = sizeBytes?.let { it / (1024 * 1024) }
}

/**
 * Model storage location
 */
enum class ModelLocation {
    INTERNAL,   // App's internal storage
    EXTERNAL    // External storage (Downloads)
}