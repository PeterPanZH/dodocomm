package com.dodocomm.app.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive SMS fields at rest with an AES-256-GCM key held in the
 * Android Keystore. The key never leaves the device and is not included in
 * backups, so a restore on another device cannot decrypt old local rows.
 *
 * Values are stored as `dodocomm:v1:<iv>:<ciphertext>` (base64). Legacy
 * plaintext values are passed through so the one-time migration can encrypt
 * them in place without data loss.
 */
class RecordCipher(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedKey: SecretKey? = null

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        if (isEncrypted(value)) return value
        return runCatching { encryptOrThrow(value) }.onFailure {
            RecordEncryptionStatus.mark(appContext, RecordEncryptionState.FAILED)
            Log.e(TAG, "加密本地记录失败: ${it.javaClass.simpleName}")
        }.getOrDefault(value)
    }

    /** Strict variant used for database rows: never writes plaintext on failure. */
    fun encryptOrThrow(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        if (isEncrypted(value)) return value
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (t: Throwable) {
            RecordEncryptionStatus.mark(appContext, RecordEncryptionState.FAILED)
            Log.e(TAG, "严格加密失败: ${t.javaClass.simpleName}")
            throw t
        }
    }

    fun decrypt(value: String?): String? {
        if (value.isNullOrEmpty() || !isEncrypted(value)) return value
        return runCatching {
            val payload = value.removePrefix(PREFIX)
            val separator = payload.indexOf(':')
            require(separator > 0)
            val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
            val encrypted = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.onFailure {
            Log.w(TAG, "解密本地记录失败: ${it.javaClass.simpleName}")
        }.getOrDefault("[已加密内容无法读取]")
    }

    object Plain {
        const val SENDER = "[已加密]"
        const val BODY = "[已加密内容]"
    }

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            val key = existing?.secretKey ?: generateKey()
            cachedKey = key
            return key
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "DODOCOMM_CRYPTO"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dodocomm_record_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "dodocomm:v1:"
    }
}
