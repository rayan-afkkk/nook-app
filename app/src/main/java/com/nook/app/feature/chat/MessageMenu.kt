package com.nook.app.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.nook.app.data.media.PlaybackState
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.SendStatus
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

val QuickReactions = listOf("❤️", "😂", "😮", "😢", "🔥", "👍")

data class MenuTarget(val ui: MessageUi, val bounds: androidx.compose.ui.geometry.Rect)

enum class MenuAction { Reply, Copy, Forward, Info, Unsend, DeleteForMe, Retry }

/**
 * Long-press overlay. The chat behind is blurred by the caller (driven by [progress]); here we
 * draw the scrim, lift an identical copy of the pressed bubble from its exact on-screen bounds,
 * pop a reaction pill above it and an action card below it — clamped so nothing leaves the screen.
 * Dismissing (tap outside / back / swipe down) plays everything in reverse.
 */
@Composable
fun MessageMenuOverlay(
    target: MenuTarget,
    progress: Animatable<Float, AnimationVector1D>,
    playback: PlaybackState,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onAction: (MenuAction) -> Unit,
) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ui = target.ui
    val m = ui.message
    var origin by remember { mutableStateOf(Offset.Zero) }
    var flying by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val fly = remember { Animatable(0f) }
    val pops = remember(target) { List(QuickReactions.size + 1) { Animatable(if (reduce) 1f else 0f) } }

    LaunchedEffect(target) {
        if (!reduce) pops.forEachIndexed { i, a -> launch { delay(60L + i * 35L); a.animateTo(1f, NookMotion.bouncy()) } }
    }
    BackHandler(onBack = onDismiss)

    val actions = buildList {
        if (m.status == SendStatus.FAILED) add(Triple(MenuAction.Retry, Icons.Outlined.Refresh, "Retry"))
        if (m.status == SendStatus.SENT) add(Triple(MenuAction.Reply, Icons.AutoMirrored.Outlined.Reply, "Reply"))
        if (m.text.isNotBlank()) add(Triple(MenuAction.Copy, Icons.Outlined.ContentCopy, "Copy"))
        if (m.status == SendStatus.SENT && m.type != MessageType.SYSTEM) add(Triple(MenuAction.Forward, Icons.AutoMirrored.Outlined.Forward, "Forward"))
        if (ui.mine && m.status == SendStatus.SENT) add(Triple(MenuAction.Info, Icons.Outlined.Info, "Info"))
        if (ui.mine && m.status == SendStatus.SENT) add(Triple(MenuAction.Unsend, Icons.AutoMirrored.Outlined.Undo, "Unsend"))
        add(Triple(MenuAction.DeleteForMe, Icons.Outlined.DeleteOutline, "Delete for me"))
    }
    val canReact = m.status == SendStatus.SENT

    val statusTop = WindowInsets.statusBars.getTop(density)
    val bottomInset = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.ime.getBottom(density))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val p = progress.value
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val gap = with(density) { 10.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val pillH = with(density) { 54.dp.toPx() }
        val pillW = with(density) { (46.dp * (QuickReactions.size + 1) + 12.dp).toPx() }
        val rowH = with(density) { 50.dp.toPx() }
        val cardW = with(density) { 236.dp.toPx() }
        val cardH = rowH * actions.size + with(density) { 12.dp.toPx() }

        val b = target.bounds.translate(-origin.x, -origin.y)
        val topNeeded = statusTop + margin + (if (canReact) pillH + gap else 0f)
        val maxTop = screenH - bottomInset - margin - cardH - gap - b.height
        val targetTop = b.top.coerceIn(topNeeded, maxOf(topNeeded, maxTop))
        val currentTop = lerp(b.top, targetTop, p)

        // Scrim: tap outside or swipe down to dismiss.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = p * if (android.os.Build.VERSION.SDK_INT >= 31) 0.55f else 0.78f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy; if (total > 80.dp.toPx()) { total = -10_000f; onDismiss() } },
                    )
                },
        )

        // The lifted bubble.
        Box(
            Modifier
                .offset { IntOffset(b.left.roundToInt(), currentTop.roundToInt()) }
                .requiredWidth(with(density) { b.width.toDp() })
                .graphicsLayer {
                    val s = 1f + 0.04f * p
                    scaleX = s; scaleY = s
                    shadowElevation = 18.dp.toPx() * p
                    shape = bubbleShape(ui.mine, ui.joinPrev, ui.joinNext)
                    clip = false
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (ui.mine) 1f else 0f, 0.5f)
                },
        ) {
            BubbleBody(ui, playback, actions = null)
        }

        // Reaction pill above the bubble.
        if (canReact) {
            val pillX = (if (ui.mine) b.right - pillW else b.left).coerceIn(margin, screenW - margin - pillW)
            val pillY = currentTop - gap - pillH
            Row(
                Modifier
                    .offset { IntOffset(pillX.roundToInt(), pillY.roundToInt()) }
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        val s = 0.85f + 0.15f * p
                        scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (ui.mine) 1f else 0f, 1f)
                    }
                    .clip(NookShapes.pill)
                    .background(c.surface)
                    .border(1.dp, c.border, NookShapes.pill)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickReactions.forEachIndexed { i, emoji ->
                    val selected = ui.myReaction == emoji
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer { val s = pops[i].value; scaleX = s; scaleY = s }
                            .clip(CircleShape)
                            .background(if (selected) c.accent.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable(role = Role.Button, onClickLabel = "React $emoji") {
                                if (flying != null) return@clickable
                                haptics.confirm()
                                val start = Offset(pillX + with(density) { (6.dp + 46.dp * i + 22.dp).toPx() }, pillY + pillH / 2)
                                flying = emoji to start
                                scope.launch {
                                    fly.snapTo(0f)
                                    fly.animateTo(1f, tween(if (reduce) 1 else 420))
                                    onReact(emoji)
                                    flying = null
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, style = NookTheme.type.title) }
                }
                Box(
                    Modifier.size(40.dp).graphicsLayer { val s = pops.last().value; scaleX = s; scaleY = s }
                        .clip(CircleShape).background(c.surfaceRaised)
                        .clickable(role = Role.Button, onClickLabel = "More reactions") { onMoreReactions() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "More reactions", tint = c.text) }
            }
        }

        // Action card below the bubble.
        val cardX = (if (ui.mine) b.right - cardW else b.left).coerceIn(margin, screenW - margin - cardW)
        val cardY = (currentTop + b.height * (1f + 0.04f * p) + gap).coerceAtMost(screenH - bottomInset - margin - cardH)
        Column(
            Modifier
                .offset { IntOffset(cardX.roundToInt(), cardY.roundToInt()) }
                .width(with(density) { cardW.toDp() })
                .graphicsLayer {
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * -14.dp.toPx()
                    val s = 0.92f + 0.08f * p
                    scaleX = s; scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (ui.mine) 1f else 0f, 0f)
                }
                .clip(NookShapes.card)
                .background(c.surface)
                .border(1.dp, c.border, NookShapes.card)
                .padding(vertical = 6.dp),
        ) {
            actions.forEach { (action, icon, label) ->
                val danger = action == MenuAction.Unsend
                MenuRow(icon, label, if (danger) c.danger else c.text) { onAction(action) }
            }
        }

        // Emoji flying from the pill to the bubble's corner.
        flying?.let { (emoji, start) ->
            val end = Offset(if (ui.mine) b.right - 16f else b.left + 16f, currentTop + b.height)
            val f = fly.value
            val x = lerp(start.x, end.x, f)
            val y = lerp(start.y, end.y, f) - sin(f * PI).toFloat() * with(density) { 60.dp.toPx() }
            Text(
                emoji,
                style = NookTheme.type.headline,
                modifier = Modifier
                    .offset { IntOffset((x - 20.dp.toPx()).roundToInt(), (y - 20.dp.toPx()).roundToInt()) }
                    .graphicsLayer { val s = 1.4f - 0.6f * f; scaleX = s; scaleY = s },
            )
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NookTheme.type.bodyStrong, color = tint, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
