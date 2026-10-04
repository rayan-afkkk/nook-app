package com.nook.app.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Original onboarding illustrations, drawn with Compose. Each takes [active] so its entrance
 * replays when the page becomes current, and renders a calm final frame when motion is reduced.
 */

private val Ink = Color(0xFF1A1714)

/** Staggered spring pop-ins keyed to [active]. */
@Composable
private fun rememberStagger(count: Int, active: Boolean, stepMs: Long = 110, initialDelay: Long = 120): List<Animatable<Float, AnimationVector1D>> {
    val reduce = NookTheme.reduceMotion
    val anims = remember { List(count) { Animatable(if (reduce) 1f else 0f) } }
    LaunchedEffect(active) {
        if (reduce) { anims.forEach { it.snapTo(1f) }; return@LaunchedEffect }
        if (!active) { anims.forEach { it.snapTo(0f) }; return@LaunchedEffect }
        delay(initialDelay)
        anims.forEachIndexed { i, a ->
            launch { delay(i * stepMs); a.animateTo(1f, NookMotion.bouncy()) }
        }
    }
    return anims
}

@Composable
private fun floatOffset(phase: Float, amplitude: Dp = 6.dp): Float {
    if (NookTheme.reduceMotion) return 0f
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "floatT")
    return sin(v + phase) * amplitude.value
}

@Composable
private fun FakeBubble(color: Color, widthFraction: Float, lines: Int, mine: Boolean, modifier: Modifier = Modifier) {
    val shape = if (mine) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    Column(
        modifier.fillMaxWidth(widthFraction).clip(shape).background(color).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(lines) { i ->
            Box(Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f).height(8.dp).clip(CircleShape).background(Ink.copy(alpha = 0.18f)))
        }
    }
}

/** 1 · Private by design — bubbles pop in with a springy stagger, then float. */
@Composable
fun BubblesIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val pops = rememberStagger(4, active)
    val specs = listOf(
        Triple(c.sky, 0.62f, false),
        Triple(c.peach, 0.55f, true),
        Triple(c.mint, 0.7f, false),
        Triple(c.lavender, 0.45f, true),
    )
    Column(
        modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        specs.forEachIndexed { i, (color, w, mine) ->
            val f = floatOffset(i * 1.3f)
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                FakeBubble(
                    color, w, if (i == 2) 2 else 1, mine,
                    Modifier.graphicsLayer {
                        val p = pops[i].value
                        scaleX = p; scaleY = p; alpha = p.coerceIn(0f, 1f)
                        translationY = f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
                    },
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.graphicsLayer { val p = pops[3].value; scaleX = p; scaleY = p }
                    .size(44.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Lock, null, tint = Ink, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** 2 · Say it your way — live waveform, GIF and sticker chips bounce in. */
@Composable
fun VoiceIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val pops = rememberStagger(3, active, stepMs = 160, initialDelay = 300)
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "wavePhase")
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Waveform pill
        Row(
            Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(42.dp))
                .background(c.peach)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = c.peach)
            }
            Canvas(Modifier.weight(1f).height(56.dp).padding(start = 14.dp)) {
                val bars = 26
                val gap = size.width / bars
                for (i in 0 until bars) {
                    val base = 0.25f + 0.75f * ((sin(i * 0.9f) + 1f) / 2f)
                    val live = if (reduce || !active) 1f else 0.55f + 0.45f * ((sin(phase + i * 0.55f) + 1f) / 2f)
                    val h = size.height * base * live
                    drawLine(
                        Ink.copy(alpha = 0.85f),
                        Offset(i * gap + gap / 2, size.height / 2 - h / 2),
                        Offset(i * gap + gap / 2, size.height / 2 + h / 2),
                        strokeWidth = gap * 0.45f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        val bob = floatOffset(0.5f, 5.dp)
        Box(
            Modifier.align(Alignment.TopStart).offset(48.dp, 70.dp)
                .graphicsLayer { val p = pops[0].value; scaleX = p; scaleY = p; rotationZ = -10f; translationY = bob * density }
                .clip(RoundedCornerShape(14.dp)).background(c.mint).padding(horizontal = 16.dp, vertical = 10.dp),
        ) { Text("GIF", style = NookTheme.type.titleSans, color = Ink) }
        Box(
            Modifier.align(Alignment.TopEnd).offset((-52).dp, 52.dp)
                .graphicsLayer { val p = pops[1].value; scaleX = p; scaleY = p; rotationZ = 12f; translationY = -bob * density }
                .size(64.dp).clip(CircleShape).background(c.lavender),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.EmojiEmotions, null, tint = Ink, modifier = Modifier.size(34.dp)) }
        Box(
            Modifier.align(Alignment.BottomCenter).offset(0.dp, (-64).dp)
                .graphicsLayer { val p = pops[2].value; scaleX = p; scaleY = p; translationY = bob * density }
                .size(56.dp).clip(CircleShape).background(c.accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mic, null, tint = Ink) }
    }
}

/** 3 · Calls with your crew — pulsing rings, friends join one by one. */
@Composable
fun CallIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val joins = rememberStagger(5, active, stepMs = 260, initialDelay = 350)
    val t = rememberInfiniteTransition(label = "rings")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring")
    val names = listOf("A", "S", "M", "Z", "R")
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val radius = (minOf(maxWidth, maxHeight) * 0.36f)
        Canvas(Modifier.fillMaxSize()) {
            val maxR = radius.toPx() * 1.25f
            for (k in 0 until 3) {
                val p = if (reduce) (k + 1) / 3.5f else (ring + k / 3f) % 1f
                drawCircle(c.accent.copy(alpha = (1f - p) * 0.45f), radius = maxR * (0.35f + p * 0.65f), style = Stroke(2.dp.toPx()))
            }
        }
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = Ink, modifier = Modifier.size(36.dp))
        }
        names.forEachIndexed { i, n ->
            val angle = (-PI / 2 + i * 2 * PI / names.size).toFloat()
            val x = radius * cos(angle)
            val y = radius * sin(angle)
            Box(
                Modifier.offset(x, y)
                    .graphicsLayer { val p = joins[i].value; scaleX = p; scaleY = p; alpha = p.coerceIn(0f, 1f) }
                    .size(56.dp).clip(CircleShape).background(c.pastels[i % c.pastels.size])
                    .border(3.dp, c.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(n, style = NookTheme.type.title, color = Ink) }
        }
    }
}

/** 4 · Yours to lock — the shackle snaps shut and the messages dissolve. Loops while visible. */
@Composable
fun LockIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val shackle = remember { Animatable(if (reduce) 0f else 1f) } // 1 = open, 0 = shut
    val dissolve = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(active) {
        if (reduce || !active) return@LaunchedEffect
        while (true) {
            shackle.snapTo(1f); dissolve.snapTo(0f)
            delay(700)
            shackle.animateTo(0f, NookMotion.bouncy())
            delay(150)
            dissolve.animateTo(1f, tween(900))
            delay(1600)
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Dissolving bubbles
        val bubbleColors = listOf(c.rose, c.sky, c.mint, c.peach)
        val positions = listOf(Offset(-110f, -120f), Offset(100f, -90f), Offset(-120f, 110f), Offset(110f, 130f))
        bubbleColors.forEachIndexed { i, col ->
            val d = dissolve.value
            Box(
                Modifier.offset(positions[i].x.dp * 0.9f, positions[i].y.dp * 0.9f)
                    .graphicsLayer {
                        alpha = 1f - d
                        scaleX = 1f + d * 0.35f; scaleY = 1f + d * 0.35f
                        translationY = -d * 18.dp.toPx()
                    }
                    .width(96.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(col),
            )
            // particles
            Canvas(Modifier.offset(positions[i].x.dp * 0.9f, positions[i].y.dp * 0.9f).size(110.dp)) {
                if (d in 0.05f..0.98f) {
                    for (k in 0 until 7) {
                        val a = k * 0.9f + i
                        val r = d * size.minDimension * 0.6f
                        drawCircle(col.copy(alpha = (1f - d)), radius = 3.dp.toPx(), center = center + Offset(cos(a) * r, sin(a) * r))
                    }
                }
            }
        }
        // The lock
        Canvas(Modifier.size(150.dp)) {
            val bodyW = size.width * 0.66f
            val bodyH = size.height * 0.5f
            val left = (size.width - bodyW) / 2
            val top = size.height * 0.42f
            val shackleW = bodyW * 0.62f
            val lift = shackle.value * size.height * 0.16f
            drawArc(
                color = c.text,
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset((size.width - shackleW) / 2, top - shackleW / 2 - lift),
                size = Size(shackleW, shackleW),
                style = Stroke(width = size.width * 0.075f, cap = StrokeCap.Round),
            )
            drawLine(c.text, Offset((size.width - shackleW) / 2, top - lift), Offset((size.width - shackleW) / 2, top + 4f), size.width * 0.075f, StrokeCap.Round)
            drawLine(c.text, Offset((size.width + shackleW) / 2, top - lift), Offset((size.width + shackleW) / 2, top + 4f - lift * 0.0f), size.width * 0.075f, StrokeCap.Round)
            drawRoundRect(c.accent, topLeft = Offset(left, top), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(size.width * 0.12f))
            drawCircle(Ink, radius = size.width * 0.055f, center = Offset(size.width / 2, top + bodyH * 0.45f))
        }
    }
}
