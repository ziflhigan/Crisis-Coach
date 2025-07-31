package com.cautious5.crisis_coach

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cautious5.crisis_coach.model.ai.ModelLoader
import com.cautious5.crisis_coach.model.ai.ModelVariant
import com.cautious5.crisis_coach.model.ai.InitializationResult as ModelInitResult
import com.cautious5.crisis_coach.model.database.DatabaseInitializer
import com.cautious5.crisis_coach.model.database.InitializationResult as DbInitResult
import com.cautious5.crisis_coach.model.embedding.EmbedderResult
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.DeviceCapabilityChecker
import com.cautious5.crisis_coach.utils.HuggingFaceAuthManager
import com.cautious5.crisis_coach.utils.ModelDownloader
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

    private val _triggerAuthFlow = MutableStateFlow<ModelVariant?>(null)
    val triggerAuthFlow: StateFlow<ModelVariant?> = _triggerAuthFlow

    enum class InitializationState { LOADING, SUCCESS, ERROR }

    data class MainUiState(
        val initState: InitializationState = InitializationState.LOADING,
        val errorTitle: String? = null,
        val errorMessage: String? = null,
        val deviceCapability: DeviceCapabilityChecker.DeviceCapability? = null,
        val downloadState: ModelDownloader.DownloadState? = null,
        val needsModelDownload: Boolean = false,
        val modelVariantForDownload: ModelVariant? = null,
        val needsTokenInput: Boolean = false
    )

    val hfAuthManager by lazy { HuggingFaceAuthManager(getApplication()) }
    private val modelDownloader by lazy { ModelDownloader(getApplication(), hfAuthManager) }


    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "MainViewModel initialized, starting app initialization...")

        // Observe download progress
        viewModelScope.launch {
            modelDownloader.downloadProgress.collect { downloadState ->
                _uiState.update { it.copy(downloadState = downloadState) }

                // When download completes successfully, retry initialization
                if (downloadState is ModelDownloader.DownloadState.Completed) {
                    initializeApp()
                }
            }
        }

        initializeApp()
    }

    /**
     * Orchestrates the entire app initialization sequence.
     * This function is safe to be called again for a retry.
     */
    fun initializeApp() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    initState = InitializationState.LOADING,
                    errorTitle = null,
                    errorMessage = null,
                    needsModelDownload = false
                )
            }

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

                // Step 4: Check if model exists BEFORE trying to initialize it
                Log.d(TAG, "Step 4: Checking for AI model...")
                when (val loadResult = app.gemmaModelManager.modelLoader.loadModel(deviceCapability.recommendedModelVariant)) {
                    is ModelLoader.LoadResult.Missing -> {
                        Log.d(TAG, "Model missing, showing download option")
                        _uiState.update {
                            it.copy(
                                initState = InitializationState.ERROR,
                                errorTitle = "AI Model Required",
                                errorMessage = "Crisis Coach needs to download the AI model (${deviceCapability.recommendedModelVariant.displayName}) to function. This is a one-time download of approximately ${deviceCapability.recommendedModelVariant.approximateRamUsageMB / 1024}GB.",
                                needsModelDownload = true
                            )
                        }
                        return@launch
                    }
                    is ModelLoader.LoadResult.Error -> {
                        setInitializationError("Model Load Error", loadResult.message)
                        return@launch
                    }
                    is ModelLoader.LoadResult.Success -> {
                        Log.i(TAG, "Model file found, proceeding with initialization...")
                    }
                }

                // Step 5: Initialize the main Gemma AI Model (now we know the file exists)
                Log.d(TAG, "Step 5: Initializing main Gemma AI model...")
                val modelConfig = com.cautious5.crisis_coach.model.ai.ModelConfig(
                    variant = deviceCapability.recommendedModelVariant,
                    hardwarePreference = deviceCapability.recommendedHardwarePreference,
                    modelPath = ""
                )

                when (val modelResult = app.gemmaModelManager.initializeModel(modelConfig)) {
                    is ModelInitResult.Error -> {
                        setInitializationError("Main AI Model Failed", modelResult.message)
                        return@launch
                    }
                    is ModelInitResult.Success -> Log.i(TAG, "Gemma model initialized successfully.")
                }

                // Step 6: Initialize all application services
                Log.d(TAG, "Step 6: Eagerly initializing core services...")
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

    fun startModelDownload(modelVariant: ModelVariant) {
        _uiState.update { it.copy(modelVariantForDownload = modelVariant) }

        if (!hfAuthManager.isAuthenticated()) {
            Log.d(TAG, "Authentication needed. Triggering auth flow event for ${modelVariant.displayName}")
            _triggerAuthFlow.value = modelVariant // Emit the event
        } else {
            Log.d(TAG, "Already authenticated. Starting download for ${modelVariant.displayName}")
            modelDownloader.startDownload(modelVariant)
        }
    }

    fun onAuthFlowTriggered() {
        _triggerAuthFlow.value = null
    }

    fun cancelDownload() {
        modelDownloader.cancel()
    }

    fun retryWithAuth(modelVariant: ModelVariant) {
        // User completed auth, try download again
        startModelDownload(modelVariant)
    }

    fun retryDownloadAfterAuth() {
        val modelVariant = _uiState.value.modelVariantForDownload
        if (modelVariant != null) {
            Log.d(TAG, "Auth successful. Checking for token before retrying download.")
            if (hfAuthManager.isAuthenticated()) {
                // This now means we have a token, so we can proceed
                startModelDownload(modelVariant)
            } else {
                // We have session cookies from the WebView, but no token. Ask the user.
                Log.d(TAG, "User is authenticated via session, but token is needed.")
                _uiState.update { it.copy(needsTokenInput = true) }
            }
        } else {
            Log.e(TAG, "Auth successful, but no model variant was selected for download.")
            // Optionally set an error state here
        }
    }

    fun saveTokenAndRetryDownload(token: String) {
        hfAuthManager.saveToken(token)
        _uiState.update { it.copy(needsTokenInput = false) }
        retryDownloadAfterAuth()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MainViewModel cleared")
    }
}