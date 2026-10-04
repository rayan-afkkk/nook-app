package com.nook.app.feature.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nook.app.data.model.CallType
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.pressScale
import com.nook.app.designsystem.theme.InstrumentSerif
import com.nook.app.designsystem.theme.NookPalette
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.TimeFormat
import com.nook.app.util.rememberHaptics
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay

/** Full-screen black call UI: big avatar, serif name, timer, controls. Video fills when present. */
@Composable
fun CallOverlay(manager: CallManager) {
    val call by manager.state.collectAsStateWithLifecycle()
    val s = call ?: return
    val context = LocalContext.current
    val c = NookTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.phase) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    BackHandler { /* stay on the call; use the end button */ }

    val needed = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (s.type == CallType.VIDEO) add(Manifest.permission.CAMERA)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val missing = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val remote = s.remoteVideo
        if (remote != null) {
            VideoRenderer(remote, Modifier.fillMaxSize(), mirror = false)
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            if (remote == null) {
                RingingAvatar(s.peerPhoto, s.peerName, pulsing = s.phase != CallPhase.Connected && s.phase != CallPhase.Ended)
                Spacer(Modifier.height(28.dp))
            }
            Text(s.peerName, fontFamily = InstrumentSerif, fontSize = 44.sp, color = NookPalette.Cream)
            Spacer(Modifier.height(6.dp))
            Text(
                when (s.phase) {
                    CallPhase.Ringing -> if (s.outgoing) "Ringing…" else "Incoming call"
                    CallPhase.Connecting -> "Connecting…"
                    CallPhase.Connected -> TimeFormat.duration((now - s.connectedAt) / 1000)
                    CallPhase.Ended -> s.message ?: "Call ended"
                },
                style = NookTheme.type.body,
                color = if (s.phase == CallPhase.Ended) c.danger else NookPalette.Muted,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CallButton(if (s.micOn) Icons.Outlined.Mic else Icons.Outlined.MicOff, if (s.micOn) "Mute" else "Unmute", active = !s.micOn, onClick = manager::toggleMic)
                CallButton(Icons.AutoMirrored.Outlined.VolumeUp, "Speaker", active = s.speakerOn, onClick = manager::toggleSpeaker)
                CallButton(if (s.cameraOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff, "Camera", active = s.cameraOn, onClick = manager::toggleCamera)
                CallButton(Icons.Outlined.Cameraswitch, "Flip camera", active = false, enabled = s.cameraOn, onClick = manager::flipCamera)
            }
            Spacer(Modifier.height(28.dp))
            EndButton(onClick = manager::hangUp)
            Spacer(Modifier.height(12.dp))
        }
        // Local preview (picture-in-picture).
        val local = s.localVideo
        AnimatedVisibility(local != null && s.cameraOn, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)) {
            if (local != null) {
                VideoRenderer(local, Modifier.size(width = 110.dp, height = 160.dp).clip(RoundedCornerShape(20.dp)), mirror = s.frontCamera)
            }
        }
    }
}

private class RendererBinding { var track: VideoTrack? = null }

@Composable
private fun VideoRenderer(track: VideoTrack, modifier: Modifier, mirror: Boolean) {
    val manager: CallManager = org.koin.compose.koinInject()
    val binding = remember { RendererBinding() }
    AndroidView(
        factory = { ctx -> TextureViewRenderer(ctx).also { manager.initRenderer(it) } },
        update = { view ->
            view.setMirror(mirror)
            if (binding.track !== track) {
                binding.track?.removeRenderer(view)
                track.addRenderer(view)
                binding.track = track
            }
        },
        onRelease = { view ->
            binding.track?.removeRenderer(view)
            binding.track = null
            view.release()
        },
        modifier = modifier,
    )
}

@Composable
fun RingingAvatar(photo: String?, name: String, pulsing: Boolean, size: Dp = 140.dp) {
    val c = NookTheme.colors
    val t = rememberInfiniteTransition(label = "ring")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ringP")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.9f)) {
        if (pulsing && !NookTheme.reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0 until 3) {
                    val f = (p + k / 3f) % 1f
                    drawCircle(c.accent.copy(alpha = (1f - f) * 0.5f), radius = (size.toPx() / 2) * (1f + f * 0.9f), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Avatar(photo, name, size)
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val haptics = rememberHaptics()
    val bg by animateColorAsState(if (active) NookPalette.Cream else NookPalette.Charcoal, label = "callBtn")
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .pressScale(interaction, 0.9f)
                .clip(CircleShape)
                .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
                .semantics { contentDescription = label; stateDescription = if (active) "On" else "Off" }
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) { haptics.tick(); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (active) Color.Black else NookPalette.Cream) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = NookTheme.type.caption, color = NookPalette.Muted)
    }
}

@Composable
private fun EndButton(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .size(76.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(NookPalette.Coral)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "End call") { haptics.reject(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.CallEnd, "End call", tint = Color.Black, modifier = Modifier.size(34.dp)) }
}
