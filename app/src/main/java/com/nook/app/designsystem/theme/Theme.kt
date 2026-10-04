package com.nook.app.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode { Dark, Light, System }

@Composable
fun NookTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkNookColors else LightNookColors
    val type = DefaultNookTypography

    // Material 3 is only a base: we map our tokens so any stock component still looks on-brand.
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.primaryButton, onPrimary = colors.onPrimaryButton,
            secondary = colors.accent, onSecondary = colors.onAccent,
            tertiary = colors.amber,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerLow = colors.surface, surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.border, outlineVariant = colors.border,
            error = colors.danger, onError = colors.onAccent,
        )
    } else {
        lightColorScheme(
            primary = colors.primaryButton, onPrimary = colors.onPrimaryButton,
            secondary = colors.accent, onSecondary = colors.onAccent,
            tertiary = colors.amber,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerLow = colors.surface, surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.border, outlineVariant = colors.border,
            error = colors.danger, onError = colors.onAccent,
        )
    }
    val m3Type = Typography(
        displayLarge = type.hero, displayMedium = type.display, headlineMedium = type.headline,
        titleLarge = type.title, titleMedium = type.titleSans, bodyLarge = type.body,
        bodyMedium = type.bodySmall, labelLarge = type.button, labelMedium = type.label, labelSmall = type.caption,
    )
    val reduceMotion = rememberSystemReduceMotion()

    CompositionLocalProvider(
        LocalNookColors provides colors,
        LocalNookTypography provides type,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, content = content)
    }
}

object NookTheme {
    val colors: NookColors
        @Composable @ReadOnlyComposable get() = LocalNookColors.current
    val type: NookTypography
        @Composable @ReadOnlyComposable get() = LocalNookTypography.current
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}
