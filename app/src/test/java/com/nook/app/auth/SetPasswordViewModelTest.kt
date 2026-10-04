package com.nook.app.auth

import com.nook.app.data.security.AppLockManager
import com.nook.app.data.security.PasswordHasher
import com.nook.app.feature.auth.PasswordStep
import com.nook.app.feature.auth.SetPasswordViewModel
import com.nook.app.security.FakeLockStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetPasswordViewModelTest {
    private val main = UnconfinedTestDispatcher()
    private val hasher = PasswordHasher(iterations = 1_000)

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `first-time flow saves a confirmed password`() = runTest(main) {
        val storage = FakeLockStorage()
        val lock = AppLockManager(storage, hasher, backgroundScope, io = StandardTestDispatcher(testScheduler))
        val vm = SetPasswordViewModel(change = false, lock = lock)
        var done = false

        vm.onInput("123"); vm.next { done = true }
        assertEquals("Use at least 6 characters", vm.state.value.error)

        vm.onInput("pass1234"); vm.next { done = true }
        assertEquals(PasswordStep.Confirm, vm.state.value.step)

        vm.onInput("pass9999"); vm.next { done = true }
        assertEquals("Those don't match", vm.state.value.error)

        vm.onInput("pass1234"); vm.next { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertNotNull(storage.storedPassword.value)
        assertTrue(lock.verify("pass1234"))
    }

    @Test fun `change flow verifies the current password first`() = runTest(main) {
        val storage = FakeLockStorage(hasher.hash("oldpass1".toCharArray()))
        val lock = AppLockManager(storage, hasher, backgroundScope, io = StandardTestDispatcher(testScheduler))
        val vm = SetPasswordViewModel(change = true, lock = lock)
        assertEquals(PasswordStep.VerifyCurrent, vm.state.value.step)

        vm.onInput("wrongpass"); vm.next {}
        advanceUntilIdle()
        assertEquals("That's not your current password", vm.state.value.error)

        vm.onInput("oldpass1"); vm.next {}
        advanceUntilIdle()
        assertEquals(PasswordStep.Enter, vm.state.value.step)
    }

    @Test fun `back from confirm returns to enter`() = runTest(main) {
        val lock = AppLockManager(FakeLockStorage(), hasher, backgroundScope, io = StandardTestDispatcher(testScheduler))
        val vm = SetPasswordViewModel(change = false, lock = lock)
        vm.onInput("pass1234"); vm.next {}
        assertTrue(vm.back())
        assertEquals(PasswordStep.Enter, vm.state.value.step)
    }
}
