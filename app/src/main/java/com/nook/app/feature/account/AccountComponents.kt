package com.nook.app.feature.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nook.app.BuildConfig
import com.nook.app.data.repo.CacheRepository
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.NookLogo
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.ProgressBar
import com.nook.app.designsystem.components.breathingGlow
import com.nook.app.designsystem.components.glow
import com.nook.app.designsystem.components.pressScale
import com.nook.app.designsystem.theme.InstrumentSerif
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookPalette
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.designsystem.theme.ThemeMode
import com.nook.app.util.formatBytes
import com.nook.app.util.rememberHaptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF1A1714)

/** Centred profile: glowing avatar in a gradient ring, camera badge, serif name, pills. */
@Composable
fun ProfileHero(name: String, username: String, photoUrl: String?, onPhoto: () -> Unit, onEdit: () -> Unit) {
    val c = NookTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .breathingGlow(c.accent, 110.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Change photo", onClick = onPhoto),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(c.peach, c.accent, c.lavender, c.sky, c.peach)))
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(c.background)
                    .padding(4.dp),
            ) {
                Avatar(photoUrl, name.ifBlank { "?" }, 82.dp, contentDescription = "Your profile photo")
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.text)
                    .border(3.dp, c.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.CameraAlt, null, tint = c.background, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(name, style = NookTheme.type.headline, color = c.text, textAlign = TextAlign.Center)
        Text("@$username", style = NookTheme.type.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NookPill("Member", background = c.mint, contentColor = Ink, bordered = false)
            NookPill("Edit profile", icon = Icons.Outlined.Edit, onClick = onEdit)
        }
    }
}

/** Three little numbers in one card, split by hairlines. */
@Composable
fun StatsRow(chats: Int, friends: Int, memberSince: Long, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val since = if (memberSince > 0) SimpleDateFormat("MMM ''yy", Locale.getDefault()).format(Date(memberSince)) else "—"
    Row(
        modifier.fillMaxWidth().height(70.dp).clip(NookShapes.card).background(c.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(chats.toString(), "Chats", Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 20.dp).background(c.border))
        Stat(friends.toString(), "Friends", Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 20.dp).background(c.border))
        Stat(since, "Member since", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = NookTheme.type.titleSans, color = NookTheme.colors.text)
        Text(label, style = NookTheme.type.caption, color = NookTheme.colors.textMuted)
    }
}

/** Navy highlight card with storage bars. */
@Composable
fun DeviceCard(mediaBytes: Long, offlineBytes: Long, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .glow(c.navy, 170.dp, 0.3f)
            .clip(NookShapes.card)
            .background(Brush.linearGradient(listOf(c.navy, Color(0xFF16293B))))
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c.onNavy.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = c.onNavy, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("This device", style = NookTheme.type.titleSans, color = c.onNavy)
                Text("${formatBytes(mediaBytes + offlineBytes)} used", style = NookTheme.type.caption, color = c.onNavy.copy(alpha = 0.65f))
            }
            NookPill("Clear cache", background = c.onNavy, contentColor = c.navy, bordered = false, onClick = onClear)
        }
        Spacer(Modifier.height(Spacing.md))
        Bar("Media cache", mediaBytes, CacheRepository.MEDIA_CACHE_BUDGET, c.peach)
        Spacer(Modifier.height(Spacing.sm))
        Bar("Offline messages", offlineBytes, CacheRepository.OFFLINE_BUDGET, c.sky)
    }
}

@Composable
private fun Bar(label: String, bytes: Long, budget: Long, color: Color) {
    val c = NookTheme.colors
    Row {
        Text(label, style = NookTheme.type.label, color = c.onNavy, modifier = Modifier.weight(1f))
        Text(formatBytes(bytes), style = NookTheme.type.label, color = c.onNavy.copy(alpha = 0.7f))
    }
    Spacer(Modifier.height(6.dp))
    ProgressBar(bytes.toFloat() / budget, color = color)
}

@Composable
fun GroupTitle(text: String) {
    Text(
        text,
        style = NookTheme.type.titleSans,
        color = NookTheme.colors.textMuted,
        modifier = Modifier.padding(start = Spacing.gutter + 4.dp, end = Spacing.gutter, top = Spacing.lg, bottom = Spacing.xs),
    )
}

/** Three tappable mini previews of the app in Dark / Light / System. */
@Composable
fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            ThemeTile(mode, mode == current, { onPick(mode) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThemeTile(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = NookTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) c.text else c.border, label = "themeRing")
    val check by animateFloatAsState(if (selected) 1f else 0f, NookMotion.bouncy(), label = "themeCheck")
    Column(
        modifier
            .pressScale(interaction, 0.96f)
            .semantics { this.selected = selected }
            .clickable(interaction, indication = null, role = Role.RadioButton) { haptics.tick(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp))
                .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(16.dp)),
        ) {
            when (mode) {
                ThemeMode.Dark -> MiniScreen(NookPalette.Black, NookPalette.Charcoal, NookPalette.Cream, Modifier.fillMaxWidth())
                ThemeMode.Light -> MiniScreen(NookPalette.LightBackground, NookPalette.LightSurface, NookPalette.Ink, Modifier.fillMaxWidth())
                ThemeMode.System -> Row {
                    MiniScreen(NookPalette.Black, NookPalette.Charcoal, NookPalette.Cream, Modifier.weight(1f))
                    MiniScreen(NookPalette.LightBackground, NookPalette.LightSurface, NookPalette.Ink, Modifier.weight(1f))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                    .graphicsLayer { scaleX = check; scaleY = check; alpha = check }
                    .clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(13.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(mode.name, style = NookTheme.type.label, color = if (selected) c.text else c.textMuted)
    }
}

/** A tiny abstract chat screen: two bubbles and a header line. */
@Composable
private fun MiniScreen(bg: Color, surface: Color, text: Color, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(bg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(28.dp).height(6.dp).clip(CircleShape).background(text.copy(alpha = 0.8f)))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(surface))
        Box(
            Modifier.fillMaxWidth().padding(start = 18.dp).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(Brush.horizontalGradient(listOf(NookPalette.Cream, NookPalette.Peach))),
        )
        Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(surface))
    }
}

/** Rounded card holding a group of rows. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth().clip(NookShapes.card).background(NookTheme.colors.surface)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    Box(Modifier.padding(start = 58.dp, end = 14.dp).fillMaxWidth().height(1.dp).background(NookTheme.colors.border))
}

/** Row with a colourful rounded icon tile, title, subtitle and chevron. */
@Composable
fun TileRow(
    icon: ImageVector,
    tile: Color,
    title: String,
    subtitle: String?,
    iconTint: Color = Ink,
    titleColor: Color = NookTheme.colors.text,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val c = NookTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NookTheme.type.bodyStrong, color = titleColor)
            if (subtitle != null) Text(subtitle, style = NookTheme.type.caption, color = c.textMuted)
        }
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted)
    }
}

@Composable
fun Footer() {
    val c = NookTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        NookLogo(Modifier.size(34.dp), strokeColor = c.textMuted)
        Spacer(Modifier.height(Spacing.xs))
        Text("nook", fontFamily = InstrumentSerif, style = NookTheme.type.title, color = c.textMuted)
        Text("Version ${BuildConfig.VERSION_NAME} · made for the crew", style = NookTheme.type.caption, color = c.textMuted)
    }
}
