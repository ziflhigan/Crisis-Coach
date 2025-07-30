package com.cautious5.crisis_coach.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Crisis Coach Material 3 theme
 * Optimized for emergency situations with high contrast and accessibility
 */

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
)

/**
 * High contrast color scheme for accessibility
 */
private val HighContrastLightColorScheme = lightColorScheme(
    primary = AccessibilityColors.HighContrastPrimary,
    onPrimary = AccessibilityColors.HighContrastBackground,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = AccessibilityColors.HighContrastText,
    secondary = AccessibilityColors.HighContrastSecondary,
    onSecondary = AccessibilityColors.HighContrastBackground,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = AccessibilityColors.HighContrastText,
    tertiary = md_theme_light_tertiary,
    onTertiary = AccessibilityColors.HighContrastBackground,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = AccessibilityColors.HighContrastText,
    error = AccessibilityColors.HighContrastPrimary,
    errorContainer = md_theme_light_errorContainer,
    onError = AccessibilityColors.HighContrastBackground,
    onErrorContainer = AccessibilityColors.HighContrastText,
    background = AccessibilityColors.HighContrastBackground,
    onBackground = AccessibilityColors.HighContrastText,
    surface = AccessibilityColors.HighContrastSurface,
    onSurface = AccessibilityColors.HighContrastText,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = AccessibilityColors.HighContrastText,
    outline = AccessibilityColors.HighContrastBorder,
    inverseOnSurface = AccessibilityColors.HighContrastBackground,
    inverseSurface = AccessibilityColors.HighContrastText,
    inversePrimary = md_theme_light_inversePrimary,
)

/**
 * Main Crisis Coach theme composable
 */
@Composable
fun CrisisCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for consistent emergency branding
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast && !darkTheme -> HighContrastLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !highContrast -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CrisisCoachTypography,
        content = content
    )
}

/**
 * Emergency theme variant with fixed high-contrast colors
 * For use in critical situations where maximum visibility is needed
 */
@Composable
fun EmergencyTheme(
    content: @Composable () -> Unit
) {
    CrisisCoachTheme(
        darkTheme = false,
        dynamicColor = false,
        highContrast = true,
        content = content
    )
}

/**
 * Extension properties for easy access to emergency colors within theme
 */
val ColorScheme.emergencyCritical get() = EmergencyColors.Critical
val ColorScheme.emergencyHigh get() = EmergencyColors.High
val ColorScheme.emergencyMedium get() = EmergencyColors.Medium
val ColorScheme.emergencyLow get() = EmergencyColors.Low
val ColorScheme.emergencyUnknown get() = EmergencyColors.Unknown

val ColorScheme.safetySafe get() = EmergencyColors.Safe
val ColorScheme.safetyCaution get() = EmergencyColors.Caution
val ColorScheme.safetyUnsafe get() = EmergencyColors.Unsafe
val ColorScheme.safetyCritical get() = EmergencyColors.CriticalSafety

val ColorScheme.voiceListening get() = EmergencyColors.Listening
val ColorScheme.voiceProcessing get() = EmergencyColors.Processing
val ColorScheme.voiceSuccess get() = EmergencyColors.Success
val ColorScheme.voiceError get() = EmergencyColors.VoiceError

val ColorScheme.sourceLanguage get() = EmergencyColors.SourceLanguage
val ColorScheme.targetLanguage get() = EmergencyColors.TargetLanguage
val ColorScheme.translationActive get() = EmergencyColors.TranslationActive

val ColorScheme.highConfidence get() = EmergencyColors.HighConfidence
val ColorScheme.mediumConfidence get() = EmergencyColors.MediumConfidence
val ColorScheme.lowConfidence get() = EmergencyColors.LowConfidence
val ColorScheme.veryLowConfidence get() = EmergencyColors.VeryLowConfidence

// Content-specific surface colors
val ColorScheme.medicalSurface get() = SemanticColors.MedicalSurface
val ColorScheme.structuralSurface get() = SemanticColors.StructuralSurface
val ColorScheme.translationSurface get() = SemanticColors.TranslationSurface
val ColorScheme.generalSurface get() = SemanticColors.GeneralSurface

// Button color extensions
val ColorScheme.dangerButton get() = ButtonColors.DangerButton
val ColorScheme.successButton get() = ButtonColors.SuccessButton
val ColorScheme.warningButton get() = ButtonColors.WarningButton
val ColorScheme.voiceInputButton get() = ButtonColors.VoiceInputButton
val ColorScheme.cameraButton get() = ButtonColors.CameraButton
val ColorScheme.playButton get() = ButtonColors.PlayButton
val ColorScheme.stopButton get() = ButtonColors.StopButton

// Card background extensions
val ColorScheme.resultCardBackground get() = ContentColors.ResultCardBackground
val ColorScheme.errorCardBackground get() = ContentColors.ErrorCardBackground
val ColorScheme.successCardBackground get() = ContentColors.SuccessCardBackground
val ColorScheme.warningCardBackground get() = ContentColors.WarningCardBackground
val ColorScheme.infoCardBackground get() = ContentColors.InfoCardBackground
val ColorScheme.loadingCardBackground get() = ContentColors.LoadingCardBackground
val ColorScheme.disabledCardBackground get() = ContentColors.DisabledCardBackground

// Status color extensions
val ColorScheme.statusLoading get() = StatusColors.Loading
val ColorScheme.statusIdle get() = StatusColors.Idle
val ColorScheme.statusActive get() = StatusColors.Active
val ColorScheme.statusError get() = StatusColors.Error
val ColorScheme.statusDisabled get() = StatusColors.Disabled
val ColorScheme.statusOffline get() = StatusColors.Offline
val ColorScheme.statusOnline get() = StatusColors.Online