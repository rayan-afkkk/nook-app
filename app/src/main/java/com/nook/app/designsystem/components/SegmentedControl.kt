package com.nook.app.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.util.rememberHaptics

/** Pill segmented control with a sliding cream indicator. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = NookTheme.colors
    val haptics = rememberHaptics()
    val reduce = NookTheme.reduceMotion
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(NookShapes.pill)
            .background(c.surface)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segment * selectedIndex, NookMotion.choose(reduce, NookMotion.bouncy()), label = "segOffset")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(NookShapes.pill)
                .background(if (c.isDark) c.surfaceRaised else c.background)
                .border(1.dp, c.border, NookShapes.pill),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(if (selected) c.text else c.textMuted, label = "segText")
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(NookShapes.pill)
                        .semantics { this.selected = selected }
                        .clickable(interaction, indication = null, role = Role.Tab) {
                            if (!selected) { haptics.tick(); onSelect(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = NookTheme.type.label, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
