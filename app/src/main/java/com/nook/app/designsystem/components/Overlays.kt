package com.nook.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing

@Composable
fun NookConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
    loading: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        NookCard(background = NookTheme.colors.surface) {
            Text(title, style = NookTheme.type.headline, color = NookTheme.colors.text)
            Spacer(Modifier.height(Spacing.xs))
            Text(message, style = NookTheme.type.body, color = NookTheme.colors.textMuted)
            Spacer(Modifier.height(Spacing.xl))
            NookButton(
                confirmLabel,
                onConfirm,
                style = if (destructive) NookButtonStyle.Destructive else NookButtonStyle.Primary,
                loading = loading,
            )
            Spacer(Modifier.height(Spacing.xs))
            NookButton(dismissLabel, onDismiss, style = NookButtonStyle.Ghost)
        }
    }
}

/** Branded modal sheet on top of Material's ModalBottomSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NookBottomSheet(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val c = NookTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.text,
        shape = NookShapes.sheet,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(NookShapes.pill).background(c.border))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.md), content = content)
    }
}

@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NookTheme.colors.text,
    subtitle: String? = null,
) {
    SettingsRow(icon = icon, title = label, subtitle = subtitle, onClick = onClick, tint = tint, modifier = modifier)
}

/** Settings-style row: icon tile, title, optional subtitle, trailing slot. 56dp+ high. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = NookTheme.colors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = NookTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.pressScale(interaction, 0.98f).clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = Spacing.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = NookTheme.type.bodyStrong, color = tint)
            if (subtitle != null) Text(subtitle, style = NookTheme.type.bodySmall, color = c.textMuted)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NookTheme.type.headline,
        color = NookTheme.colors.text,
        modifier = modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.sm),
    )
}

@Composable
fun RowSpacer(width: androidx.compose.ui.unit.Dp) = Spacer(Modifier.width(width))
