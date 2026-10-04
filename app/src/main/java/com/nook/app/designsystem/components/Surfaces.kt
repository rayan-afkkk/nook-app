package com.nook.app.designsystem.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.util.rememberHaptics

/** 24dp-radius card with a 1dp hairline border. */
@Composable
fun NookCard(
    modifier: Modifier = Modifier,
    background: Color = NookTheme.colors.surface,
    shape: Shape = NookShapes.card,
    bordered: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.98f) else Modifier)
            .clip(shape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, NookTheme.colors.border, shape) else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** First-run card: dashed border, round accent "+" button. */
@Composable
fun DashedAddCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = NookTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(NookShapes.card)
            .drawBehind {
                drawRoundRect(
                    color = c.border,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clickable(interaction, indication = null, role = Role.Button) { haptics.tap(); onClick() }
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NookTheme.type.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = NookTheme.type.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.width(Spacing.md))
        Box(
            Modifier
                .size(48.dp)
                .glow(c.accent, 48.dp, 0.4f)
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onAccent)
        }
    }
}

/** Hairline divider. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = NookTheme.colors.border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = NookTheme.type.caption,
        color = NookTheme.colors.textMuted,
        modifier = modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
    )
}

/** Orange unread badge. */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    Box(
        modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(c.accent)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = NookTheme.type.label, color = c.onAccent)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = NookTheme.colors.accent) {
    val c = NookTheme.colors
    Box(modifier.fillMaxWidth().height(8.dp).clip(NookShapes.pill).background(c.border)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(NookShapes.pill)
                .background(color),
        )
    }
}

@Composable
fun Stack(modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.Dp = Spacing.sm, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}
