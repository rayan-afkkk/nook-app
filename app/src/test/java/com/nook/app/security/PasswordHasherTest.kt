package com.nook.app.security

import com.nook.app.data.security.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher(iterations = 1_000) // fast for tests; prod uses 120k

    @Test fun `correct password verifies, wrong one does not`() {
        val stored = hasher.hash("hunter22".toCharArray())
        assertTrue(hasher.verify("hunter22".toCharArray(), stored))
        assertFalse(hasher.verify("hunter23".toCharArray(), stored))
    }

    @Test fun `same password hashes differently thanks to the salt`() {
        val a = hasher.hash("123456".toCharArray())
        val b = hasher.hash("123456".toCharArray())
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertEquals(1_000, a.iterations)
    }

    @Test fun `corrupt stored values never verify`() {
        val stored = hasher.hash("abcdef".toCharArray()).copy(salt = "not base64 !!")
        assertFalse(hasher.verify("abcdef".toCharArray(), stored))
    }

    @Test fun `validation enforces minimum length and confirmation`() {
        assertEquals("Use at least 6 characters", PasswordHasher.validate("12345"))
        assertEquals("Those don't match", PasswordHasher.validate("123456", "123457"))
        assertNull(PasswordHasher.validate("123456", "123456"))
    }
}
