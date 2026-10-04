package com.nook.app.feature.auth

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookButtonStyle
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing

private data class PermissionItem(
    val key: String,
    val permission: String?,
    val icon: ImageVector,
    val title: String,
    val why: String,
    val tint: Color,
)

/** Explains each permission BEFORE the system dialog appears. Every one is optional. */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val c = NookTheme.colors
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }

    val items = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionItem("notif", Manifest.permission.POST_NOTIFICATIONS, Icons.Outlined.Notifications, "Notifications",
                "So you know when friends message or call. We only ever say who — never what they wrote.", c.peach))
        }
        add(PermissionItem("camera", Manifest.permission.CAMERA, Icons.Outlined.CameraAlt, "Camera & photos",
            "Snap photos, change your profile picture and make video calls. Photos you pick use Android's private photo picker.", c.sky))
        add(PermissionItem("mic", Manifest.permission.RECORD_AUDIO, Icons.Outlined.Mic, "Microphone",
            "Record voice notes and talk on calls. Only while you're holding the mic or on a call.", c.mint))
        if (Build.VERSION.SDK_INT >= 34) {
            add(PermissionItem("fsi", null, Icons.Outlined.Call, "Full-screen calls",
                "Lets incoming calls ring full-screen even when your phone is locked.", c.lavender))
        }
    }

    fun granted(item: PermissionItem): Boolean {
        if (refresh < 0) return false
        return if (item.permission != null) {
            ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else true
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
    ) {
        Text("A few things first", style = NookTheme.type.display, color = c.text)
        Spacer(Modifier.height(Spacing.xs))
        Text("Here's what Nook asks for and why. You can change these anytime in Settings.", style = NookTheme.type.body, color = c.textMuted)
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items.forEach { item ->
                val ok = granted(item)
                NookCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(item.tint), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = Color(0xFF1A1714))
                        }
                        Spacer(Modifier.size(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = NookTheme.type.titleSans, color = c.text)
                            Spacer(Modifier.height(4.dp))
                            Text(item.why, style = NookTheme.type.bodySmall, color = c.textMuted)
                            Spacer(Modifier.height(Spacing.sm))
                            AnimatedContent(ok, transitionSpec = { (fadeIn() + scaleIn()) togetherWith fadeOut() }, label = "perm") { isOk ->
                                if (isOk) {
                                    NookPill("Allowed", icon = Icons.Rounded.Check, background = c.mint.copy(alpha = 0.18f), contentColor = c.mint)
                                } else {
                                    NookPill("Allow", background = c.text, contentColor = c.background, bordered = false, onClick = {
                                        if (item.permission != null) {
                                            launcher.launch(item.permission)
                                        } else if (Build.VERSION.SDK_INT >= 34) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                                            )
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        NookButton("Continue", onDone)
        Spacer(Modifier.height(Spacing.xs))
        NookButton("Not now", onDone, style = NookButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
    }
}
