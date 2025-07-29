package com.cautious5.crisis_coach.model.database

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cautious5.crisis_coach.model.database.entities.EmergencyInfo
import com.cautious5.crisis_coach.model.database.entities.EmergencyInfo.Companion.Priorities
import com.cautious5.crisis_coach.model.embedding.TextEmbedder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Handles initialization and population of the emergency knowledge base
 * Loads initial data from JSON assets and generates embeddings
 */
class DatabaseInitializer(
    private val context: Context,
    private val knowledgeBase: KnowledgeBase,
    private val textEmbedder: TextEmbedder
) {

    companion object {
        private const val TAG = "DatabaseInitializer"
        private const val PREFS_NAME = "knowledge_base_prefs"
        private const val KEY_DB_VERSION = "db_version"
        private const val KEY_LAST_INITIALIZED = "last_initialized"
        private const val CURRENT_DB_VERSION = 2

        // Asset file paths
        private const val INITIAL_DATA_ASSET = "data/initial_knowledge_base.json"
        private const val EMERGENCY_DATA_ASSET = "raw/emergency_knowledge.json"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Initializes the knowledge base if needed
     */
    suspend fun initializeIfNeeded(): InitializationResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Checking if knowledge base initialization is needed")

        val currentVersion = prefs.getInt(KEY_DB_VERSION, 0)
        val entryCount = knowledgeBase.getEntryCount()

        val needsInitialization = currentVersion < CURRENT_DB_VERSION ||
                entryCount == 0L ||
                isDataStale()

        if (!needsInitialization) {
            Log.d(TAG, "Knowledge base is up to date (version: $currentVersion, entries: $entryCount)")
            return@withContext InitializationResult.AlreadyInitialized
        }

        Log.i(TAG, "Initializing knowledge base (current version: $currentVersion, target: $CURRENT_DB_VERSION)")

        try {
            // Clear existing data if upgrading
            if (currentVersion in 1..<CURRENT_DB_VERSION) {
                Log.d(TAG, "Upgrading database from version $currentVersion to $CURRENT_DB_VERSION")
                knowledgeBase.clearAll()
            }

            // Load and populate initial data
            when (val loadResult = loadInitialData()) {
                is DataLoadResult.Success -> {
                    Log.i(TAG, "Successfully loaded ${loadResult.entriesAdded} entries")

                    // Update version and timestamp
                    prefs.edit()
                        .putInt(KEY_DB_VERSION, CURRENT_DB_VERSION)
                        .putLong(KEY_LAST_INITIALIZED, System.currentTimeMillis())
                        .apply()

                    InitializationResult.Success(
                        entriesAdded = loadResult.entriesAdded,
                        initializationTimeMs = loadResult.processingTimeMs
                    )
                }
                is DataLoadResult.Error -> {
                    Log.e(TAG, "Failed to load initial data: ${loadResult.message}")
                    InitializationResult.Error(loadResult.message, loadResult.cause)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Database initialization failed", e)
            InitializationResult.Error("Initialization failed: ${e.message}", e)
        }
    }

    /**
     * Forces re-initialization of the knowledge base
     */
    suspend fun forceReinitialize(): InitializationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Force reinitializing knowledge base")

        try {
            // Clear all existing data
            knowledgeBase.clearAll()

            // Reset preferences
            prefs.edit()
                .putInt(KEY_DB_VERSION, 0)
                .remove(KEY_LAST_INITIALIZED)
                .apply()

            // Reinitialize
            initializeIfNeeded()

        } catch (e: Exception) {
            Log.e(TAG, "Force reinitialization failed", e)
            InitializationResult.Error("Force reinitialization failed: ${e.message}", e)
        }
    }

    /**
     * Loads initial data from assets and populates the database
     */
    private suspend fun loadInitialData(): DataLoadResult = withContext(Dispatchers.IO) {

        val startTime = System.currentTimeMillis()

        try {
            // Try to load from primary asset file first
            val initialEntries = loadFromAsset(INITIAL_DATA_ASSET)
                ?: loadFromAsset(EMERGENCY_DATA_ASSET)
                ?: run {
                    Log.w(TAG, "No asset files found, using default emergency data")
                    createDefaultEmergencyData()
                }

            if (initialEntries.isEmpty()) {
                return@withContext DataLoadResult.Error("No emergency data available to load")
            }

            Log.d(TAG, "Processing ${initialEntries.size} emergency entries")

            // Generate embeddings for entries that don't have them
            val processedEntries = mutableListOf<EmergencyInfo>()
            var embeddingGenerationCount = 0

            for (entry in initialEntries) {
                try {
                    if (entry.embedding.isEmpty() && entry.text.isNotBlank()) {
                        entry.embedding = textEmbedder.embedText(entry.text)
                        embeddingGenerationCount++

                        // Log progress every 10 entries
                        if (embeddingGenerationCount % 10 == 0) {
                            Log.d(TAG, "Generated embeddings for $embeddingGenerationCount entries")
                        }
                    }

                    // Validate the entry
                    if (entry.hasValidEmbedding() && entry.text.isNotBlank()) {
                        processedEntries.add(entry)
                    } else {
                        Log.w(TAG, "Skipping invalid entry: ${entry.title}")
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process entry: ${entry.title} - ${e.message}")
                }
            }

            Log.d(TAG, "Generated embeddings for $embeddingGenerationCount entries")

            // Add entries to database in batch
            val batchResult = knowledgeBase.addEmergencyInfoBatch(processedEntries)

            val processingTime = System.currentTimeMillis() - startTime

            when (batchResult) {
                is BatchAddResult.Success -> {
                    Log.i(TAG, "Successfully added ${batchResult.successfulIds.size} entries " +
                            "in ${processingTime}ms (${batchResult.failures.size} failures)")

                    DataLoadResult.Success(
                        entriesAdded = batchResult.successfulIds.size,
                        processingTimeMs = processingTime,
                        failures = batchResult.failures
                    )
                }
                is BatchAddResult.Error -> {
                    DataLoadResult.Error("Batch add failed: ${batchResult.message}", batchResult.cause)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load initial data", e)
            DataLoadResult.Error("Data loading failed: ${e.message}", e)
        }
    }

    /**
     * Loads emergency information from an asset JSON file
     */
    private fun loadFromAsset(assetPath: String): List<EmergencyInfo>? {
        return try {
            Log.d(TAG, "Loading data from asset: $assetPath")

            val jsonString = context.assets.open(assetPath).use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }

            // Parse JSON to EmergencyInfo list
            val listType = object : TypeToken<List<EmergencyDataEntry>>() {}.type
            val dataEntries: List<EmergencyDataEntry> = gson.fromJson(jsonString, listType)

            // Convert to EmergencyInfo entities
            val result = dataEntries.map { dataEntry ->
                EmergencyInfo(
                    title = dataEntry.title,
                    text = dataEntry.text,
                    category = dataEntry.category,
                    priority = dataEntry.priority ?: 3,
                    keywords = dataEntry.keywords ?: "",
                    source = dataEntry.source ?: "",
                    languageCode = dataEntry.languageCode ?: "en",
                    fieldSuitable = dataEntry.fieldSuitable ?: true,
                    metadata = dataEntry.metadata ?: "{}"
                )
            }

            Log.d(TAG, "Loaded ${result.size} entries from $assetPath")
            result

        } catch (e: IOException) {
            Log.d(TAG, "Asset file not found: $assetPath")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse asset file: $assetPath", e)
            null
        }
    }

    /**
     * Creates default emergency data when no asset files are available
     */
    private fun createDefaultEmergencyData(): List<EmergencyInfo> {
        Log.d(TAG, "Creating default emergency data")

        return listOf(
            EmergencyInfo.createMedicalEntry(
                title = "Cardiac Arrest Response",
                text = "1. Check responsiveness - tap shoulders and shout. 2. Call for help immediately. " +
                        "3. Check pulse for no more than 10 seconds. 4. If no pulse, begin CPR: " +
                        "30 chest compressions (rate 100-120/min, depth 2+ inches) followed by 2 rescue breaths. " +
                        "5. Continue cycles until help arrives or AED available. 6. Use AED if available - follow voice prompts.",
                priority = Priorities.CRITICAL,
                keywords = "cardiac arrest CPR chest compressions rescue breathing AED",
                source = "American Heart Association Guidelines"
            ),

            EmergencyInfo.createMedicalEntry(
                title = "Severe Bleeding Control",
                text = "1. Ensure scene safety. 2. Apply direct pressure to wound with clean cloth or bandage. " +
                        "3. Elevate injured area above heart level if possible. 4. If bleeding continues, " +
                        "apply additional layers without removing first bandage. 5. For severe arterial bleeding, " +
                        "consider tourniquet application 2-3 inches above wound. 6. Monitor for shock symptoms.",
                priority = Priorities.CRITICAL,
                keywords = "bleeding hemorrhage tourniquet direct pressure shock",
                source = "Red Cross First Aid Manual"
            ),

            EmergencyInfo.createMedicalEntry(
                title = "Airway Obstruction (Choking)",
                text = "For conscious adult: 1. Ask 'Are you choking?' 2. If unable to speak/cough, " +
                        "perform 5 back blows between shoulder blades. 3. If unsuccessful, perform 5 abdominal thrusts " +
                        "(Heimlich maneuver). 4. Alternate back blows and abdominal thrusts until object dislodged. " +
                        "For unconscious: Begin CPR, check mouth before rescue breaths.",
                priority = Priorities.CRITICAL,
                keywords = "choking airway obstruction Heimlich maneuver back blows abdominal thrusts",
                source = "American Heart Association"
            ),

            EmergencyInfo.createStructuralEntry(
                title = "Building Collapse Assessment",
                text = "1. Do not enter damaged structures. 2. Look for: cracks in walls/foundation, " +
                        "sagging floors/roofs, tilting walls, separated joints. 3. Listen for creaking or settling sounds. " +
                        "4. Check for gas leaks (smell of gas). 5. Turn off utilities if safe to do so. " +
                        "6. Establish safety perimeter. 7. Mark building as unsafe if structural damage present.",
                priority = Priorities.HIGH,
                keywords = "building collapse structural damage safety assessment utilities gas leak",
                source = "FEMA Structural Assessment Guidelines"
            ),

            EmergencyInfo.createCommunicationEntry(
                title = "Emergency Communication Protocols",
                text = "1. Establish command post with reliable communication. 2. Use clear, concise language. " +
                        "3. Report: What happened, When, Where, Who is involved, What help is needed. " +
                        "4. Maintain communication log. 5. Use standardized codes if applicable. " +
                        "6. Ensure backup communication methods available (satellite phone, radio, etc.).",
                priority = Priorities.MEDIUM,
                keywords = "communication protocols radio emergency codes command post",
                source = "Emergency Management Institute"
            )
        )
    }

    /**
     * Checks if the current data is stale and needs updating
     */
    private fun isDataStale(): Boolean {
        val lastInitialized = prefs.getLong(KEY_LAST_INITIALIZED, 0)
        val daysSinceInit = (System.currentTimeMillis() - lastInitialized) / (24 * 60 * 60 * 1000)

        // Consider data stale after 30 days
        return daysSinceInit > 30
    }
}

/**
 * Data structure for JSON parsing
 */
private data class EmergencyDataEntry(
    val title: String,
    val text: String,
    val category: String,
    val priority: Int? = null,
    val keywords: String? = null,
    val source: String? = null,
    val languageCode: String? = null,
    val fieldSuitable: Boolean? = null,
    val metadata: String? = null
)

/**
 * Result classes for initialization operations
 */
sealed class InitializationResult {
    data class Success(
        val entriesAdded: Int,
        val initializationTimeMs: Long
    ) : InitializationResult()

    object AlreadyInitialized : InitializationResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : InitializationResult()
}

private sealed class DataLoadResult {
    data class Success(
        val entriesAdded: Int,
        val processingTimeMs: Long,
        val failures: List<String> = emptyList()
    ) : DataLoadResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : DataLoadResult()
}