package com.cautious5.crisis_coach.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cautious5.crisis_coach.ui.theme.SemanticColors

/**
 * Voice input button component with animated states
 * Supports press-and-hold for voice input with visual feedback
 */
@Composable
fun VoiceInputButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: VoiceButtonSize = VoiceButtonSize.LARGE
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Animation states
    val scale by animateFloatAsState(
        targetValue = if (isListening) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // Pulsing animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Pulse effect when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(size.buttonSize + 24.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        SemanticColors.VoiceListening.copy(alpha = pulseAlpha)
                    )
            )
        }

        // Main button
        FloatingActionButton(
            onClick = {
                if (enabled) {
                    if (isListening) {
                        onStopListening()
                    } else {
                        onStartListening()
                    }
                    hapticFeedback.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                }
            },
            modifier = Modifier
                .size(size.buttonSize)
                .scale(scale),
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant
                isListening -> SemanticColors.VoiceListening
                else -> MaterialTheme.colorScheme.primary
            },
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> Color.White
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (isListening) 8.dp else 4.dp
            )
        ) {
            Icon(
                imageVector = when {
                    !enabled -> Icons.Default.MicOff
                    isListening -> Icons.Default.Stop
                    else -> Icons.Default.Mic
                },
                contentDescription = when {
                    !enabled -> "Voice input disabled"
                    isListening -> "Stop listening"
                    else -> "Start voice input"
                },
                modifier = Modifier.size(size.iconSize)
            )
        }
    }
}

/**
 * Centered voice input with status text
 */
@Composable
fun CenteredVoiceInput(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusText: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        VoiceInputButton(
            isListening = isListening,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            enabled = enabled,
            size = VoiceButtonSize.LARGE
        )

        // Status text
        Text(
            text = statusText ?: when {
                !enabled -> "Voice input unavailable"
                isListening -> "Listening..."
                else -> "Tap to speak"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                isListening -> SemanticColors.VoiceListening
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isListening) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Voice input button specifically for search/query input
 */
@Composable
fun CompactVoiceButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = {
            if (enabled) {
                if (isListening) onStopListening() else onStartListening()
            }
        },
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = if (isListening) SemanticColors.VoiceListening else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop" else "Voice input"
        )
    }
}

enum class VoiceButtonSize(val buttonSize: Dp, val iconSize: Dp) {
    SMALL(56.dp, 24.dp),
    MEDIUM(72.dp, 32.dp),
    LARGE(80.dp, 36.dp)
}