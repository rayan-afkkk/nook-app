package com.nook.app.feature.account

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nook.app.AppConfig
import com.nook.app.data.media.ImageCompressor
import com.nook.app.data.remote.MediaAsset
import com.nook.app.data.remote.WorkerApi
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.MediaRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookTopBar
import com.nook.app.designsystem.components.breathingGlow
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.navigation.CropTargets
import com.nook.app.util.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface CropState {
    data object Loading : CropState
    data class Ready(val bitmap: Bitmap) : CropState
    data class Saving(val preview: Bitmap) : CropState
    data class Done(val url: String) : CropState
    data class Failed(val message: String) : CropState
}

class CropViewModel(
    private val uri: String,
    private val target: String,
    private val context: Context,
    private val media: MediaRepository,
    private val users: UserRepository,
    private val chats: ChatRepository,
    private val worker: WorkerApi,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CropState>(CropState.Loading)
    val state: StateFlow<CropState> = _state.asStateFlow()
    private var source: Bitmap? = null

    init {
        viewModelScope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { ImageCompressor.decode(context, Uri.parse(uri), 2048) } }
                .fold({ source = it; CropState.Ready(it) }, { CropState.Failed("Couldn't open that photo") })
        }
    }

    /** [left], [top], [side] are in source-bitmap pixels. */
    fun save(left: Int, top: Int, side: Int) {
        val src = source ?: return
        viewModelScope.launch {
            try {
                val cropped = withContext(Dispatchers.Default) {
                    val s = side.coerceIn(1, min(src.width, src.height))
                    val l = left.coerceIn(0, src.width - s)
                    val t = top.coerceIn(0, src.height - s)
                    Bitmap.createScaledBitmap(Bitmap.createBitmap(src, l, t, s, s), 512, 512, true)
                }
                _state.value = CropState.Saving(cropped)
                val url = when {
                    target.startsWith("group:") -> {
                        val chatId = target.removePrefix("group:")
                        val m = media.uploadBitmap(cropped, media.chatFolder(chatId) + "/group")
                        chats.setGroupPhoto(chatId, m.url)
                        m.url
                    }
                    else -> {
                        val uid = auth.requireUid()
                        val old = runCatching { users.currentPhotoPublicId() }.getOrNull()
                        val m = media.uploadBitmap(cropped, media.avatarFolder(uid))
                        users.updatePhoto(m.url, m.publicId)
                        if (old != null && AppConfig.hasWorker) launch { runCatching { worker.deleteMedia("avatar", listOf(MediaAsset(old, "image"))) } }
                        m.url
                    }
                }
                _state.value = CropState.Done(url)
            } catch (e: Exception) {
                _state.value = CropState.Failed(e.message ?: "Upload failed")
            }
        }
    }
}

/**
 * Square-to-circle crop: the mask animates from a square to a circle, pinch/drag to frame,
 * then an animated circular preview pops while it uploads.
 */
@Composable
fun CropScreen(uri: String, target: String, onDone: () -> Unit, vm: CropViewModel = koinViewModel { parametersOf(uri, target) }) {
    val c = NookTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    LaunchedEffect(state) {
        if (state is CropState.Done) { haptics.confirm(); delay(900); onDone() }
    }
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar(if (target == CropTargets.AVATAR) "Frame your photo" else "Group photo", onBack = onDone)
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(150)) },
            label = "crop",
            modifier = Modifier.weight(1f),
        ) { s ->
            when (s) {
                CropState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                is CropState.Failed -> Box(Modifier.fillMaxSize().padding(Spacing.gutter), contentAlignment = Alignment.Center) {
                    Text(s.message, style = NookTheme.type.body, color = c.danger)
                }
                is CropState.Ready -> Cropper(s.bitmap) { l, t, side -> vm.save(l, t, side) }
                is CropState.Saving -> Preview(s.preview.asImageBitmap(), saving = true)
                is CropState.Done -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.breathingGlow(c.mint, 120.dp)) { Avatar(s.url, "You", 160.dp) }
                        Spacer(Modifier.height(Spacing.lg))
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.mint), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, "Saved", tint = Color(0xFF1A1714))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(image: ImageBitmap, saving: Boolean) {
    val c = NookTheme.colors
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, NookMotion.bouncy()) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.breathingGlow(c.peach, 120.dp)) {
                Canvas(Modifier.size(160.dp).clip(CircleShape)) {
                    drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            if (saving) Text("Uploading…", style = NookTheme.type.body, color = c.textMuted)
        }
    }
}

@Composable
private fun Cropper(bitmap: Bitmap, onCrop: (Int, Int, Int) -> Unit) {
    val c = NookTheme.colors
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val morph = remember { Animatable(0f) } // 0 = square mask, 1 = circle
    val reduce = NookTheme.reduceMotion
    LaunchedEffect(Unit) { delay(250); morph.animateTo(1f, tween(if (reduce) 0 else 520)) }
    var viewport by remember { mutableFloatStateOf(1f) }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = with(androidx.compose.ui.platform.LocalDensity.current) { (minOf(maxWidth, maxHeight) - 48.dp).toPx() }
            viewport = side
            val base = max(side / bitmap.width, side / bitmap.height)
            Canvas(
                Modifier.fillMaxSize().pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val s = base * scale
                        val maxX = (bitmap.width * s - side) / 2
                        val maxY = (bitmap.height * s - side) / 2
                        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                    }
                },
            ) {
                val s = base * scale
                val w = bitmap.width * s; val h = bitmap.height * s
                val left = center.x - w / 2 + offset.x
                val top = center.y - h / 2 + offset.y
                drawImage(
                    image,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                )
                // Dim outside the mask; mask morphs from square to circle.
                val boxLeft = center.x - side / 2; val boxTop = center.y - side / 2
                val radius = side / 2 * morph.value
                val hole = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(boxLeft, boxTop, boxLeft + side, boxTop + side, CornerRadius(radius)))
                }
                clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.62f)) }
                drawRoundRect(c.text.copy(alpha = 0.9f), Offset(boxLeft, boxTop), Size(side, side), CornerRadius(radius), style = Stroke(2.dp.toPx()))
            }
        }
        Text("Pinch and drag to frame", style = NookTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        NookButton(
            "Use photo",
            onClick = {
                val s = max(viewport / bitmap.width, viewport / bitmap.height) * scale
                val sideSrc = (viewport / s).roundToInt()
                val centerX = bitmap.width / 2f - offset.x / s
                val centerY = bitmap.height / 2f - offset.y / s
                onCrop((centerX - sideSrc / 2f).roundToInt(), (centerY - sideSrc / 2f).roundToInt(), sideSrc)
            },
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

@Suppress("unused")
private fun Modifier.square() = this.aspectRatio(1f)
