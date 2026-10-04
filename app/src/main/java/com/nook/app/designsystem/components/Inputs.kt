package com.nook.app.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.rememberHaptics

/** Charcoal pill text field; border warms to accent on focus or coral on error. */
@Composable
fun NookTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    maxLines: Int = if (singleLine) 1 else 6,
) {
    val c = NookTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            isError -> c.danger
            focused -> c.accent.copy(alpha = 0.7f)
            else -> c.border
        },
        label = "fieldBorder",
    )
    val shape = if (singleLine) NookShapes.pill else NookShapes.cardSmall
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = NookTheme.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, borderColor, shape)
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = NookTheme.type.body, color = c.textMuted)
                    inner()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        },
    )
}

/** Custom pill switch with a springy thumb. */
@Composable
fun NookSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = NookTheme.colors
    val haptics = rememberHaptics()
    val reduce = NookTheme.reduceMotion
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp, NookMotion.choose(reduce, NookMotion.bouncy()), label = "thumb")
    val track by animateColorAsState(if (checked) c.accent else c.border, label = "track")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(NookShapes.pill)
            .background(track)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Switch) {
                haptics.tick(); onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = thumbX).size(24.dp).clip(CircleShape).background(if (checked) c.onAccent else c.text))
    }
}

@Composable
fun FieldHint(text: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NookTheme.type.caption,
        color = if (isError) NookTheme.colors.danger else NookTheme.colors.textMuted,
        modifier = modifier.padding(start = 18.dp, top = 6.dp).height(16.dp),
    )
}
