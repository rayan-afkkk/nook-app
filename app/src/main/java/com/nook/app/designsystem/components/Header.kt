package com.nook.app.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing

/** Tab header: big serif title on the left, 1–2 outline icon buttons on the right, hairline below. */
@Composable
fun NookHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showHairline: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.sm, top = Spacing.lg, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = NookTheme.type.display,
                    color = NookTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(subtitle, style = NookTheme.type.bodySmall, color = NookTheme.colors.textMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (showHairline) Hairline()
    }
}

/** Compact header for pushed screens: back arrow, serif title, actions. */
@Composable
fun NookTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showHairline: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                NookIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            } else {
                Spacer(Modifier.width(Spacing.md))
            }
            Text(
                title,
                style = NookTheme.type.title,
                color = NookTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (showHairline) Hairline()
    }
}
