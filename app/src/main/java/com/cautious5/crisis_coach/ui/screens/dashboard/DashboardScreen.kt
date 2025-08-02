package com.cautious5.crisis_coach.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.ai.ModelState
import com.cautious5.crisis_coach.ui.components.*
import com.cautious5.crisis_coach.ui.theme.*

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

/**
 * Dashboard screen - streamlined design focusing on key actions
 */
@Composable
fun DashboardScreen(
    onNavigateToTranslate: () -> Unit,
    onNavigateToImageTriage: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        DashboardContent(
            uiState = uiState,
            onNavigateToTranslate = onNavigateToTranslate,
            onNavigateToImageTriage = onNavigateToImageTriage,
            onNavigateToKnowledge = onNavigateToKnowledge,
            onActivityClick = { activity ->
                when (activity.type) {
                    ActivityType.TRANSLATION -> onNavigateToTranslate()
                    ActivityType.MEDICAL_ANALYSIS,
                    ActivityType.STRUCTURAL_ANALYSIS -> onNavigateToImageTriage()
                    ActivityType.KNOWLEDGE_QUERY -> onNavigateToKnowledge()
                }
            }
        )

        // Loading overlay
        AnimatedVisibility(
            visible = uiState.isModelInitializing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingOverlay(
                modelState = uiState.modelState,
                progress = uiState.loadingProgress
            )
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardViewModel.DashboardUiState,
    onNavigateToTranslate: () -> Unit,
    onNavigateToImageTriage: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    onActivityClick: (ActivityItem) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status section
        ModelStatusSection(
            modelState = uiState.modelState,
            isOfflineMode = uiState.isOfflineMode
        )

        // Quick actions
        QuickActionsSection(
            onTranslateClick = onNavigateToTranslate,
            onImageAnalysisClick = onNavigateToImageTriage,
            onKnowledgeClick = onNavigateToKnowledge,
            isModelReady = uiState.modelState == ModelState.READY
        )

        // System info (compact)
        if (uiState.deviceInfo.isNotBlank() || uiState.memoryUsage.isNotBlank()) {
            SystemInfoSection(
                deviceInfo = uiState.deviceInfo,
                memoryUsage = uiState.memoryUsage
            )
        }

        // Recent activity
        RecentActivitySection(
            activities = uiState.recentActivities,
            onActivityClick = onActivityClick
        )
    }
}

@Composable
private fun ModelStatusSection(
    modelState: ModelState,
    isOfflineMode: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Model status
        val (color, text, icon) = when (modelState) {
            ModelState.READY -> Triple(
                SemanticColors.Success,
                "AI Ready",
                Icons.Default.CheckCircle
            )
            ModelState.LOADING -> Triple(
                SemanticColors.Info,
                "Loading AI...",
                Icons.Default.Sync
            )
            ModelState.BUSY -> Triple(
                SemanticColors.Warning,
                "Processing",
                Icons.Default.Psychology
            )
            ModelState.ERROR -> Triple(
                SemanticColors.Error,
                "AI Error",
                Icons.Default.Error
            )
            ModelState.UNINITIALIZED -> Triple(
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Initializing...",
                Icons.Default.HourglassEmpty
            )
        }

        StatusChip(
            icon = icon,
            text = text,
            color = color
        )

        // Offline mode indicator
        if (isOfflineMode) {
            StatusChip(
                icon = Icons.Default.CloudOff,
                text = "Offline Mode",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    onTranslateClick: () -> Unit,
    onImageAnalysisClick: () -> Unit,
    onKnowledgeClick: () -> Unit,
    isModelReady: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Voice Translation
        ActionCard(
            title = "Voice Translation",
            subtitle = "Translate speech instantly",
            icon = Icons.Default.Mic,
            onClick = onTranslateClick,
            enabled = isModelReady,
            containerColor = MaterialTheme.colorScheme.primary
        )

        // Image Analysis
        ActionCard(
            title = "Image Analysis",
            subtitle = "Medical & structural assessment",
            icon = Icons.Default.CameraAlt,
            onClick = onImageAnalysisClick,
            enabled = isModelReady,
            containerColor = MaterialTheme.colorScheme.secondary
        )

        // Emergency Guide
        ActionCard(
            title = "Emergency Guide",
            subtitle = "Search protocols and procedures",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onKnowledgeClick,
            enabled = isModelReady,
            containerColor = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun SystemInfoSection(
    deviceInfo: String,
    memoryUsage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (deviceInfo.isNotBlank()) {
                InfoRow(
                    label = "Device",
                    value = deviceInfo,
                    icon = Icons.Default.PhoneAndroid
                )
            }

            if (memoryUsage.isNotBlank()) {
                InfoRow(
                    label = "Memory",
                    value = memoryUsage,
                    icon = Icons.Default.Memory
                )
            }
        }
    }
}

@Composable
private fun RecentActivitySection(
    activities: List<ActivityItem>,
    onActivityClick: (ActivityItem) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Recent Activity",
            action = if (activities.isNotEmpty()) {
                {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${activities.size}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else null
        )

        if (activities.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "No recent activity",
                subtitle = "Your recent analyses and translations will appear here"
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activities.forEach { activity ->
                    ActivityCard(
                        activity = activity,
                        onClick = { onActivityClick(activity) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: ActivityItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = getActivityColor(activity.type).copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = activity.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    tint = getActivityColor(activity.type)
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = activity.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = activity.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Chevron
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
private fun getActivityColor(type: ActivityType): Color {
    return when (type) {
        ActivityType.TRANSLATION -> MaterialTheme.colorScheme.primary
        ActivityType.MEDICAL_ANALYSIS -> MaterialTheme.colorScheme.secondary
        ActivityType.STRUCTURAL_ANALYSIS -> SemanticColors.Warning
        ActivityType.KNOWLEDGE_QUERY -> MaterialTheme.colorScheme.tertiary
    }
}

@Composable
private fun LoadingOverlay(
    modelState: ModelState,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Initializing Crisis Coach",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(200.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = when (modelState) {
                        ModelState.LOADING -> "Loading AI model..."
                        else -> "Please wait..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}