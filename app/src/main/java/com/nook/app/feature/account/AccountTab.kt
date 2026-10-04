package com.nook.app.feature.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nook.app.BuildConfig
import com.nook.app.data.model.Disappearing
import com.nook.app.data.repo.CacheRepository
import com.nook.app.data.security.AppLockManager
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.Hairline
import com.nook.app.designsystem.components.NookBottomSheet
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookButtonStyle
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookConfirmDialog
import com.nook.app.designsystem.components.NookHeader
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.ProgressBar
import com.nook.app.designsystem.components.SectionLabel
import com.nook.app.designsystem.components.SegmentedControl
import com.nook.app.designsystem.components.SettingsRow
import com.nook.app.designsystem.components.SheetAction
import com.nook.app.designsystem.components.SheetTitle
import com.nook.app.designsystem.components.glow
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.designsystem.theme.ThemeMode
import com.nook.app.navigation.AppLockSettingsRoute
import com.nook.app.navigation.BlockedUsersRoute
import com.nook.app.navigation.CropRoute
import com.nook.app.navigation.CropTargets
import com.nook.app.navigation.EditProfileRoute
import com.nook.app.navigation.LegalRoute
import com.nook.app.navigation.LoginRoute
import com.nook.app.navigation.NotificationSettingsRoute
import com.nook.app.navigation.OnboardingRoute
import com.nook.app.navigation.resetTo
import com.nook.app.util.formatBytes
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@Composable
fun AccountTab(nav: NavHostController, vm: AccountViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val context = LocalContext.current
    val me by vm.me.collectAsStateWithLifecycle()
    val settings by vm.privateSettings.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val lock: AppLockManager = koinInject()
    var photoSheet by remember { mutableStateOf(false) }
    var disappearingSheet by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) { vm.refreshStorage(); onPauseOrDispose { } }
    LaunchedEffect(message) { if (message != null) { delay(1800); vm.clearMessage() } }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        lock.clearSuppression()
        if (uri != null) nav.navigate(CropRoute(uri.toString(), CropTargets.AVATAR))
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        lock.clearSuppression()
        val u = cameraUri
        if (ok && u != null) nav.navigate(CropRoute(u, CropTargets.AVATAR))
    }
    fun launchCamera() {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "avatar_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        cameraUri = uri.toString()
        lock.suppressNextLock()
        takePhoto.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NookHeader("Account")
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                item {
                    // Profile card
                    NookCard(Modifier.padding(Spacing.gutter)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.clickable(role = Role.Button, onClickLabel = "Change photo") { photoSheet = true },
                            ) {
                                Avatar(me?.photoUrl, me?.name ?: "?", 76.dp, contentDescription = "Your profile photo")
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(c.accent)
                                        .border(2.dp, c.surface, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.CameraAlt, null, tint = c.onAccent, modifier = Modifier.size(15.dp)) }
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(me?.name ?: "", style = NookTheme.type.headline, color = c.text)
                                Text("@${me?.username.orEmpty()}", style = NookTheme.type.bodySmall, color = c.textMuted)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    NookPill("Member", background = c.mint, contentColor = androidx.compose.ui.graphics.Color(0xFF1A1714), bordered = false)
                                    NookPill("Edit", onClick = { nav.navigate(EditProfileRoute) })
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Change photo", style = NookTheme.type.label, color = c.accent, modifier = Modifier.clickable { photoSheet = true }.padding(vertical = 6.dp))
                    }
                }
                item {
                    // Device card (navy highlight)
                    val s = storage
                    NookCard(Modifier.padding(horizontal = Spacing.gutter).glow(c.navy, 160.dp, 0.25f), background = c.navy, bordered = false) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("This device", style = NookTheme.type.title, color = c.onNavy, modifier = Modifier.weight(1f))
                            NookPill("Clear cache", background = c.onNavy, contentColor = c.navy, bordered = false, onClick = vm::clearCache)
                        }
                        Spacer(Modifier.height(Spacing.md))
                        StorageBar("Media cache", s?.mediaCacheBytes ?: 0, CacheRepository.MEDIA_CACHE_BUDGET, c.peach)
                        Spacer(Modifier.height(Spacing.sm))
                        StorageBar("Offline messages", s?.offlineDataBytes ?: 0, CacheRepository.OFFLINE_BUDGET, c.sky)
                    }
                }
                item {
                    Column {
                        SectionLabel("Appearance", Modifier.padding(top = Spacing.lg))
                        SegmentedControl(
                            listOf("Dark", "Light", "System"),
                            theme.ordinal,
                            { vm.setTheme(ThemeMode.entries[it]) },
                            Modifier.padding(horizontal = Spacing.gutter),
                        )
                    }
                }
                item { Column {
                    SectionLabel("Privacy & settings", Modifier.padding(top = Spacing.lg))
                    Chevron(Icons.Outlined.Lock, "App lock", "Password, fingerprint, auto-lock") { nav.navigate(AppLockSettingsRoute) }
                    Chevron(Icons.Outlined.Notifications, "Notifications", if (settings.notificationsEnabled) "On" else "Off") { nav.navigate(NotificationSettingsRoute) }
                    Chevron(Icons.Outlined.Timer, "Disappearing default", settings.disappearingDefault.label) { disappearingSheet = true }
                    Chevron(Icons.Outlined.Block, "Blocked users", if (settings.blocked.isEmpty()) "None" else "${settings.blocked.size} blocked") { nav.navigate(BlockedUsersRoute) }
                    SectionLabel("Nook", Modifier.padding(top = Spacing.lg))
                    Chevron(Icons.Outlined.AutoAwesome, "View onboarding", null) { nav.navigate(OnboardingRoute(replay = true)) }
                    Chevron(Icons.AutoMirrored.Outlined.HelpOutline, "Help & feedback", null) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, "Nook feedback (v${BuildConfig.VERSION_NAME})"))
                        }
                    }
                    Chevron(Icons.Outlined.Policy, "Privacy & terms", null) { nav.navigate(LegalRoute("privacy")) }
                } }
                item {
                    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xl)) {
                        NookButton("Sign out", { confirmSignOut = true }, style = NookButtonStyle.Secondary, icon = Icons.AutoMirrored.Outlined.Logout)
                        Spacer(Modifier.height(Spacing.sm))
                        NookButton("Delete account", { confirmDelete = true }, style = NookButtonStyle.Destructive, icon = Icons.Outlined.DeleteForever)
                        Spacer(Modifier.height(Spacing.lg))
                        Text("Nook ${BuildConfig.VERSION_NAME} · made for the crew", style = NookTheme.type.caption, color = c.textMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
        AnimatedVisibility(
            message != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
        ) { NookPill(message.orEmpty(), background = c.surfaceRaised) }
    }

    if (photoSheet) {
        NookBottomSheet(onDismiss = { photoSheet = false }) {
            SheetTitle("Profile photo")
            SheetAction(Icons.Outlined.CameraAlt, "Take photo", {
                photoSheet = false
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                else cameraPermission.launch(android.Manifest.permission.CAMERA)
            })
            SheetAction(Icons.Outlined.Image, "Choose from gallery", {
                photoSheet = false
                lock.suppressNextLock()
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            if (me?.photoUrl != null) {
                SheetAction(Icons.Outlined.DeleteForever, "Remove photo", { photoSheet = false; vm.removePhoto() }, tint = c.danger)
            }
        }
    }
    if (disappearingSheet) {
        NookBottomSheet(onDismiss = { disappearingSheet = false }) {
            SheetTitle("Disappearing default")
            Text("New chats you start will use this timer.", style = NookTheme.type.body, color = c.textMuted, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Spacer(Modifier.height(Spacing.md))
            SegmentedControl(
                Disappearing.entries.map { it.label }, settings.disappearingDefault.ordinal,
                { vm.setDisappearingDefault(Disappearing.entries[it]) },
                Modifier.padding(horizontal = Spacing.gutter),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
    if (confirmSignOut) {
        NookConfirmDialog(
            "Sign out?", "Your app lock will be reset on this device. Your chats stay in your account.", "Sign out",
            onConfirm = { vm.signOut(context) { confirmSignOut = false; nav.resetTo(LoginRoute) } },
            onDismiss = { confirmSignOut = false }, loading = busy,
        )
    }
    if (confirmDelete) {
        NookConfirmDialog(
            "Delete your account?",
            "This permanently deletes your profile, username, devices and every message you've sent. It can't be undone.",
            "Delete forever",
            onConfirm = { vm.deleteAccount(context) { confirmDelete = false; nav.resetTo(LoginRoute) } },
            onDismiss = { confirmDelete = false }, destructive = true, loading = busy,
        )
    }
}

@Composable
private fun StorageBar(label: String, bytes: Long, budget: Long, color: androidx.compose.ui.graphics.Color) {
    val c = NookTheme.colors
    Row {
        Text(label, style = NookTheme.type.label, color = c.onNavy, modifier = Modifier.weight(1f))
        Text(formatBytes(bytes), style = NookTheme.type.label, color = c.onNavy.copy(alpha = 0.7f))
    }
    Spacer(Modifier.height(6.dp))
    ProgressBar(bytes.toFloat() / budget, color = color)
}

@Composable
private fun Chevron(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    SettingsRow(icon, title, onClick = onClick, subtitle = subtitle, trailing = {
        Icon(Icons.Rounded.ChevronRight, null, tint = NookTheme.colors.textMuted)
    })
}

@Suppress("unused")
@Composable
private fun Divider() = Hairline(Modifier.padding(horizontal = Spacing.gutter))
