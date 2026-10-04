package com.nook.app.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class StoredPassword(val salt: String, val hash: String, val iterations: Int)

/**
 * Salted, deliberately slow PBKDF2 (HMAC-SHA256). Only the hash ever touches disk.
 * Call from a background dispatcher: one hash takes ~100–300 ms on a phone by design.
 */
class PasswordHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    fun hash(password: CharArray): StoredPassword {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt, iterations)
        return StoredPassword(encode(salt), encode(derived), iterations)
    }

    fun verify(password: CharArray, stored: StoredPassword): Boolean {
        val salt = runCatching { decode(stored.salt) }.getOrNull() ?: return false
        val expected = runCatching { decode(stored.hash) }.getOrNull() ?: return false
        val actual = derive(password, salt, stored.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun decode(s: String) = Base64.getDecoder().decode(s)

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        const val MIN_LENGTH = 6
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        /** Returns an error message, or null when the password is acceptable. */
        fun validate(password: String, confirm: String? = null): String? = when {
            password.length < MIN_LENGTH -> "Use at least $MIN_LENGTH characters"
            confirm != null && password != confirm -> "Those don't match"
            else -> null
        }
    }
}
