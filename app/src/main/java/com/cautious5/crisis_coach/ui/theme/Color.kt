package com.cautious5.crisis_coach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette for Crisis Coach app
 * Designed for high contrast and accessibility in emergency situations
 */

// Primary Colors - Emergency Red Theme
val md_theme_light_primary = Color(0xFFD32F2F)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFFFDAD4)
val md_theme_light_onPrimaryContainer = Color(0xFF410001)

// Secondary Colors - Safety Orange
val md_theme_light_secondary = Color(0xFFFF6B00)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFFDCC2)
val md_theme_light_onSecondaryContainer = Color(0xFF2A1800)

// Tertiary Colors - Medical Blue
val md_theme_light_tertiary = Color(0xFF1976D2)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFD0E4FF)
val md_theme_light_onTertiaryContainer = Color(0xFF001D36)

// Error Colors
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)

// Background Colors
val md_theme_light_background = Color(0xFFFFFBFF)
val md_theme_light_onBackground = Color(0xFF201A19)

// Surface Colors
val md_theme_light_surface = Color(0xFFFFFBFF)
val md_theme_light_onSurface = Color(0xFF201A19)
val md_theme_light_surfaceVariant = Color(0xFFF5DDDA)
val md_theme_light_onSurfaceVariant = Color(0xFF534341)

// Outline Colors
val md_theme_light_outline = Color(0xFF857371)
val md_theme_light_inverseOnSurface = Color(0xFFFBEEEC)
val md_theme_light_inverseSurface = Color(0xFF362F2E)
val md_theme_light_inversePrimary = Color(0xFFFFB4A8)

// Dark Theme Colors
val md_theme_dark_primary = Color(0xFFFFB4A8)
val md_theme_dark_onPrimary = Color(0xFF690002)
val md_theme_dark_primaryContainer = Color(0xFF930006)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFDAD4)

val md_theme_dark_secondary = Color(0xFFFFB68A)
val md_theme_dark_onSecondary = Color(0xFF452B00)
val md_theme_dark_secondaryContainer = Color(0xFF633F00)
val md_theme_dark_onSecondaryContainer = Color(0xFFFFDCC2)

val md_theme_dark_tertiary = Color(0xFF9ECAFF)
val md_theme_dark_onTertiary = Color(0xFF003258)
val md_theme_dark_tertiaryContainer = Color(0xFF00497E)
val md_theme_dark_onTertiaryContainer = Color(0xFFD0E4FF)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = Color(0xFF201A19)
val md_theme_dark_onBackground = Color(0xFFEDE0DE)

val md_theme_dark_surface = Color(0xFF201A19)
val md_theme_dark_onSurface = Color(0xFFEDE0DE)
val md_theme_dark_surfaceVariant = Color(0xFF534341)
val md_theme_dark_onSurfaceVariant = Color(0xFFD8C2BE)

val md_theme_dark_outline = Color(0xFFA08C89)
val md_theme_dark_inverseOnSurface = Color(0xFF201A19)
val md_theme_dark_inverseSurface = Color(0xFFEDE0DE)
val md_theme_dark_inversePrimary = Color(0xFFD32F2F)

// Custom Colors for Emergency States
object EmergencyColors {
    // Urgency Levels
    val Critical = Color(0xFFD32F2F)      // Red
    val High = Color(0xFFFF6B00)          // Orange
    val Medium = Color(0xFFFFCC00)        // Yellow
    val Low = Color(0xFF4CAF50)           // Green
    val Unknown = Color(0xFF9E9E9E)       // Gray

    // Safety Status
    val Safe = Color(0xFF4CAF50)          // Green
    val Caution = Color(0xFFFFCC00)       // Yellow
    val Unsafe = Color(0xFFFF6B00)        // Orange
    val CriticalSafety = Color(0xFFD32F2F) // Red

    // Voice Input States
    val Listening = Color(0xFF2196F3)     // Blue
    val Processing = Color(0xFFFF9800)    // Amber
    val Success = Color(0xFF4CAF50)       // Green
    val VoiceError = Color(0xFFD32F2F)    // Red

    // Translation States
    val SourceLanguage = Color(0xFF1976D2)   // Blue
    val TargetLanguage = Color(0xFF388E3C)   // Green
    val TranslationActive = Color(0xFFFF6B00) // Orange

    // Confidence Levels
    val HighConfidence = Color(0xFF4CAF50)    // Green
    val MediumConfidence = Color(0xFFFFCC00)  // Yellow
    val LowConfidence = Color(0xFFFF6B00)     // Orange
    val VeryLowConfidence = Color(0xFFD32F2F) // Red
}

// Additional Semantic Colors
object SemanticColors {
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFCC00)
    val Info = Color(0xFF2196F3)
    val Danger = Color(0xFFD32F2F)

    // Surface variants for different content types
    val MedicalSurface = Color(0xFFF3E5F5)      // Light purple
    val StructuralSurface = Color(0xFFF1F8E9)   // Light green
    val TranslationSurface = Color(0xFFE3F2FD)  // Light blue
    val GeneralSurface = Color(0xFFF5F5F5)      // Light gray

    // Text colors for high contrast
    val HighContrastText = Color(0xFF000000)
    val MediumContrastText = Color(0xFF424242)
    val DisabledText = Color(0xFF9E9E9E)
}

// Button Color Variants
object ButtonColors {
    val PrimaryButton = md_theme_light_primary
    val SecondaryButton = md_theme_light_secondary
    val TertiaryButton = md_theme_light_tertiary
    val DangerButton = EmergencyColors.Critical
    val SuccessButton = EmergencyColors.Safe
    val WarningButton = EmergencyColors.Medium

    // Large touch-friendly button variants
    val VoiceInputButton = EmergencyColors.Listening
    val CameraButton = md_theme_light_secondary
    val PlayButton = EmergencyColors.Success
    val StopButton = EmergencyColors.Critical
}

// Card and Surface Colors for Content Types
object ContentColors {
    val ResultCardBackground = Color(0xFFFAFAFA)
    val ErrorCardBackground = Color(0xFFFFEBEE)
    val SuccessCardBackground = Color(0xFFE8F5E8)
    val WarningCardBackground = Color(0xFFFFF8E1)
    val InfoCardBackground = Color(0xFFE3F2FD)

    val LoadingCardBackground = Color(0xFFF5F5F5)
    val DisabledCardBackground = Color(0xFFEEEEEE)
}

// Accessibility Colors - High Contrast Variants
object AccessibilityColors {
    val HighContrastPrimary = Color(0xFFB71C1C)     // Darker red
    val HighContrastSecondary = Color(0xFFE65100)   // Darker orange
    val HighContrastBackground = Color(0xFFFFFFFF)  // Pure white
    val HighContrastSurface = Color(0xFFFFFFFF)     // Pure white
    val HighContrastText = Color(0xFF000000)        // Pure black
    val HighContrastBorder = Color(0xFF000000)      // Pure black borders
}

// Status Colors for Different States
object StatusColors {
    val Loading = Color(0xFF2196F3)
    val Idle = Color(0xFF9E9E9E)
    val Active = Color(0xFF4CAF50)
    val Error = Color(0xFFD32F2F)
    val Disabled = Color(0xFFBDBDBD)
    val Offline = Color(0xFF424242)
    val Online = Color(0xFF4CAF50)
}