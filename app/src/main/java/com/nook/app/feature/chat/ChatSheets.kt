package com.nook.app.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nook.app.data.model.Message
import com.nook.app.data.model.User
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.NookBottomSheet
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.SheetAction
import com.nook.app.designsystem.components.SheetTitle
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.chats.ChatsUiState
import com.nook.app.feature.chats.ChatsViewModel
import com.nook.app.util.TimeFormat
import org.koin.androidx.compose.koinViewModel

@Composable
fun AttachSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit, onFile: () -> Unit) {
    val c = NookTheme.colors
    NookBottomSheet(onDismiss) {
        SheetTitle("Share")
        SheetAction(Icons.Outlined.CameraAlt, "Camera", onCamera, subtitle = "Take a photo")
        SheetAction(Icons.Outlined.Image, "Photos", onGallery, subtitle = "Pick from your gallery")
        SheetAction(Icons.Outlined.AttachFile, "File", onFile, subtitle = "Up to 10 MB")
        Spacer(Modifier.size(Spacing.xs))
        Text("Photos and files are uploaded to Cloudinary.", style = NookTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
    }
}

@Composable
fun ForwardSheet(onDismiss: () -> Unit, onSend: (List<String>) -> Unit, vm: ChatsViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val picked = remember { mutableStateListOf<String>() }
    NookBottomSheet(onDismiss) {
        SheetTitle("Forward to…")
        val rows = (state as? ChatsUiState.Ready)?.rows.orEmpty()
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(rows, key = { it.chatId }) { row ->
                val sel = row.chatId in picked
                Row(
                    Modifier.fillMaxWidth().clickable { if (sel) picked.remove(row.chatId) else picked.add(row.chatId) }
                        .padding(horizontal = Spacing.gutter, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(row.photoUrl, row.title, 40.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Text(row.title, style = NookTheme.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (sel) c.accent else c.border), contentAlignment = Alignment.Center) {
                        if (sel) Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        NookButton(
            if (picked.isEmpty()) "Pick chats" else "Send to ${picked.size}",
            onClick = { onSend(picked.toList()) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

@Composable
fun MessageInfoSheet(message: Message, seenBy: List<User>, onDismiss: () -> Unit) {
    val c = NookTheme.colors
    NookBottomSheet(onDismiss) {
        SheetTitle("Message info")
        Text(
            "Sent ${TimeFormat.separator(message.createdAt)} at ${TimeFormat.clock(message.createdAt)}",
            style = NookTheme.type.body, color = c.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        Spacer(Modifier.size(Spacing.md))
        Text("Seen by", style = NookTheme.type.titleSans, color = c.text, modifier = Modifier.padding(horizontal = Spacing.gutter))
        if (seenBy.isEmpty()) {
            Text("Nobody yet", style = NookTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs))
        }
        seenBy.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(u.photoUrl, u.name, 36.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text(u.name, style = NookTheme.type.bodyStrong, color = c.text)
            }
        }
        Spacer(Modifier.size(Spacing.md))
    }
}

/** Full-screen photo viewer: pinch to zoom, swipe down or tap ✕ to close. */
@Composable
fun ImageViewer(url: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dismissDrag = remember { Animatable(0f) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset(0f, offset.y + pan.y)
    }
    BackHandler(onBack = onClose)
    LaunchedEffect(transform.isTransformInProgress) {
        if (!transform.isTransformInProgress && scale <= 1f) {
            if (kotlin.math.abs(offset.y) > 260f) onClose() else offset = Offset.Zero
        }
    }
    val fade = (1f - kotlin.math.abs(offset.y) / 1200f).coerceIn(0.3f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = fade))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
            .transformable(transform),
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y + dismissDrag.value
            },
        )
        NookIconButton(
            Icons.Rounded.Close, "Close", onClose,
            background = Color.Black.copy(alpha = 0.5f), tint = Color.White,
            modifier = Modifier.statusBarsPadding().padding(Spacing.md).align(Alignment.TopEnd),
        )
    }
}
