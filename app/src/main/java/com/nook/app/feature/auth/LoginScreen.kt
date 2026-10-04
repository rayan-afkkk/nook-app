package com.nook.app.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookLogo
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoginScreen(onSignedIn: () -> Unit, vm: LoginViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduce = NookTheme.reduceMotion
    val items = remember { List(5) { Animatable(if (reduce) 1f else 0f) } }
    LaunchedEffect(Unit) {
        items.forEachIndexed { i, a -> launch { delay(80L + i * 90L); a.animateTo(1f, NookMotion.gentle()) } }
    }
    fun Modifier.enter(i: Int) = graphicsLayer {
        alpha = items[i].value
        translationY = (1f - items[i].value) * 28.dp.toPx()
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        AnimatedGradient(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
        ) {
            NookLogo(Modifier.size(56.dp).enter(0))
            Spacer(Modifier.weight(1f))
            Text("Your people.", style = NookTheme.type.hero, color = c.text, modifier = Modifier.enter(1))
            Text("Your nook.", style = NookTheme.type.hero.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = c.peach, modifier = Modifier.enter(2))
            Spacer(Modifier.height(Spacing.md))
            Text(
                "A private place for you and your friends to talk, share and call.",
                style = NookTheme.type.body,
                color = c.textMuted,
                modifier = Modifier.enter(3),
            )
            Spacer(Modifier.height(Spacing.xxl))
            Column(Modifier.enter(4)) {
                NookButton(
                    text = "Continue with Google",
                    onClick = { vm.signIn(context, onSignedIn) },
                    loading = state.loading,
                    icon = Icons.Rounded.AccountCircle,
                )
                AnimatedVisibility(state.error != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        state.error.orEmpty(),
                        style = NookTheme.type.bodySmall,
                        color = c.danger,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "By continuing you agree to the Privacy & Terms. Messages are stored on Firebase and are not end-to-end encrypted.",
                    style = NookTheme.type.caption,
                    color = c.textMuted,
                )
            }
        }
    }
}

/** Slow drifting blobs of accent / peach / lavender. Static when motion is reduced. */
@Composable
fun AnimatedGradient(modifier: Modifier = Modifier) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val t = rememberInfiniteTransition(label = "grad")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "gradPhase")
    val p = if (reduce) 0.8f else phase
    Canvas(modifier) {
        fun blob(color: Color, cx: Float, cy: Float, r: Float, a: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r),
            radius = r, center = Offset(cx, cy),
        )
        val w = size.width; val h = size.height
        blob(c.accent, w * (0.25f + 0.12f * cos(p)), h * (0.22f + 0.06f * sin(p)), w * 0.75f, 0.30f)
        blob(c.peach, w * (0.85f + 0.08f * sin(p * 1.3f)), h * (0.42f + 0.08f * cos(p)), w * 0.7f, 0.22f)
        blob(c.lavender, w * (0.4f + 0.15f * sin(p * 0.7f)), h * (0.68f + 0.05f * cos(p * 1.6f)), w * 0.8f, 0.16f)
    }
}
