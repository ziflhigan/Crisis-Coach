package com.cautious5.crisis_coach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Crisis Coach Color System
 */

// Primary - Modern Blue (Trust & Reliability)
val md_theme_light_primary = Color(0xFF1565C0)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD4E3FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001C3A)

// Secondary - Emergency Red (Urgent Actions)
val md_theme_light_secondary = Color(0xFFD32F2F)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFFDAD4)
val md_theme_light_onSecondaryContainer = Color(0xFF410001)

// Tertiary - Teal (Medical/Health)
val md_theme_light_tertiary = Color(0xFF00897B)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFB2DFDB)
val md_theme_light_onTertiaryContainer = Color(0xFF00201D)

// Error
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)

// Background & Surface
val md_theme_light_background = Color(0xFFFAFAFA)
val md_theme_light_onBackground = Color(0xFF1C1C1E)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF1C1C1E)
val md_theme_light_surfaceVariant = Color(0xFFF5F5F5)
val md_theme_light_onSurfaceVariant = Color(0xFF49454E)

// Outline
val md_theme_light_outline = Color(0xFFE0E0E0)
val md_theme_light_outlineVariant = Color(0xFFF0F0F0)
val md_theme_light_inverseOnSurface = Color(0xFFF0F0F0)
val md_theme_light_inverseSurface = Color(0xFF313033)
val md_theme_light_inversePrimary = Color(0xFF9BBCFF)

// Dark Theme
val md_theme_dark_primary = Color(0xFF9BBCFF)
val md_theme_dark_onPrimary = Color(0xFF003060)
val md_theme_dark_primaryContainer = Color(0xFF004788)
val md_theme_dark_onPrimaryContainer = Color(0xFFD4E3FF)

val md_theme_dark_secondary = Color(0xFFFFB4A8)
val md_theme_dark_onSecondary = Color(0xFF690002)
val md_theme_dark_secondaryContainer = Color(0xFF930006)
val md_theme_dark_onSecondaryContainer = Color(0xFFFFDAD4)

val md_theme_dark_tertiary = Color(0xFF4DB6AC)
val md_theme_dark_onTertiary = Color(0xFF003733)
val md_theme_dark_tertiaryContainer = Color(0xFF00504A)
val md_theme_dark_onTertiaryContainer = Color(0xFFB2DFDB)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = Color(0xFF121212)
val md_theme_dark_onBackground = Color(0xFFE3E3E3)
val md_theme_dark_surface = Color(0xFF1E1E1E)
val md_theme_dark_onSurface = Color(0xFFE3E3E3)
val md_theme_dark_surfaceVariant = Color(0xFF2A2A2A)
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4CF)

val md_theme_dark_outline = Color(0xFF3C3C3C)
val md_theme_dark_outlineVariant = Color(0xFF2C2C2C)
val md_theme_dark_inverseOnSurface = Color(0xFF1C1C1E)
val md_theme_dark_inverseSurface = Color(0xFFE3E3E3)
val md_theme_dark_inversePrimary = Color(0xFF1565C0)

// Simplified Semantic Colors
object SemanticColors {
    // Status Colors
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFA726)
    val Info = Color(0xFF29B6F6)
    val Error = Color(0xFFEF5350)

    // Priority Levels (Simplified)
    val Critical = Color(0xFFD32F2F)
    val High = Color(0xFFFF6B00)
    val Medium = Color(0xFFFFA726)
    val Low = Color(0xFF66BB6A)

    // Voice States
    val VoiceActive = Color(0xFF2196F3)
    val VoiceListening = Color(0xFF1976D2)
    val VoiceProcessing = Color(0xFF1565C0)
}

// Surface Tints for Cards
object SurfaceTints {
    val Blue = Color(0xFFE3F2FD)
    val Red = Color(0xFFFFEBEE)
    val Green = Color(0xFFE8F5E9)
    val Orange = Color(0xFFFFF3E0)
    val Purple = Color(0xFFF3E5F5)
    val Teal = Color(0xFFE0F2F1)

    // Dark mode tints
    val BlueDark = Color(0xFF0D47A1).copy(alpha = 0.12f)
    val RedDark = Color(0xFFB71C1C).copy(alpha = 0.12f)
    val GreenDark = Color(0xFF1B5E20).copy(alpha = 0.12f)
    val OrangeDark = Color(0xFFE65100).copy(alpha = 0.12f)
    val PurpleDark = Color(0xFF4A148C).copy(alpha = 0.12f)
    val TealDark = Color(0xFF004D40).copy(alpha = 0.12f)
}