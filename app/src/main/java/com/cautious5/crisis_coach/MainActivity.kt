package com.cautious5.crisis_coach

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
                        AppNavigation()

                        if (uiState.initState == MainViewModel.InitializationState.ERROR) {
                            ErrorScreen(
                                title = uiState.errorTitle ?: "Error",
                                message = uiState.errorMessage ?: ErrorMessages.UNKNOWN_ERROR,
                                onRetry = { mainViewModel.initializeApp() },
                                onDownload = if (uiState.needsModelDownload) {
                                    { modelVariant -> mainViewModel.startModelDownload(modelVariant) }
                                } else null,
                                onCancelDownload = { mainViewModel.cancelDownload() },
                                downloadState = uiState.downloadState
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (downloadState) {
                    is ModelDownloader.DownloadState.InProgress -> {
                        Button(
                            onClick = { onCancelDownload?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel Download")
                        }
                    }
                    else -> {
                        if (onDownload != null) {
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
        }
    }
}