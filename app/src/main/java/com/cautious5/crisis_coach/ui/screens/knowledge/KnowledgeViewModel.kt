package com.cautious5.crisis_coach.ui.screens.knowledge

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cautious5.crisis_coach.CrisisCoachApplication
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.GenerationResult
import com.cautious5.crisis_coach.model.database.KnowledgeBase
import com.cautious5.crisis_coach.model.database.SearchEntry
import com.cautious5.crisis_coach.model.database.SearchResult
import com.cautious5.crisis_coach.model.services.*
import com.cautious5.crisis_coach.utils.Constants.KNOWLEDGE_QUERY_MAX_LENGTH
import com.cautious5.crisis_coach.utils.Constants.KNOWLEDGE_QUERY_MIN_LENGTH
import com.cautious5.crisis_coach.utils.Constants.KNOWLEDGE_SEARCH_LIMIT
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.Constants.SAMPLE_KNOWLEDGE_QUERIES
import com.cautious5.crisis_coach.utils.PromptUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for KnowledgeScreen
 * Manages knowledge base search, RAG functionality, and voice input for queries
 */
class KnowledgeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = LogTags.KNOWLEDGE_VM
        private const val SEARCH_DEBOUNCE_DELAY_MS = 500L
    }

    // Services and components
    private val knowledgeBase: KnowledgeBase by lazy {
        (getApplication<CrisisCoachApplication>()).knowledgeBase
    }

    private val gemmaModelManager: GemmaModelManager by lazy {
        GemmaModelManager.getInstance(getApplication())
    }

    private val speechService: SpeechService by lazy {
        (getApplication<CrisisCoachApplication>()).speechService
    }

    /**
     * UI state for knowledge screen
     */
    data class KnowledgeUiState(
        val query: String = "",
        val isSearching: Boolean = false,
        val isGeneratingAnswer: Boolean = false,
        val isListening: Boolean = false,
        val searchResults: List<SearchEntry> = emptyList(),
        val ragAnswer: String = "",
        val error: String? = null,
        val selectedCategory: String? = null,
        val suggestedQueries: List<String> = SAMPLE_KNOWLEDGE_QUERIES,
        val searchHistory: List<String> = emptyList(),
        val totalSearchTime: Long = 0,
        val confidenceScore: Float = 0f,
        val sourcesUsed: List<String> = emptyList(),
        val showVoiceInput: Boolean = false,
        val recognitionState: RecognitionState = RecognitionState.IDLE
    ) {
        /**
         * Derived property to determine if the screen is in a busy state.
         */
        val isBusy: Boolean
            get() = isSearching || isGeneratingAnswer
    }

    /**
     * Knowledge categories for filtering
     */
    object Categories {
        const val ALL = "all"
        const val MEDICAL = "medical"
        const val STRUCTURAL = "structural"
        const val COMMUNICATION = "communication"
        const val GENERAL = "general"
    }

    // State flows
    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    // Search job for debouncing
    private var searchJob: Job? = null

    init {
        Log.d(TAG, "KnowledgeViewModel initialized")
        initialize()
    }

    /**
     * Initialize the ViewModel
     */
    private fun initialize() {
        // Load search history from preferences if needed
        loadSearchHistory()

        // Observe speech recognition state
        viewModelScope.launch {
            speechService.recognitionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    recognitionState = state,
                    isListening = state == RecognitionState.LISTENING || state == RecognitionState.SPEAKING
                )
            }
        }
    }

    /**
     * Update search query with debouncing
     */
    fun updateQuery(query: String) {
        if (query.length > KNOWLEDGE_QUERY_MAX_LENGTH) {
            Log.w(TAG, "Query too long, truncating")
            val truncatedQuery = query.take(KNOWLEDGE_QUERY_MAX_LENGTH)
            _uiState.value = _uiState.value.copy(query = truncatedQuery)
            return
        }

        _uiState.value = _uiState.value.copy(query = query)
    }

    /**
     * Explicitly triggers the knowledge base search.
     */
    fun triggerSearch(query: String) {
        if (query.length < KNOWLEDGE_QUERY_MIN_LENGTH) {
            setError("Query must be at least $KNOWLEDGE_QUERY_MIN_LENGTH characters long.")
            return
        }

        if (_uiState.value.isSearching || _uiState.value.isGeneratingAnswer) {
            Log.w(TAG, "Search already in progress.")
            return
        }

        // Cancel any pending search job (e.g., if the user was typing then hit search)
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            // Delay for a very short debounce (optional, but good practice for quick input)
            delay(100)
            performSearch(query)
        }
    }

    /**
     * Stops the ongoing search/generation and resets the UI to idle state.
     */
    fun cancelSearchAndReset() {
        Log.d(TAG, "Cancelling ongoing search/generation and resetting state.")

        // 1. Cancel the search/RAG job
        searchJob?.cancel()
        searchJob = null

        // 2. Clear LLM response streams (if applicable, in this flow, they are tied to the job)
        // If generateText was streaming, it would also stop when the scope is cancelled

        // 3. Reset the UI state
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            isGeneratingAnswer = false,
            searchResults = emptyList(),
            ragAnswer = "",
            error = null,
            sourcesUsed = emptyList(),
            confidenceScore = 0f,
            totalSearchTime = 0
        )
    }

    /**
     * Perform immediate search without debouncing
     */
    fun searchImmediate(query: String) {
        if (query.isBlank()) return

        Log.d(TAG, "Performing immediate search: $query")
        _uiState.value = _uiState.value.copy(query = query)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(query)
        }
    }

    /**
     * Start voice input for query
     */
    fun startVoiceInput() {
        Log.d(TAG, "Starting voice input")

        if (_uiState.value.isListening) {
            Log.w(TAG, "Voice input already active")
            return
        }

        _uiState.value = _uiState.value.copy(showVoiceInput = true)
        clearError()

        viewModelScope.launch {
            try {
                when (val result = speechService.startRecognition("en-US", "Ask your emergency question")) {
                    is SpeechResult.Success -> {
                        val spokenText = result.results.firstOrNull()?.text
                        if (!spokenText.isNullOrBlank()) {
                            Log.d(TAG, "Voice input received: $spokenText")
                            updateQuery(spokenText)
                            _uiState.value = _uiState.value.copy(showVoiceInput = false)
                            // Explicitly trigger search after voice input
                            triggerSearch(spokenText)
                        } else {
                            setError("No speech detected. Please try again.")
                        }
                    }
                    is SpeechResult.Error -> {
                        Log.e(TAG, "Voice input failed: ${result.message}")
                        setError("Voice input failed: ${result.message}")
                        _uiState.value = _uiState.value.copy(showVoiceInput = false)
                    }
                    is SpeechResult.Cancelled -> {
                        Log.d(TAG, "Voice input cancelled")
                        _uiState.value = _uiState.value.copy(showVoiceInput = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice input exception", e)
                setError("Voice input error: ${e.message}")
                _uiState.value = _uiState.value.copy(showVoiceInput = false)
            }
        }
    }

    /**
     * Stop voice input
     */
    fun stopVoiceInput() {
        Log.d(TAG, "Stopping voice input")
        speechService.cancelRecognition()
        _uiState.value = _uiState.value.copy(
            showVoiceInput = false,
            isListening = false
        )
    }

    /**
     * Perform knowledge base search and RAG
     */
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) return

        Log.d(TAG, "Performing knowledge search: $query")
        val startTime = System.currentTimeMillis()

        _uiState.value = _uiState.value.copy(
            isSearching = true,
            error = null,
            ragAnswer = "",
            sourcesUsed = emptyList()
        )

        try {
            // Step 1: Search knowledge base
            when (val searchResult = knowledgeBase.searchRelevantInfo(
                query = query,
                limit = KNOWLEDGE_SEARCH_LIMIT,
                category = _uiState.value.selectedCategory
            )) {
                is SearchResult.Success -> {
                    val searchTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Knowledge search completed in ${searchTime}ms, found ${searchResult.entries.size} results")

                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResults = searchResult.entries,
                        error = null,
                        totalSearchTime = searchTime
                    )

                    // Step 2: Generate RAG answer if we have results
                    if (searchResult.entries.isNotEmpty()) {
                        generateRAGAnswer(query, searchResult.entries)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            ragAnswer = "No relevant information found in the emergency database for this query. Please try rephrasing your question or check the suggested queries below.",
                            isGeneratingAnswer = false
                        )
                    }
                }
                is SearchResult.NoResults -> {
                    Log.d(TAG, "No search results found for: $query")
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        isGeneratingAnswer = false,
                        searchResults = emptyList()
                    )
                    // Still generate an answer using LLM without context
                    generateFallbackAnswer(query)
                }
                is SearchResult.Error -> {
                    Log.e(TAG, "Knowledge search failed: ${searchResult.message}")
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        isGeneratingAnswer = false,
                        searchResults = emptyList()
                    )
                    setError("Search failed: ${searchResult.message}")
                }
            }

            // Add to search history
            addToSearchHistory(query)

        } catch (e: Exception) {
            Log.e(TAG, "Search exception", e)
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                isGeneratingAnswer = false
            )
            setError("Search error: ${e.message}")
        }
    }

    private suspend fun generateFallbackAnswer(query: String) {
        _uiState.value = _uiState.value.copy(isGeneratingAnswer = true)

        try {
            val fallbackPrompt = PromptUtils.buildRAGPrompt(
                question = query,
                retrievedDocs = emptyList(), // No docs found
                includeConfidenceNote = true
            )

            gemmaModelManager.generateText(fallbackPrompt).collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        val finalAnswer = PromptUtils.addSafetyDisclaimer(
                            result.text,
                            PromptUtils.DisclaimerType.GENERAL
                        )
                        _uiState.value = _uiState.value.copy(
                            ragAnswer = finalAnswer,
                            confidenceScore = 0.2f, // Low confidence without sources
                            isGeneratingAnswer = false
                        )
                    }
                    is GenerationResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            ragAnswer = "Unable to generate answer. Please try again.",
                            isGeneratingAnswer = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback answer generation failed", e)
            _uiState.value = _uiState.value.copy(
                isGeneratingAnswer = false,
                error = "Failed to generate answer"
            )
        }
    }

    /**
     * Generate RAG answer using retrieved documents
     */
    private suspend fun generateRAGAnswer(query: String, searchEntries: List<SearchEntry>) {
        Log.d(TAG, "Generating RAG answer for query: $query")

        _uiState.value = _uiState.value.copy(isGeneratingAnswer = true)

        try {
            // Prepare retrieved documents
            val retrievedDocs = searchEntries.map { entry ->
                "${entry.info.title}: ${entry.info.text}"
            }

            // Build RAG prompt
            val ragPrompt = PromptUtils.buildRAGPrompt(
                question = query,
                retrievedDocs = retrievedDocs,
                maxDocsToInclude = 3,
                includeConfidenceNote = true
            )

            Log.d(TAG, "RAG prompt built, generating answer...")

            // Generate answer using Gemma
            gemmaModelManager.generateText(ragPrompt).collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        Log.d(TAG, "RAG answer generated successfully")

                        // Extract sources used
                        val sources = searchEntries.take(3).map { it.info.source }.distinct()

                        // Calculate average confidence
                        val avgConfidence = searchEntries.take(3).map { it.relevanceScore }.average().toFloat()

                        val finalAnswer = PromptUtils.addSafetyDisclaimer(
                            result.text,
                            PromptUtils.DisclaimerType.GENERAL
                        )

                        _uiState.value = _uiState.value.copy(
                            ragAnswer = finalAnswer,
                            confidenceScore = avgConfidence,
                            sourcesUsed = sources,
                            isGeneratingAnswer = false
                        )
                    }
                    is GenerationResult.Error -> {
                        Log.e(TAG, "RAG answer generation failed: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            ragAnswer = "I found relevant information but encountered an error generating the answer. Please refer to the search results below.",
                            isGeneratingAnswer = false
                        )
                        setError("Answer generation failed: ${result.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "RAG answer generation exception", e)
            _uiState.value = _uiState.value.copy(
                ragAnswer = "I found relevant information but encountered an error generating the answer. Please refer to the search results below.",
                isGeneratingAnswer = false
            )
            setError("Answer generation error: ${e.message}")
        }
    }

    /**
     * Select a category for filtering
     */
    fun selectCategory(category: String?) {
        Log.d(TAG, "Selecting category: $category")
        _uiState.value = _uiState.value.copy(selectedCategory = category)

        // Re-search with the new category if we have a query
        if (_uiState.value.query.length >= KNOWLEDGE_QUERY_MIN_LENGTH) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(_uiState.value.query)
            }
        }
    }

    /**
     * Use a suggested query
     */
    fun useSuggestedQuery(query: String) {
        Log.d(TAG, "Using suggested query: $query")
        updateQuery(query)
    }

    /**
     * Clear search results and query
     */
    private fun clearResults() {
        Log.d(TAG, "Clearing search results")
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            ragAnswer = "",
            error = null,
            sourcesUsed = emptyList(),
            confidenceScore = 0f
        )
        searchJob?.cancel()
    }

    /**
     * Clear query and results
     */
    fun clearQuery() {
        Log.d(TAG, "Clearing query")
        _uiState.value = _uiState.value.copy(
            query = "",
            searchResults = emptyList(),
            ragAnswer = "",
            error = null,
            sourcesUsed = emptyList(),
            confidenceScore = 0f
        )
        searchJob?.cancel()
    }

    /**
     * Load search history from preferences
     */
    private fun loadSearchHistory() {
        // In a real implementation, this would load from SharedPreferences
        // For now, we'll start with an empty history
        Log.d(TAG, "Loading search history")
    }

    /**
     * Add query to search history
     */
    private fun addToSearchHistory(query: String) {
        val currentHistory = _uiState.value.searchHistory.toMutableList()

        // Remove if already exists to avoid duplicates
        currentHistory.remove(query)

        // Add to beginning
        currentHistory.add(0, query)

        // Keep only last 10 searches
        val limitedHistory = currentHistory.take(10)

        _uiState.value = _uiState.value.copy(searchHistory = limitedHistory)

        Log.d(TAG, "Added to search history: $query")

        // In a real implementation, save to SharedPreferences here
    }

    /**
     * Get confidence level description
     */
    fun getConfidenceDescription(confidence: Float): String {
        return when {
            confidence >= 0.9f -> "Very High Confidence"
            confidence >= 0.8f -> "High Confidence"
            confidence >= 0.7f -> "Good Confidence"
            confidence >= 0.6f -> "Moderate Confidence"
            confidence >= 0.5f -> "Low Confidence"
            else -> "Very Low Confidence"
        }
    }

    /**
     * Get confidence color
     */
    fun getConfidenceColor(confidence: Float): androidx.compose.ui.graphics.Color {
        return when {
            confidence >= 0.8f -> androidx.compose.ui.graphics.Color.Green
            confidence >= 0.6f -> androidx.compose.ui.graphics.Color(0xFFFFCC00) // Yellow
            confidence >= 0.4f -> androidx.compose.ui.graphics.Color(0xFFFF6B00) // Orange
            else -> androidx.compose.ui.graphics.Color.Red
        }
    }

    /**
     * Format relevance score for display
     */
    fun formatRelevanceScore(score: Float): String {
        return "${(score * 100).toInt()}%"
    }

    /**
     * Get available categories
     */
    fun getAvailableCategories(): List<Pair<String?, String>> {
        return listOf(
            null to "All Categories",
            Categories.MEDICAL to "Medical",
            Categories.STRUCTURAL to "Structural",
            Categories.COMMUNICATION to "Communication",
            Categories.GENERAL to "General"
        )
    }

    /**
     * Set error message
     */
    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Check if we can perform voice input
     */
    fun canUseVoiceInput(): Boolean {
        return !_uiState.value.isListening &&
                !_uiState.value.isSearching &&
                !_uiState.value.isGeneratingAnswer
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "KnowledgeViewModel cleared")
        searchJob?.cancel()
        speechService.cancelRecognition()
    }
}