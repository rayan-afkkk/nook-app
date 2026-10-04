package com.nook.app.feature.stickers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TagFaces
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.nook.app.data.model.GiphyItem
import com.nook.app.data.model.Sticker
import com.nook.app.data.model.StickerPack
import com.nook.app.designsystem.components.DashedAddCard
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.NookBottomSheet
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookHeader
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.SegmentedControl
import com.nook.app.designsystem.components.SheetAction
import com.nook.app.designsystem.components.SheetTitle
import com.nook.app.designsystem.components.SkeletonList
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.chat.ForwardSheet
import com.nook.app.navigation.StickerPackRoute
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.math.PI
import kotlin.math.sin

private val Categories = listOf("Trending", "LOL", "Love", "Hype", "Sad", "Cute", "Party", "Food", "Animals", "Yes", "No", "Bye")
private val Ink = Color(0xFF1A1714)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickersTab(nav: NavHostController, vm: StickersViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val tab by vm.tab.collectAsStateWithLifecycle()
    val giphy by vm.giphy.state.collectAsStateWithLifecycle()
    val packs by vm.packs.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    var sendGiphy by remember { mutableStateOf<GiphyItem?>(null) }
    var sendSticker by remember { mutableStateOf<Sticker?>(null) }
    var newPack by rememberSaveable { mutableStateOf(false) }
    var activeCategory by rememberSaveable { mutableStateOf("Trending") }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); vm.clearToast() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NookHeader("Stickers", showHairline = false)
            Text(
                "Say it without\nsaying it.",
                style = NookTheme.type.headline,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            if (tab != 2) {
                FloatingCategories(activeCategory) { cat ->
                    activeCategory = cat
                    vm.category(if (cat == "Trending") "" else cat)
                }
                NookTextField(
                    value = giphy.query,
                    onValueChange = { activeCategory = ""; vm.giphy.setQuery(it) },
                    placeholder = if (tab == 0) "Search GIFs" else "Search stickers",
                    leadingIcon = Icons.Outlined.Search,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                )
            }
            SegmentedControl(
                listOf("GIFs", "Stickers", "Packs"), tab, vm::setTab,
                Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    2 -> PacksList(packs, onOpen = { nav.navigate(StickerPackRoute(it.id)) }, onSend = { sendSticker = it }, onNew = { newPack = true })
                    else -> if (!giphy.configured) {
                        EmptyState(Icons.Outlined.TagFaces, "GIFs need a key", "Add GIPHY_API_KEY to local.properties to browse GIFs and stickers.")
                    } else if (giphy.error != null && giphy.items.isEmpty()) {
                        EmptyState(Icons.Outlined.TagFaces, "Couldn't load", giphy.error.orEmpty())
                    } else {
                        GiphyGrid(
                            items = giphy.items,
                            loading = giphy.loading,
                            onPick = { sendGiphy = it },
                            onEndReached = vm.giphy::loadMore,
                            sticker = tab == 1,
                            columns = if (tab == 1) 3 else 2,
                            contentPadding = PaddingValues(Spacing.gutter, 8.dp, Spacing.gutter, Spacing.xxl),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            toast != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
        ) { NookPill(toast.orEmpty(), background = c.surfaceRaised) }
    }

    sendGiphy?.let { g ->
        ForwardSheet(onDismiss = { sendGiphy = null }, onSend = { ids -> vm.sendGiphy(g, tab == 1, ids); sendGiphy = null })
    }
    sendSticker?.let { s ->
        ForwardSheet(onDismiss = { sendSticker = null }, onSend = { ids -> vm.sendPackSticker(s, ids); sendSticker = null })
    }
    if (newPack) {
        NookBottomSheet(onDismiss = { newPack = false }) {
            SheetTitle("Which group is it for?")
            if (groups.isEmpty()) {
                Text("Sticker packs belong to a group. Create a group first.", style = NookTheme.type.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
            }
            groups.forEach { g ->
                SheetAction(Icons.Outlined.EmojiEmotions, g.name ?: "Group", {
                    newPack = false
                    vm.createPack(g, "") { id -> nav.navigate(StickerPackRoute(id)) }
                }, subtitle = "${g.members.size} members")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingCategories(active: String, onPick: (String) -> Unit) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val t = rememberInfiniteTransition(label = "cats")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "catPhase")
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Categories.forEachIndexed { i, cat ->
            val tilt = listOf(-6f, 4f, -3f, 7f, -5f, 3f)[i % 6]
            val bob = if (reduce) 0f else sin(phase + i * 0.8f) * 3f
            val selected = cat == active
            Box(
                Modifier
                    .graphicsLayer { rotationZ = tilt + bob * 0.4f; translationY = bob * density }
                    .clip(NookShapes.pill)
                    .background(if (selected) c.accent else c.pastels[i % c.pastels.size])
                    .clickable(role = Role.Button) { onPick(cat) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(cat, style = NookTheme.type.label, color = Ink) }
        }
    }
}

@Composable
private fun PacksList(packs: List<StickerPack>?, onOpen: (StickerPack) -> Unit, onSend: (Sticker) -> Unit, onNew: () -> Unit) {
    val c = NookTheme.colors
    if (packs == null) { SkeletonList(3); return }
    LazyColumn(contentPadding = PaddingValues(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item { DashedAddCard("Make a sticker pack", "Turn your group's photos into stickers", onClick = onNew) }
        if (packs.isEmpty()) {
            item { EmptyState(Icons.Outlined.EmojiEmotions, "No packs yet", "Packs you make with your groups show up here.") }
        }
        items(packs, key = { it.id }) { p ->
            NookCard(onClick = { onOpen(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = NookTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
                    NookPill("${p.stickers.size}", background = c.navy, contentColor = c.onNavy, bordered = false)
                }
                if (p.stickers.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(p.stickers.size) { i ->
                            val s = p.stickers[i]
                            AsyncImage(
                                model = s.url, contentDescription = "Sticker", contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(c.pastels[i % c.pastels.size])
                                    .clickable { onSend(s) },
                            )
                        }
                    }
                }
            }
        }
    }
}
