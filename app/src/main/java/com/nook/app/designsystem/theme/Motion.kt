package com.nook.app.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion rules (see CLAUDE.md): spring based, 200–400 ms, never block the UI thread.
 * When the system "Remove animations" setting is on, [LocalReduceMotion] is true and
 * components must fall back to short fades (use [NookMotion.fade]).
 */
object NookMotion {
    /** Default UI spring: quick, no visible overshoot. */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 420f)

    /** Playful spring for pops, reactions and icon bounces. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 420f)

    /** Gentle spring for large surfaces (sheets, overlays, lifted bubbles). */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    fun <T> fade(durationMs: Int = 200): FiniteAnimationSpec<T> = tween(durationMs)

    /** Picks [full] normally, or a plain fade when the user disabled animations. */
    fun <T> choose(reduceMotion: Boolean, full: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reduceMotion) fade() else full
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
