package com.nook.app.feature.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.components.Hairline
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookShapes
import com.nook.app.designsystem.theme.NookTheme
import kotlin.math.PI
import kotlin.math.sin

enum class NookTab(val label: String, val outline: ImageVector, val filled: ImageVector) {
    Chats("Chats", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble),
    Friends("Friends", Icons.Outlined.People, Icons.Rounded.People),
    Stickers("Stickers", Icons.Outlined.EmojiEmotions, Icons.Rounded.EmojiEmotions),
    Calls("Calls", Icons.Outlined.Call, Icons.Rounded.Call),
    Account("Account", Icons.Outlined.Person, Icons.Rounded.Person),
}

/** Bottom bar: the selection pill slides (and stretches mid-flight) to the new tab. */
@Composable
fun NookBottomBar(indicatorPosition: Float, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = NookTheme.colors
    Column(Modifier.fillMaxWidth().background(c.background)) {
        Hairline()
        BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp)) {
            val itemWidth = maxWidth / NookTab.entries.size
            val frac = indicatorPosition - indicatorPosition.toInt()
            val stretch = 1f + 0.45f * sin(frac * PI).toFloat()
            val pillWidth = 56.dp
            Box(
                Modifier
                    .offset(x = itemWidth * indicatorPosition + (itemWidth - pillWidth) / 2, y = 8.dp)
                    .graphicsLayer { scaleX = stretch }
                    .width(pillWidth)
                    .height(32.dp)
                    .background(c.surfaceRaised, NookShapes.pill)
                    .border(1.dp, c.border, NookShapes.pill),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                NookTab.entries.forEachIndexed { i, tab ->
                    TabItem(tab, selected = i == selectedIndex, onClick = { onSelect(i) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TabItem(tab: NookTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val tint by animateColorAsState(if (selected) c.text else c.textMuted, label = "tabTint")
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduce) {
            bounce.snapTo(0.7f)
            bounce.animateTo(1f, NookMotion.bouncy())
        }
    }
    Column(
        modifier
            .fillMaxHeight()
            .semantics { this.selected = selected; contentDescription = tab.label }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Crossfade(selected, label = "tabIcon") { sel ->
            Icon(
                if (sel) tab.filled else tab.outline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(tab.label, style = NookTheme.type.caption, color = tint)
    }
}
