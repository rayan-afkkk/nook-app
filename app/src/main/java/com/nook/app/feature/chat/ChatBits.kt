package com.nook.app.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.components.glow
import com.nook.app.designsystem.theme.NookTheme
import kotlin.math.PI
import kotlin.math.sin

/** Three dots bouncing in a charcoal bubble. */
@Composable
fun TypingBubble(names: List<String>, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "typingPhase")
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 6.dp).semantics { contentDescription = ChatViewModel.typingLabel(names) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).background(c.bubbleTheirs)
                .border(1.dp, c.border, RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val y = if (reduce) 0f else sin(phase - i * 0.9f).coerceAtLeast(0f)
                Box(
                    Modifier.size(8.dp)
                        .graphicsLayer { translationY = -y * 5.dp.toPx(); alpha = 0.45f + 0.55f * y }
                        .clip(CircleShape).background(c.textMuted),
                )
            }
        }
        if (names.size > 1 || names.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(ChatViewModel.typingLabel(names), style = NookTheme.type.caption, color = c.textMuted)
        }
    }
}

@Composable
fun DateSeparator(label: String, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = NookTheme.type.caption,
            color = c.textMuted,
            modifier = Modifier.clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun NewMessagesPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    Row(
        modifier
            .glow(c.accent, 60.dp, 0.35f)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("New messages", style = NookTheme.type.label, color = c.onAccent)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.onAccent, modifier = Modifier.size(18.dp))
    }
}
