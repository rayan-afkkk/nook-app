package com.nook.app.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.components.NookLogo
import com.nook.app.designsystem.components.glow
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Logo draws in, the dot pops, the wordmark rises — then we hand off to whatever's next. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val draw = remember { Animatable(if (reduce) 1f else 0f) }
    val dot = remember { Animatable(if (reduce) 1f else 0f) }
    val word = remember { Animatable(if (reduce) 1f else 0f) }
    val glowA = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        if (reduce) {
            delay(350)
        } else {
            launch { glowA.animateTo(0.45f, tween(1200)) }
            draw.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            launch { dot.animateTo(1f, NookMotion.bouncy()) }
            delay(120)
            word.animateTo(1f, NookMotion.gentle())
            delay(380)
        }
        finish()
    }

    Box(
        Modifier.fillMaxSize().background(c.background).semantics { contentDescription = "Nook" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NookLogo(
                modifier = Modifier.size(120.dp).glow(c.accent, 150.dp, glowA.value),
                drawProgress = draw.value,
                dotScale = dot.value,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "nook",
                style = NookTheme.type.hero,
                color = c.text,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 16.dp.toPx()
                },
            )
        }
    }
}
