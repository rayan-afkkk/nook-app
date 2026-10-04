package com.nook.app.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Sizes
import com.nook.app.util.rememberHaptics

enum class NookButtonStyle { Primary, Secondary, Destructive, Ghost }

/**
 * Pill button. Primary = cream→peach gradient with black text (inverted on light theme),
 * Secondary = charcoal, Destructive = coral, Ghost = text only.
 */
@Composable
fun NookButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NookButtonStyle = NookButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    fillWidth: Boolean = true,
    shimmerOnce: Boolean = false,
) {
    val c = NookTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val contentColor = when (style) {
        NookButtonStyle.Primary -> c.onPrimaryButton
        NookButtonStyle.Secondary -> c.text
        NookButtonStyle.Destructive -> Color.Black
        NookButtonStyle.Ghost -> c.text
    }
    val sweep = remember { Animatable(-0.4f) }
    LaunchedEffect(shimmerOnce) {
        if (shimmerOnce) {
            sweep.snapTo(-0.4f)
            sweep.animateTo(1.4f, tween(900))
        }
    }
    val base = modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .defaultMinSize(minWidth = 96.dp)
        .height(Sizes.buttonHeight)
        .pressScale(interaction)
        .alpha(if (enabled) 1f else 0.45f)
        .clip(NookShapes.pill)
    val styled = when (style) {
        NookButtonStyle.Primary -> base.background(c.primaryButtonBrush)
        NookButtonStyle.Secondary -> base.background(c.surface).border(1.dp, c.border, NookShapes.pill)
        NookButtonStyle.Destructive -> base.background(c.danger)
        NookButtonStyle.Ghost -> base
    }
    Box(
        modifier = styled
            .drawWithContent {
                drawContent()
                if (sweep.value > -0.4f && sweep.value < 1.4f) {
                    val x = size.width * sweep.value
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(x - 120f, 0f),
                            end = Offset(x + 120f, size.height),
                        ),
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "btnLoading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(text, style = NookTheme.type.button, color = contentColor)
                }
            }
        }
    }
}

/** Small pill used for chips ("Member", timers, filters). */
@Composable
fun NookPill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = NookTheme.colors.surface,
    contentColor: Color = NookTheme.colors.text,
    icon: ImageVector? = null,
    bordered: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .clip(NookShapes.pill)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, NookTheme.colors.border, NookShapes.pill) else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = NookTheme.type.label, color = contentColor)
    }
}
