package com.cautious5.crisis_coach.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cautious5.crisis_coach.model.ai.GenerationParams
import com.cautious5.crisis_coach.model.ai.HardwarePreference
import com.cautious5.crisis_coach.model.ai.ModelConfig
import com.cautious5.crisis_coach.model.ai.ModelVariant
import com.cautious5.crisis_coach.utils.DeviceCapabilityChecker
import com.cautious5.crisis_coach.utils.ModelDownloader
import java.util.*

/**
 * Converts a ModelConfig object to a GenerationParams object.
 */
fun ModelConfig.toGenerationParams(): GenerationParams {
    return GenerationParams(
        temperature = this.temperature,
        topK = this.topK,
        topP = this.topP,
        maxOutputTokens = this.maxOutputTokens
    )
}

@Composable
fun SettingsDialog(
    currentConfig: ModelConfig,
    availableVariants: List<ModelVariant>,
    downloadState: ModelDownloader.DownloadState?,
    modelDownloadStatus: Map<ModelVariant, Boolean>,
    modelVariantForDownload: ModelVariant?,
    pendingGenerationParams: GenerationParams?,
    isApplyingParams: Boolean,
    onDismiss: () -> Unit,
    onModelVariantSelected: (ModelVariant) -> Unit,
    onHardwarePreferenceChanged: (HardwarePreference) -> Unit,
    onGenerationParamsChanged: (GenerationParams) -> Unit,
    onApplyGenerationParams: () -> Unit,
    onDownloadModel: (ModelVariant) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close settings")
                    }
                }

                // Tab Row
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Model") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Hardware") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Generation") })
                }

                // Content area with padding
                Box(modifier = Modifier.padding(16.dp)) {
                    when (selectedTab) {
                        0 -> ModelSettingsTab(
                            currentVariant = currentConfig.variant,
                            availableVariants = availableVariants,
                            downloadState = downloadState,
                            modelDownloadStatus = modelDownloadStatus,
                            modelVariantForDownload = modelVariantForDownload,
                            onModelVariantSelected = onModelVariantSelected,
                            onDownloadModel = onDownloadModel
                        )
                        1 -> HardwareSettingsTab(
                            currentPreference = currentConfig.hardwarePreference,
                            onHardwarePreferenceChanged = onHardwarePreferenceChanged
                        )
                        2 -> GenerationSettingsTab(
                            currentConfig = currentConfig,
                            pendingGenerationParams = pendingGenerationParams,
                            isApplyingParams = isApplyingParams,
                            onGenerationParamsChanged = onGenerationParamsChanged,
                            onApplyGenerationParams = onApplyGenerationParams
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsTab(
    currentVariant: ModelVariant,
    availableVariants: List<ModelVariant>,
    downloadState: ModelDownloader.DownloadState?,
    modelDownloadStatus: Map<ModelVariant, Boolean>,
    modelVariantForDownload: ModelVariant?,
    onModelVariantSelected: (ModelVariant) -> Unit,
    onDownloadModel: (ModelVariant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        availableVariants.forEach { variant ->
            val isSelected = currentVariant == variant
            val isDownloaded = modelDownloadStatus[variant] ?: false
            val isDownloadingThis = downloadState is ModelDownloader.DownloadState.InProgress && modelVariantForDownload == variant

            ModelVariantItem(
                variant = variant,
                isSelected = isSelected,
                isDownloaded = isDownloaded,
                isDownloading = isDownloadingThis,
                downloadProgressState = if (isDownloadingThis) downloadState as ModelDownloader.DownloadState.InProgress else null,
                onClick = {
                    if (isDownloaded) onModelVariantSelected(variant) else onDownloadModel(variant)
                }
            )
        }
    }
}

@Composable
private fun ModelVariantItem(
    variant: ModelVariant,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgressState: ModelDownloader.DownloadState.InProgress?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = !isDownloading,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(variant.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        // FIX 3b: Changed `paramSize` to `effectiveParams`
                        "RAM: ~${variant.approximateRamUsageMB}MB | Params: ${variant.effectiveParams}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    isDownloading -> CircularProgressIndicator(Modifier.size(24.dp))
                    isDownloaded && isSelected -> Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                    isDownloaded && !isSelected -> Icon(Icons.Default.RadioButtonUnchecked, "Selectable")
                    else -> Button(onClick = onClick) { Text("Download") }
                }
            }
            if (downloadProgressState != null) {
                val downloadedMb = downloadProgressState.bytesDownloaded / (1024 * 1024)
                val totalMb = downloadProgressState.totalSize / (1024 * 1024)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadProgressState.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${downloadProgressState.progress}% (${downloadedMb}MB / ${totalMb}MB)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
private fun HardwareSettingsTab(
    currentPreference: HardwarePreference,
    onHardwarePreferenceChanged: (HardwarePreference) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HardwarePreference.entries.forEach { preference ->
            val isEnabled = DeviceCapabilityChecker.isSupported(context, preference)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = isEnabled) { onHardwarePreferenceChanged(preference) }
                    .padding(vertical = 8.dp)
                    .alpha(if (isEnabled) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentPreference == preference,
                    onClick = { onHardwarePreferenceChanged(preference) },
                    enabled = isEnabled
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(preference.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(preference.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GenerationSettingsTab(
    currentConfig: ModelConfig,
    pendingGenerationParams: GenerationParams?,
    isApplyingParams: Boolean,
    onGenerationParamsChanged: (GenerationParams) -> Unit,
    onApplyGenerationParams: () -> Unit
) {
    // FIX 3c: Use the new extension function `toGenerationParams()`
    val activeParams = pendingGenerationParams ?: currentConfig.toGenerationParams()
    var temperature by remember(activeParams) { mutableFloatStateOf(activeParams.temperature) }
    var topK by remember(activeParams) { mutableIntStateOf(activeParams.topK) }
    var topP by remember(activeParams) { mutableFloatStateOf(activeParams.topP) }
    var maxTokens by remember(activeParams) { mutableIntStateOf(activeParams.maxOutputTokens) }

    val hasChanges = pendingGenerationParams != null

    LaunchedEffect(temperature, topK, topP, maxTokens) {
        onGenerationParamsChanged(GenerationParams(temperature, topK, topP, maxTokens))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ParameterSlider(
            label = "Temperature",
            value = temperature,
            onValueChange = { temperature = it },
            range = 0f..2f,
            steps = 19,
            valueLabel = "%.2f".format(Locale.US, temperature),
            enabled = !isApplyingParams
        )
        ParameterSlider(
            label = "Top-K",
            value = topK.toFloat(),
            onValueChange = { topK = it.toInt() },
            range = 1f..100f,
            steps = 98,
            valueLabel = topK.toString(),
            enabled = !isApplyingParams
        )
        ParameterSlider(
            label = "Top-P",
            value = topP,
            onValueChange = { topP = it },
            range = 0f..1f,
            steps = 19,
            valueLabel = "%.2f".format(Locale.US, topP),
            enabled = !isApplyingParams
        )
        ParameterSlider(
            label = "Max Output Tokens",
            value = maxTokens.toFloat(),
            onValueChange = { maxTokens = it.toInt() },
            range = 128f..4096f,
            steps = 30,
            valueLabel = maxTokens.toString(),
            enabled = !isApplyingParams
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isApplyingParams) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Button(
                onClick = onApplyGenerationParams,
                enabled = hasChanges && !isApplyingParams
            ) {
                Text("Apply Changes")
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    enabled: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}