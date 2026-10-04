package com.nook.app.security

import com.nook.app.data.security.AppLockManager
import com.nook.app.data.security.PasswordHasher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {
    private val hasher = PasswordHasher(iterations = 1_000)
    private var now = 1_000_000L

    private fun TestScope.manager(storage: FakeLockStorage) = AppLockManager(
        settings = storage,
        hasher = hasher,
        scope = backgroundScope,
        clock = { now },
        io = StandardTestDispatcher(testScheduler),
    )

    @Test fun `cold start is locked only when a password exists`() = runTest {
        val noPw = manager(FakeLockStorage()).also { it.init() }
        assertFalse(noPw.locked.value)
        val withPw = manager(FakeLockStorage(hasher.hash("secret1".toCharArray()))).also { it.init() }
        assertTrue(withPw.locked.value)
    }

    @Test fun `relocks after 30 seconds in the background, not before`() = runTest {
        val storage = FakeLockStorage()
        val m = manager(storage)
        m.init()
        m.setPassword("secret1")
        advanceUntilIdle()
        assertFalse(m.locked.value)

        m.onBackground(); now += 29_000; m.onForeground()
        assertFalse("29 s should not lock", m.locked.value)

        m.onBackground(); now += 31_000; m.onForeground()
        assertTrue("31 s should lock", m.locked.value)
    }

    @Test fun `own pickers do not trigger the lock`() = runTest {
        val m = manager(FakeLockStorage())
        m.init(); m.setPassword("secret1"); advanceUntilIdle()
        m.suppressNextLock()
        m.onBackground(); now += 60_000; m.onForeground()
        assertFalse(m.locked.value)
    }

    @Test fun `unlock needs the right password`() = runTest {
        val m = manager(FakeLockStorage(hasher.hash("secret1".toCharArray())))
        m.init()
        assertFalse(m.unlock("nope"))
        assertTrue(m.locked.value)
        assertTrue(m.unlock("secret1"))
        assertFalse(m.locked.value)
    }

    @Test fun `reset clears the password (forgot-password path)`() = runTest {
        val storage = FakeLockStorage(hasher.hash("secret1".toCharArray()))
        val m = manager(storage)
        m.init()
        m.reset()
        assertFalse(m.locked.value)
        assertFalse(m.hasPassword())
    }
}
