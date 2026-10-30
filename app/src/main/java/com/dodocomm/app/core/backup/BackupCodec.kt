package com.dodocomm.app.core.backup

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

data class BackupInfo(
    val createdAt: Long,
    val hasDeviceWrapper: Boolean,
)

data class WrappedKey(val iv: ByteArray, val ciphertext: ByteArray)

/**
 * Password + device-key protection for a backup file.
 *
 * The payload is encrypted with a random DEK. The DEK is wrapped twice:
 *  - a portable wrapper encrypted with a PBKDF2-derived key from the user's
 *    password (restorable on another phone);
 *  - an optional local wrapper encrypted with a device-bound key (fast local
 *    restore; the UI gates it behind biometric/device authentication).
 */
object BackupCodec {
    const val FORMAT = "dodocomm-backup"
    const val VERSION = 1
    private const val ITERATIONS = 200_000
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val KEY_BYTES = 32

    fun encode(
        payload: String,
        password: String,
        deviceWrap: ((ByteArray) -> WrappedKey)? = null,
    ): ByteArray {
        require(password.length >= 8) { "备份密码至少 8 位" }
        val random = SecureRandom()
        val dek = ByteArray(KEY_BYTES).also(random::nextBytes)
        val payloadIv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val payloadCipher = aesGcm(Cipher.ENCRYPT_MODE, dek, payloadIv)
        val payloadCiphertext = payloadCipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        val salt = ByteArray(16).also(random::nextBytes)
        val passphraseKey = derive(password, salt)
        val passIv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val wrappedWithPassword = aesGcm(Cipher.ENCRYPT_MODE, passphraseKey, passIv)
            .doFinal(dek)

        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("salt", b64(salt))
            .put("iterations", ITERATIONS)
            .put("passwordIv", b64(passIv))
            .put("passwordWrappedDek", b64(wrappedWithPassword))
            .put("payloadIv", b64(payloadIv))
            .put("payload", b64(payloadCiphertext))

        if (deviceWrap != null) {
            val wrapped = deviceWrap(dek)
            json.put("deviceIv", b64(wrapped.iv))
            json.put("deviceWrappedDek", b64(wrapped.ciphertext))
        }
        return json.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun info(bytes: ByteArray): BackupInfo {
        val json = root(bytes)
        return BackupInfo(
            createdAt = json.optLong("createdAt", 0L),
            hasDeviceWrapper = json.has("deviceWrappedDek"),
        )
    }

    fun decodeWithPassword(bytes: ByteArray, password: String): String {
        val json = root(bytes)
        val salt = unb64(json.getString("salt"))
        val key = derive(password, salt)
        val dek = aesGcm(
            Cipher.DECRYPT_MODE,
            key,
            unb64(json.getString("passwordIv")),
        ).doFinal(unb64(json.getString("passwordWrappedDek")))
        return decryptPayload(json, dek)
    }

    fun decodeWithDeviceWrapper(
        bytes: ByteArray,
        unwrap: (iv: ByteArray, ciphertext: ByteArray) -> ByteArray,
    ): String {
        val json = root(bytes)
        require(json.has("deviceWrappedDek")) { "该备份没有本机快速恢复信息" }
        val dek = unwrap(
            unb64(json.getString("deviceIv")),
            unb64(json.getString("deviceWrappedDek")),
        )
        return decryptPayload(json, dek)
    }

    private fun decryptPayload(json: JSONObject, dek: ByteArray): String {
        val plain = aesGcm(
            Cipher.DECRYPT_MODE,
            dek,
            unb64(json.getString("payloadIv")),
        ).doFinal(unb64(json.getString("payload")))
        return String(plain, Charsets.UTF_8)
    }

    private fun root(bytes: ByteArray): JSONObject {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        require(json.optString("format") == FORMAT) { "不是 DodoComm 备份文件" }
        require(json.optInt("version") == VERSION) { "不支持的备份版本" }
        return json
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun aesGcm(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
