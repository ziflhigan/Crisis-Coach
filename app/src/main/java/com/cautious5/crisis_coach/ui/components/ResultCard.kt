package com.cautious5.crisis_coach.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cautious5.crisis_coach.ui.theme.*

/**
 * Reusable result card component for displaying analysis results
 * Provides consistent layout and functionality across all result types
 */

@Composable
fun ResultCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.resultCardBackground,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    isExpandable: Boolean = false,
    initiallyExpanded: Boolean = true,
    actionButtons: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isExpandable) {
                            Modifier.clickable { isExpanded = !isExpanded }
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = iconColor.copy(alpha = 0.1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Title and Subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        fontWeight = FontWeight.Bold
                    )

                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expand/Collapse Icon
                if (isExpandable) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Content
            if (!isExpandable || isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                content()

                // Action Buttons
                if (actionButtons != {}) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = actionButtons
                    )
                }
            }
        }
    }
}

/**
 * Success result card variant
 */
@Composable
fun SuccessResultCard(
    title: String,
    icon: ImageVector = Icons.Default.CheckCircle,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = MaterialTheme.colorScheme.successCardBackground,
        iconColor = MaterialTheme.colorScheme.successButton,
        content = content
    )
}

/**
 * Warning result card variant
 */
@Composable
fun WarningResultCard(
    title: String,
    icon: ImageVector = Icons.Default.Warning,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = MaterialTheme.colorScheme.warningCardBackground,
        iconColor = MaterialTheme.colorScheme.warningButton,
        content = content
    )
}

/**
 * Error result card variant
 */
@Composable
fun ErrorResultCard(
    title: String,
    icon: ImageVector = Icons.Default.Error,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = MaterialTheme.colorScheme.errorCardBackground,
        iconColor = MaterialTheme.colorScheme.dangerButton,
        content = content
    )
}

/**
 * Info result card variant
 */
@Composable
fun InfoResultCard(
    title: String,
    icon: ImageVector = Icons.Default.Info,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = MaterialTheme.colorScheme.infoCardBackground,
        iconColor = MaterialTheme.colorScheme.tertiary,
        content = content
    )
}

/**
 * Loading result card variant
 */
@Composable
fun LoadingResultCard(
    title: String,
    icon: ImageVector = Icons.Default.Sync,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = MaterialTheme.colorScheme.loadingCardBackground,
        iconColor = MaterialTheme.colorScheme.statusLoading,
        content = {
            // Progress indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = {
                            progress
                        },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.statusLoading,
                        strokeWidth = 3.dp,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                    )
                }

                Text(
                    text = "Processing...",
                    style = EmergencyTextStyles.LoadingText,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            content()
        }
    )
}

/**
 * Expandable result card with collapsible content
 */
@Composable
fun ExpandableResultCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.resultCardBackground,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = containerColor,
        isExpandable = true,
        initiallyExpanded = initiallyExpanded,
        content = content
    )
}

/**
 * Result card with copy functionality
 */
@Composable
fun CopyableResultCard(
    title: String,
    icon: ImageVector,
    copyableText: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.resultCardBackground,
    content: @Composable ColumnScope.() -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedMessage by remember { mutableStateOf(false) }

    ResultCard(
        title = title,
        icon = icon,
        modifier = modifier,
        subtitle = subtitle,
        containerColor = containerColor,
        actionButtons = {
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(copyableText))
                    showCopiedMessage = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }

            if (showCopiedMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showCopiedMessage = false
                }
            }
        },
        content = {
            content()

            if (showCopiedMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Copied to clipboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.successButton,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

/**
 * Priority level badge component for result cards
 */
@Composable
fun PriorityBadge(
    level: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = level,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
            }
            Text(
                text = level,
                style = EmergencyTextStyles.UrgencyLevel,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Confidence score indicator for result cards
 */
@Composable
fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.highConfidence
        confidence >= 0.6f -> MaterialTheme.colorScheme.mediumConfidence
        confidence >= 0.4f -> MaterialTheme.colorScheme.lowConfidence
        else -> MaterialTheme.colorScheme.veryLowConfidence
    }

    val description = when {
        confidence >= 0.9f -> "Very High"
        confidence >= 0.8f -> "High"
        confidence >= 0.7f -> "Good"
        confidence >= 0.6f -> "Moderate"
        confidence >= 0.5f -> "Low"
        else -> "Very Low"
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress indicator
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
                .background(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    RoundedCornerShape(3.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(confidence)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(3.dp))
            )
        }

        if (showLabel) {
            Text(
                text = "$description (${(confidence * 100).toInt()}%)",
                style = EmergencyTextStyles.ConfidenceText,
                color = color,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = "${(confidence * 100).toInt()}%",
                style = EmergencyTextStyles.ConfidenceText,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Metadata row for result cards (time, source, etc.)
 */
@Composable
fun MetadataRow(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { (label, value) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = value,
                    style = EmergencyTextStyles.TimeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Action list component for recommendations/steps
 */
@Composable
fun ActionList(
    title: String,
    actions: List<String>,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowRight,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        actions.forEach { action ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Action",
                    modifier = Modifier.size(16.dp),
                    tint = iconColor
                )
                Text(
                    text = action,
                    style = EmergencyTextStyles.RecommendationText,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}