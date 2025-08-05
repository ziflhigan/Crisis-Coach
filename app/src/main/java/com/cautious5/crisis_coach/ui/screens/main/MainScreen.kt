package com.cautious5.crisis_coach.ui.screens.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cautious5.crisis_coach.MainViewModel
import com.cautious5.crisis_coach.model.ai.HardwarePreference
import com.cautious5.crisis_coach.model.ai.ModelConfig
import com.cautious5.crisis_coach.model.ai.ModelVariant
import com.cautious5.crisis_coach.ui.dialogs.InitializationProgressDialog
import com.cautious5.crisis_coach.ui.dialogs.ModelReloadingDialog
import com.cautious5.crisis_coach.ui.dialogs.SettingsDialog
import com.cautious5.crisis_coach.ui.navigation.AppNavigation
import com.cautious5.crisis_coach.utils.Constants

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (uiState.initState) {
            MainViewModel.InitializationState.LOADING -> {
                InitializationProgressDialog(
                    phase = uiState.initPhase,
                    progress = uiState.initProgress,
                    statusText = uiState.initStatusText
                )
            }
            MainViewModel.InitializationState.ERROR -> {
                ErrorScreen(
                    title = uiState.errorTitle ?: "Initialization Error",
                    message = uiState.errorMessage ?: Constants.ErrorMessages.UNKNOWN_ERROR,
                    downloadState = uiState.downloadState,
                    deviceCapability = uiState.deviceCapability,
                    onRetry = viewModel::initializeApp,
                    onDownload = if (uiState.needsModelDownload) viewModel::startModelDownload else null,
                    onCancelDownload = viewModel::cancelDownload
                )
            }
            MainViewModel.InitializationState.SUCCESS -> {
                // Show the main navigation - app is ready!
                AppNavigation(
                    onSettingsClick = viewModel::showSettings
                )

                // Model reloading dialog (for settings changes) - overlaid on main navigation
                if (uiState.isModelReloading) {
                    ModelReloadingDialog(
                        isApplyingParams = uiState.isApplyingParams
                    )
                }

                // Settings dialog - overlaid on main navigation
                if (uiState.showSettingsDialog) {
                    val defaultConfig = uiState.currentModelConfig ?: ModelConfig(
                        variant = ModelVariant.GEMMA_3N_E2B,
                        hardwarePreference = HardwarePreference.AUTO,
                        modelPath = ""
                    )

                    SettingsDialog(
                        currentConfig = defaultConfig,
                        availableVariants = ModelVariant.entries,
                        downloadState = uiState.downloadState,
                        modelDownloadStatus = uiState.modelDownloadStatus,
                        modelVariantForDownload = uiState.modelVariantForDownload,
                        pendingGenerationParams = uiState.pendingGenerationParams,
                        isApplyingParams = uiState.isApplyingParams,
                        onDismiss = viewModel::hideSettings,
                        onModelVariantSelected = viewModel::selectModelVariant,
                        onHardwarePreferenceChanged = viewModel::updateHardwarePreference,
                        onGenerationParamsChanged = viewModel::updateGenerationParams,
                        onApplyGenerationParams = viewModel::applyGenerationParams,
                        onDownloadModel = viewModel::startModelDownload
                    )
                }
            }
        }
    }
}