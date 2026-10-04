package com.nook.app.data.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Persistence the lock needs; implemented by SettingsStore (DataStore) and by fakes in tests. */
interface LockStorage {
    val storedPassword: Flow<StoredPassword?>
    val biometricEnabled: Flow<Boolean>
    suspend fun setPassword(p: StoredPassword)
    suspend fun setBiometric(enabled: Boolean)
    suspend fun clearAccountScoped()
}

/**
 * App lock state machine.
 *  - Cold start: locked if a password exists.
 *  - Background ≥ [timeoutMs]: locked again on return.
 *  - [suppressNextLock] for our own system pickers (camera, photo picker, file picker).
 */
class AppLockManager(
    private val settings: LockStorage,
    private val hasher: PasswordHasher,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val timeoutMs: Long = 30_000,
) {
    /** null = still loading from disk. */
    val lockEnabled: StateFlow<Boolean?> = settings.storedPassword
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val biometricEnabled: StateFlow<Boolean> = settings.biometricEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var initialised = false
    private var backgroundAt = 0L
    private var suppressUntil = 0L

    /** Call once at cold start. */
    suspend fun init() {
        if (initialised) return
        initialised = true
        _locked.value = settings.storedPassword.first() != null
    }

    fun onBackground() { backgroundAt = clock() }

    fun onForeground() {
        val now = clock()
        val enabled = lockEnabled.value == true
        val suppressed = now < suppressUntil
        if (enabled && !suppressed && backgroundAt > 0 && now - backgroundAt >= timeoutMs) _locked.value = true
        backgroundAt = 0
    }

    /** We are about to open a system activity (picker/camera) — don't lock when we come back. */
    fun suppressNextLock(windowMs: Long = 5 * 60_000) { suppressUntil = clock() + windowMs }

    fun clearSuppression() { suppressUntil = 0 }

    suspend fun unlock(password: String): Boolean {
        val stored = settings.storedPassword.first() ?: run { _locked.value = false; return true }
        val ok = withContext(io) { hasher.verify(password.toCharArray(), stored) }
        if (ok) _locked.value = false
        return ok
    }

    fun unlockWithBiometric() { _locked.value = false }

    suspend fun setPassword(password: String) {
        val hashed = withContext(io) { hasher.hash(password.toCharArray()) }
        settings.setPassword(hashed)
        _locked.value = false
    }

    suspend fun verify(password: String): Boolean {
        val stored = settings.storedPassword.first() ?: return true
        return withContext(io) { hasher.verify(password.toCharArray(), stored) }
    }

    suspend fun setBiometric(enabled: Boolean) = settings.setBiometric(enabled)

    suspend fun hasPassword(): Boolean = settings.storedPassword.first() != null

    /** Sign-out / account deletion. */
    suspend fun reset() {
        settings.clearAccountScoped()
        _locked.value = false
    }
}
