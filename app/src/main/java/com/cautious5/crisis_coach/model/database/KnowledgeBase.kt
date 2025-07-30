package com.cautious5.crisis_coach.model.database

import android.util.Log
import com.cautious5.crisis_coach.model.database.entities.EmergencyInfo
import com.cautious5.crisis_coach.model.database.entities.EmergencyInfo_
import com.cautious5.crisis_coach.model.embedding.TextEmbedder
import com.cautious5.crisis_coach.utils.VectorUtils
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the on-device knowledge base using ObjectBox vector database
 * Provides semantic search capabilities for emergency information retrieval
 */
class KnowledgeBase(
    private val boxStore: BoxStore,
    private val textEmbedder: TextEmbedder
) {

    companion object {
        private const val TAG = "KnowledgeBase"
        private const val DEFAULT_SEARCH_LIMIT = 5
        private const val SIMILARITY_THRESHOLD = 0.6f
    }

    private val emergencyInfoBox: Box<EmergencyInfo> = boxStore.boxFor()

    /**
     * Searches for relevant emergency information using semantic vector search
     */
    suspend fun searchRelevantInfo(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT,
        category: String? = null,
        priorityThreshold: Int = 5
    ): SearchResult = withContext(Dispatchers.IO) {

        if (query.isBlank()) {
            return@withContext SearchResult.Error("Query cannot be empty")
        }

        Log.d(TAG, "Searching for: '$query' (limit: $limit, category: $category)")

        try {
            // Generate embedding for the query
            val queryEmbedding = textEmbedder.embedText(query)
            if (queryEmbedding.isEmpty()) {
                return@withContext SearchResult.Error("Failed to generate query embedding")
            }

            // Build ObjectBox query with vector search
            val queryBuilder = emergencyInfoBox.query(
                EmergencyInfo_.embedding.nearestNeighbors(queryEmbedding, limit * 2)
            ).apply {
                // Priority filter – always on
                apply(EmergencyInfo_.priority.lessOrEqual(priorityThreshold))

                // Optional category filter
                category?.let { cat ->
                    apply(EmergencyInfo_.category.equal(cat))
                }
            }

            val results = queryBuilder.build().find()
            // Convert to search entries with relevance scoring
            val searchEntries = results.mapNotNull { info ->
                val similarity = VectorUtils.calculateCosineSimilarity(queryEmbedding, info.embedding)

                // Filter by similarity threshold
                if (similarity >= SIMILARITY_THRESHOLD) {
                    SearchEntry(
                        info = info,
                        relevanceScore = calculateRelevanceScore(similarity, info),
                        similarity = similarity
                    )
                } else {
                    null
                }
            }
                .sortedByDescending { it.relevanceScore }
                .take(limit)

            Log.d(TAG, "Found ${searchEntries.size} relevant entries for query: '$query'")

            if (searchEntries.isEmpty()) {
                SearchResult.NoResults("No relevant information found for: '$query'")
            } else {
                SearchResult.Success(searchEntries)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: '$query'", e)
            SearchResult.Error("Search failed: ${e.message}", e)
        }
    }

    /**
     * Adds new emergency information to the knowledge base
     */
    suspend fun addEmergencyInfo(
        title: String,
        text: String,
        category: String,
        priority: Int = 3,
        keywords: String = "",
        source: String = "",
        languageCode: String = "en"
    ): AddResult = withContext(Dispatchers.IO) {

        if (text.isBlank()) {
            return@withContext AddResult.Error("Text content cannot be empty")
        }

        Log.d(TAG, "Adding emergency info: '$title'")

        try {
            // Generate embedding for the text
            val embedding = textEmbedder.embedText(text)
            if (embedding.isEmpty()) {
                return@withContext AddResult.Error("Failed to generate text embedding")
            }

            // Create the emergency info entry
            val emergencyInfo = EmergencyInfo(
                title = title,
                text = text,
                embedding = embedding,
                category = category,
                priority = priority,
                keywords = keywords,
                source = source,
                languageCode = languageCode,
                lastUpdated = System.currentTimeMillis()
            )

            // Store in database
            val id = emergencyInfoBox.put(emergencyInfo)

            Log.d(TAG, "Added emergency info with ID: $id")
            AddResult.Success(id)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add emergency info: '$title'", e)
            AddResult.Error("Failed to add information: ${e.message}", e)
        }
    }

    /**
     * Adds multiple emergency information entries in batch
     */
    suspend fun addEmergencyInfoBatch(entries: List<EmergencyInfo>): BatchAddResult = withContext(Dispatchers.IO) {

        if (entries.isEmpty()) {
            return@withContext BatchAddResult.Error("No entries provided")
        }

        Log.d(TAG, "Adding ${entries.size} emergency info entries in batch")

        try {
            val successfulIds = mutableListOf<Long>()
            val failures = mutableListOf<String>()

            // Process each entry
            for ((index, entry) in entries.withIndex()) {
                try {
                    // Generate embedding if not present
                    if (entry.embedding.isEmpty() && entry.text.isNotBlank()) {
                        entry.embedding = textEmbedder.embedText(entry.text)
                    }

                    // Validate embedding
                    if (!entry.hasValidEmbedding()) {
                        failures.add("Entry $index: Invalid embedding")
                        continue
                    }

                    // Update timestamp
                    entry.lastUpdated = System.currentTimeMillis()

                    // Store in database
                    val id = emergencyInfoBox.put(entry)
                    successfulIds.add(id)

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add entry $index: ${e.message}")
                    failures.add("Entry $index: ${e.message}")
                }
            }

            Log.d(TAG, "Batch add completed: ${successfulIds.size} successful, ${failures.size} failed")

            BatchAddResult.Success(
                successfulIds = successfulIds,
                failures = failures
            )

        } catch (e: Exception) {
            Log.e(TAG, "Batch add failed", e)
            BatchAddResult.Error("Batch add failed: ${e.message}", e)
        }
    }

    /**
     * Gets all emergency information entries for a specific category
     */
    suspend fun getByCategory(category: String): List<EmergencyInfo> = withContext(Dispatchers.IO) {
        try {
            val results = emergencyInfoBox.query(
                EmergencyInfo_.category.equal(category)
            ).build().find()

            Log.d(TAG, "Found ${results.size} entries for category: $category")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get entries for category: $category", e)
            emptyList()
        }
    }

    /**
     * Gets emergency information by priority level
     */
    suspend fun getByPriority(maxPriority: Int): List<EmergencyInfo> = withContext(Dispatchers.IO) {
        try {
            val results = emergencyInfoBox.query(
                EmergencyInfo_.priority.lessOrEqual(maxPriority)
            ).build().find()

            Log.d(TAG, "Found ${results.size} entries with priority <= $maxPriority")
            results.sortedBy { it.priority }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get entries by priority: $maxPriority", e)
            emptyList()
        }
    }

    /**
     * Gets total count of entries in the knowledge base
     */
    fun getEntryCount(): Long {
        return emergencyInfoBox.count()
    }

    /**
     * Gets knowledge base statistics.
     * This version is optimized to query the database only once.
     */
    suspend fun getStatistics(): KnowledgeBaseStats = withContext(Dispatchers.IO) {
        try {
            // Fetch all entries from the database in a single operation
            val allEntries = emergencyInfoBox.all
            val totalEntries = allEntries.size.toLong()

            Log.d(TAG, "Generating statistics for $totalEntries entries.")

            // Perform grouping operations on the in-memory list
            val categories = allEntries.groupBy { it.category }
                .mapValues { it.value.size }

            val priorities = allEntries.groupBy { it.priority }
                .mapValues { it.value.size }

            val languages = allEntries.groupBy { it.languageCode }
                .mapValues { it.value.size }

            KnowledgeBaseStats(
                totalEntries = totalEntries,
                categoryCounts = categories,
                priorityCounts = priorities,
                languageCounts = languages,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate statistics", e)
            KnowledgeBaseStats() // Return empty stats on error
        }
    }

    /**
     * Clears all entries from the knowledge base
     */
    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        try {
            emergencyInfoBox.removeAll()
            Log.d(TAG, "Knowledge base cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear knowledge base", e)
        }
    }

    // Private helper methods

    /**
     * Calculates overall relevance score combining similarity and other factors
     */
    private fun calculateRelevanceScore(similarity: Float, info: EmergencyInfo): Float {
        val priorityWeight = info.getRelevanceScore()
        val fieldSuitabilityWeight = if (info.fieldSuitable) 1.0f else 0.8f

        return similarity * 0.7f + priorityWeight * 0.2f + fieldSuitabilityWeight * 0.1f
    }
}

/**
 * Result classes for knowledge base operations
 */
sealed class SearchResult {
    data class Success(val entries: List<SearchEntry>) : SearchResult()
    data class NoResults(val message: String) : SearchResult()
    data class Error(val message: String, val cause: Throwable? = null) : SearchResult()
}

sealed class AddResult {
    data class Success(val id: Long) : AddResult()
    data class Error(val message: String, val cause: Throwable? = null) : AddResult()
}

sealed class BatchAddResult {
    data class Success(
        val successfulIds: List<Long>,
        val failures: List<String>
    ) : BatchAddResult()
    data class Error(val message: String, val cause: Throwable? = null) : BatchAddResult()
}

/**
 * Search result entry with relevance scoring
 */
data class SearchEntry(
    val info: EmergencyInfo,
    val relevanceScore: Float,
    val similarity: Float
) {
    val isHighlyRelevant: Boolean get() = relevanceScore >= 0.8f
    val isRelevant: Boolean get() = relevanceScore >= 0.6f
}

/**
 * Knowledge base statistics
 */
data class KnowledgeBaseStats(
    val totalEntries: Long = 0,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val priorityCounts: Map<Int, Int> = emptyMap(),
    val languageCounts: Map<String, Int> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)