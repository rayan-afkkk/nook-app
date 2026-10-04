package com.nook.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nook.app.data.media.PlaybackState
import com.nook.app.data.model.Message
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.SendStatus
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.TimeFormat
import com.nook.app.util.formatBytes
import com.nook.app.util.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Callbacks a bubble can raise. Kept in one holder so rows stay cheap to recompose. */
class BubbleActions(
    val onLongPress: (MessageUi, Rect) -> Unit,
    val onReply: (Message) -> Unit,
    val onReaction: (Message, String) -> Unit,
    val onRetry: (Message) -> Unit,
    val onOpenMedia: (Message) -> Unit,
    val onTogglePlay: (Message) -> Unit,
    val onAnimated: (String) -> Unit,
)

private class CoordsHolder { var coords: LayoutCoordinates? = null }

@Composable
fun MessageRow(
    ui: MessageUi,
    isGroup: Boolean,
    lifted: Boolean,
    playback: PlaybackState,
    actions: BubbleActions,
    modifier: Modifier = Modifier,
) {
    val m = ui.message
    if (m.type == MessageType.SYSTEM) {
        SystemMessage(ui, modifier)
        return
    }
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // --- entrance: only for genuinely new messages ---
    val enter = remember { Animatable(if (ui.isNew) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            enter.animateTo(1f, if (reduce) tween(180) else spring(dampingRatio = 0.72f, stiffness = 380f))
            actions.onAnimated(m.id)
        }
    }

    // --- swipe to reply ---
    val drag = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }
    val threshold = with(density) { 56.dp.toPx() }
    val maxDrag = with(density) { 88.dp.toPx() }
    val holder = remember { CoordsHolder() }
    // Gesture handlers are keyed on the id, so read the latest message/state through this.
    val current by rememberUpdatedState(ui)
    val sendingAlpha by animateFloatAsState(if (m.status == SendStatus.SENDING) 0.72f else 1f, tween(250), label = "sendDim")

    Box(
        modifier
            .fillMaxWidth()
            .padding(top = if (ui.joinPrev) 2.dp else 8.dp, bottom = if (ui.reactionSummary.isNotEmpty()) 14.dp else 0.dp)
            .graphicsLayer {
                val p = enter.value
                alpha = p.coerceIn(0f, 1f)
                if (!reduce) {
                    if (ui.mine) {
                        translationY = (1f - p) * 56.dp.toPx()
                        val s = 0.9f + 0.1f * p
                        scaleX = s; scaleY = s
                    } else {
                        translationX = -(1f - p) * 24.dp.toPx()
                        val s = 0.95f + 0.05f * p
                        scaleX = s; scaleY = s
                    }
                    transformOrigin = TransformOrigin(if (ui.mine) 1f else 0f, 1f)
                }
            }
            .pointerInput(m.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (triggered) actions.onReply(current.message)
                        triggered = false
                        scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                    },
                    onDragCancel = { triggered = false; scope.launch { drag.animateTo(0f, spring()) } },
                    onHorizontalDrag = { change, amount ->
                        val next = (drag.value + amount * 0.55f).coerceIn(0f, maxDrag)
                        scope.launch { drag.snapTo(next) }
                        if (!triggered && next >= threshold) { triggered = true; haptics.tick() }
                        else if (triggered && next < threshold) triggered = false
                        change.consume()
                    },
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Reply") { actions.onReply(current.message); true },
                    CustomAccessibilityAction("More options") {
                        holder.coords?.let { actions.onLongPress(current, it.boundsInRoot()) }; true
                    },
                )
            },
    ) {
        // Reply hint icon revealed behind the bubble while swiping.
        val hint = (drag.value / threshold).coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                .graphicsLayer { alpha = hint; scaleX = 0.5f + 0.5f * hint; scaleY = 0.5f + 0.5f * hint }
                .size(32.dp).clip(CircleShape).background(c.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = c.text, modifier = Modifier.size(18.dp)) }

        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = drag.value
                    val stretch = 1f + 0.05f * (drag.value / maxDrag)
                    scaleX = stretch
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .padding(horizontal = 12.dp),
            horizontalArrangement = if (ui.mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (isGroup && !ui.mine) {
                if (!ui.joinNext) Avatar(ui.senderPhoto, ui.senderName ?: "?", 28.dp)
                else Spacer(Modifier.width(28.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (ui.mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
                if (ui.showSender) {
                    Text(
                        ui.senderName ?: "",
                        style = NookTheme.type.label,
                        color = nameColor(ui.message.senderId),
                        modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                    )
                }
                Box {
                    BubbleBody(
                        ui = ui,
                        playback = playback,
                        actions = actions,
                        modifier = Modifier
                            .alpha(if (lifted) 0f else sendingAlpha)
                            .onGloballyPositioned { holder.coords = it }
                            .pointerInput(m.id) {
                                detectTapGestures(
                                    onLongPress = {
                                        val coords = holder.coords ?: return@detectTapGestures
                                        haptics.longPress()
                                        actions.onLongPress(current, coords.boundsInRoot())
                                    },
                                    onTap = {
                                        val msg = current.message
                                        when {
                                            msg.status == SendStatus.FAILED -> actions.onRetry(msg)
                                            msg.type == MessageType.IMAGE || msg.type == MessageType.FILE || msg.type == MessageType.GIF -> actions.onOpenMedia(msg)
                                        }
                                    },
                                )
                            },
                    )
                    if (ui.reactionSummary.isNotEmpty() && !lifted) {
                        ReactionPills(
                            ui,
                            onTap = { emoji -> actions.onReaction(m, emoji) },
                            modifier = Modifier
                                .align(if (ui.mine) Alignment.BottomEnd else Alignment.BottomStart)
                                .offset(x = if (ui.mine) (-8).dp else 8.dp, y = 16.dp),
                        )
                    }
                }
                AnimatedVisibility(ui.seenLabel != null, enter = fadeIn(tween(300))) {
                    Text(
                        ui.seenLabel.orEmpty(),
                        style = NookTheme.type.caption,
                        color = c.textMuted,
                        modifier = Modifier.padding(top = if (ui.reactionSummary.isNotEmpty()) 18.dp else 4.dp, end = 6.dp),
                    )
                }
                if (m.status == SendStatus.FAILED) {
                    Text(
                        "Not sent · tap to retry",
                        style = NookTheme.type.caption,
                        color = c.danger,
                        modifier = Modifier.padding(top = 4.dp, end = 6.dp).clickable { actions.onRetry(m) },
                    )
                }
            }
        }
    }
}

@Composable
fun nameColor(uid: String): Color {
    val c = NookTheme.colors
    val palette = listOf(c.peach, c.sky, c.mint, c.lavender, c.rose, c.amber)
    return palette[abs(uid.hashCode()) % palette.size]
}

fun bubbleShape(mine: Boolean, joinPrev: Boolean, joinNext: Boolean): Shape {
    val big = 22.dp
    val small = 6.dp
    return if (mine) {
        RoundedCornerShape(topStart = big, topEnd = if (joinPrev) small else big, bottomEnd = if (joinNext) small else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (joinPrev) small else big, topEnd = big, bottomEnd = big, bottomStart = small)
    }
}

/** The visual bubble only — reused by the long-press overlay to "lift" an identical copy. */
@Composable
fun BubbleBody(ui: MessageUi, playback: PlaybackState, actions: BubbleActions?, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val m = ui.message
    val shape = bubbleShape(ui.mine, ui.joinPrev, ui.joinNext)
    val isSticker = m.type == MessageType.STICKER
    val contentColor = if (ui.mine) c.onBubbleMine else c.onBubbleTheirs
    val bg = when {
        isSticker -> Modifier
        ui.mine -> Modifier.background(c.mineBubbleBrush, shape)
        else -> Modifier.background(c.bubbleTheirs, shape).then(if (c.isDark) Modifier else Modifier.border(1.dp, c.border, shape))
    }
    Column(
        modifier
            .clip(if (isSticker) RoundedCornerShape(0.dp) else shape)
            .then(bg)
            .padding(if (isSticker) 0.dp else 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(if (ui.mine) "You" else ui.senderName ?: "Them")
                    append(": ")
                    append(m.previewText())
                    append(", ")
                    append(TimeFormat.clock(m.createdAt))
                    if (ui.mine) append(if (ui.seen) ", seen" else if (m.status == SendStatus.SENDING) ", sending" else ", sent")
                }
            },
    ) {
        if (m.forwarded) {
            Row(Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.Forward, null, tint = contentColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Forwarded", style = NookTheme.type.caption.copy(fontStyle = FontStyle.Italic), color = contentColor.copy(alpha = 0.6f))
            }
        }
        m.replyTo?.let { r ->
            Row(
                Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp)).background(contentColor.copy(alpha = 0.08f))
                    .padding(end = 10.dp).height(IntrinsicSize.Min),
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
                Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(ui.replySenderName ?: "Message", style = NookTheme.type.label, color = if (ui.mine) Color(0xFF8A3A16) else c.accent, maxLines = 1)
                    Text(r.preview.ifBlank { "Message" }, style = NookTheme.type.caption, color = contentColor.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        when (m.type) {
            MessageType.IMAGE -> ImageContent(m, ui)
            MessageType.GIF -> AsyncImage(
                model = m.media?.url,
                contentDescription = "GIF",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(220.dp).aspectRatio(ratio(m)).clip(RoundedCornerShape(18.dp)),
            )
            MessageType.STICKER -> AsyncImage(
                model = m.media?.url,
                contentDescription = "Sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(150.dp),
            )
            MessageType.VOICE -> VoiceContent(m, ui, playback, actions)
            MessageType.FILE -> FileContent(m, contentColor)
            else -> Unit
        }
        if (m.text.isNotBlank() && m.type != MessageType.SYSTEM) {
            Text(
                m.text,
                style = NookTheme.type.body,
                color = contentColor,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            )
        }
        MetaRow(ui, contentColor, overlay = isSticker)
    }
}

@Composable
private fun ColumnScope.MetaRow(ui: MessageUi, contentColor: Color, overlay: Boolean) {
    val c = NookTheme.colors
    val m = ui.message
    Row(
        Modifier
            .align(Alignment.End)
            .padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 6.dp)
            .then(if (overlay) Modifier.clip(CircleShape).background(c.surface).padding(horizontal = 8.dp, vertical = 2.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(TimeFormat.clock(m.createdAt), style = NookTheme.type.caption, color = (if (overlay) c.textMuted else contentColor.copy(alpha = 0.55f)))
        if (ui.mine) {
            Spacer(Modifier.width(4.dp))
            val status = when {
                m.status == SendStatus.FAILED -> 3
                m.status == SendStatus.SENDING -> 0
                ui.seen -> 2
                else -> 1
            }
            Crossfade(status, animationSpec = tween(250), label = "tick") { s ->
                when (s) {
                    0 -> Icon(Icons.Outlined.Schedule, "Sending", tint = contentColor.copy(alpha = 0.55f), modifier = Modifier.size(13.dp))
                    1 -> Icon(Icons.Rounded.Done, "Sent", tint = contentColor.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                    2 -> Icon(Icons.Rounded.DoneAll, "Seen", tint = c.accent, modifier = Modifier.size(15.dp))
                    else -> Icon(Icons.Outlined.ErrorOutline, "Failed", tint = c.danger, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun ratio(m: Message): Float {
    val w = m.media?.width ?: 0; val h = m.media?.height ?: 0
    return if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.6f, 1.6f) else 1f
}

@Composable
private fun ImageContent(m: Message, ui: MessageUi) {
    val c = NookTheme.colors
    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = m.localUri ?: m.media?.url,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(240.dp).aspectRatio(ratio(m)).clip(RoundedCornerShape(18.dp)).background(c.surfaceRaised),
        )
        if (m.status == SendStatus.SENDING && m.localUri != null) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { m.progress }, color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun FileContent(m: Message, contentColor: Color) {
    val c = NookTheme.colors
    Row(Modifier.padding(8.dp).widthIn(min = 200.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(contentColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (m.status == SendStatus.SENDING) CircularProgressIndicator(progress = { m.progress }, color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            else Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = contentColor)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(m.media?.name ?: "File", style = NookTheme.type.bodyStrong, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(m.media?.size ?: 0), style = NookTheme.type.caption, color = contentColor.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun VoiceContent(m: Message, ui: MessageUi, playback: PlaybackState, actions: BubbleActions?) {
    val c = NookTheme.colors
    val contentColor = if (ui.mine) c.onBubbleMine else c.onBubbleTheirs
    val isThis = playback.messageId == m.id
    val playing = isThis && playback.playing
    val duration = (m.media?.durationMs ?: 0).coerceAtLeast(1)
    val progress = if (isThis) (playback.positionMs.toFloat() / (if (playback.durationMs > 0) playback.durationMs else duration)).coerceIn(0f, 1f) else 0f
    val bars = m.media?.waveform?.takeIf { it.isNotEmpty() } ?: List(32) { 0.3f }
    Row(Modifier.padding(6.dp).width(230.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (ui.mine) Color(0xFF1A1714) else c.text)
                .clickable(enabled = m.status == SendStatus.SENT && actions != null) { actions?.onTogglePlay(m) },
            contentAlignment = Alignment.Center,
        ) {
            if (m.status == SendStatus.SENDING) {
                CircularProgressIndicator(progress = { m.progress }, color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Crossfade(playing, label = "play") { p ->
                    Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pause" else "Play voice message", tint = if (ui.mine) c.peach else c.background)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val gap = size.width / bars.size
                bars.forEachIndexed { i, v ->
                    val h = size.height * v.coerceIn(0.08f, 1f)
                    val played = (i + 0.5f) / bars.size <= progress
                    drawLine(
                        color = if (played) c.accent else contentColor.copy(alpha = 0.35f),
                        start = Offset(i * gap + gap / 2, (size.height - h) / 2),
                        end = Offset(i * gap + gap / 2, (size.height + h) / 2),
                        strokeWidth = gap * 0.55f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                TimeFormat.duration(((if (isThis && playback.positionMs > 0) playback.positionMs else duration) / 1000)),
                style = NookTheme.type.caption,
                color = contentColor.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ReactionPills(ui: MessageUi, onTap: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ui.reactionSummary.take(4).forEach { (emoji, count) ->
            val mine = ui.myReaction == emoji
            AnimatedVisibility(true, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(c.surfaceRaised)
                        .border(1.dp, if (mine) c.accent else c.border, CircleShape)
                        .clickable { onTap(emoji) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, style = NookTheme.type.bodySmall)
                    if (count > 1) {
                        Spacer(Modifier.width(3.dp))
                        Text(count.toString(), style = NookTheme.type.caption, color = c.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemMessage(ui: MessageUi, modifier: Modifier) {
    val c = NookTheme.colors
    val who = if (ui.mine) "You" else ui.senderName?.substringBefore(' ') ?: "Someone"
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            "$who ${ui.message.text}",
            style = NookTheme.type.caption,
            color = c.textMuted,
            modifier = Modifier.clip(CircleShape).background(c.surface).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
