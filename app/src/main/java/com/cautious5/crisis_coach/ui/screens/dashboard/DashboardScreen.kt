package com.cautious5.crisis_coach.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.ai.ModelState
import com.cautious5.crisis_coach.ui.theme.*
import com.cautious5.crisis_coach.utils.Constants

/**
 * Dashboard screen - main hub for Crisis Coach app
 * Provides immediate access to critical functions and shows recent activity
 */

/**
 * Recent activity item data class
 */
data class ActivityItem(
    val id: String,
    val type: ActivityType,
    val title: String,
    val snippet: String,
    val timestamp: String,
    val icon: ImageVector
)

enum class ActivityType {
    TRANSLATION,
    MEDICAL_ANALYSIS,
    STRUCTURAL_ANALYSIS,
    KNOWLEDGE_QUERY
}

@Composable
fun DashboardScreen(
    onNavigateToTranslate: () -> Unit,
    onNavigateToImageTriage: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        DashboardHeader(
            modelState = uiState.modelState,
            isOfflineMode = uiState.isOfflineMode
        )

        // Primary Action Area
        PrimaryActionArea(
            onTranslateClick = onNavigateToTranslate,
            onImageAnalysisClick = onNavigateToImageTriage,
            isModelReady = uiState.modelState == ModelState.READY
        )

        // Recent Activity Feed
        RecentActivitySection(
            activities = uiState.recentActivities,
            onActivityClick = { activity ->
                when (activity.type) {
                    ActivityType.TRANSLATION -> onNavigateToTranslate()
                    ActivityType.MEDICAL_ANALYSIS,
                    ActivityType.STRUCTURAL_ANALYSIS -> onNavigateToImageTriage()
                    ActivityType.KNOWLEDGE_QUERY -> onNavigateToKnowledge()
                }
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    modelState: ModelState,
    isOfflineMode: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Constants.APP_NAME,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Model Status Indicator
                ModelStatusIndicator(modelState = modelState)

                // Offline Mode Indicator
                if (isOfflineMode) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Offline Mode",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Offline Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusIndicator(modelState: ModelState) {
    val (color, text, icon) = when (modelState) {
        ModelState.READY -> Triple(
            MaterialTheme.colorScheme.statusOnline,
            "AI Ready",
            Icons.Default.CheckCircle
        )
        ModelState.LOADING -> Triple(
            MaterialTheme.colorScheme.statusLoading,
            "Loading AI...",
            Icons.Default.Sync
        )
        ModelState.BUSY -> Triple(
            MaterialTheme.colorScheme.statusActive,
            "AI Processing",
            Icons.Default.Psychology
        )
        ModelState.ERROR -> Triple(
            MaterialTheme.colorScheme.statusError,
            "AI Error",
            Icons.Default.Error
        )
        ModelState.UNINITIALIZED -> Triple(
            MaterialTheme.colorScheme.statusIdle,
            "Initializing...",
            Icons.Default.HourglassEmpty
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PrimaryActionArea(
    onTranslateClick: () -> Unit,
    onImageAnalysisClick: () -> Unit,
    isModelReady: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Emergency Actions",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Voice Translation Button
                PrimaryActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Voice Translation",
                    subtitle = "Translate speech instantly",
                    icon = Icons.Default.Mic,
                    onClick = onTranslateClick,
                    enabled = isModelReady,
                    containerColor = MaterialTheme.colorScheme.voiceInputButton
                )

                // Image Analysis Button
                PrimaryActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Analyze Image",
                    subtitle = "Medical & structural analysis",
                    icon = Icons.Default.CameraAlt,
                    onClick = onImageAnalysisClick,
                    enabled = isModelReady,
                    containerColor = MaterialTheme.colorScheme.cameraButton
                )
            }

            if (!isModelReady) {
                Text(
                    text = "Please wait for AI model to finish loading...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = MaterialTheme.colorScheme.statusDisabled
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = title,
                style = EmergencyTextStyles.ButtonText,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentActivitySection(
    activities: List<ActivityItem>,
    onActivityClick: (ActivityItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                if (activities.isNotEmpty()) {
                    Text(
                        text = "${activities.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activities.isEmpty()) {
                EmptyActivityState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp) // Limit height for scrollable area
                ) {
                    items(activities) { activity ->
                        ActivityItemCard(
                            activity = activity,
                            onClick = { onActivityClick(activity) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyActivityState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "No activity",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No recent activity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your recent analyses and translations will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityItemCard(
    activity: ActivityItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Activity Icon
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = getActivityTypeColor(activity.type)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activity.icon,
                        contentDescription = activity.type.name,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Activity Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = activity.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )

                Text(
                    text = activity.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Navigation Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun getActivityTypeColor(type: ActivityType): androidx.compose.ui.graphics.Color {
    return when (type) {
        ActivityType.TRANSLATION -> MaterialTheme.colorScheme.translationActive
        ActivityType.MEDICAL_ANALYSIS -> MaterialTheme.colorScheme.emergencyMedium
        ActivityType.STRUCTURAL_ANALYSIS -> MaterialTheme.colorScheme.emergencyHigh
        ActivityType.KNOWLEDGE_QUERY -> MaterialTheme.colorScheme.tertiary
    }
}