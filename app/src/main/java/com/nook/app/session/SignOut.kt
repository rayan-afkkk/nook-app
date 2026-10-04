package com.nook.app.session

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.nook.app.data.prefs.SettingsStore
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.RealtimeRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.security.AppLockManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Shared by Account → Sign out and Lock → "Forgot password". */
class SignOutUseCase(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val lock: AppLockManager,
    private val settings: SettingsStore,
) {
    suspend operator fun invoke(context: Context) {
        settings.fcmToken.first()?.let { runCatching { users.removeFcmToken(it) } }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        realtime.goOffline()
        lock.reset()
        auth.signOut(context)
    }
}
