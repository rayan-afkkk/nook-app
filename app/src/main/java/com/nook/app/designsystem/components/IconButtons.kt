package com.nook.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.rememberHaptics

/** 48dp outline icon button with a springy press. Optionally a filled circular background. */
@Composable
fun NookIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NookTheme.colors.text,
    background: Color? = null,
    bordered: Boolean = false,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
    haptic: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction, 0.88f)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .then(if (bordered) Modifier.border(1.dp, NookTheme.colors.border, CircleShape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (haptic) haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
