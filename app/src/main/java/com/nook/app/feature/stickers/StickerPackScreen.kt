package com.nook.app.feature.stickers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.nook.app.data.media.ImageCompressor
import com.nook.app.data.model.StickerPack
import com.nook.app.data.repo.MediaRepository
import com.nook.app.data.repo.StickerRepository
import com.nook.app.data.security.AppLockManager
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.LoadingBox
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookTopBar
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.math.min

class StickerPackViewModel(
    val packId: String,
    private val stickers: StickerRepository,
    private val media: MediaRepository,
    private val context: Context,
) : ViewModel() {
    val pack: StateFlow<StickerPack?> = stickers.observe(packId).catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _uploading = MutableStateFlow(0)
    val uploading: StateFlow<Int> = _uploading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Centre-crops each photo to a 512px square and adds it to the pack. */
    fun add(uris: List<Uri>) {
        val p = pack.value ?: return
        uris.forEach { uri ->
            _uploading.value++
            viewModelScope.launch {
                try {
                    val square = withContext(Dispatchers.Default) {
                        val bmp = ImageCompressor.decode(context, uri, 1024)
                        val side = min(bmp.width, bmp.height)
                        val cropped = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                        Bitmap.createScaledBitmap(cropped, 512, 512, true)
                    }
                    val m = media.uploadBitmap(square, media.chatFolder(p.chatId) + "/stickers")
                    stickers.addSticker(packId, m)
                } catch (e: Exception) {
                    _error.value = e.message ?: "Upload failed"
                } finally {
                    _uploading.value--
                }
            }
        }
    }
}

@Composable
fun StickerPackScreen(packId: String, nav: NavHostController, vm: StickerPackViewModel = koinViewModel { parametersOf(packId) }) {
    val c = NookTheme.colors
    val pack by vm.pack.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val lock: AppLockManager = koinInject()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        lock.clearSuppression()
        if (uris.isNotEmpty()) vm.add(uris)
    }
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar(pack?.name ?: "Sticker pack", onBack = { nav.popBackStack() })
        val p = pack
        if (p == null) { LoadingBox(Modifier.weight(1f)) } else {
            Box(Modifier.weight(1f)) {
                if (p.stickers.isEmpty() && uploading == 0) {
                    EmptyState(Icons.Outlined.EmojiEmotions, "An empty pack", "Add photos of your crew — we'll square them up into stickers.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(p.stickers, key = { _, s -> s.url }) { i, s ->
                            AsyncImage(
                                model = s.url, contentDescription = "Sticker", contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(NookShapes.tile).background(c.pastels[i % c.pastels.size]),
                            )
                        }
                    }
                }
            }
        }
        if (error != null) Text(error.orEmpty(), color = c.danger, style = NookTheme.type.bodySmall, modifier = Modifier.padding(horizontal = Spacing.gutter))
        NookButton(
            if (uploading > 0) "Adding $uploading…" else "Add from photos",
            onClick = {
                lock.suppressNextLock()
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            loading = uploading > 0,
            icon = Icons.Outlined.AddPhotoAlternate,
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
