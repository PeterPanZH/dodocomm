package com.dodocomm.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Keyed dedupe token for SMS rows. A raw SHA-256 of sender+timestamp+body is a
 * confirmation/OTP guessing oracle if the database file leaks; HMAC with a
 * non-exportable Keystore key is not.
 */
class HmacDedupe(private val context: android.content.Context) {
    @Volatile
    private var cachedKey: SecretKey? = null

    fun token(value: String): String = runCatching {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key())
        mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }.getOrElse {
        Log.w(TAG, "HMAC 去重失败，回退到 SHA-256: ${it.javaClass.simpleName}")
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            val secret = existing?.secretKey ?: run {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
                generator.init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .build(),
                )
                generator.generateKey()
            }
            cachedKey = secret
            return secret
        }
    }

    private companion object {
        const val TAG = "DODOCOMM_DEDUPE"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "dodocomm_dedupe_hmac_v1"
    }
}
