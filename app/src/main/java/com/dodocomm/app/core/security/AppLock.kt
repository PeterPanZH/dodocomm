package com.dodocomm.app.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage for the app lock.
 *
 * Only a salted PBKDF2 hash is stored — never the PIN itself. The PIN is
 * intentionally short (6 digits) for usability, so a high iteration count and a
 * random per-install salt are used to make offline guessing expensive.
 */
object AppLock {
    const val PIN_LENGTH = 6
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            Base64.getEncoder().encodeToString(key)
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPin(pin: String, saltBase64: String, hashBase64: String): Boolean {
        if (!isValidPin(pin) || saltBase64.isBlank() || hashBase64.isBlank()) return false
        return runCatching {
            val expected = Base64.getDecoder().decode(hashBase64)
            val actual = Base64.getDecoder().decode(hashPin(pin, saltBase64))
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun setPin(pin: String): Pair<String, String> {
        require(isValidPin(pin)) { "PIN 必须为 $PIN_LENGTH 位数字" }
        val salt = newSalt()
        return salt to hashPin(pin, salt)
    }
}
