package com.nook.app.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.prefs.SettingsStore
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.security.AppLockManager
import com.nook.app.designsystem.theme.ThemeMode
import com.nook.app.navigation.LoginRoute
import com.nook.app.navigation.MainRoute
import com.nook.app.navigation.OnboardingRoute
import com.nook.app.navigation.PermissionsRoute
import com.nook.app.navigation.SetPasswordRoute
import com.nook.app.navigation.UsernameRoute
import com.nook.app.session.DeepLinkBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/** Decides which gate (onboarding → login → username → permissions → password → main) is next. */
class RootViewModel(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    private val users: UserRepository,
    val lock: AppLockManager,
    val deepLinks: DeepLinkBus,
) : ViewModel() {

    val theme: StateFlow<ThemeMode> = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Dark)

    suspend fun nextDestination(): Any {
        if (!settings.onboardingDone.first()) return OnboardingRoute()
        val user = auth.currentUser ?: return LoginRoute
        if (!users.profileExists(user.uid)) return UsernameRoute
        if (!settings.permissionsPrimed.first()) return PermissionsRoute
        if (!lock.hasPassword()) return SetPasswordRoute()
        return MainRoute
    }
}
