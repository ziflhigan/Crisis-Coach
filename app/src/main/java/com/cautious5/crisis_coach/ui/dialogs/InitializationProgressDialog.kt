package com.cautious5.crisis_coach.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cautious5.crisis_coach.MainViewModel

/**
 * A dialog that shows the initialization progress with dynamic content based on the current phase.
 */
@Composable
fun InitializationProgressDialog(
    phase: MainViewModel.InitializationPhase,
    progress: Float,
    statusText: String
) {
    Dialog(
        onDismissRequest = { /* Non-dismissible */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Setting Up Crisis Coach",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                // Current phase with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = getPhaseIcon(phase),
                        contentDescription = null,
                        tint = if (phase == MainViewModel.InitializationPhase.COMPLETED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = getPhaseTitle(phase),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                // Progress percentage and status text
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Phase indicators (step indicators)
                PhaseIndicators(currentPhase = phase)

                // Loading spinner
                if (phase != MainViewModel.InitializationPhase.COMPLETED) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseIndicators(currentPhase: MainViewModel.InitializationPhase) {
    // Updated order: Model check comes before database initialization
    val phases = listOf(
        MainViewModel.InitializationPhase.CHECKING_DEVICE to "Device",
        MainViewModel.InitializationPhase.CHECKING_MODEL to "Model Check",
        MainViewModel.InitializationPhase.INITIALIZING_EMBEDDER to "Embedder",
        MainViewModel.InitializationPhase.INITIALIZING_DATABASE to "Database",
        MainViewModel.InitializationPhase.LOADING_MODEL to "AI Model",
        MainViewModel.InitializationPhase.INITIALIZING_SERVICES to "Services",
        MainViewModel.InitializationPhase.COMPLETED to "Complete"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        phases.forEachIndexed { _, (phase, label) ->
            val isCompleted = currentPhase.ordinal > phase.ordinal
            val isCurrent = currentPhase == phase

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Step indicator
                val indicatorColor = when {
                    isCompleted -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outline
                }

                Card(
                    modifier = Modifier.size(8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = indicatorColor
                    )
                ) {}

                // Label
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = indicatorColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun getPhaseIcon(phase: MainViewModel.InitializationPhase): ImageVector {
    return when (phase) {
        MainViewModel.InitializationPhase.CHECKING_DEVICE -> Icons.Default.Dashboard
        MainViewModel.InitializationPhase.CHECKING_MODEL -> Icons.Default.CloudDownload
        MainViewModel.InitializationPhase.INITIALIZING_EMBEDDER -> Icons.Default.Psychology
        MainViewModel.InitializationPhase.INITIALIZING_DATABASE -> Icons.Default.Storage
        MainViewModel.InitializationPhase.LOADING_MODEL -> Icons.Default.Architecture
        MainViewModel.InitializationPhase.INITIALIZING_SERVICES -> Icons.Default.AccountTree
        MainViewModel.InitializationPhase.COMPLETED -> Icons.Default.CheckCircle
    }
}

private fun getPhaseTitle(phase: MainViewModel.InitializationPhase): String {
    return when (phase) {
        MainViewModel.InitializationPhase.CHECKING_DEVICE -> "Checking Device"
        MainViewModel.InitializationPhase.CHECKING_MODEL -> "Checking AI Model"
        MainViewModel.InitializationPhase.INITIALIZING_EMBEDDER -> "Setting Up AI Embedder"
        MainViewModel.InitializationPhase.INITIALIZING_DATABASE -> "Building Knowledge Base"
        MainViewModel.InitializationPhase.LOADING_MODEL -> "Loading AI Model"
        MainViewModel.InitializationPhase.INITIALIZING_SERVICES -> "Starting Services"
        MainViewModel.InitializationPhase.COMPLETED -> "Setup Complete"
    }
}