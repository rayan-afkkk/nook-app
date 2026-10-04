package com.nook.app.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.messaging.FirebaseMessaging
import com.nook.app.data.prefs.SettingsStore
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.RealtimeRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Process-wide glue: foreground/background → app lock + presence; sign-in → FCM token.
 * Presence/RTDB is only connected while the app is visible AND signed in.
 */
class SessionManager(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val lock: AppLockManager,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    fun start() {
        scope.launch { lock.init() }
        scope.launch {
            combine(auth.authState, _foreground) { user, fg -> user != null && fg }
                .distinctUntilChanged()
                .collect { online -> if (online) realtime.goOnline() else realtime.goOffline() }
        }
        scope.launch {
            auth.authState.collect { user -> if (user != null) registerToken() }
        }
    }

    suspend fun registerToken() {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            users.saveFcmToken(token)
            settings.setFcmToken(token)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lock.onForeground()
        _foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        lock.onBackground()
        _foreground.value = false
    }
}
