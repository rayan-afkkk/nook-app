package com.nook.app.security

import com.nook.app.data.security.LockStorage
import com.nook.app.data.security.StoredPassword
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLockStorage(initial: StoredPassword? = null) : LockStorage {
    override val storedPassword = MutableStateFlow(initial)
    override val biometricEnabled = MutableStateFlow(false)
    override suspend fun setPassword(p: StoredPassword) { storedPassword.value = p }
    override suspend fun setBiometric(enabled: Boolean) { biometricEnabled.value = enabled }
    override suspend fun clearAccountScoped() { storedPassword.value = null; biometricEnabled.value = false }
}
