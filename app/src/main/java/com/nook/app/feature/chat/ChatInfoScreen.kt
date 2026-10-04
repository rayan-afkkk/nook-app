package com.nook.app.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nook.app.data.model.Disappearing
import com.nook.app.data.security.AppLockManager
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.Hairline
import com.nook.app.designsystem.components.LoadingBox
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookConfirmDialog
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.NookSwitch
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.NookTopBar
import com.nook.app.designsystem.components.SectionLabel
import com.nook.app.designsystem.components.SegmentedControl
import com.nook.app.designsystem.components.SettingsRow
import com.nook.app.designsystem.components.breathingGlow
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.navigation.CropRoute
import com.nook.app.navigation.CropTargets
import com.nook.app.navigation.MainRoute
import com.nook.app.navigation.NewChatRoute
import com.nook.app.navigation.StickerPackRoute
import com.nook.app.util.TimeFormat
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun ChatInfoScreen(chatId: String, nav: NavHostController, vm: ChatInfoViewModel = koinViewModel { parametersOf(chatId) }) {
    val c = NookTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val packs by vm.packs.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val lock: AppLockManager = koinInject()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var newPack by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        lock.clearSuppression()
        if (uri != null) nav.navigate(CropRoute(uri.toString(), CropTargets.group(chatId)))
    }

    val chat = s.chat
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar(if (chat?.isGroup == true) "Group info" else "Chat info", onBack = { nav.popBackStack() })
        if (s.loading || chat == null) { LoadingBox(); return@Column }
        val other = s.members.firstOrNull { !it.isMe }
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.breathingGlow(c.peach, 110.dp)) {
                        Avatar(if (chat.isGroup) chat.photoUrl else other?.user?.photoUrl, if (chat.isGroup) chat.name ?: "G" else other?.user?.name ?: "?", 104.dp, online = !chat.isGroup && other?.presence?.online == true)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text(if (chat.isGroup) chat.name ?: "Group" else other?.user?.name ?: "", style = NookTheme.type.display, color = c.text)
                    Text(
                        if (chat.isGroup) "${chat.members.size} members" else other?.let { "@${it.user.username} · " + if (it.presence.online) "Online" else TimeFormat.lastSeen(it.presence.lastSeen) } ?: "",
                        style = NookTheme.type.bodySmall, color = c.textMuted,
                    )
                    if (chat.isGroup) {
                        Spacer(Modifier.height(Spacing.md))
                        Row {
                            NookPill("Rename", icon = Icons.Outlined.Edit, onClick = { renaming = true })
                            Spacer(Modifier.width(Spacing.xs))
                            NookPill("Photo", icon = Icons.Outlined.Image, onClick = {
                                lock.suppressNextLock()
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                    }
                }
            }
            item {
                NookCard(Modifier.padding(horizontal = Spacing.gutter), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    SettingsRow(Icons.Outlined.NotificationsOff, "Mute notifications", onClick = { vm.setMuted(!s.muted) }, trailing = { NookSwitch(s.muted, vm::setMuted) })
                    Hairline(Modifier.padding(horizontal = Spacing.gutter))
                    Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Outlined.Timer, null, tint = c.text)
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text("Disappearing messages", style = NookTheme.type.bodyStrong, color = c.text)
                                Text("New messages vanish for everyone after this time.", style = NookTheme.type.caption, color = c.textMuted)
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        SegmentedControl(Disappearing.entries.map { it.label }, chat.disappearing.ordinal, { vm.setDisappearing(Disappearing.entries[it]) })
                    }
                }
            }
            if (chat.isGroup) {
                item { SectionLabel("Sticker packs", Modifier.padding(top = Spacing.lg)) }
                items(packs, key = { it.id }) { p ->
                    SettingsRow(Icons.Outlined.EmojiEmotions, p.name, subtitle = "${p.stickers.size} stickers", onClick = { nav.navigate(StickerPackRoute(p.id)) })
                }
                item { SettingsRow(Icons.Outlined.EmojiEmotions, "New sticker pack", subtitle = "Make stickers from your photos", onClick = { newPack = true }, tint = c.accent) }
                item { SectionLabel("Members", Modifier.padding(top = Spacing.lg)) }
                item { SettingsRow(Icons.Outlined.PersonAdd, "Add people", onClick = { nav.navigate(NewChatRoute(group = true, addToChatId = chatId)) }, tint = c.accent) }
                items(s.members, key = { it.user.uid }) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.user.photoUrl, m.user.name, 44.dp, online = m.presence.online)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(if (m.isMe) "${m.user.name} (you)" else m.user.name, style = NookTheme.type.bodyStrong, color = c.text)
                            Text("@${m.user.username}", style = NookTheme.type.caption, color = c.textMuted)
                        }
                        if (m.isAdmin) NookPill("Admin", background = c.navy, contentColor = c.onNavy, bordered = false)
                    }
                }
                item {
                    SettingsRow(Icons.AutoMirrored.Outlined.ExitToApp, "Leave group", onClick = { confirmLeave = true }, tint = c.danger, modifier = Modifier.padding(top = Spacing.lg))
                }
            } else {
                item {
                    SettingsRow(Icons.Outlined.Block, if (s.blocked) "Unblock" else "Block", onClick = { if (s.blocked) vm.setBlocked(false) else confirmBlock = true }, tint = c.danger, modifier = Modifier.padding(top = Spacing.lg))
                }
            }
            if (error != null) item { Text(error.orEmpty(), color = c.danger, style = NookTheme.type.bodySmall, modifier = Modifier.padding(Spacing.gutter)) }
        }
    }

    if (renaming) {
        var name by rememberSaveable { mutableStateOf(s.chat?.name.orEmpty()) }
        Dialog(onDismissRequest = { renaming = false }) {
            NookCard {
                Text("Rename group", style = NookTheme.type.headline, color = c.text)
                Spacer(Modifier.height(Spacing.md))
                NookTextField(name, { name = it.take(40) }, "Group name")
                Spacer(Modifier.height(Spacing.md))
                NookButton("Save", { vm.rename(name); renaming = false }, enabled = name.isNotBlank())
            }
        }
    }
    if (newPack) {
        var name by rememberSaveable { mutableStateOf("") }
        Dialog(onDismissRequest = { newPack = false }) {
            NookCard {
                Text("New sticker pack", style = NookTheme.type.headline, color = c.text)
                Spacer(Modifier.height(Spacing.md))
                NookTextField(name, { name = it.take(30) }, "Pack name")
                Spacer(Modifier.height(Spacing.md))
                NookButton("Create", { newPack = false; vm.createPack(name) { id -> nav.navigate(StickerPackRoute(id)) } })
            }
        }
    }
    if (confirmLeave) {
        NookConfirmDialog(
            "Leave group?", "You won't get new messages from this group.", "Leave",
            onConfirm = { confirmLeave = false; vm.leave { nav.navigate(MainRoute) { popUpTo<MainRoute> { inclusive = true } } } },
            onDismiss = { confirmLeave = false }, destructive = true,
        )
    }
    if (confirmBlock) {
        NookConfirmDialog(
            "Block ${s.members.firstOrNull { !it.isMe }?.user?.name ?: "them"}?",
            "They won't be able to reach you here and you won't get their notifications.", "Block",
            onConfirm = { confirmBlock = false; vm.setBlocked(true) },
            onDismiss = { confirmBlock = false }, destructive = true,
        )
    }
}
