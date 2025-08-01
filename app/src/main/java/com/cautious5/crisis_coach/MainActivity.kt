package com.cautious5.crisis_coach

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cautious5.crisis_coach.model.ai.GenerationParams
import com.cautious5.crisis_coach.model.ai.HardwarePreference
import com.cautious5.crisis_coach.model.ai.ModelConfig
import com.cautious5.crisis_coach.model.ai.ModelVariant
import com.cautious5.crisis_coach.ui.AuthWebViewActivity
import com.cautious5.crisis_coach.ui.navigation.AppNavigation
import com.cautious5.crisis_coach.ui.theme.CrisisCoachTheme
import com.cautious5.crisis_coach.utils.Constants.ErrorMessages
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.DeviceCapabilityChecker
import com.cautious5.crisis_coach.utils.LocalPermissionManager
import com.cautious5.crisis_coach.utils.ModelDownloader
import com.cautious5.crisis_coach.utils.PermissionManager
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = LogTags.MAIN_ACTIVITY
    }

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        val authActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AuthWebViewActivity.RESULT_AUTH_SUCCESS) {
                Log.d(TAG, "Auth flow completed successfully. Retrying download.")
                mainViewModel.retryDownloadAfterAuth()
            } else {
                Log.d(TAG, "Auth flow was cancelled or failed.")
            }
        }

        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        Log.d(TAG, "MainActivity onCreate started")

        splashScreen.setKeepOnScreenCondition {
            mainViewModel.uiState.value.initState == MainViewModel.InitializationState.LOADING
        }

        enableEdgeToEdge()

        setContent {
            CrisisCoachTheme {
                CompositionLocalProvider(LocalPermissionManager provides permissionManager) {
                    val uiState by mainViewModel.uiState.collectAsState()
                    val context = LocalContext.current

                    val modelToAuth by mainViewModel.triggerAuthFlow.collectAsState()
                    LaunchedEffect(modelToAuth) {
                        modelToAuth?.let { variant ->
                            val intent = AuthWebViewActivity.newIntent(
                                context = context,
                                modelUrl = "https://huggingface.co/login?next=" +
                                        Uri.encode("/${variant.huggingFaceRepo}"),
                                modelName = variant.displayName
                            )
                            authActivityLauncher.launch(intent)
                            mainViewModel.onAuthFlowTriggered() // Reset the trigger
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (uiState.initState) {
                            MainViewModel.InitializationState.LOADING -> {
                                LoadingScreen(deviceCapability = uiState.deviceCapability)
                            }

                            MainViewModel.InitializationState.ERROR -> {
                                ErrorScreen(
                                    title = uiState.errorTitle ?: "Error",
                                    message = uiState.errorMessage ?: ErrorMessages.UNKNOWN_ERROR,
                                    onRetry = { mainViewModel.initializeApp() },
                                    onDownload = if (uiState.needsModelDownload) {
                                        { modelVariant ->
                                            mainViewModel.startModelDownload(
                                                modelVariant
                                            )
                                        }
                                    } else null,
                                    onCancelDownload = { mainViewModel.cancelDownload() },
                                    downloadState = uiState.downloadState
                                )
                            }

                            MainViewModel.InitializationState.SUCCESS -> {
                                AppNavigation(
                                    onSettingsClick = { mainViewModel.showSettings() }
                                )
                            }
                        }

                        // Settings Dialog - Always available
                        if (uiState.showSettingsDialog) {
                            SettingsDialog(
                                currentConfig = uiState.currentModelConfig,
                                availableVariants = ModelVariant.entries,
                                downloadState = uiState.downloadState,
                                modelDownloadStatus = uiState.modelDownloadStatus,
                                modelVariantForDownload = uiState.modelVariantForDownload, // Pass the state
                                onDismiss = { mainViewModel.hideSettings() },
                                onModelVariantSelected = { variant ->
                                    mainViewModel.selectModelVariant(variant)
                                },
                                onHardwarePreferenceChanged = { pref ->
                                    mainViewModel.updateHardwarePreference(pref)
                                },
                                onGenerationParamsChanged = { params ->
                                    mainViewModel.updateGenerationParams(params)
                                },
                                onDownloadModel = { variant ->
                                    mainViewModel.startModelDownload(variant)
                                }
                            )
                        }
                    }

                    if (uiState.needsTokenInput) {
                        PasteTokenDialog(
                            onDismiss = { /* Handle dismiss if needed */ },
                            onConfirm = { token ->
                                mainViewModel.saveTokenAndRetryDownload(token)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteTokenDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Access Token") },
        text = {
            Column {
                Text("Please paste your Hugging Face User Access Token with 'read' permissions. This is a one-time setup.")
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("hf_...") },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(token) }, enabled = token.isNotBlank()) {
                Text("Confirm")
            }
        }
    )
}

@Composable
private fun LoadingScreen(deviceCapability: DeviceCapabilityChecker.DeviceCapability?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
            Text(
                text = "Initializing Crisis Coach",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Loading AI model and emergency database...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            if (deviceCapability != null) {
                Card(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Optimizing for your device",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${deviceCapability.deviceInfo.manufacturer} ${deviceCapability.deviceInfo.model}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Model: ${deviceCapability.recommendedModelVariant.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDownload: ((modelVariant: ModelVariant) -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    downloadState: ModelDownloader.DownloadState? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            val isDownloadActive = downloadState is ModelDownloader.DownloadState.Preparing ||
                    downloadState is ModelDownloader.DownloadState.InProgress

            // Download progress UI
            when (downloadState) {
                is ModelDownloader.DownloadState.Preparing -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Text("Preparing download...", modifier = Modifier.padding(top = 8.dp))
                }

                is ModelDownloader.DownloadState.InProgress -> {
                    val downloadedMb = downloadState.bytesDownloaded / (1024 * 1024)
                    val totalMb = downloadState.totalSize / (1024 * 1024)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    Text(
                        text = "${downloadState.progress}% - ${downloadedMb}MB / ${totalMb}MB",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is ModelDownloader.DownloadState.Failed -> {
                    Text(
                        text = "Download failed: ${downloadState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is ModelDownloader.DownloadState.AuthRequired -> {
                    Text(
                        text = "Please sign in to Hugging Face and accept the Gemma license to download the model.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                else -> {} // Idle, Completed, or null state
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Retry button is always available, but enabled based on download state.
                Button(
                    onClick = onRetry,
                    enabled = !isDownloadActive
                ) {
                    Text("Retry")
                }

                // Show cancel button only when a download is active
                if (isDownloadActive) {
                    Button(
                        onClick = { onCancelDownload?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Download")
                    }
                }
            }

            // Download buttons for specific models (only show if needed)
            if (onDownload != null && !isDownloadActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(
                        "Please select a model to download:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    // Button for the Standard E2B model
                    Button(onClick = { onDownload(ModelVariant.GEMMA_3N_E2B) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Download Standard Model")
                            Text(
                                "~2GB | Recommended for most devices",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    // Button for the High-Quality E4B model
                    Button(onClick = { onDownload(ModelVariant.GEMMA_3N_E4B) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Download High-Quality Model")
                            Text(
                                "~3GB | For high-end devices (6GB+ RAM)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Retry button is always available
                Button(
                    onClick = onRetry,
                    enabled = downloadState !is ModelDownloader.DownloadState.InProgress
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentConfig: ModelConfig?,
    availableVariants: List<ModelVariant>,
    downloadState: ModelDownloader.DownloadState?,
    modelDownloadStatus: Map<ModelVariant, Boolean>,
    modelVariantForDownload: ModelVariant?,
    onDismiss: () -> Unit,
    onModelVariantSelected: (ModelVariant) -> Unit,
    onHardwarePreferenceChanged: (HardwarePreference) -> Unit,
    onGenerationParamsChanged: (GenerationParams) -> Unit,
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
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Tab Row
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Model") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Hardware") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Generation") }
                    )
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> ModelSettingsTab(
                            currentConfig = currentConfig,
                            availableVariants = availableVariants,
                            downloadState = downloadState,
                            modelDownloadStatus = modelDownloadStatus,
                            modelVariantForDownload = modelVariantForDownload,
                            onModelVariantSelected = onModelVariantSelected,
                            onDownloadModel = onDownloadModel
                        )

                        1 -> HardwareSettingsTab(
                            currentPreference = currentConfig?.hardwarePreference
                                ?: HardwarePreference.AUTO,
                            onHardwarePreferenceChanged = onHardwarePreferenceChanged
                        )

                        2 -> GenerationSettingsTab(
                            currentConfig = currentConfig,
                            onGenerationParamsChanged = onGenerationParamsChanged
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsTab(
    currentConfig: ModelConfig?,
    availableVariants: List<ModelVariant>,
    downloadState: ModelDownloader.DownloadState?,
    modelDownloadStatus: Map<ModelVariant, Boolean>,
    modelVariantForDownload: ModelVariant?,
    onModelVariantSelected: (ModelVariant) -> Unit,
    onDownloadModel: (ModelVariant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Select AI Model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        availableVariants.forEach { variant ->
            val isSelected = currentConfig?.variant == variant
            val isDownloaded = modelDownloadStatus[variant] ?: false

            val isDownloadingThisVariant =
                downloadState is ModelDownloader.DownloadState.InProgress &&
                        variant == modelVariantForDownload

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // Action is only possible if not currently downloading this variant
                    if (!isDownloadingThisVariant) {
                        if (isDownloaded) {
                            onModelVariantSelected(variant) // Select if downloaded
                        } else {
                            onDownloadModel(variant) // Download if not
                        }
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected)
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else
                    null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                variant.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "RAM Usage: ~${variant.approximateRamUsageMB}MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isDownloadingThisVariant) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (isDownloaded) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            TextButton(onClick = { onDownloadModel(variant) }) {
                                Text("Download")
                            }
                        }
                    }

                    // Show progress bar only for the variant being downloaded
                    if (isDownloadingThisVariant && downloadState is ModelDownloader.DownloadState.InProgress) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val downloadedMB = downloadState.bytesDownloaded / (1024 * 1024)
                        val totalMB = downloadState.totalSize / (1024 * 1024)
                        if (totalMB > 0) {
                            Text(
                                "${downloadState.progress}% - ${downloadedMB}MB / ${totalMB}MB",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareSettingsTab(
    currentPreference: HardwarePreference,
    onHardwarePreferenceChanged: (HardwarePreference) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Hardware Acceleration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "Choose how the AI model runs on your device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HardwarePreference.entries.forEach { preference ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onHardwarePreferenceChanged(preference) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentPreference == preference,
                    onClick = { onHardwarePreferenceChanged(preference) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        preference.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        preference.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "GPU acceleration can significantly speed up AI processing but may use more battery. CPU-only mode is more power efficient.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun GenerationSettingsTab(
    currentConfig: ModelConfig?,
    onGenerationParamsChanged: (GenerationParams) -> Unit
) {
    var temperature by remember { mutableFloatStateOf(currentConfig?.temperature ?: 0.7f) }
    var topK by remember { mutableIntStateOf(currentConfig?.topK ?: 64) }
    var topP by remember { mutableFloatStateOf(currentConfig?.topP ?: 0.95f) }
    var maxTokens by remember { mutableIntStateOf(currentConfig?.maxOutputTokens ?: 512) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Temperature
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Temperature",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    String.format(Locale.getDefault(), "%.2f", temperature),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Controls randomness. Lower = more focused, Higher = more creative",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = temperature,
                onValueChange = {
                    temperature = it
                    onGenerationParamsChanged(
                        GenerationParams(temperature, topK, topP, maxTokens)
                    )
                },
                valueRange = 0f..1f,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Top-K
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Top-K",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    topK.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Number of top tokens to consider",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = topK.toFloat(),
                onValueChange = {
                    topK = it.toInt()
                    onGenerationParamsChanged(
                        GenerationParams(temperature, topK, topP, maxTokens)
                    )
                },
                valueRange = 1f..100f,
                steps = 99,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Top-P
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Top-P",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    String.format(Locale.getDefault(), "%.2f", topP),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Cumulative probability cutoff",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = topP,
                onValueChange = {
                    topP = it
                    onGenerationParamsChanged(
                        GenerationParams(temperature, topK, topP, maxTokens)
                    )
                },
                valueRange = 0f..1f,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Max Output Tokens
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Max Output Tokens",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    maxTokens.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Maximum length of generated response",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = {
                    maxTokens = it.toInt()
                    onGenerationParamsChanged(
                        GenerationParams(temperature, topK, topP, maxTokens)
                    )
                },
                valueRange = 50f..2048f,
                steps = 39,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Reset button
        TextButton(
            onClick = {
                temperature = 0.7f
                topK = 64
                topP = 0.95f
                maxTokens = 512
                onGenerationParamsChanged(
                    GenerationParams(temperature, topK, topP, maxTokens)
                )
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset to Defaults")
        }
    }
}