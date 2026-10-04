package com.nook.app.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nook.app.designsystem.theme.InstrumentSerif
import com.nook.app.designsystem.theme.NookTheme
import kotlin.math.absoluteValue

/**
 * Circular avatar. Always pass the URL read from the *user document* so photo changes show
 * everywhere instantly. Falls back to a pastel initial when there is no photo (or it fails).
 */
@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    contentDescription: String? = null,
) {
    val c = NookTheme.colors
    val pastel = c.pastels[(name.hashCode().absoluteValue) % c.pastels.size]
    Box(
        modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(pastel),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color(0xFF1A1714),
                fontFamily = InstrumentSerif,
                fontSize = (size.value * 0.46f).sp,
            )
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            }
        }
        OnlineDot(online, size)
    }
}

@Composable
private fun BoxScope.OnlineDot(online: Boolean, avatarSize: Dp) {
    val dot = (avatarSize.value * 0.28f).coerceIn(10f, 18f).dp
    AnimatedVisibility(
        visible = online,
        enter = scaleIn(),
        exit = scaleOut(),
        modifier = Modifier.align(Alignment.BottomEnd),
    ) {
        Box(
            Modifier
                .size(dot)
                .clip(CircleShape)
                .background(NookTheme.colors.online)
                .border(2.dp, NookTheme.colors.background, CircleShape),
        )
    }
}
