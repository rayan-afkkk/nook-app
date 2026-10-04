package com.nook.app.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing

/** Centered outline icon, serif headline, one muted sentence. Fades up on entry. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, NookMotion.gentle()) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl)
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 24.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .breathingGlow(c.accent, 80.dp)
                .clip(CircleShape)
                .border(1.dp, c.border, CircleShape)
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(title, style = NookTheme.type.headline, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = NookTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xl))
            NookButton(actionLabel, onAction, fillWidth = false)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = "Something slipped",
        message = message,
        actionLabel = if (onRetry != null) "Try again" else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Skeleton list rows with shimmer, used for every first load. */
@Composable
fun SkeletonList(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).shimmer())
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(NookShapes.pill).shimmer())
                    Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(NookShapes.pill).shimmer())
                }
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { SkeletonList() }
}

/** Amber pill that drops in from the top while offline. */
@Composable
fun OfflineBanner(offline: Boolean, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    AnimatedVisibility(
        visible = offline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(NookShapes.pill).background(c.amber.copy(alpha = 0.16f)).border(1.dp, c.amber.copy(alpha = 0.5f), NookShapes.pill)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CloudOff, null, tint = c.amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("You're offline — we'll send when you're back", style = NookTheme.type.label, color = c.amber)
            }
        }
    }
}
