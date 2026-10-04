package com.nook.app.feature.friends

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nook.app.data.model.User
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.DashedAddCard
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.NookBottomSheet
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookConfirmDialog
import com.nook.app.designsystem.components.NookHeader
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.OfflineBanner
import com.nook.app.designsystem.components.SectionLabel
import com.nook.app.designsystem.components.SheetAction
import com.nook.app.designsystem.components.SheetTitle
import com.nook.app.designsystem.components.SkeletonList
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.chats.PersonRow
import com.nook.app.feature.chats.UserSearch
import com.nook.app.navigation.ChatRoute
import com.nook.app.util.TimeFormat
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun FriendsTab(nav: NavHostController, vm: FriendsViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<User?>(null) }
    var pickGroupFor by remember { mutableStateOf<User?>(null) }
    var confirmBlock by remember { mutableStateOf<User?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(message) { if (message != null) { delay(2000); vm.clearMessage() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NookHeader("Friends") {
                NookIconButton(Icons.Outlined.PersonAddAlt, "Find a friend", { runCatching { focus.requestFocus() } })
            }
            OfflineBanner(!online)
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                item {
                    NookTextField(
                        value = query,
                        onValueChange = vm::onQuery,
                        placeholder = "Find by exact @username",
                        leadingIcon = Icons.Outlined.AlternateEmail,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).focusRequester(focus),
                    )
                }
                item {
                    AnimatedContent(search, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() }, label = "fsearch") { s ->
                        when (s) {
                            is UserSearch.Found -> FoundCard(
                                s.user,
                                onMessage = { vm.message(s.user) { id -> nav.navigate(ChatRoute(id)) } },
                                onCall = { vm.call(s.user, false) },
                                onGroup = { pickGroupFor = s.user },
                                onBlock = { confirmBlock = s.user },
                            )
                            UserSearch.NotFound -> Hint("No one goes by @$query")
                            UserSearch.Self -> Hint("That's you 👋")
                            is UserSearch.Failed -> Hint(s.message)
                            else -> Spacer(Modifier.height(1.dp))
                        }
                    }
                }
                when (val list = contacts) {
                    null -> item { SkeletonList(4) }
                    else -> if (list.isEmpty()) {
                        item {
                            Column(Modifier.padding(Spacing.gutter)) {
                                DashedAddCard("Find your first friend", "Ask for their @username and search above", onClick = { runCatching { focus.requestFocus() } })
                                EmptyState(Icons.Outlined.People, "Your people live here", "Everyone you chat with shows up here, with who's online right now.")
                            }
                        }
                    } else {
                        val onlineCount = list.count { it.presence.online }
                        item { SectionLabel(if (onlineCount > 0) "People you chat with · $onlineCount online" else "People you chat with", Modifier.padding(top = Spacing.sm)) }
                        items(list, key = { it.user.uid }) { contact ->
                            Row(Modifier.animateItem()) {
                                PersonRow(contact.user, selected = false, multi = false, online = contact.presence.online) { actionsFor = contact.user }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            message != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
        ) { NookPill(message.orEmpty(), background = c.surfaceRaised) }
    }

    actionsFor?.let { u ->
        val contact = contacts?.firstOrNull { it.user.uid == u.uid }
        NookBottomSheet(onDismiss = { actionsFor = null }) {
            Row(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Avatar(u.photoUrl, u.name, 56.dp, online = contact?.presence?.online == true)
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(u.name, style = NookTheme.type.title, color = c.text)
                    Text(
                        "@${u.username}" + (contact?.presence?.let { if (it.online) " · Online" else " · " + TimeFormat.lastSeen(it.lastSeen) } ?: ""),
                        style = NookTheme.type.bodySmall, color = c.textMuted,
                    )
                }
            }
            SheetAction(Icons.Outlined.ChatBubbleOutline, "Message", { actionsFor = null; vm.message(u) { id -> nav.navigate(ChatRoute(id)) } })
            SheetAction(Icons.Outlined.Call, "Voice call", { actionsFor = null; vm.call(u, false) })
            SheetAction(Icons.Outlined.Videocam, "Video call", { actionsFor = null; vm.call(u, true) })
            SheetAction(Icons.Outlined.GroupAdd, "Add to group", { actionsFor = null; pickGroupFor = u })
            SheetAction(Icons.Outlined.Block, "Block", { actionsFor = null; confirmBlock = u }, tint = c.danger)
        }
    }
    pickGroupFor?.let { u ->
        NookBottomSheet(onDismiss = { pickGroupFor = null }) {
            SheetTitle("Add ${u.name.substringBefore(' ')} to…")
            if (groups.isEmpty()) {
                Text("You're not in any groups yet. Create one from Chats → compose.", style = NookTheme.type.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
            }
            groups.forEach { g ->
                SheetAction(Icons.Outlined.People, g.name ?: "Group", { pickGroupFor = null; vm.addToGroup(u, g) }, subtitle = "${g.members.size} members")
            }
        }
    }
    confirmBlock?.let { u ->
        NookConfirmDialog(
            "Block ${u.name}?", "They won't be able to reach you and you won't see their messages.", "Block",
            onConfirm = { vm.block(u); confirmBlock = null }, onDismiss = { confirmBlock = null }, destructive = true,
        )
    }
}

@Composable
private fun FoundCard(user: User, onMessage: () -> Unit, onCall: () -> Unit, onGroup: () -> Unit, onBlock: () -> Unit) {
    val c = NookTheme.colors
    NookCard(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.photoUrl, user.name, 52.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = NookTheme.type.titleSans, color = c.text)
                Text("@${user.username}", style = NookTheme.type.bodySmall, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            NookPill("Message", icon = Icons.Outlined.ChatBubbleOutline, background = c.text, contentColor = c.background, bordered = false, onClick = onMessage)
            NookPill("Call", icon = Icons.Outlined.Call, onClick = onCall)
            NookPill("Group", icon = Icons.Outlined.GroupAdd, onClick = onGroup)
            NookPill("Block", icon = Icons.Outlined.Block, contentColor = c.danger, onClick = onBlock)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = NookTheme.type.bodySmall, color = NookTheme.colors.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter + 4.dp, vertical = Spacing.xs))
}
