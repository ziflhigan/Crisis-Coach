package com.cautious5.crisis_coach.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/**
 * Centralized image processing utilities for the Crisis Coach app
 * Handles image loading, preprocessing, compression, and format conversions
 */
object ImageUtils {

    private const val TAG = "ImageUtils"

    // Standard sizes for different use cases
    const val ANALYSIS_IMAGE_SIZE = 256
    const val THUMBNAIL_SIZE = 128
    const val MAX_UPLOAD_SIZE = 1024
    const val JPEG_QUALITY_HIGH = 90
    const val JPEG_QUALITY_MEDIUM = 75
    const val JPEG_QUALITY_LOW = 60

    /**
     * Configuration for image preprocessing
     */
    data class PreprocessConfig(
        val targetSize: Int = ANALYSIS_IMAGE_SIZE,
        val maintainAspectRatio: Boolean = true,
        val correctOrientation: Boolean = true,
        val outputFormat: Bitmap.Config = Bitmap.Config.ARGB_8888,
        val compressionQuality: Int = JPEG_QUALITY_HIGH
    )

    /**
     * Loads and preprocesses an image from URI
     */
    fun loadAndPreprocessImage(
        context: Context,
        uri: Uri,
        config: PreprocessConfig = PreprocessConfig()
    ): Result<Bitmap> = runCatching {

        val bitmap = loadBitmapFromUri(context, uri)
            ?: throw IOException("Failed to load bitmap from URI")

        preprocessBitmap(bitmap, config)
    }

    /**
     * Preprocesses a bitmap according to the given configuration
     */
    fun preprocessBitmap(
        bitmap: Bitmap,
        config: PreprocessConfig = PreprocessConfig()
    ): Bitmap {
        var processed = bitmap

        // Resize if needed
        if (shouldResize(processed, config.targetSize)) {
            processed = resizeBitmap(
                processed,
                config.targetSize,
                config.maintainAspectRatio
            )
        }

        // Ensure correct format
        if (processed.config != config.outputFormat) {
            processed = processed.copy(config.outputFormat, false)
                ?: throw IllegalStateException("Failed to convert bitmap format")
        }

        return processed
    }

    /**
     * Resizes bitmap to fit within target dimensions
     */
    fun resizeBitmap(
        bitmap: Bitmap,
        targetSize: Int,
        maintainAspectRatio: Boolean = true
    ): Bitmap {
        if (!maintainAspectRatio) {
            return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        }

        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val (targetWidth, targetHeight) = calculateTargetDimensions(
            bitmap.width,
            bitmap.height,
            targetSize
        )

        Log.d(TAG, "Resizing from ${bitmap.width}x${bitmap.height} to ${targetWidth}x${targetHeight}")

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Compresses bitmap to JPEG with specified quality
     */
    fun compressBitmap(
        bitmap: Bitmap,
        quality: Int = JPEG_QUALITY_MEDIUM
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Saves bitmap to file
     */
    fun saveBitmapToFile(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = JPEG_QUALITY_HIGH
    ): Result<File> = runCatching {
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
        }
        file
    }

    /**
     * Corrects image orientation based on EXIF data
     */
    fun correctImageOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to correct image orientation: ${e.message}")
            bitmap
        }
    }

    /**
     * Creates a thumbnail from bitmap
     */
    fun createThumbnail(
        bitmap: Bitmap,
        size: Int = THUMBNAIL_SIZE
    ): Bitmap {
        return resizeBitmap(bitmap, size, maintainAspectRatio = true)
    }

    /**
     * Estimates memory usage of a bitmap
     */
    fun estimateBitmapMemory(bitmap: Bitmap): Long {
        return bitmap.allocationByteCount.toLong()
    }

    /**
     * Checks if bitmap should be recycled (for memory management)
     */
    fun recycleBitmapIfNeeded(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    // Private helper methods

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI: ${e.message}")
            null
        }
    }

    private fun shouldResize(bitmap: Bitmap, targetSize: Int): Boolean {
        return bitmap.width > targetSize || bitmap.height > targetSize
    }

    private fun calculateTargetDimensions(
        originalWidth: Int,
        originalHeight: Int,
        maxSize: Int
    ): Pair<Int, Int> {
        val ratio = min(
            maxSize.toFloat() / originalWidth,
            maxSize.toFloat() / originalHeight
        )

        val targetWidth = (originalWidth * ratio).toInt()
        val targetHeight = (originalHeight * ratio).toInt()

        return targetWidth to targetHeight
    }

    /**
     * Validates if image meets minimum requirements
     */
    fun validateImage(bitmap: Bitmap, minSize: Int = 64): ValidationResult {
        return when {
            bitmap.width < minSize || bitmap.height < minSize -> {
                ValidationResult.Error("Image too small: ${bitmap.width}x${bitmap.height}. Minimum size is ${minSize}x${minSize}")
            }
            bitmap.isRecycled -> {
                ValidationResult.Error("Bitmap is recycled")
            }
            else -> ValidationResult.Success
        }
    }

    sealed class ValidationResult {
        data object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}

/**
 * Extension functions for easier usage
 */
fun Bitmap.preprocess(
    config: ImageUtils.PreprocessConfig = ImageUtils.PreprocessConfig()
): Bitmap = ImageUtils.preprocessBitmap(this, config)

fun Bitmap.toByteArray(quality: Int = ImageUtils.JPEG_QUALITY_MEDIUM): ByteArray =
    ImageUtils.compressBitmap(this, quality)

fun Bitmap.createThumbnail(size: Int = ImageUtils.THUMBNAIL_SIZE): Bitmap =
    ImageUtils.createThumbnail(this, size)