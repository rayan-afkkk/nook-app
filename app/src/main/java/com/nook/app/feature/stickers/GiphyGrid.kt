package com.nook.app.feature.stickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nook.app.data.model.GiphyItem
import com.nook.app.designsystem.components.shimmer
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme

/** Pastel-tiled Giphy grid with infinite scroll and shimmer placeholders. */
@Composable
fun GiphyGrid(
    items: List<GiphyItem>,
    loading: Boolean,
    onPick: (GiphyItem) -> Unit,
    onEndReached: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    sticker: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    state: LazyGridState = rememberLazyGridState(),
    spacing: Dp = 8.dp,
) {
    val c = NookTheme.colors
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) onEndReached() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (loading && items.isEmpty()) {
            items(columns * 4) { Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(NookShapes.tile).shimmer()) }
        }
        itemsIndexed(items, key = { _, g -> g.id }) { i, g ->
            val pastel = c.pastels[i % c.pastels.size]
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(NookShapes.tile)
                    .background(if (sticker) pastel else c.surfaceRaised)
                    .clickable(onClickLabel = g.title.ifBlank { "Send" }) { onPick(g) },
            ) {
                AsyncImage(
                    model = g.previewUrl,
                    contentDescription = g.title.ifBlank { if (sticker) "Sticker" else "GIF" },
                    contentScale = if (sticker) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(if (sticker) 10.dp else 0.dp),
                )
            }
        }
    }
}
