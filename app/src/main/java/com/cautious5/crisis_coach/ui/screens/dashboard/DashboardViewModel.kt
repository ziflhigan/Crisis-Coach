package com.cautious5.crisis_coach.ui.screens.dashboard

import android.app.Application
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cautious5.crisis_coach.CrisisCoachApplication
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.ai.ModelState
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.DeviceCapabilityChecker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for DashboardScreen
 * Manages dashboard state, model status, and recent activity tracking
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = LogTags.MAIN_ACTIVITY
        private const val MAX_RECENT_ACTIVITIES = 5
    }

    // Model manager for status monitoring
    private val gemmaModelManager: GemmaModelManager by lazy {
        (getApplication<CrisisCoachApplication>()).gemmaModelManager
    }

    /**
     * UI state for dashboard screen
     */
    data class DashboardUiState(
        val isModelInitializing: Boolean = true,
        val modelState: ModelState = ModelState.UNINITIALIZED,
        val loadingProgress: Float = 0f,
        val deviceInfo: String = "",
        val memoryUsage: String = "",
        val recentActivities: List<ActivityItem> = emptyList(),
        val isOfflineMode: Boolean = true
    )

    // State flows
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Recent activities storage
    private val recentActivities = mutableListOf<ActivityItem>()

    init {
        Log.d(TAG, "DashboardViewModel initialized")
        initialize()
    }

    /**
     * Initialize the ViewModel
     */
    private fun initialize() {
        // Observe the central model state from GemmaModelManager
        viewModelScope.launch {
            gemmaModelManager.modelState.collect { state ->
                Log.d(TAG, "Dashboard detected model state change: $state")
                _uiState.update {
                    it.copy(
                        modelState = state,
                        isModelInitializing = state in listOf(
                            ModelState.LOADING,
                            ModelState.UNINITIALIZED
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            gemmaModelManager.loadProgress.collect { progress ->
                _uiState.update { it.copy(loadingProgress = progress) }
            }
        }

        loadInitialData()
    }

    /**
     * Load initial data for dashboard
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // Get device info
                val deviceInfo = getDeviceInfo()

                // Get memory usage
                val memoryUsage = getMemoryUsage()

                // Load sample activities for demo
                loadSampleActivities()

                _uiState.value = _uiState.value.copy(
                    deviceInfo = deviceInfo,
                    memoryUsage = memoryUsage,
                    recentActivities = recentActivities.toList()
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
            }
        }
    }

    /**
     * Add new activity to recent list
     */
    fun addActivity(
        type: ActivityType,
        title: String,
        snippet: String
    ) {
        Log.d(TAG, "Adding activity: $type - $title")

        val newActivity = ActivityItem(
            id = System.currentTimeMillis().toString(),
            type = type,
            title = title,
            snippet = snippet,
            timestamp = formatTimestamp(System.currentTimeMillis()),
            icon = getIconForActivityType(type)
        )

        // Add to beginning of list
        recentActivities.add(0, newActivity)

        // Keep only last MAX_RECENT_ACTIVITIES items
        if (recentActivities.size > MAX_RECENT_ACTIVITIES) {
            recentActivities.removeAt(recentActivities.size - 1)
        }

        // Update UI state
        _uiState.value = _uiState.value.copy(
            recentActivities = recentActivities.toList()
        )
    }

    /**
     * Clear all recent activities
     */
    fun clearActivities() {
        Log.d(TAG, "Clearing all activities")
        recentActivities.clear()
        _uiState.value = _uiState.value.copy(
            recentActivities = emptyList()
        )
    }

    /**
     * Refresh dashboard data
     */
    fun refresh() {
        Log.d(TAG, "Refreshing dashboard")
        loadInitialData()
    }

    /**
     * Get current model status description
     */
    fun getModelStatusDescription(): String {
        return when (_uiState.value.modelState) {
            ModelState.READY -> "AI model is ready for use"
            ModelState.LOADING -> "Loading AI model, please wait..."
            ModelState.BUSY -> "AI model is currently processing"
            ModelState.ERROR -> "AI model encountered an error"
            ModelState.UNINITIALIZED -> "AI model is starting up..."
        }
    }

    /**
     * Check if features are available
     */
    fun isFeaturesAvailable(): Boolean {
        return _uiState.value.modelState == ModelState.READY
    }

    // Private helper methods

    /**
     * Load sample activities for demonstration
     */
    private fun loadSampleActivities() {
        // In a real app, this would load from persistent storage
        if (recentActivities.isEmpty()) {
            val sampleActivities = listOf(
                ActivityItem(
                    id = "sample_1",
                    type = ActivityType.TRANSLATION,
                    title = "Translation: English to Arabic",
                    snippet = "I need help -> أحتاج مساعدة",
                    timestamp = "2 min ago",
                    icon = Icons.Default.Translate
                ),
                ActivityItem(
                    id = "sample_2",
                    type = ActivityType.MEDICAL_ANALYSIS,
                    title = "Medical Analysis: Laceration",
                    snippet = "Assessment: Deep laceration, requires cleaning...",
                    timestamp = "5 min ago",
                    icon = Icons.Default.LocalHospital
                ),
                ActivityItem(
                    id = "sample_3",
                    type = ActivityType.KNOWLEDGE_QUERY,
                    title = "Emergency Guide: CPR procedure",
                    snippet = "How to perform CPR on an adult patient...",
                    timestamp = "10 min ago",
                    icon = Icons.AutoMirrored.Filled.Help
                )
            )

            recentActivities.addAll(sampleActivities)
        }
    }

    /**
     * Get device information string
     */
    private fun getDeviceInfo(): String {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            val version = android.os.Build.VERSION.RELEASE
            "$manufacturer $model (Android $version)"
        } catch (e: Exception) {
            "Device information unavailable"
        }
    }

    /**
     * Get memory usage information
     */
    private fun getMemoryUsage(): String {
        val context = getApplication<Application>().applicationContext
        return try {
            val totalRam = DeviceCapabilityChecker.getTotalRamMB(context)
            val availableRam = DeviceCapabilityChecker.getAvailableRamMB(context)
            val usedRam = totalRam - availableRam
            val usedRamGB = "%.1f".format(usedRam / 1024f)
            val totalRamGB = "%.1f".format(totalRam / 1024f)

            val usageString = "RAM: $usedRamGB GB / $totalRamGB GB Used"
            Log.d(TAG, "Memory Usage Calculated: $usageString")
            usageString
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get real memory usage", e)
            // Fallback to the model's approximate usage if the real check fails
            val modelMemory = gemmaModelManager.getMemoryUsage()
            if (modelMemory > 0) "AI Model: ${modelMemory}MB" else "Memory usage unavailable"
        }
    }

    /**
     * Get icon for activity type
     */
    private fun getIconForActivityType(type: ActivityType) = when (type) {
        ActivityType.TRANSLATION -> Icons.Default.Translate
        ActivityType.MEDICAL_ANALYSIS -> Icons.Default.LocalHospital
        ActivityType.STRUCTURAL_ANALYSIS -> Icons.Default.Engineering
        ActivityType.KNOWLEDGE_QUERY -> Icons.AutoMirrored.Filled.Help
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} min ago"
            diff < 86400000 -> "${diff / 3600000} hr ago"
            else -> "${diff / 86400000} days ago"
        }
    }

    /**
     * Update activity timestamps
     */
    fun updateTimestamps() {
        val updatedActivities = recentActivities.map { activity ->
            val originalTimestamp = activity.id.toLongOrNull() ?: System.currentTimeMillis()
            activity.copy(timestamp = formatTimestamp(originalTimestamp))
        }

        recentActivities.clear()
        recentActivities.addAll(updatedActivities)

        _uiState.value = _uiState.value.copy(
            recentActivities = recentActivities.toList()
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "DashboardViewModel cleared")
    }
}