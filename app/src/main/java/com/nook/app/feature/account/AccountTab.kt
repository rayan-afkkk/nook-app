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
    val stats by vm.stats.collectAsStateWithLifecycle()
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
                    ProfileHero(
                        name = me?.name ?: "",
                        username = me?.username.orEmpty(),
                        photoUrl = me?.photoUrl,
                        onPhoto = { photoSheet = true },
                        onEdit = { nav.navigate(EditProfileRoute) },
                    )
                }
                item {
                    StatsRow(
                        chats = stats.first,
                        friends = stats.second,
                        memberSince = me?.createdAt ?: 0L,
                        modifier = Modifier.padding(horizontal = Spacing.gutter),
                    )
                }
                item {
                    val s = storage
                    DeviceCard(
                        mediaBytes = s?.mediaCacheBytes ?: 0,
                        offlineBytes = s?.offlineDataBytes ?: 0,
                        onClear = vm::clearCache,
                        modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md),
                    )
                }
                item {
                    Column {
                        GroupTitle("Appearance")
                        ThemePicker(theme, onPick = vm::setTheme, modifier = Modifier.padding(horizontal = Spacing.gutter))
                    }
                }
                item {
                    Column {
                        GroupTitle("Privacy & security")
                        SettingsGroup {
                            TileRow(Icons.Outlined.Lock, c.lavender, "App lock", "Password, fingerprint, auto-lock") { nav.navigate(AppLockSettingsRoute) }
                            GroupDivider()
                            TileRow(Icons.Outlined.Timer, c.peach, "Disappearing messages", "Default for new chats · ${settings.disappearingDefault.label}") { disappearingSheet = true }
                            GroupDivider()
                            TileRow(Icons.Outlined.Block, c.rose, "Blocked users", if (settings.blocked.isEmpty()) "Nobody blocked" else "${settings.blocked.size} blocked") { nav.navigate(BlockedUsersRoute) }
                        }
                    }
                }
                item {
                    Column {
                        GroupTitle("Preferences")
                        SettingsGroup {
                            TileRow(Icons.Outlined.Notifications, c.sky, "Notifications", if (settings.notificationsEnabled) "On · content never shown" else "Off") { nav.navigate(NotificationSettingsRoute) }
                            GroupDivider()
                            TileRow(Icons.Outlined.AutoAwesome, c.mint, "View onboarding", "Replay the intro") { nav.navigate(OnboardingRoute(replay = true)) }
                        }
                    }
                }
                item {
                    Column {
                        GroupTitle("Support")
                        SettingsGroup {
                            TileRow(Icons.AutoMirrored.Outlined.HelpOutline, c.amber, "Help & feedback", "Tell us what to fix or add") {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, "Nook feedback (v${BuildConfig.VERSION_NAME})"))
                                }
                            }
                            GroupDivider()
                            TileRow(Icons.Outlined.Policy, c.lavender, "Privacy & terms", "Where your data lives") { nav.navigate(LegalRoute("privacy")) }
                        }
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(Spacing.lg))
                        SettingsGroup {
                            TileRow(Icons.AutoMirrored.Outlined.Logout, c.surfaceRaised, "Sign out", null, iconTint = c.text, chevron = false) { confirmSignOut = true }
                            GroupDivider()
                            TileRow(Icons.Outlined.DeleteForever, c.danger.copy(alpha = 0.18f), "Delete account", null, iconTint = c.danger, titleColor = c.danger, chevron = false) { confirmDelete = true }
                        }
                    }
                }
                item { Footer() }
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

