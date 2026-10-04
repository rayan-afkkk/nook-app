package com.nook.app.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class Slide(val eyebrow: String, val title: String, val body: String)

private val slides = listOf(
    Slide("01 — Private", "Private by design", "Just you and your people. No strangers, no feeds, no ads — a quiet corner of the internet."),
    Slide("02 — Expressive", "Say it your way", "Voice notes, GIFs, stickers you make from your own photos, and reactions that land."),
    Slide("03 — Together", "Calls with your crew", "Crisp voice and video calls, one tap away — even when the app is closed."),
    Slide("04 — Locked", "Yours to lock", "Your own password and fingerprint, and messages that disappear when you want them to."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val haptics = rememberHaptics()
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val position by remember { derivedStateOf { pager.currentPage + pager.currentPageOffsetFraction } }
    val glowColors = listOf(c.sky, c.peach, c.mint, c.lavender)
    val isLast = pager.currentPage == slides.lastIndex

    LaunchedEffect(pager) {
        var first = true
        androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect {
            if (!first) haptics.tick()
            first = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .drawBehind {
                // Background glow that shifts pastel tint as you swipe.
                val i = position.toInt().coerceIn(0, glowColors.lastIndex)
                val next = (i + 1).coerceAtMost(glowColors.lastIndex)
                val tint = lerp(glowColors[i], glowColors[next], (position - i).coerceIn(0f, 1f))
                drawCircle(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.5f, size.height * 0.3f),
                )
            },
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            val active = pager.currentPage == page && !pager.isScrollInProgress || pager.settledPage == page
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 72.dp, bottom = 180.dp)) {
                // Parallax: the illustration drifts at half speed relative to the text.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = offset * pageWidthPx * 0.5f
                            alpha = 1f - abs(offset).coerceIn(0f, 1f) * 0.6f
                        },
                ) {
                    when (page) {
                        0 -> BubblesIllustration(active)
                        1 -> VoiceIllustration(active)
                        2 -> CallIllustration(active)
                        else -> LockIllustration(active)
                    }
                }
                SlideText(slides[page], active = pager.settledPage == page, reduce = reduce)
            }
        }

        // Skip (top-right)
        if (!isLast) {
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.md)) {
                NookPill("Skip", onClick = onDone, background = c.surface)
            }
        }

        // Progress pill + Continue
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageIndicator(position, slides.size)
            Spacer(Modifier.height(Spacing.xl))
            NookButton(
                text = if (isLast) "Get started" else "Continue",
                onClick = {
                    if (isLast) onDone()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(if (reduce) 0 else 420)) }
                },
                shimmerOnce = isLast,
            )
        }
    }
}

@Composable
private fun SlideText(slide: Slide, active: Boolean, reduce: Boolean) {
    val c = NookTheme.colors
    val words = slide.title.split(" ")
    val anims = remember(slide) { List(words.size + 2) { Animatable(if (reduce) 1f else 0f) } }
    LaunchedEffect(active) {
        if (reduce) return@LaunchedEffect
        if (!active) return@LaunchedEffect
        anims.forEach { it.snapTo(0f) }
        anims.forEachIndexed { i, a -> launch { delay(i * 70L); a.animateTo(1f, NookMotion.gentle()) } }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).semantics(mergeDescendants = true) {}) {
        Text(
            slide.eyebrow.uppercase(),
            style = NookTheme.type.label,
            color = c.accent,
            modifier = Modifier.graphicsLayer { alpha = anims[0].value },
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            // Simple wrap: two lines max for our titles.
            Column {
                val lines = wrapWords(words, 2)
                var idx = 0
                lines.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        line.forEach { w ->
                            val a = anims[1 + idx++]
                            Text(
                                w,
                                style = NookTheme.type.hero,
                                color = c.text,
                                modifier = Modifier.graphicsLayer {
                                    alpha = a.value
                                    translationY = (1f - a.value) * 22.dp.toPx()
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            slide.body,
            style = NookTheme.type.body,
            color = c.textMuted,
            modifier = Modifier.graphicsLayer {
                val a = anims.last().value
                alpha = a; translationY = (1f - a) * 14.dp.toPx()
            },
        )
    }
}

private fun wrapWords(words: List<String>, perLine: Int): List<List<String>> = words.chunked(perLine)

/** Dots where the active one morphs into a pill, following the finger. */
@Composable
private fun PageIndicator(position: Float, count: Int) {
    val c = NookTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "Page ${position.toInt() + 1} of $count" },
    ) {
        repeat(count) { i ->
            val closeness = (1f - abs(position - i)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(8.dp)
                    .width(lerp(8f, 28f, closeness).dp)
                    .clip(NookShapes.pill)
                    .background(lerp(c.border, c.text, closeness)),
            )
        }
    }
}
