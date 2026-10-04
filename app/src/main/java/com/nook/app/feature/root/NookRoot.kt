package com.nook.app.feature.root

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.feature.calls.CallManager
import com.nook.app.feature.calls.CallOverlay
import com.nook.app.feature.lock.LockScreen
import com.nook.app.navigation.ChatRoute
import com.nook.app.navigation.LoginRoute
import com.nook.app.navigation.NookNavHost
import com.nook.app.navigation.OnboardingRoute
import com.nook.app.navigation.PermissionsRoute
import com.nook.app.navigation.SetPasswordRoute
import com.nook.app.navigation.SplashRoute
import com.nook.app.navigation.UsernameRoute
import com.nook.app.navigation.resetTo
import com.nook.app.session.DeepLink
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun NookRoot(vm: RootViewModel = koinViewModel()) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    NookTheme(theme) {
        val c = NookTheme.colors
        val context = LocalContext.current
        LaunchedEffect(c.isDark) {
            val activity = context as? ComponentActivity ?: return@LaunchedEffect
            val style = if (c.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, c.text.toArgb())
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val dest = entry?.destination
        val pastGates = dest != null && !(
            dest.hasRoute<SplashRoute>() || dest.hasRoute<OnboardingRoute>() || dest.hasRoute<LoginRoute>() ||
                dest.hasRoute<UsernameRoute>() || dest.hasRoute<PermissionsRoute>() ||
                (dest.hasRoute<SetPasswordRoute>() && nav.previousBackStackEntry == null)
            )

        val locked by vm.lock.locked.collectAsStateWithLifecycle()
        val lockEnabled by vm.lock.lockEnabled.collectAsStateWithLifecycle()
        val callManager: CallManager = koinInject()
        val call by callManager.state.collectAsStateWithLifecycle()
        val pending by vm.deepLinks.pending.collectAsStateWithLifecycle()

        LaunchedEffect(pending, pastGates) {
            when (val link = pending) {
                is DeepLink.OpenChat -> if (pastGates) {
                    nav.navigate(ChatRoute(link.chatId)) { launchSingleTop = true }
                    vm.deepLinks.consume()
                }
                is DeepLink.AnswerCall -> if (pastGates) {
                    callManager.answer(link.callId)
                    vm.deepLinks.consume()
                }
                null -> Unit
            }
        }

        Box(Modifier.fillMaxSize().background(c.background)) {
            NookNavHost(nav, vm, Modifier.fillMaxSize())

            AnimatedVisibility(
                visible = pastGates && lockEnabled == true && locked && call == null,
                enter = fadeIn(tween(1)),
                exit = fadeOut(tween(250)) + scaleOutLock(),
            ) {
                LockScreen(onSignedOut = { nav.resetTo(LoginRoute) })
            }

            AnimatedVisibility(
                visible = call != null,
                enter = slideInVertically(tween(320)) { it / 3 } + fadeIn() + scaleIn(initialScale = 0.96f),
                exit = slideOutVertically(tween(260)) { it / 3 } + fadeOut(),
            ) {
                CallOverlay(manager = callManager)
            }
        }
    }
}

private fun scaleOutLock() = androidx.compose.animation.scaleOut(targetScale = 1.04f)
