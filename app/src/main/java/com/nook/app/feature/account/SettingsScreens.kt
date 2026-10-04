package com.nook.app.feature.account

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.nook.app.data.model.User
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.security.AppLockManager
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.FieldHint
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookButtonStyle
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.NookSwitch
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.NookTopBar
import com.nook.app.designsystem.components.SettingsRow
import com.nook.app.designsystem.components.SkeletonList
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.lock.Biometrics
import com.nook.app.navigation.SetPasswordRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// ---------- Edit profile ----------

@Composable
fun EditProfileScreen(nav: NavHostController, vm: AccountViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val me by vm.me.collectAsStateWithLifecycle()
    val users: UserRepository = koinInject()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(me?.uid) { mutableStateOf(me?.displayName.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(me?.displayName) { if (name.isBlank()) name = me?.displayName.orEmpty() }
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding().imePadding()) {
        NookTopBar("Edit profile", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.gutter)) {
            Avatar(me?.photoUrl, me?.name ?: "?", 88.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Spacing.xl))
            NookTextField(name, { name = it.take(40); error = null }, "Display name", leadingIcon = Icons.Outlined.Person, isError = error != null)
            FieldHint(error ?: "This is what friends see.", isError = error != null)
            Spacer(Modifier.height(Spacing.sm))
            NookTextField("@" + me?.username.orEmpty(), {}, "Username", leadingIcon = Icons.Outlined.AlternateEmail, enabled = false,
                trailing = { androidx.compose.material3.Icon(Icons.Outlined.Lock, "Permanent", tint = c.textMuted) })
            FieldHint("Usernames are permanent so nobody can take yours.")
        }
        NookButton(
            "Save", loading = saving, enabled = name.isNotBlank() && name != me?.displayName,
            onClick = {
                saving = true
                scope.launch {
                    runCatching { users.updateDisplayName(name) }
                        .onSuccess { nav.popBackStack() }
                        .onFailure { error = it.message ?: "Couldn't save" }
                    saving = false
                }
            },
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

// ---------- App lock ----------

@Composable
fun AppLockSettingsScreen(nav: NavHostController) {
    val c = NookTheme.colors
    val lock: AppLockManager = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometric by lock.biometricEnabled.collectAsStateWithLifecycle()
    val available = remember { Biometrics.available(context) }
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar("App lock", onBack = { nav.popBackStack() })
        Column(Modifier.padding(vertical = Spacing.md)) {
            NookCard(Modifier.padding(horizontal = Spacing.gutter), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                SettingsRow(Icons.Outlined.Lock, "Change password", subtitle = "Stored only on this phone, as a salted hash", onClick = { nav.navigate(SetPasswordRoute(change = true)) })
                if (available) {
                    SettingsRow(
                        Icons.Outlined.Fingerprint, "Unlock with fingerprint",
                        onClick = { scope.launch { lock.setBiometric(!biometric) } },
                        trailing = { NookSwitch(biometric, { on -> scope.launch { lock.setBiometric(on) } }) },
                    )
                }
                SettingsRow(Icons.Outlined.LockClock, "Auto-lock", subtitle = "On every launch and after 30 seconds away", onClick = null)
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Forgot your password? Sign out and sign back in with Google to set a new one. Nook also hides its preview in Recents and blocks screenshots.",
                style = NookTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
        }
    }
}

// ---------- Blocked users ----------

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedUsersViewModel(private val users: UserRepository) : ViewModel() {
    val blocked: StateFlow<List<User>?> = users.observePrivateSettings().flatMapLatest { s ->
        if (s.blocked.isEmpty()) flowOf(emptyList())
        else combine(s.blocked.map { users.observeUser(it) }) { arr -> arr.filterNotNull() }
    }.map<List<User>, List<User>?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun unblock(uid: String) { viewModelScope.launch { runCatching { users.unblock(uid) } } }
}

@Composable
fun BlockedUsersScreen(nav: NavHostController, vm: BlockedUsersViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val list by vm.blocked.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar("Blocked users", onBack = { nav.popBackStack() })
        when (val l = list) {
            null -> SkeletonList(3)
            else -> if (l.isEmpty()) {
                EmptyState(Icons.Outlined.Block, "Nobody blocked", "People you block can't message you and won't send you notifications.")
            } else {
                LazyColumn {
                    items(l, key = { it.uid }) { u ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.photoUrl, u.name, 44.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(u.name, style = NookTheme.type.bodyStrong, color = c.text)
                                Text("@${u.username}", style = NookTheme.type.caption, color = c.textMuted)
                            }
                            NookPill("Unblock", onClick = { vm.unblock(u.uid) })
                        }
                    }
                }
            }
        }
    }
}

// ---------- Notifications ----------

@Composable
fun NotificationSettingsScreen(nav: NavHostController, vm: AccountViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val context = LocalContext.current
    val settings by vm.privateSettings.collectAsStateWithLifecycle()
    val users: UserRepository = koinInject()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val nm = context.getSystemService(NotificationManager::class.java)
    val systemEnabled = remember(refresh) { nm.areNotificationsEnabled() }
    val fullScreen = remember(refresh) { if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true }

    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar("Notifications", onBack = { nav.popBackStack() })
        Column(Modifier.padding(vertical = Spacing.md)) {
            NookCard(Modifier.padding(horizontal = Spacing.gutter), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                SettingsRow(
                    if (settings.notificationsEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    "Message notifications",
                    subtitle = "“Ali sent you a message” — never the message itself",
                    onClick = { scope.launch { runCatching { users.setNotificationsEnabled(!settings.notificationsEnabled) } } },
                    trailing = { NookSwitch(settings.notificationsEnabled, { on -> scope.launch { runCatching { users.setNotificationsEnabled(on) } } }) },
                )
                SettingsRow(
                    Icons.Outlined.Settings, "System settings",
                    subtitle = if (systemEnabled) "Allowed" else "Blocked by Android — tap to allow",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    },
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    SettingsRow(
                        Icons.Outlined.Call, "Full-screen calls",
                        subtitle = if (fullScreen) "Allowed" else "Tap to let calls ring full-screen",
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                if (settings.mutedChats.isEmpty()) "No muted chats. Mute a chat from its info screen."
                else "${settings.mutedChats.size} muted chat(s). Unmute from each chat's info screen.",
                style = NookTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
        }
    }
}

// ---------- Legal ----------

@Composable
fun LegalScreen(kind: String, onBack: () -> Unit) {
    val c = NookTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        NookTopBar("Privacy & terms", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.gutter)) {
            LegalSection("What Nook is", "A private chat app for a small group of friends. There are no ads, no tracking SDKs and no public profiles.")
            LegalSection("Where your data lives", "Messages, profiles and chat metadata are stored in Google Firebase (Firestore and Realtime Database). Photos, voice notes, files and stickers are stored on Cloudinary. Calls are relayed through LiveKit.")
            LegalSection("Not end-to-end encrypted", "Data is encrypted in transit and at rest by those providers, but it is NOT end-to-end encrypted. Whoever administers the Firebase, Cloudinary and LiveKit accounts can technically access it.")
            LegalSection("Notifications", "Push notifications only ever say who messaged you (\"Ali sent you a photo\"). Message content is never included in notification payloads or server logs.")
            LegalSection("App lock", "Your app password is stored only on your phone as a salted PBKDF2 hash. We can't see or recover it — sign out and back in to reset it.")
            LegalSection("Disappearing messages", "When enabled, messages are hidden immediately once they expire and are deleted from the database (and their media from Cloudinary) shortly after.")
            LegalSection("Deleting your account", "Account → Delete account removes your profile, username, device tokens and every message you've sent.")
            LegalSection("Be kind", "Don't use Nook to harass anyone or share things you don't have the right to share. Group admins may remove people.")
            if (kind != "privacy") Text(kind, color = c.textMuted)
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    Text(title, style = NookTheme.type.title, color = NookTheme.colors.text)
    Spacer(Modifier.height(6.dp))
    Text(body, style = NookTheme.type.body, color = NookTheme.colors.textMuted)
    Spacer(Modifier.height(Spacing.xl))
}

@Suppress("unused")
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) = NookButton(text, onClick, style = NookButtonStyle.Secondary)
