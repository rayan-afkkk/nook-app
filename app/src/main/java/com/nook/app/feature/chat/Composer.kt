package com.nook.app.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nook.app.data.media.Recording
import com.nook.app.data.media.VoiceRecorder
import com.nook.app.data.model.Message
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.glow
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.TimeFormat
import com.nook.app.util.rememberHaptics

@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    replyTo: Message?,
    replyName: String?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    attachOpen: Boolean,
    onAttach: () -> Unit,
    onExpressions: () -> Unit,
    recorder: VoiceRecorder,
    hasMicPermission: () -> Boolean,
    requestMic: () -> Unit,
    onVoice: (Recording) -> Unit,
    onHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    var recording by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val attachRotation by animateFloatAsState(if (attachOpen) 45f else 0f, NookMotion.choose(reduce, NookMotion.bouncy()), label = "attachRot")

    Column(modifier.fillMaxWidth().background(c.background)) {
        AnimatedVisibility(replyTo != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val r = replyTo
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).heightIn(min = 34.dp).clip(CircleShape).background(c.accent))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Replying to ${replyName ?: ""}", style = NookTheme.type.label, color = c.accent)
                    Text(r?.previewText().orEmpty(), style = NookTheme.type.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NookIconButton(Icons.Rounded.Close, "Cancel reply", onCancelReply, tint = c.textMuted, size = 40.dp, iconSize = 20.dp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NookIconButton(
                Icons.Rounded.Add, "Attach", onAttach,
                background = c.surface,
                modifier = Modifier.graphicsLayer { rotationZ = attachRotation },
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    recording,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "composerMode",
                ) { isRec ->
                    if (isRec) RecordingStrip(recorder, dragX)
                    else TextPill(text, onTextChange, onExpressions)
                }
            }
            Spacer(Modifier.width(6.dp))
            MicOrSend(
                hasText = text.isNotBlank(),
                recording = recording,
                onSend = onSend,
                recorder = recorder,
                hasMicPermission = hasMicPermission,
                requestMic = requestMic,
                onRecordingChange = { recording = it },
                onDrag = { dragX = it },
                onVoice = onVoice,
                onHint = onHint,
            )
        }
    }
}

@Composable
private fun TextPill(text: String, onTextChange: (String) -> Unit, onExpressions: () -> Unit) {
    val c = NookTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(start = 18.dp, top = 12.dp, bottom = 12.dp)) {
            if (text.isEmpty()) Text("Message", style = NookTheme.type.body, color = c.textMuted)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = NookTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message" },
            )
        }
        NookIconButton(Icons.Outlined.EmojiEmotions, "Emoji, GIFs and stickers", onExpressions, tint = c.textMuted, size = 44.dp)
    }
}

@Composable
private fun RecordingStrip(recorder: VoiceRecorder, dragX: Float) {
    val c = NookTheme.colors
    val level by recorder.level.collectAsStateWithLifecycle()
    val elapsed by recorder.elapsed.collectAsStateWithLifecycle()
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(level) {
        history += level
        if (history.size > 40) history.removeAt(0)
    }
    val t = rememberInfiniteTransition(label = "rec")
    val pulse by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recPulse")
    val density = LocalDensity.current
    val cancelProgress = with(density) { (-dragX / 120.dp.toPx()).coerceIn(0f, 1f) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(c.danger))
        Spacer(Modifier.width(8.dp))
        Text(TimeFormat.duration(elapsed / 1000), style = NookTheme.type.label, color = c.text)
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 14.dp)) {
            val n = 40
            val gap = size.width / n
            history.forEachIndexed { i, v ->
                val h = size.height * v.coerceIn(0.08f, 1f)
                val x = size.width - (history.size - i) * gap + gap / 2
                drawLine(c.accent, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), gap * 0.5f, StrokeCap.Round)
            }
        }
        Text(
            "‹ Slide to cancel",
            style = NookTheme.type.caption,
            color = c.textMuted,
            modifier = Modifier.graphicsLayer { alpha = 1f - cancelProgress; translationX = dragX * 0.4f },
        )
    }
}

@Composable
private fun MicOrSend(
    hasText: Boolean,
    recording: Boolean,
    onSend: () -> Unit,
    recorder: VoiceRecorder,
    hasMicPermission: () -> Boolean,
    requestMic: () -> Unit,
    onRecordingChange: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onVoice: (Recording) -> Unit,
    onHint: (String) -> Unit,
) {
    val c = NookTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cancelPx = with(density) { 120.dp.toPx() }
    val scale by animateFloatAsState(if (recording) 1.35f else 1f, NookMotion.bouncy(), label = "micScale")
    AnimatedContent(
        hasText,
        transitionSpec = { (scaleIn(initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut()) },
        label = "micSend",
    ) { text ->
        if (text) {
            NookIconButton(
                Icons.AutoMirrored.Rounded.Send, "Send",
                onClick = { haptics.confirm(); onSend() },
                background = c.accent, tint = c.onAccent, haptic = false,
                modifier = Modifier.glow(c.accent, 40.dp, 0.35f),
            )
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(if (recording) Modifier.glow(c.danger, 50.dp, 0.4f) else Modifier)
                    .clip(CircleShape)
                    .background(if (recording) c.danger else c.surface)
                    .semantics { contentDescription = "Hold to record a voice message"; role = Role.Button }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (!hasMicPermission()) { requestMic(); return@awaitEachGesture }
                            if (!recorder.start(scope)) { onHint("Couldn't start the microphone"); return@awaitEachGesture }
                            haptics.longPress()
                            onRecordingChange(true)
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = (change.position.x - down.position.x).coerceAtMost(0f)
                                onDrag(dx)
                                change.consume()
                                if (dx < -cancelPx) { cancelled = true; break }
                            }
                            onDrag(0f)
                            onRecordingChange(false)
                            if (cancelled) {
                                recorder.cancel(); haptics.reject()
                            } else {
                                val rec = recorder.stop()
                                if (rec != null) { haptics.confirm(); onVoice(rec) } else onHint("Hold the mic to record")
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, null, tint = if (recording) c.onAccent else c.text)
            }
        }
    }
}
