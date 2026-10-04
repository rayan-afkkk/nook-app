package com.nook.app.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Raw palette. Never reference these from UI code — use [NookTheme.colors] tokens instead. */
object NookPalette {
    val Black = Color(0xFF000000)
    val Charcoal = Color(0xFF1E1B18)
    val CharcoalRaised = Color(0xFF26221E)
    val Border = Color(0xFF2E2A26)
    val Cream = Color(0xFFF5EFE6)
    val Muted = Color(0xFF9A938A)
    val Orange = Color(0xFFFF6A33)
    val Amber = Color(0xFFE8B04B)
    val Coral = Color(0xFFF26B5E)
    val Mint = Color(0xFFCDEFD9)
    val Sky = Color(0xFFD4E6FB)
    val Peach = Color(0xFFFBE3C0)
    val Lavender = Color(0xFFDDD0F5)
    val Rose = Color(0xFFF6C9C4)
    val Navy = Color(0xFF1F3A52)

    val LightBackground = Color(0xFFF7F2EA)
    val LightSurface = Color(0xFFEFE7DB)
    val LightSurfaceRaised = Color(0xFFFFFBF5)
    val LightBorder = Color(0xFFDDD3C4)
    val Ink = Color(0xFF171411)
    val LightMuted = Color(0xFF6F675E)
}

@Immutable
data class NookColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val amber: Color,
    val danger: Color,
    val mint: Color,
    val sky: Color,
    val peach: Color,
    val lavender: Color,
    val rose: Color,
    val navy: Color,
    val onNavy: Color,
    /** Primary pill button: cream on dark, ink on light. */
    val primaryButton: Color,
    val primaryButtonEnd: Color,
    val onPrimaryButton: Color,
    val bubbleMineStart: Color,
    val bubbleMineEnd: Color,
    val onBubbleMine: Color,
    val bubbleTheirs: Color,
    val onBubbleTheirs: Color,
    val scrim: Color,
    val online: Color,
) {
    val pastels: List<Color> get() = listOf(mint, sky, peach, lavender, rose)
    val mineBubbleBrush: Brush get() = Brush.linearGradient(listOf(bubbleMineStart, bubbleMineEnd))
    val primaryButtonBrush: Brush get() = Brush.horizontalGradient(listOf(primaryButton, primaryButtonEnd))
}

val DarkNookColors = NookColors(
    isDark = true,
    background = NookPalette.Black,
    surface = NookPalette.Charcoal,
    surfaceRaised = NookPalette.CharcoalRaised,
    border = NookPalette.Border,
    text = NookPalette.Cream,
    textMuted = NookPalette.Muted,
    accent = NookPalette.Orange,
    onAccent = NookPalette.Black,
    amber = NookPalette.Amber,
    danger = NookPalette.Coral,
    mint = NookPalette.Mint,
    sky = NookPalette.Sky,
    peach = NookPalette.Peach,
    lavender = NookPalette.Lavender,
    rose = NookPalette.Rose,
    navy = NookPalette.Navy,
    onNavy = NookPalette.Cream,
    primaryButton = NookPalette.Cream,
    primaryButtonEnd = NookPalette.Peach,
    onPrimaryButton = NookPalette.Black,
    bubbleMineStart = NookPalette.Cream,
    bubbleMineEnd = NookPalette.Peach,
    onBubbleMine = NookPalette.Black,
    bubbleTheirs = NookPalette.Charcoal,
    onBubbleTheirs = NookPalette.Cream,
    scrim = NookPalette.Black,
    online = Color(0xFF6BD69A),
)

val LightNookColors = NookColors(
    isDark = false,
    background = NookPalette.LightBackground,
    surface = NookPalette.LightSurface,
    surfaceRaised = NookPalette.LightSurfaceRaised,
    border = NookPalette.LightBorder,
    text = NookPalette.Ink,
    textMuted = NookPalette.LightMuted,
    accent = NookPalette.Orange,
    onAccent = NookPalette.Black,
    amber = Color(0xFFC98F22),
    danger = Color(0xFFD9473A),
    mint = NookPalette.Mint,
    sky = NookPalette.Sky,
    peach = NookPalette.Peach,
    lavender = NookPalette.Lavender,
    rose = NookPalette.Rose,
    navy = NookPalette.Navy,
    onNavy = NookPalette.Cream,
    primaryButton = NookPalette.Ink,
    primaryButtonEnd = Color(0xFF3A2E25),
    onPrimaryButton = NookPalette.Cream,
    bubbleMineStart = NookPalette.Peach,
    bubbleMineEnd = Color(0xFFF8C9A2),
    onBubbleMine = NookPalette.Black,
    bubbleTheirs = NookPalette.LightSurfaceRaised,
    onBubbleTheirs = NookPalette.Ink,
    scrim = NookPalette.Black,
    online = Color(0xFF2FA866),
)

val LocalNookColors = staticCompositionLocalOf { DarkNookColors }
