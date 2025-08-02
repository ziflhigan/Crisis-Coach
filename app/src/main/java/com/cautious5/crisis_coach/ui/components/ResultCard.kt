package com.cautious5.crisis_coach.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cautious5.crisis_coach.ui.theme.SemanticColors
import com.cautious5.crisis_coach.ui.theme.SurfaceTints

/**
 * Reusable result card component for displaying analysis results
 * Provides consistent layout and functionality across all result types
 */
@Composable
fun ResultCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    surfaceTint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = surfaceTint ?: MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            content()
        }
    }
}

/**
 * Status-specific card variants
 */
@Composable
fun SuccessCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    ResultCard(
        title = title,
        modifier = modifier,
        icon = Icons.Default.CheckCircle,
        iconTint = SemanticColors.Success,
        surfaceTint = if (isSystemInDarkTheme) SurfaceTints.GreenDark else SurfaceTints.Green,
        content = content
    )
}

@Composable
fun ErrorCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    ResultCard(
        title = title,
        modifier = modifier,
        icon = Icons.Default.Error,
        iconTint = SemanticColors.Error,
        surfaceTint = if (isSystemInDarkTheme) SurfaceTints.RedDark else SurfaceTints.Red,
        content = content
    )
}

@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    ResultCard(
        title = title,
        modifier = modifier,
        icon = Icons.Default.Info,
        iconTint = SemanticColors.Info,
        surfaceTint = if (isSystemInDarkTheme) SurfaceTints.BlueDark else SurfaceTints.Blue,
        content = content
    )
}

/**
 * Priority badge component
 */
@Composable
fun PriorityBadge(
    priority: Priority,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (priority) {
        Priority.CRITICAL -> SemanticColors.Critical to "Critical"
        Priority.HIGH -> SemanticColors.High to "High"
        Priority.MEDIUM -> SemanticColors.Medium to "Medium"
        Priority.LOW -> SemanticColors.Low to "Low"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Action button for cards
 */
@Composable
fun CardActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

enum class Priority {
    CRITICAL, HIGH, MEDIUM, LOW
}