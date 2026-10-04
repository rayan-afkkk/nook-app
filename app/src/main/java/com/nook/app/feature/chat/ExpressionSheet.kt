package com.nook.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TagFaces
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.nook.app.data.model.GiphyItem
import com.nook.app.data.model.GiphyKind
import com.nook.app.data.model.Sticker
import com.nook.app.data.remote.GiphyApi
import com.nook.app.data.repo.StickerRepository
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.NookBottomSheet
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.SegmentedControl
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.stickers.GiphyBrowser
import com.nook.app.feature.stickers.GiphyGrid
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel

class ExpressionsViewModel(api: GiphyApi, stickers: StickerRepository) : ViewModel() {
    val giphy = GiphyBrowser(api, viewModelScope)
    val packs = stickers.myPacks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Emoji / GIFs / Stickers picker. In [reactionOnly] mode it's just the emoji grid. */
@Composable
fun ExpressionSheet(
    onDismiss: () -> Unit,
    onEmoji: (String) -> Unit,
    onGif: (GiphyItem, Boolean) -> Unit = { _, _ -> },
    onPackSticker: (Sticker) -> Unit = {},
    reactionOnly: Boolean = false,
    vm: ExpressionsViewModel = koinViewModel(),
) {
    val c = NookTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val giphy by vm.giphy.state.collectAsStateWithLifecycle()
    val packs by vm.packs.collectAsStateWithLifecycle()
    LaunchedEffect(tab) {
        when (tab) {
            1 -> vm.giphy.start(GiphyKind.GIFS)
            2 -> vm.giphy.start(GiphyKind.STICKERS)
        }
    }
    NookBottomSheet(onDismiss = onDismiss) {
        if (!reactionOnly) {
            SegmentedControl(listOf("Emoji", "GIFs", "Stickers"), tab, { tab = it }, Modifier.padding(horizontal = Spacing.gutter))
        }
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            when (tab) {
                0 -> EmojiGrid(onEmoji)
                else -> Column {
                    NookTextField(
                        value = giphy.query,
                        onValueChange = vm.giphy::setQuery,
                        placeholder = if (tab == 1) "Search GIFs" else "Search stickers",
                        leadingIcon = Icons.Outlined.Search,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    )
                    if (tab == 2 && packs.any { it.stickers.isNotEmpty() } && giphy.query.isBlank()) {
                        PackStrip(packs.flatMap { it.stickers }.take(24), onPackSticker)
                    }
                    if (!giphy.configured) {
                        EmptyState(Icons.Outlined.TagFaces, "GIFs need a key", "Add GIPHY_API_KEY to local.properties to browse GIFs and stickers.")
                    } else {
                        GiphyGrid(
                            items = giphy.items,
                            loading = giphy.loading,
                            onPick = { onGif(it, tab == 2) },
                            onEndReached = vm.giphy::loadMore,
                            sticker = tab == 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackStrip(stickers: List<Sticker>, onPick: (Sticker) -> Unit) {
    val c = NookTheme.colors
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.gutter),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(stickers.size) { i ->
            val s = stickers[i]
            AsyncImage(
                model = s.url,
                contentDescription = "Group sticker",
                contentScale = ContentScale.Crop,
                modifier = Modifier.height(64.dp).aspectRatio(1f).clip(NookShapes.tile)
                    .background(c.pastels[i % c.pastels.size]).clickable { onPick(s) },
            )
        }
    }
}

@Composable
fun EmojiGrid(onEmoji: (String) -> Unit) {
    val c = NookTheme.colors
    LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        EmojiCatalog.categories.forEach { (name, list) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(name, style = NookTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
            }
            items(list.filter { it.isNotBlank() }) { e ->
                Box(
                    Modifier.aspectRatio(1f).clip(CircleShape).clickable(onClickLabel = e) { onEmoji(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, style = NookTheme.type.title) }
            }
        }
    }
}
