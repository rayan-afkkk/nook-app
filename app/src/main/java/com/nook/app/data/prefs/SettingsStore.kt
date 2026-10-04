package com.nook.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nook.app.data.security.LockStorage
import com.nook.app.data.security.StoredPassword
import com.nook.app.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nook_settings")

/** Device-local settings (DataStore). Nothing here ever leaves the device. */
class SettingsStore(context: Context) : LockStorage {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val permissionsPrimed = booleanPreferencesKey("permissions_primed")
        val theme = stringPreferencesKey("theme")
        val lockSalt = stringPreferencesKey("lock_salt")
        val lockHash = stringPreferencesKey("lock_hash")
        val lockIterations = intPreferencesKey("lock_iterations")
        val biometric = booleanPreferencesKey("lock_biometric")
        val fcmToken = stringPreferencesKey("fcm_token")
    }

    val onboardingDone: Flow<Boolean> = store.data.map { it[Keys.onboardingDone] ?: false }
    val permissionsPrimed: Flow<Boolean> = store.data.map { it[Keys.permissionsPrimed] ?: false }
    val theme: Flow<ThemeMode> = store.data.map { p ->
        p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Dark
    }
    override val storedPassword: Flow<StoredPassword?> = store.data.map { p ->
        val salt = p[Keys.lockSalt]; val hash = p[Keys.lockHash]; val it = p[Keys.lockIterations]
        if (salt != null && hash != null && it != null) StoredPassword(salt, hash, it) else null
    }
    override val biometricEnabled: Flow<Boolean> = store.data.map { it[Keys.biometric] ?: false }
    val fcmToken: Flow<String?> = store.data.map { it[Keys.fcmToken] }

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[Keys.onboardingDone] = done }
    }
    suspend fun setPermissionsPrimed(done: Boolean) {
        store.edit { it[Keys.permissionsPrimed] = done }
    }
    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[Keys.theme] = mode.name }
    }
    override suspend fun setBiometric(enabled: Boolean) {
        store.edit { it[Keys.biometric] = enabled }
    }
    suspend fun setFcmToken(token: String?) {
        store.edit { if (token == null) it.remove(Keys.fcmToken) else it[Keys.fcmToken] = token }
    }

    override suspend fun setPassword(p: StoredPassword) {
        store.edit {
            it[Keys.lockSalt] = p.salt
            it[Keys.lockHash] = p.hash
            it[Keys.lockIterations] = p.iterations
        }
    }

    suspend fun currentPassword(): StoredPassword? = storedPassword.first()

    /** Sign-out wipes the lock (that's the "forgot password" path) but keeps onboarding/theme. */
    override suspend fun clearAccountScoped() {
        store.edit {
            it.remove(Keys.lockSalt); it.remove(Keys.lockHash); it.remove(Keys.lockIterations)
            it.remove(Keys.biometric); it.remove(Keys.permissionsPrimed); it.remove(Keys.fcmToken)
        }
    }
}
