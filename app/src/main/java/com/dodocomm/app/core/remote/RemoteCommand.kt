package com.dodocomm.app.core.remote

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class RemoteAction { STATUS, PAUSE, RESUME }

data class RemoteCommand(
    val action: RemoteAction,
    val minutes: Int = 30,
    val nonce: Long,
    val signature: String,
)

object RemoteCommandParser {
    private val whitespace = Regex("\\s+")

    /** Parses `#DODO ACTION [minutes] NONCE HMAC`; returns null on malformed input. */
    fun parse(body: String): RemoteCommand? {
        val tokens = body.trim().split(whitespace)
        if (tokens.size < 4 || !tokens[0].equals("#DODO", ignoreCase = true)) return null
        val action = when (tokens[1].uppercase()) {
            "STATUS" -> RemoteAction.STATUS
            "RESUME" -> RemoteAction.RESUME
            "PAUSE" -> RemoteAction.PAUSE
            else -> return null
        }
        return if (action == RemoteAction.PAUSE) {
            if (tokens.size != 5) return null
            val minutes = tokens[2].toIntOrNull()?.coerceIn(1, 1440) ?: return null
            val nonce = tokens[3].toLongOrNull() ?: return null
            RemoteCommand(action, minutes, nonce, tokens[4].lowercase())
        } else {
            if (tokens.size != 4) return null
            val nonce = tokens[2].toLongOrNull() ?: return null
            RemoteCommand(action, 0, nonce, tokens[3].lowercase())
        }
    }

    fun canonical(command: RemoteCommand): String =
        "${command.action.name}|${command.minutes}|${command.nonce}"

    fun sign(secret: String, command: RemoteCommand): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical(command).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun verify(secret: String, command: RemoteCommand): Boolean {
        if (secret.isBlank() || command.signature.isBlank()) return false
        val expected = sign(secret, command)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            command.signature.toByteArray(Charsets.UTF_8),
        )
    }

    fun nonceIsFresh(nonce: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        kotlin.math.abs(nowSeconds - nonce) <= 300

    fun normalizeNumber(number: String): String = number.filter { it.isDigit() }
}
