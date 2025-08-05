package com.cautious5.crisis_coach

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cautious5.crisis_coach.model.ai.GenerationParams
import com.cautious5.crisis_coach.model.ai.HardwarePreference
import com.cautious5.crisis_coach.model.ai.ModelConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val sharedPrefs by lazy {
        getApplication<Application>().getSharedPreferences("model_settings", Context.MODE_PRIVATE)
    }

    enum class InitializationState { LOADING, SUCCESS, ERROR }

    enum class InitializationPhase {
        CHECKING_DEVICE,
        CHECKING_MODEL,  // Moved before database initialization
        INITIALIZING_EMBEDDER,
        INITIALIZING_DATABASE,
        LOADING_MODEL,
        INITIALIZING_SERVICES,
        COMPLETED
    }

    data class MainUiState(
        val initState: InitializationState = InitializationState.LOADING,
        val initPhase: InitializationPhase = InitializationPhase.CHECKING_DEVICE,
        val initProgress: Float = 0f,
        val initStatusText: String = "Starting initialization...",
        val errorTitle: String? = null,
        val errorMessage: String? = null,
        val deviceCapability: DeviceCapabilityChecker.DeviceCapability? = null,
        val downloadState: ModelDownloader.DownloadState? = null,
        val needsModelDownload: Boolean = false,
        val modelVariantForDownload: ModelVariant? = null,
        val needsTokenInput: Boolean = false,
        val showSettingsDialog: Boolean = false,
        val currentModelConfig: ModelConfig? = null,
        val isModelReloading: Boolean = false,
        val reloadProgress: Float = 0f,
        val modelDownloadStatus: Map<ModelVariant, Boolean> = emptyMap(),
        val isApplyingParams: Boolean = false,
        val pendingGenerationParams: GenerationParams? = null,
        val showInitializationProgress: Boolean = true
    )

    private val hfAuthManager by lazy { HuggingFaceAuthManager(getApplication()) }
    private val modelDownloader by lazy { ModelDownloader(getApplication(), hfAuthManager) }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "MainViewModel initialized, starting app initialization...")
        initializeApp()

        // Observe download progress
        viewModelScope.launch {
            modelDownloader.downloadProgress.collect { downloadState ->
                // When the download is no longer in progress, clear the target variant
                if (downloadState !is ModelDownloader.DownloadState.InProgress) {
                    _uiState.update { it.copy(
                        downloadState = downloadState,
                        modelVariantForDownload = null // Clear the variant when done
                    )}
                } else {
                    _uiState.update { it.copy(downloadState = downloadState) }
                }

                // When download completes successfully, re-check statuses
                if (downloadState is ModelDownloader.DownloadState.Completed) {
                    checkAllModelStatuses()
                }
            }
        }
    }

    /**
     * Orchestrates the entire app initialization sequence.
     * This function is safe to be called again for a retry.
     */
    fun initializeApp() {
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    initState = InitializationState.LOADING,
                    initPhase = InitializationPhase.CHECKING_DEVICE,
                    initProgress = 0f,
                    initStatusText = "Checking device capabilities...",
                    errorTitle = null,
                    errorMessage = null,
                    needsModelDownload = false,
                    showInitializationProgress = true
                )
            }

            // Check model statuses on IO thread
            withContext(Dispatchers.IO) {
                checkAllModelStatuses()
            }

            try {
                val app = getApplication<CrisisCoachApplication>()

                // Step 1: Assess Device Capability (0-10%) - FAST WORK ON MAIN
                Log.d(TAG, "Step 1: Assessing device capabilities...")
                updateInitProgress(InitializationPhase.CHECKING_DEVICE, 0.05f, "Assessing device capabilities...")

                // Move heavy device capability check to IO thread
                val deviceCapability = withContext(Dispatchers.IO) {
                    DeviceCapabilityChecker.assessDeviceCapability(getApplication())
                }
                _uiState.update { it.copy(deviceCapability = deviceCapability) }

                if (!deviceCapability.canRunApp) {
                    setInitializationError("Device Not Supported", deviceCapability.limitations.joinToString("\n"))
                    return@launch
                }

                updateInitProgress(InitializationPhase.CHECKING_DEVICE, 0.1f, "Device capability assessment complete")

                // Step 2: Check if model exists BEFORE other heavy initialization (10-20%)
                Log.d(TAG, "Step 2: Checking for AI model...")
                updateInitProgress(InitializationPhase.CHECKING_MODEL, 0.1f, "Checking AI model availability...")

                val loadResult = withContext(Dispatchers.IO) {
                    app.gemmaModelManager.modelLoader.loadModel(deviceCapability.recommendedModelVariant)
                }

                when (loadResult) {
                    is ModelLoader.LoadResult.Missing -> {
                        Log.d(TAG, "Model missing, showing download option")
                        _uiState.update {
                            it.copy(
                                initState = InitializationState.ERROR,
                                showInitializationProgress = false,
                                errorTitle = "AI Model Required",
                                errorMessage = "Crisis Coach needs to download the AI model to function. This is a one-time download.",
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
                        updateInitProgress(InitializationPhase.CHECKING_MODEL, 0.2f, "AI model found")
                    }
                }

                // Step 3: Initialize Text Embedder (20-30%)
                Log.d(TAG, "Step 3: Initializing Text Embedder...")
                updateInitProgress(InitializationPhase.INITIALIZING_EMBEDDER, 0.2f, "Initializing AI text embedder...")

                // Ensure embedder initialization happens on worker thread
                val embedderResult = withContext(Dispatchers.IO) {
                    app.textEmbedder.initialize()
                }

                when (embedderResult) {
                    is EmbedderResult.Error -> {
                        setInitializationError("AI Component Failed", embedderResult.message)
                        return@launch
                    }
                    is EmbedderResult.Success -> {
                        Log.i(TAG, "TextEmbedder initialized successfully.")
                        updateInitProgress(InitializationPhase.INITIALIZING_EMBEDDER, 0.3f, "Text embedder ready")
                    }
                }

                // Step 4: Initialize Knowledge Base Database (30-70%)
                Log.d(TAG, "Step 4: Initializing knowledge base...")
                updateInitProgress(InitializationPhase.INITIALIZING_DATABASE, 0.3f, "Setting up knowledge base...")

                val dbInitializer = DatabaseInitializer(getApplication(), app.knowledgeBase, app.textEmbedder)

                // Move heavy database initialization to IO thread
                val dbResult = withContext(Dispatchers.IO) {
                    dbInitializer.initializeIfNeeded()
                }

                when (dbResult) {
                    is DbInitResult.Success -> {
                        Log.i(TAG, "Database initialized with ${dbResult.entriesAdded} entries from ${dbResult.sourcesProcessed} sources in ${dbResult.initializationTimeMs}ms")
                        updateInitProgress(InitializationPhase.INITIALIZING_DATABASE, 0.7f, "Knowledge base ready (${dbResult.entriesAdded} entries loaded)")
                    }
                    is DbInitResult.AlreadyInitialized -> {
                        Log.i(TAG, "Database was already initialized")
                        updateInitProgress(InitializationPhase.INITIALIZING_DATABASE, 0.7f, "Knowledge base already initialized")
                    }
                    is DbInitResult.Error -> {
                        Log.e(TAG, "Database initialization failed: ${dbResult.message}")
                        setInitializationError("Database Failed", dbResult.message)
                        return@launch
                    }
                }

                // Debug: Check what's actually in the database
                launch(Dispatchers.IO) {
                    app.knowledgeBase.debugDatabaseContent()
                }

                // Step 5: Initialize the main Gemma AI Model (70-90%)
                Log.d(TAG, "Step 5: Initializing main Gemma AI model...")
                updateInitProgress(InitializationPhase.LOADING_MODEL, 0.7f, "Loading AI model...")

                // Transition to showing the main navigation with model loading dialog
                _uiState.update { it.copy(
                    initState = InitializationState.SUCCESS, // Set to SUCCESS early
                    showInitializationProgress = false, // Hide initialization progress
                    isModelReloading = true, // Show model loading dialog
                    reloadProgress = 0f
                )}

                // Monitor model loading progress
                viewModelScope.launch {
                    app.gemmaModelManager.loadProgress.collect { progress ->
                        _uiState.update { it.copy(reloadProgress = progress) }
                    }
                }

                val savedParams = loadGenerationParams()

                val modelConfig = ModelConfig(
                    variant = deviceCapability.recommendedModelVariant,
                    hardwarePreference = deviceCapability.recommendedHardwarePreference,
                    modelPath = "",
                    temperature = savedParams.temperature,
                    topK = savedParams.topK,
                    topP = savedParams.topP,
                    maxOutputTokens = savedParams.maxOutputTokens
                )

                _uiState.update { it.copy(currentModelConfig = modelConfig) }

                // Model initialization already happens on IO thread inside GemmaModelManager
                when (val modelResult = app.gemmaModelManager.initializeModel(modelConfig)) {
                    is ModelInitResult.Error -> {
                        _uiState.update { it.copy(
                            isModelReloading = false,
                            initState = InitializationState.ERROR,
                            errorTitle = "Main AI Model Failed",
                            errorMessage = modelResult.message
                        )}
                        return@launch
                    }
                    is ModelInitResult.Success -> {
                        Log.i(TAG, "Gemma model initialized successfully.")
                        _uiState.update { it.copy(isModelReloading = false) }
                    }
                }

                // Step 6: Initialize all application services (90-100%)
                Log.d(TAG, "Step 6: Eagerly initializing core services...")
                updateInitProgress(InitializationPhase.INITIALIZING_SERVICES, 0.9f, "Starting services...")

                // Service initialization is lightweight, but let's be safe
                withContext(Dispatchers.IO) {
                    app.translationService
                    app.imageAnalysisService
                }

                Log.i(TAG, "All services are ready.")

                // Step 7: Finalize initialization (100%)
                updateInitProgress(InitializationPhase.COMPLETED, 1.0f, "Setup complete!")
                Log.i(TAG, "App initialization completed successfully.")

                _uiState.update {
                    it.copy(
                        initState = InitializationState.SUCCESS,
                        isModelReloading = false // Hide model loading dialog
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "A critical error occurred during app initialization", e)
                setInitializationError("Critical Error", e.message ?: "An unknown error occurred during setup.")
            }
        }
    }

    /**
     * Updates initialization progress with phase, progress percentage, and status text
     */
    private fun updateInitProgress(phase: InitializationPhase, progress: Float, statusText: String) {
        _uiState.update {
            it.copy(
                initPhase = phase,
                initProgress = progress,
                initStatusText = statusText
            )
        }
    }

    /**
     * Updates the UI state to show an error and hide progress dialog.
     */
    private fun setInitializationError(title: String, message: String) {
        Log.e(TAG, "Initialization error set: $title - $message")
        _uiState.update {
            it.copy(
                initState = InitializationState.ERROR,
                showInitializationProgress = false,
                errorTitle = title,
                errorMessage = message
            )
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun selectModelVariant(variant: ModelVariant) {
        val isDownloaded = _uiState.value.modelDownloadStatus[variant] ?: false

        if (isDownloaded) {
            // Model exists, update the config and re-initialize the model
            val app = getApplication<CrisisCoachApplication>()
            val newConfig = ModelConfig(
                variant = variant,
                hardwarePreference = _uiState.value.currentModelConfig?.hardwarePreference ?: HardwarePreference.AUTO,
                modelPath = app.gemmaModelManager.modelLoader.getInternalModelPath(variant)
            )
            _uiState.update { it.copy(currentModelConfig = newConfig) }
            viewModelScope.launch {
                app.gemmaModelManager.initializeModel(newConfig)
            }
        } else {
            // Model needs to be downloaded, set it as the target for download
            _uiState.update { it.copy(modelVariantForDownload = variant) }
            // Trigger the download flow
            startModelDownload(variant)
        }
    }

    fun updateHardwarePreference(preference: HardwarePreference) {
        _uiState.value.currentModelConfig?.let { config ->
            if (config.hardwarePreference == preference) {
                Log.d(TAG, "Hardware preference already set to $preference. No change.")
                return
            }

            Log.d(TAG, "Updating hardware preference from ${config.hardwarePreference} to $preference")
            val newConfig = config.copy(hardwarePreference = preference)
            _uiState.update { it.copy(
                currentModelConfig = newConfig,
                isModelReloading = true,
                reloadProgress = 0f
            )}

            // Reinitialize model with new preference
            viewModelScope.launch {
                try {
                    Log.d(TAG, "Re-initializing Gemma model with new hardware preference.")
                    val app = getApplication<CrisisCoachApplication>()

                    // First release the current model
                    app.gemmaModelManager.releaseModel()

                    // Monitor progress
                    launch {
                        app.gemmaModelManager.loadProgress.collect { progress ->
                            _uiState.update { it.copy(reloadProgress = progress) }
                        }
                    }

                    // Reinitialize with new config
                    when (val result = app.gemmaModelManager.initializeModel(newConfig)) {
                        is ModelInitResult.Success -> {
                            Log.i(TAG, "Model reinitialized successfully with $preference")
                            _uiState.update { it.copy(
                                isModelReloading = false,
                                reloadProgress = 1f
                            )}
                        }
                        is ModelInitResult.Error -> {
                            Log.e(TAG, "Failed to reinitialize model: ${result.message}")
                            // Revert to previous preference
                            _uiState.update { it.copy(
                                currentModelConfig = config,
                                isModelReloading = false
                            )}
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reinitializing model", e)
                    _uiState.update { it.copy(
                        currentModelConfig = config,
                        isModelReloading = false
                    )}
                }
            }
        }
    }

    fun updateGenerationParams(params: GenerationParams) {
        // Just save the pending params, don't apply immediately
        _uiState.update {
            it.copy(pendingGenerationParams = params)
        }

        // Save to SharedPreferences immediately for persistence
        saveGenerationParams(params)

        Log.d(TAG, "Generation parameters updated in pending state")
    }

    fun applyGenerationParams() {
        val pendingParams = _uiState.value.pendingGenerationParams ?: return
        val currentConfig = _uiState.value.currentModelConfig ?: return

        Log.d(TAG, "Applying pending generation parameters")

        // Update the config in UI state
        val newConfig = currentConfig.copy(
            temperature = pendingParams.temperature,
            topK = pendingParams.topK,
            topP = pendingParams.topP,
            maxOutputTokens = pendingParams.maxOutputTokens
        )

        _uiState.update {
            it.copy(
                currentModelConfig = newConfig,
                isApplyingParams = true,
                isModelReloading = true,
                reloadProgress = 0f,
                pendingGenerationParams = null
            )
        }

        // Apply the parameters to the model
        viewModelScope.launch {
            try {
                // Monitor model loading progress during parameter application
                launch {
                    getApplication<CrisisCoachApplication>()
                        .gemmaModelManager.loadProgress.collect { progress ->
                            _uiState.update { it.copy(reloadProgress = progress) }
                        }
                }

                getApplication<CrisisCoachApplication>()
                    .gemmaModelManager.applyGenerationParams(pendingParams)

                Log.d(TAG, "Generation parameters applied successfully")

                _uiState.update {
                    it.copy(
                        isApplyingParams = false,
                        isModelReloading = false,
                        reloadProgress = 1f
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply generation parameters: ${e.message}", e)

                // Revert to previous config on error
                _uiState.update {
                    it.copy(
                        currentModelConfig = currentConfig,
                        isApplyingParams = false,
                        isModelReloading = false,
                        reloadProgress = 0f,
                        pendingGenerationParams = pendingParams // Keep pending params for retry
                    )
                }

            }
        }
    }

    private fun saveGenerationParams(params: GenerationParams) {
        sharedPrefs.edit().apply {
            putFloat("temperature", params.temperature)
            putInt("top_k", params.topK)
            putFloat("top_p", params.topP)
            putInt("max_tokens", params.maxOutputTokens)
            apply()
        }
    }

    private fun loadGenerationParams(): GenerationParams {
        return GenerationParams(
            temperature = sharedPrefs.getFloat("temperature", 0.7f),
            topK = sharedPrefs.getInt("top_k", 64),
            topP = sharedPrefs.getFloat("top_p", 0.95f),
            maxOutputTokens = sharedPrefs.getInt("max_tokens", 4096)
        )
    }

    private suspend fun checkAllModelStatuses() {
        val app = getApplication<CrisisCoachApplication>()
        val statuses = ModelVariant.entries.associateWith { variant ->
            val modelPath = app.gemmaModelManager.modelLoader.getInternalModelPath(variant)
            app.gemmaModelManager.modelLoader.isValidModelFile(modelPath)
        }
        _uiState.update { it.copy(modelDownloadStatus = statuses) }
    }

    fun startModelDownload(modelVariant: ModelVariant) {
        _uiState.update { it.copy(modelVariantForDownload = modelVariant) }

        if (!hfAuthManager.isAuthenticated()) {
            Log.d(TAG, "Authentication needed. Triggering auth flow for ${modelVariant.displayName}")
            _triggerAuthFlow.value = modelVariant
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
        // Also clear the targeted variant when cancelling
        _uiState.update { it.copy(modelVariantForDownload = null) }
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