package com.nook.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.feature.account.AppLockSettingsScreen
import com.nook.app.feature.account.BlockedUsersScreen
import com.nook.app.feature.account.CropScreen
import com.nook.app.feature.account.EditProfileScreen
import com.nook.app.feature.account.LegalScreen
import com.nook.app.feature.account.NotificationSettingsScreen
import com.nook.app.feature.auth.LoginScreen
import com.nook.app.feature.auth.PermissionsScreen
import com.nook.app.feature.auth.SetPasswordScreen
import com.nook.app.feature.auth.UsernameScreen
import com.nook.app.feature.chat.ChatInfoScreen
import com.nook.app.feature.chat.ChatScreen
import com.nook.app.feature.chats.NewChatScreen
import com.nook.app.feature.onboarding.OnboardingScreen
import com.nook.app.feature.root.RootViewModel
import com.nook.app.feature.shell.MainShell
import com.nook.app.feature.splash.SplashScreen
import com.nook.app.feature.stickers.StickerPackScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun NavController.resetTo(route: Any) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

@Composable
fun NookNavHost(nav: NavHostController, root: RootViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val reduce = NookTheme.reduceMotion
    val settings: com.nook.app.data.prefs.SettingsStore = koinInject()
    fun advance() { scope.launch { nav.resetTo(root.nextDestination()) } }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) fadeIn(tween(150)) else slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(220))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) fadeOut(tween(150)) else slideOutHorizontally(tween(320)) { -it / 10 } + fadeOut(tween(200))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) fadeIn(tween(150)) else slideInHorizontally(tween(320)) { -it / 10 } + fadeIn(tween(220))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) fadeOut(tween(150)) else slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200)) + scaleOut(targetScale = 0.98f)
    }

    NavHost(
        navController = nav,
        startDestination = SplashRoute,
        modifier = modifier,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable<SplashRoute>(enterTransition = { fadeIn() }, exitTransition = { fadeOut(tween(350)) }) {
            SplashScreen(onFinished = ::advance)
        }
        composable<OnboardingRoute>(enterTransition = { fadeIn(tween(450)) }) { entry ->
            val replay = entry.toRoute<OnboardingRoute>().replay
            OnboardingScreen(onDone = {
                if (replay) nav.popBackStack()
                else scope.launch { settings.setOnboardingDone(true); advance() }
            })
        }
        composable<LoginRoute> { LoginScreen(onSignedIn = ::advance) }
        composable<UsernameRoute> { UsernameScreen(onDone = ::advance) }
        composable<PermissionsRoute> {
            PermissionsScreen(onDone = { scope.launch { settings.setPermissionsPrimed(true); advance() } })
        }
        composable<SetPasswordRoute> { entry ->
            val change = entry.toRoute<SetPasswordRoute>().change
            SetPasswordScreen(
                change = change,
                onDone = { if (change) nav.popBackStack() else advance() },
                onBack = if (change) ({ nav.popBackStack(); Unit }) else null,
            )
        }
        composable<MainRoute>(enterTransition = { fadeIn(tween(350)) }) {
            MainShell(nav)
        }
        composable<ChatRoute> { entry ->
            val chatId = entry.toRoute<ChatRoute>().chatId
            ChatScreen(chatId = chatId, nav = nav)
        }
        composable<ChatInfoRoute> { entry ->
            ChatInfoScreen(chatId = entry.toRoute<ChatInfoRoute>().chatId, nav = nav)
        }
        composable<NewChatRoute> { entry ->
            val r = entry.toRoute<NewChatRoute>()
            NewChatScreen(group = r.group, addToChatId = r.addToChatId, nav = nav)
        }
        composable<CropRoute> { entry ->
            val r = entry.toRoute<CropRoute>()
            CropScreen(uri = r.uri, target = r.target, onDone = { nav.popBackStack() })
        }
        composable<EditProfileRoute> { EditProfileScreen(nav) }
        composable<AppLockSettingsRoute> { AppLockSettingsScreen(nav) }
        composable<BlockedUsersRoute> { BlockedUsersScreen(nav) }
        composable<NotificationSettingsRoute> { NotificationSettingsScreen(nav) }
        composable<LegalRoute> { entry -> LegalScreen(kind = entry.toRoute<LegalRoute>().kind, onBack = { nav.popBackStack() }) }
        composable<StickerPackRoute> { entry -> StickerPackScreen(packId = entry.toRoute<StickerPackRoute>().packId, nav = nav) }
    }
}
