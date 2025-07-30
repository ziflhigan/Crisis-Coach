package com.cautious5.crisis_coach

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cautious5.crisis_coach.model.ai.InitializationResult as ModelInitResult
import com.cautious5.crisis_coach.model.database.DatabaseInitializer
import com.cautious5.crisis_coach.model.database.InitializationResult as DbInitResult
import com.cautious5.crisis_coach.model.embedding.EmbedderResult
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.DeviceCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main ViewModel for MainActivity
 * Manages app-wide initialization state and error handling by orchestrating the setup process.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = LogTags.MAIN_ACTIVITY
    }

    enum class InitializationState { LOADING, SUCCESS, ERROR }

    data class MainUiState(
        val initState: InitializationState = InitializationState.LOADING,
        val errorTitle: String? = null,
        val errorMessage: String? = null,
        val deviceCapability: DeviceCapabilityChecker.DeviceCapability? = null
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "MainViewModel initialized, starting app initialization...")
        initializeApp()
    }

    /**
     * Orchestrates the entire app initialization sequence.
     * This function is safe to be called again for a retry.
     */
    fun initializeApp() {
        viewModelScope.launch {
            // Reset state to LOADING for retries
            _uiState.update { it.copy(initState = InitializationState.LOADING, errorTitle = null, errorMessage = null) }
            try {
                val app = getApplication<CrisisCoachApplication>()

                // Step 1: Assess Device Capability
                Log.d(TAG, "Step 1: Assessing device capabilities...")
                val deviceCapability = DeviceCapabilityChecker.assessDeviceCapability(getApplication())
                _uiState.update { it.copy(deviceCapability = deviceCapability) }

                if (!deviceCapability.canRunApp) {
                    setInitializationError("Device Not Supported", deviceCapability.limitations.joinToString("\n"))
                    return@launch
                }

                // Step 2: Initialize Text Embedder (critical dependency for the database)
                Log.d(TAG, "Step 2: Initializing Text Embedder...")
                when (val embedderResult = app.textEmbedder.initialize()) {
                    is EmbedderResult.Error -> {
                        setInitializationError("AI Component Failed", embedderResult.message)
                        return@launch
                    }
                    is EmbedderResult.Success -> Log.i(TAG, "TextEmbedder initialized successfully.")
                }

                // Step 3: Initialize Knowledge Base Database
                Log.d(TAG, "Step 3: Initializing knowledge base...")
                val dbInitializer = DatabaseInitializer(getApplication(), app.knowledgeBase, app.textEmbedder)
                when (val dbResult = dbInitializer.initializeIfNeeded()) {
                    is DbInitResult.Error -> {
                        setInitializationError("Database Failed", dbResult.message)
                        return@launch
                    }
                    else -> Log.i(TAG, "Database is ready.")
                }

                // Step 4: Initialize the main Gemma AI Model
                Log.d(TAG, "Step 4: Initializing main Gemma AI model...")
                val modelConfig = com.cautious5.crisis_coach.model.ai.ModelConfig(
                    variant = deviceCapability.recommendedModelVariant,
                    hardwarePreference = deviceCapability.recommendedHardwarePreference,
                    modelPath = "" // Path is determined by the ModelLoader
                )

                when (val modelResult = app.gemmaModelManager.initializeModel(modelConfig)) {
                    is ModelInitResult.Error -> {
                        setInitializationError("Main AI Model Failed", modelResult.message)
                        return@launch
                    }
                    is ModelInitResult.Success -> Log.i(TAG, "Gemma model initialized successfully.")
                }

                // Step 5: Initialize all application services
                // This is now safe because the lazy initializers have their dependencies ready.
                Log.d(TAG, "Step 5: Eagerly initializing core services...")
                app.translationService
                app.imageAnalysisService
                Log.i(TAG, "All services are ready.")


                // If all steps complete, set state to SUCCESS
                Log.i(TAG, "App initialization completed successfully.")
                _uiState.update { it.copy(initState = InitializationState.SUCCESS) }

            } catch (e: Exception) {
                Log.e(TAG, "A critical error occurred during app initialization", e)
                setInitializationError("Critical Error", e.message ?: "An unknown error occurred during setup.")
            }
        }
    }

    /**
     * Updates the UI state to show an error.
     */
    private fun setInitializationError(title: String, message: String) {
        Log.e(TAG, "Initialization error set: $title - $message")
        _uiState.update {
            it.copy(
                initState = InitializationState.ERROR,
                errorTitle = title,
                errorMessage = message
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MainViewModel cleared")
    }
}