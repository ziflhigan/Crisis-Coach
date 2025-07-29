package com.cautious5.crisis_coach.model.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.HnswIndex

/**
 * ObjectBox entity representing emergency knowledge base entries
 * Stores medical information, first aid procedures, and emergency protocols
 * with vector embeddings for semantic search
 */
@Entity
data class EmergencyInfo(
    @Id
    var id: Long = 0,

    /**
     * Human-readable title/category of the emergency information
     * e.g., "Snake Bite Treatment", "Cardiac Arrest Response"
     */
    @Index
    var title: String = "",

    /**
     * Main content text containing detailed emergency information
     * This is the text that will be used for both embedding generation and retrieval
     */
    var text: String = "",

    /**
     * Vector embedding of the text content for semantic search
     * Generated using MediaPipe TextEmbedder or similar embedding model
     * HNSW index enables fast nearest-neighbor search
     */
    @HnswIndex(dimensions = 512) // Adjust dimensions based on embedding model
    var embedding: FloatArray = floatArrayOf(),

    /**
     * Category of emergency information for filtering and organization
     * e.g., "medical", "structural", "environmental", "communication"
     */
    @Index
    var category: String = "",

    /**
     * Priority level for emergency situations (1-5, where 1 is highest priority)
     * Used to rank results when multiple relevant entries are found
     */
    @Index
    var priority: Int = 3,

    /**
     * Keywords for additional search capabilities
     * Space-separated list of relevant terms
     */
    var keywords: String = "",

    /**
     * Source reference for the information (e.g., "Red Cross Manual", "WHO Guidelines")
     * Important for credibility and verification
     */
    var source: String = "",

    /**
     * Language code for multilingual support (ISO 639-1 format)
     * e.g., "en", "es", "fr", "ar"
     */
    @Index
    var languageCode: String = "en",

    /**
     * Timestamp when this entry was created or last updated
     */
    var lastUpdated: Long = System.currentTimeMillis(),

    /**
     * Flag indicating if this entry is suitable for field use
     * Some information might be too complex for emergency situations
     */
    var fieldSuitable: Boolean = true,

    /**
     * JSON string containing additional metadata
     * Can include images, diagrams, step-by-step procedures, etc.
     */
    var metadata: String = "{}"
) {

    /**
     * Checks if the embedding is properly initialized
     */
    fun hasValidEmbedding(): Boolean {
        return embedding.isNotEmpty() && embedding.all { !it.isNaN() && it.isFinite() }
    }

    /**
     * Gets a preview of the text content (first 100 characters)
     */
    fun getTextPreview(): String {
        return if (text.length > 100) {
            "${text.take(100)}..."
        } else {
            text
        }
    }

    /**
     * Gets keywords as a list
     */
    fun getKeywordsList(): List<String> {
        return keywords.split(" ").filter { it.isNotBlank() }
    }

    /**
     * Checks if this entry matches the given category
     */
    fun matchesCategory(searchCategory: String): Boolean {
        return category.equals(searchCategory, ignoreCase = true)
    }

    /**
     * Calculates relevance score based on priority and other factors
     * Lower priority numbers = higher relevance scores
     */
    fun getRelevanceScore(): Float {
        return when (priority) {
            1 -> 1.0f
            2 -> 0.8f
            3 -> 0.6f
            4 -> 0.4f
            5 -> 0.2f
            else -> 0.1f
        }
    }

    companion object {
        /**
         * Creates an EmergencyInfo entry for medical information
         */
        fun createMedicalEntry(
            title: String,
            text: String,
            priority: Int = 2,
            keywords: String = "",
            source: String = ""
        ): EmergencyInfo {
            return EmergencyInfo(
                title = title,
                text = text,
                category = "medical",
                priority = priority,
                keywords = keywords,
                source = source,
                fieldSuitable = true
            )
        }

        /**
         * Creates an EmergencyInfo entry for structural/safety information
         */
        fun createStructuralEntry(
            title: String,
            text: String,
            priority: Int = 3,
            keywords: String = "",
            source: String = ""
        ): EmergencyInfo {
            return EmergencyInfo(
                title = title,
                text = text,
                category = "structural",
                priority = priority,
                keywords = keywords,
                source = source,
                fieldSuitable = true
            )
        }

        /**
         * Creates an EmergencyInfo entry for communication protocols
         */
        fun createCommunicationEntry(
            title: String,
            text: String,
            priority: Int = 4,
            keywords: String = "",
            source: String = ""
        ): EmergencyInfo {
            return EmergencyInfo(
                title = title,
                text = text,
                category = "communication",
                priority = priority,
                keywords = keywords,
                source = source,
                fieldSuitable = true
            )
        }

        /**
         * Standard emergency categories
         */
        object Categories {
            const val MEDICAL = "medical"
            const val STRUCTURAL = "structural"
            const val ENVIRONMENTAL = "environmental"
            const val COMMUNICATION = "communication"
            const val EVACUATION = "evacuation"
            const val SEARCH_RESCUE = "search_rescue"
        }

        /**
         * Standard priority levels
         */
        object Priorities {
            const val CRITICAL = 1  // Life-threatening situations
            const val HIGH = 2      // Serious injuries/damage
            const val MEDIUM = 3    // Moderate concerns
            const val LOW = 4       // Minor issues
            const val INFO = 5      // General information
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmergencyInfo

        if (id != other.id) return false
        if (title != other.title) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}