package com.nook.app.feature.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TagFaces
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nook.app.data.model.MessageType
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.DashedAddCard
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.ErrorState
import com.nook.app.designsystem.components.NookHeader
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.OfflineBanner
import com.nook.app.designsystem.components.SegmentedControl
import com.nook.app.designsystem.components.SkeletonList
import com.nook.app.designsystem.components.pressScale
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.navigation.ChatRoute
import com.nook.app.navigation.NewChatRoute
import com.nook.app.util.TimeFormat
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatsTab(nav: NavHostController, vm: ChatsViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        NookHeader("Chats") {
            NookIconButton(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, if (searching) "Close search" else "Search chats", { vm.setSearching(!searching) })
            NookIconButton(Icons.Outlined.EditNote, "New chat", { nav.navigate(NewChatRoute()) })
        }
        OfflineBanner(!online)
        AnimatedVisibility(searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            NookTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = "Search chats",
                leadingIcon = Icons.Outlined.Search,
                modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.sm),
            )
        }
        SegmentedControl(
            options = ChatFilter.entries.map { it.label },
            selectedIndex = filter.ordinal,
            onSelect = { vm.setFilter(ChatFilter.entries[it]) },
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        )
        when (val s = state) {
            ChatsUiState.Loading -> SkeletonList()
            is ChatsUiState.Error -> ErrorState(s.message, onRetry = null)
            is ChatsUiState.Ready -> {
                if (s.totalChats == 0) {
                    Column(Modifier.padding(Spacing.gutter)) {
                        DashedAddCard("Start your first chat", "Find a friend by their @username", onClick = { nav.navigate(NewChatRoute()) })
                        EmptyState(Icons.Outlined.ChatBubbleOutline, "It's quiet in here", "Your conversations will live here. Say hi to someone.")
                    }
                } else if (s.rows.isEmpty()) {
                    EmptyState(
                        if (query.isNotBlank()) Icons.Outlined.SearchOff else Icons.Outlined.ChatBubbleOutline,
                        if (query.isNotBlank()) "No matches" else "Nothing here",
                        when {
                            query.isNotBlank() -> "No chats match “$query”."
                            filter == ChatFilter.Unread -> "You're all caught up."
                            filter == ChatFilter.Groups -> "No groups yet. Start one from the compose button."
                            else -> "No direct chats yet."
                        },
                    )
                } else {
                    // Keep the newest row in view when a message bumps a chat to the top.
                    val firstId = s.rows.firstOrNull()?.chatId
                    LaunchedEffect(firstId) {
                        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.xl), modifier = Modifier.fillMaxSize()) {
                        items(s.rows, key = { it.chatId }) { row ->
                            ChatRow(
                                row,
                                onClick = { nav.navigate(ChatRoute(row.chatId)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatRow(row: ChatRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // Highlight pulse whenever this chat gets a newer message.
    val pulse = remember { Animatable(0f) }
    val lastSeenTime = remember { longArrayOf(row.time) }
    LaunchedEffect(row.time) {
        if (row.time > lastSeenTime[0] && lastSeenTime[0] != 0L) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(1200))
        }
        lastSeenTime[0] = row.time
    }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .drawBehind { if (pulse.value > 0f) drawRect(c.accent.copy(alpha = 0.14f * pulse.value)) }
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(row.title); if (row.online) append(", online"); if (row.unread) append(", unread")
                    append(". ${row.preview}. ${TimeFormat.listTime(row.time)}")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.photoUrl, row.title, 46.dp, online = row.online)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    style = NookTheme.type.titleSans,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    TimeFormat.listTime(row.time),
                    style = NookTheme.type.caption,
                    color = if (row.unread) c.accent else c.textMuted,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                previewIcon(row.previewType)?.let {
                    Icon(it, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                }
                Text(
                    row.preview,
                    style = if (row.unread) NookTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold) else NookTheme.type.bodySmall,
                    color = if (row.unread) c.text else c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.unread) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
                } else if (row.previewIsMine) {
                    Icon(Icons.Rounded.Done, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun previewIcon(type: MessageType?): ImageVector? = when (type) {
    MessageType.IMAGE -> Icons.Rounded.Image
    MessageType.VOICE -> Icons.Rounded.Mic
    MessageType.GIF -> Icons.Rounded.Gif
    MessageType.STICKER -> Icons.Rounded.TagFaces
    MessageType.FILE -> Icons.Rounded.AttachFile
    else -> null
}
