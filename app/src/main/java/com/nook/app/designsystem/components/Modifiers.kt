package com.nook.app.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.LocalNookColors
import com.nook.app.designsystem.theme.LocalReduceMotion
import com.nook.app.designsystem.theme.NookMotion

/** Springy scale-down while pressed — the base micro-interaction for every tappable surface. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = NookMotion.bouncy(),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Soft coloured glow painted behind the element (not clipped to its bounds). */
fun Modifier.glow(color: Color, radius: Dp = 140.dp, alpha: Float = 0.35f): Modifier = drawBehind {
    val r = radius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.4f), Color.Transparent),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
}

/** Slow breathing glow for hero elements. Falls back to a static glow when motion is reduced. */
fun Modifier.breathingGlow(color: Color, radius: Dp = 160.dp): Modifier = composed {
    val reduce = LocalReduceMotion.current
    if (reduce) return@composed glow(color, radius)
    val t = rememberInfiniteTransition(label = "breath")
    val a by t.animateFloat(0.22f, 0.42f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "breathA")
    glow(color, radius, a)
}

/** Shimmer sweep used by skeleton placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val colors = LocalNookColors.current
    val reduce = LocalReduceMotion.current
    val base = colors.surface
    val highlight = colors.surfaceRaised.copy(alpha = 1f)
    if (reduce) return@composed drawBehind { drawRect(base) }
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmerX")
    drawWithContent {
        val w = size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(w * x - w * 0.6f, 0f),
                end = Offset(w * x, size.height),
            ),
        )
        drawContent()
    }
}
