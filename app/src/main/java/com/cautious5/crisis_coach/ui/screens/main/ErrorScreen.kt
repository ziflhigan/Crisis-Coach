package com.cautious5.crisis_coach.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cautious5.crisis_coach.model.ai.ModelVariant
import com.cautious5.crisis_coach.ui.components.ActionCard
import com.cautious5.crisis_coach.ui.components.LoadingIndicator
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.utils.ModelDownloader

/**
 * A full-screen composable for displaying critical initialization or download errors.
 * Provides clear user actions like retrying or downloading required models.
 */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    downloadState: ModelDownloader.DownloadState?,
    onRetry: () -> Unit,
    onDownload: ((modelVariant: ModelVariant) -> Unit)?,
    onCancelDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ResultCard(
            title = title,
            icon = Icons.Default.Error,
            iconTint = MaterialTheme.colorScheme.error,
            surfaceTint = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Show download status or action buttons, but not both.
        val isDownloadActive = downloadState is ModelDownloader.DownloadState.InProgress
        if (isDownloadActive && downloadState != null) {
            DownloadProgressSection(state = downloadState, onCancel = onCancelDownload)
        } else {
            // Main action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Retry")
                }
            }
        }

        // Show download options if the error requires it (e.g., model missing)
        if (onDownload != null && !isDownloadActive) {
            Spacer(modifier = Modifier.height(24.dp))
            DownloadOptionsSection(onDownload = onDownload)
        }
    }
}

@Composable
private fun DownloadOptionsSection(onDownload: (ModelVariant) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Model Download Required",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            "Please select a model to download to enable AI features.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Standard Model
        ActionCard(
            title = "Standard Model (~3.1GB)",
            subtitle = "Recommended for most devices. Good balance of speed and quality.",
            icon = Icons.Default.Download,
            onClick = { onDownload(ModelVariant.GEMMA_3N_E2B) },
            containerColor = MaterialTheme.colorScheme.primary
        )

        // High Quality Model
        ActionCard(
            title = "High-Quality Model (~4.4GB)",
            subtitle = "For high-end devices (6GB+ RAM). Slower but more accurate.",
            icon = Icons.Default.WorkspacePremium,
            onClick = { onDownload(ModelVariant.GEMMA_3N_E4B) },
            containerColor = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun DownloadProgressSection(
    state: ModelDownloader.DownloadState,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state is ModelDownloader.DownloadState.InProgress) {
            val downloadedMb = state.bytesDownloaded / (1024 * 1024)
            val totalMb = state.totalSize / (1024 * 1024)

            Text(
                "Downloading Model...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Text(
                text = "${state.progress}% - ${downloadedMb}MB / ${totalMb}MB",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text("Cancel")
            }
        } else if (state is ModelDownloader.DownloadState.Preparing) {
            LoadingIndicator("Preparing download...")
        } else if (state is ModelDownloader.DownloadState.Failed) {
            ResultCard(
                title = "Download Failed",
                icon = Icons.Default.CloudOff,
                iconTint = MaterialTheme.colorScheme.error,
                surfaceTint = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}