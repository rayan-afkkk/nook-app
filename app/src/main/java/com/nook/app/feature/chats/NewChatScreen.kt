package com.nook.app.feature.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nook.app.data.model.User
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.NookTopBar
import com.nook.app.designsystem.components.SectionLabel
import com.nook.app.designsystem.components.SegmentedControl
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.navigation.ChatRoute
import com.nook.app.navigation.NewChatRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NewChatScreen(
    group: Boolean,
    addToChatId: String?,
    nav: NavHostController,
    vm: NewChatViewModel = koinViewModel { parametersOf(addToChatId) },
) {
    val c = NookTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var groupMode by rememberSaveable { mutableStateOf(group || addToChatId != null) }
    val adding = addToChatId != null
    val multi = groupMode

    fun open(chatId: String) = nav.navigate(ChatRoute(chatId)) { popUpTo<NewChatRoute> { inclusive = true } }

    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding().imePadding()) {
        NookTopBar(if (adding) "Add people" else if (groupMode) "New group" else "New chat", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            if (!adding) {
                item {
                    SegmentedControl(
                        listOf("Direct", "Group"),
                        if (groupMode) 1 else 0,
                        { groupMode = it == 1 },
                        Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    )
                }
            }
            if (multi && !adding) {
                item {
                    NookTextField(
                        value = s.groupName,
                        onValueChange = vm::onGroupName,
                        placeholder = "Group name (optional)",
                        leadingIcon = Icons.Outlined.Groups,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                    )
                }
            }
            item {
                NookTextField(
                    value = s.query,
                    onValueChange = vm::onQuery,
                    placeholder = "Exact username",
                    leadingIcon = Icons.Outlined.AlternateEmail,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                )
            }
            if (multi && s.selected.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.padding(vertical = Spacing.xs),
                    ) {
                        items(s.selected, key = { it.uid }) { u -> SelectedChip(u) { vm.toggle(u) } }
                    }
                }
            }
            item {
                AnimatedContent(
                    s.search,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                    label = "search",
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                ) { search ->
                    when (search) {
                        UserSearch.Idle -> Spacer(Modifier.height(1.dp))
                        UserSearch.Searching -> Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp)
                        }
                        is UserSearch.Found -> PersonCard(
                            user = search.user,
                            selected = s.selected.any { it.uid == search.user.uid },
                            multi = multi,
                            onClick = { if (multi) vm.toggle(search.user) else vm.openDirect(search.user, ::open) },
                        )
                        UserSearch.NotFound -> Text("No one goes by @${s.query}", style = NookTheme.type.bodySmall, color = c.textMuted)
                        UserSearch.Self -> Text("That's you 👋", style = NookTheme.type.bodySmall, color = c.textMuted)
                        is UserSearch.Failed -> Text(search.message, style = NookTheme.type.bodySmall, color = c.danger)
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                item { SectionLabel("People you chat with", Modifier.padding(top = Spacing.md)) }
                items(suggestions, key = { "s_" + it.uid }) { u ->
                    PersonRow(u, selected = s.selected.any { it.uid == u.uid }, multi = multi) {
                        if (multi) vm.toggle(u) else vm.openDirect(u, ::open)
                    }
                }
            } else if (s.search == UserSearch.Idle) {
                item { EmptyState(Icons.Outlined.PersonSearch, "Find your people", "Type a friend's exact @username to start talking.") }
            }
        }
        AnimatedVisibility(s.error != null) {
            Text(s.error.orEmpty(), color = c.danger, style = NookTheme.type.bodySmall, modifier = Modifier.padding(horizontal = Spacing.gutter))
        }
        if (multi) {
            NookButton(
                text = if (adding) "Add ${s.selected.size} to group" else "Create group",
                onClick = { if (adding) vm.addToGroup { nav.popBackStack() } else vm.createGroup(::open) },
                enabled = s.selected.isNotEmpty(),
                loading = s.working,
                modifier = Modifier.padding(Spacing.gutter),
            )
        }
    }
}

@Composable
private fun PersonCard(user: User, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val c = NookTheme.colors
    NookCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.photoUrl, user.name, 52.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = NookTheme.type.titleSans, color = c.text)
                Text("@${user.username}", style = NookTheme.type.bodySmall, color = c.textMuted)
            }
            SelectMark(selected, multi)
        }
    }
}

@Composable
fun PersonRow(user: User, selected: Boolean, multi: Boolean, online: Boolean = false, onClick: () -> Unit) {
    val c = NookTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.photoUrl, user.name, 44.dp, online = online)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = NookTheme.type.bodyStrong, color = c.text)
            Text("@${user.username}", style = NookTheme.type.caption, color = c.textMuted)
        }
        SelectMark(selected, multi)
    }
}

@Composable
private fun SelectMark(selected: Boolean, multi: Boolean) {
    if (!multi) return
    val c = NookTheme.colors
    AnimatedContent(selected, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "sel") { sel ->
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (sel) c.accent else c.border),
            contentAlignment = Alignment.Center,
        ) { if (sel) Icon(Icons.Rounded.Check, "Selected", tint = c.onAccent, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun SelectedChip(user: User, onRemove: () -> Unit) {
    val c = NookTheme.colors
    Row(
        Modifier.clip(CircleShape).background(c.surface).clickable(onClick = onRemove).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.photoUrl, user.name, 28.dp)
        Spacer(Modifier.width(6.dp))
        Text(user.name.substringBefore(' '), style = NookTheme.type.label, color = c.text)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.Close, "Remove ${user.name}", tint = c.textMuted, modifier = Modifier.size(14.dp))
    }
}
