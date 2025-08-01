package com.cautious5.crisis_coach.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.ai.ModelState
import com.cautious5.crisis_coach.ui.theme.*
import com.cautious5.crisis_coach.utils.Constants

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
 * Dashboard screen - main hub for Crisis Coach app
 * Provides immediate access to critical functions and shows recent activity
 */
@Composable
fun DashboardScreen(
    onNavigateToTranslate: () -> Unit,
    onNavigateToImageTriage: () -> Unit,
    onNavigateToKnowledge: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()){
        DashboardScreenContent(
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

        if (uiState.isModelInitializing) {
            LoadingOverlay(
                modelState = uiState.modelState,
                progress = uiState.loadingProgress
            )
        }
    }
}

@Composable
private fun DashboardScreenContent(
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header with Status
        DashboardHeader(
            modelState = uiState.modelState,
            isOfflineMode = uiState.isOfflineMode
        )

        // Quick Actions Grid
        QuickActionsSection(
            onTranslateClick = onNavigateToTranslate,
            onImageAnalysisClick = onNavigateToImageTriage,
            onKnowledgeClick = onNavigateToKnowledge,
            isModelReady = uiState.modelState == ModelState.READY
        )

        // System Status Card
        SystemStatusCard(
            modelState = uiState.modelState,
            deviceInfo = uiState.deviceInfo,
            memoryUsage = uiState.memoryUsage
        )

        // Recent Activity Feed
        RecentActivitySection(
            activities = uiState.recentActivities,
            onActivityClick = onActivityClick
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Icon and Title
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = Constants.APP_NAME,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = Constants.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Emergency AI Assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            // Status Indicators Row
            StatusIndicatorsRow(
                modelState = modelState,
                isOfflineMode = isOfflineMode
            )
        }
    }
}

@Composable
private fun StatusIndicatorsRow(
    modelState: ModelState,
    isOfflineMode: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Model Status
        ModelStatusChip(modelState = modelState)

        // Offline Mode Indicator
        if (isOfflineMode) {
            OfflineModeChip()
        }
    }
}

@Composable
private fun ModelStatusChip(modelState: ModelState) {
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

    StatusChip(
        icon = icon,
        text = text,
        color = color
    )
}

@Composable
private fun OfflineModeChip() {
    StatusChip(
        icon = Icons.Default.WifiOff,
        text = "Offline Ready",
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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

            // Primary Actions Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Voice Translation
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Voice Translation",
                        subtitle = "Translate speech instantly",
                        icon = Icons.Default.Mic,
                        onClick = onTranslateClick,
                        enabled = isModelReady,
                        containerColor = MaterialTheme.colorScheme.voiceInputButton
                    )

                    // Image Analysis
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Analyze Image",
                        subtitle = "Medical & structural analysis",
                        icon = Icons.Default.CameraAlt,
                        onClick = onImageAnalysisClick,
                        enabled = isModelReady,
                        containerColor = MaterialTheme.colorScheme.cameraButton
                    )
                }

                // Knowledge Base (Full width)
                QuickActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Emergency Guide",
                    subtitle = "Search emergency protocols and first aid procedures",
                    icon = Icons.Default.MenuBook,
                    onClick = onKnowledgeClick,
                    enabled = isModelReady,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    isFullWidth = true
                )
            }

            // Status Message
            if (!isModelReady) {
                ReadinessMessage()
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    isFullWidth: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(if (isFullWidth) 80.dp else 100.dp),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else MaterialTheme.colorScheme.statusDisabled,
            disabledContainerColor = MaterialTheme.colorScheme.statusDisabled
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (enabled) 2.dp else 0.dp
        )
    ) {
        if (isFullWidth) {
            // Horizontal layout for full-width cards
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (enabled) 1f else 0.5f
                    )
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = EmergencyTextStyles.ButtonText,
                        color = MaterialTheme.colorScheme.onPrimary.copy(
                            alpha = if (enabled) 1f else 0.5f
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(
                            alpha = if (enabled) 0.9f else 0.4f
                        )
                    )
                }
            }
        } else {
            // Vertical layout for square cards
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (enabled) 1f else 0.5f
                    )
                )
                Text(
                    text = title,
                    style = EmergencyTextStyles.ButtonText,
                    color = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (enabled) 1f else 0.5f
                    ),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (enabled) 0.9f else 0.4f
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReadinessMessage() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.warningCardBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colorScheme.warningButton,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Please wait for AI model to finish loading...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SystemStatusCard(
    modelState: ModelState,
    deviceInfo: String,
    memoryUsage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            SystemStatusGrid(
                modelState = modelState,
                deviceInfo = deviceInfo,
                memoryUsage = memoryUsage
            )
        }
    }
}

@Composable
private fun SystemStatusGrid(
    modelState: ModelState,
    deviceInfo: String,
    memoryUsage: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // AI Model Status
        SystemStatusItem(
            label = "AI Model",
            value = getModelStatusText(modelState),
            icon = Icons.Default.Psychology
        )

        // Device Info
        if (deviceInfo.isNotBlank()) {
            SystemStatusItem(
                label = "Device",
                value = deviceInfo,
                icon = Icons.Default.PhoneAndroid
            )
        }

        // Memory Usage
        if (memoryUsage.isNotBlank()) {
            SystemStatusItem(
                label = "Memory",
                value = memoryUsage,
                icon = Icons.Default.Memory
            )
        }
    }
}

@Composable
private fun SystemStatusItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActivitySectionHeader(activityCount = activities.size)

            if (activities.isEmpty()) {
                EmptyActivityState()
            } else {
                ActivityList(
                    activities = activities,
                    onActivityClick = onActivityClick
                )
            }
        }
    }
}

@Composable
private fun ActivitySectionHeader(activityCount: Int) {
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

        if (activityCount > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "$activityCount items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ActivityList(
    activities: List<ActivityItem>,
    onActivityClick: (ActivityItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.height(300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(activities) { activity ->
            ActivityItemCard(
                activity = activity,
                onClick = { onActivityClick(activity) }
            )
        }
    }
}

@Composable
private fun EmptyActivityState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "No activity",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Text(
            text = "No recent activity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "Your recent analyses and translations will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActivityItemCard(
    activity: ActivityItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Activity Icon
            ActivityIconBadge(
                activity = activity,
                size = 40.dp
            )

            // Activity Content
            ActivityContent(
                activity = activity,
                modifier = Modifier.weight(1f)
            )

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
private fun ActivityIconBadge(
    activity: ActivityItem,
    size: androidx.compose.ui.unit.Dp
) {
    Surface(
        modifier = Modifier.size(size),
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
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
private fun ActivityContent(
    activity: ActivityItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = activity.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// Helper Functions
@Composable
private fun getActivityTypeColor(type: ActivityType): androidx.compose.ui.graphics.Color {
    return when (type) {
        ActivityType.TRANSLATION -> MaterialTheme.colorScheme.translationActive
        ActivityType.MEDICAL_ANALYSIS -> MaterialTheme.colorScheme.emergencyMedium
        ActivityType.STRUCTURAL_ANALYSIS -> MaterialTheme.colorScheme.emergencyHigh
        ActivityType.KNOWLEDGE_QUERY -> MaterialTheme.colorScheme.tertiary
    }
}

private fun getModelStatusText(modelState: ModelState): String {
    return when (modelState) {
        ModelState.READY -> "Ready"
        ModelState.LOADING -> "Loading..."
        ModelState.BUSY -> "Processing"
        ModelState.ERROR -> "Error"
        ModelState.UNINITIALIZED -> "Initializing..."
    }
}

// Preview Parameter Provider
class DashboardUiStateProvider : PreviewParameterProvider<DashboardViewModel.DashboardUiState> {
    override val values = sequenceOf(
        // Ready state with activities
        DashboardViewModel.DashboardUiState(
            modelState = ModelState.READY,
            isOfflineMode = true,
            recentActivities = getSampleActivities(),
            deviceInfo = "Samsung Galaxy S21",
            memoryUsage = "AI Model: 2.1GB"
        ),
        // Loading state
        DashboardViewModel.DashboardUiState(
            modelState = ModelState.LOADING,
            isOfflineMode = true,
            recentActivities = emptyList(),
            deviceInfo = "Pixel 6 Pro",
            memoryUsage = "Loading..."
        ),
        // Error state
        DashboardViewModel.DashboardUiState(
            modelState = ModelState.ERROR,
            isOfflineMode = true,
            recentActivities = emptyList(),
            deviceInfo = "OnePlus 9",
            memoryUsage = "Error loading model"
        )
    )

    private fun getSampleActivities() = listOf(
        ActivityItem(
            id = "1",
            type = ActivityType.TRANSLATION,
            title = "Translation: English to Arabic",
            snippet = "I need help -> أحتاج مساعدة",
            timestamp = "2 min ago",
            icon = Icons.Default.Translate
        ),
        ActivityItem(
            id = "2",
            type = ActivityType.MEDICAL_ANALYSIS,
            title = "Medical Analysis: Laceration",
            snippet = "Assessment: Deep laceration, requires cleaning...",
            timestamp = "5 min ago",
            icon = Icons.Default.LocalHospital
        ),
        ActivityItem(
            id = "3",
            type = ActivityType.KNOWLEDGE_QUERY,
            title = "Emergency Guide: CPR procedure",
            snippet = "How to perform CPR on an adult patient...",
            timestamp = "10 min ago",
            icon = Icons.Default.Help
        )
    )
}

// Preview Functions
@Preview(
    name = "Dashboard Screen",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun DashboardScreenPreview(
    @PreviewParameter(DashboardUiStateProvider::class) uiState: DashboardViewModel.DashboardUiState
) {
    CrisisCoachTheme {
        Surface {
            DashboardScreenContent(
                uiState = uiState,
                onNavigateToTranslate = {},
                onNavigateToImageTriage = {},
                onNavigateToKnowledge = {},
                onActivityClick = {}
            )
        }
    }
}

@Preview(
    name = "Dashboard Screen - Dark",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun DashboardScreenDarkPreview() {
    CrisisCoachTheme(darkTheme = true) {
        Surface {
            DashboardScreenContent(
                uiState = DashboardViewModel.DashboardUiState(
                    modelState = ModelState.READY,
                    isOfflineMode = true,
                    recentActivities = listOf(
                        ActivityItem(
                            id = "1",
                            type = ActivityType.TRANSLATION,
                            title = "Emergency Translation",
                            snippet = "English -> Spanish translation completed",
                            timestamp = "Just now",
                            icon = Icons.Default.Translate
                        )
                    ),
                    deviceInfo = "Pixel 8 Pro",
                    memoryUsage = "AI Model: 3.2GB"
                ),
                onNavigateToTranslate = {},
                onNavigateToImageTriage = {},
                onNavigateToKnowledge = {},
                onActivityClick = {}
            )
        }
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
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Initializing Crisis Coach",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )

            Text(
                text = if (modelState == ModelState.LOADING) "Loading AI model..." else "Please wait...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}