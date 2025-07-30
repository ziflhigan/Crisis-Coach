package com.cautious5.crisis_coach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cautious5.crisis_coach.ui.theme.*

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
    size: VoiceButtonSize = VoiceButtonSize.LARGE,
    style: VoiceButtonStyle = VoiceButtonStyle.PRIMARY
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Animation states
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isListening) 0f else 0f, // Can add rotation animation if needed
        animationSpec = tween(durationMillis = 300),
        label = "rotation"
    )

    // Pulsing animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Color animations
    val buttonColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.statusDisabled
            isListening -> MaterialTheme.colorScheme.voiceListening
            else -> when (style) {
                VoiceButtonStyle.PRIMARY -> MaterialTheme.colorScheme.voiceInputButton
                VoiceButtonStyle.SECONDARY -> MaterialTheme.colorScheme.secondary
                VoiceButtonStyle.DANGER -> MaterialTheme.colorScheme.dangerButton
            }
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        // Main voice button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.buttonSize)
                .scale(scale * if (isListening) pulseScale else 1f)
        ) {
            // Background circle with pulse effect
            if (isListening) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.3f)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.3f))
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
                    .fillMaxSize()
                    .pointerInput(enabled, isListening) {
                        if (enabled) {
                            detectTapGestures(
                                onPress = {
                                    if (!isListening) {
                                        onStartListening()
                                        hapticFeedback.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                        )
                                    }
                                    tryAwaitRelease()
                                    if (isListening) {
                                        onStopListening()
                                    }
                                }
                            )
                        }
                    },
                containerColor = buttonColor,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = if (isListening) 8.dp else 6.dp,
                    hoveredElevation = 10.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Icon(
                    imageVector = getVoiceIcon(isListening, enabled),
                    contentDescription = getContentDescription(isListening, enabled),
                    modifier = Modifier.size(size.iconSize),
                    tint = Color.White
                )
            }
        }

        // Status text
        Text(
            text = getStatusText(isListening, enabled),
            style = when (size) {
                VoiceButtonSize.SMALL -> MaterialTheme.typography.labelSmall
                VoiceButtonSize.MEDIUM -> MaterialTheme.typography.labelMedium
                VoiceButtonSize.LARGE -> EmergencyTextStyles.VoiceStatus
            },
            color = buttonColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 120.dp)
        )

        // Instruction text
        if (enabled && !isListening) {
            Text(
                text = "Tap to speak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else if (isListening) {
            Text(
                text = "Listening...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.voiceListening,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Compact voice input button for smaller spaces
 */
@Composable
fun CompactVoiceInputButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current

    val buttonColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.statusDisabled
            isListening -> MaterialTheme.colorScheme.voiceListening
            else -> MaterialTheme.colorScheme.voiceInputButton
        },
        animationSpec = tween(300),
        label = "compactButtonColor"
    )

    IconButton(
        onClick = {
            if (enabled) {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
                hapticFeedback.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                )
            }
        },
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .background(
                buttonColor.copy(alpha = 0.2f),
                CircleShape
            )
    ) {
        Icon(
            imageVector = getVoiceIcon(isListening, enabled),
            contentDescription = getContentDescription(isListening, enabled),
            tint = buttonColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Voice input button specifically for search/query input
 */
@Composable
fun VoiceSearchButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    VoiceInputButton(
        isListening = isListening,
        onStartListening = onStartListening,
        onStopListening = onStopListening,
        modifier = modifier,
        enabled = enabled,
        size = VoiceButtonSize.MEDIUM,
        style = VoiceButtonStyle.SECONDARY
    )
}

// Helper functions and enums

enum class VoiceButtonSize(
    val buttonSize: androidx.compose.ui.unit.Dp,
    val iconSize: androidx.compose.ui.unit.Dp
) {
    SMALL(56.dp, 20.dp),
    MEDIUM(72.dp, 28.dp),
    LARGE(96.dp, 36.dp)
}

enum class VoiceButtonStyle {
    PRIMARY,
    SECONDARY,
    DANGER
}

private fun getVoiceIcon(isListening: Boolean, enabled: Boolean): ImageVector {
    return when {
        !enabled -> Icons.Default.MicOff
        isListening -> Icons.Default.Stop
        else -> Icons.Default.Mic
    }
}

private fun getContentDescription(isListening: Boolean, enabled: Boolean): String {
    return when {
        !enabled -> "Voice input disabled"
        isListening -> "Stop listening"
        else -> "Start voice input"
    }
}

private fun getStatusText(isListening: Boolean, enabled: Boolean): String {
    return when {
        !enabled -> "Unavailable"
        isListening -> "Listening"
        else -> "Hold to Speak"
    }
}

/**
 * Voice waveform animation component (optional enhancement)
 */
@Composable
fun VoiceWaveform(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val animationDelay = index * 100
            val height by infiniteTransition.animateFloat(
                initialValue = 4.dp.value,
                targetValue = if (isActive) 16.dp.value else 4.dp.value,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = animationDelay,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .background(
                        color = if (isActive) color else color.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp)
                    )
            )
        }
    }
}