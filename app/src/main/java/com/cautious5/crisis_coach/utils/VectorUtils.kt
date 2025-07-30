package com.cautious5.crisis_coach.utils

import kotlin.math.sqrt

/**
 * Centralized utility object for vector mathematics, such as calculating similarity.
 * This ensures that vector operations are consistent across the application.
 */
object VectorUtils {

    /**
     * Calculates the cosine similarity between two float vectors.
     * Cosine similarity measures the cosine of the angle between two vectors,
     * indicating their orientation similarity. A value of 1 means identical,
     * 0 means orthogonal, and -1 means diametrically opposed.
     *
     * @param vector1 The first float array vector.
     * @param vector2 The second float array vector.
     * @return The cosine similarity as a Float, ranging from -1.0 to 1.0. Returns 0f if vectors
     *         are invalid (e.g., different sizes, zero magnitude).
     */
    fun calculateCosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        // Ensure vectors are valid and have the same dimensions
        if (vector1.size != vector2.size || vector1.isEmpty()) {
            return 0f
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            norm1 += vector1[i] * vector1[i]
            norm2 += vector2[i] * vector2[i]
        }

        // Calculate the magnitude of the vectors
        val magnitude = sqrt(norm1) * sqrt(norm2)

        // Avoid division by zero
        return if (magnitude > 0f) dotProduct / magnitude else 0f
    }
}