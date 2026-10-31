package com.dodocomm.app.core.channel

import java.util.UUID
import org.json.JSONObject

enum class ChannelType(val label: String) {
    BARK("Bark"),
    NTFY("ntfy"),
    TELEGRAM("Telegram"),
    PUSHOVER("Pushover"),
    EMAIL("Email");

    companion object {
        fun fromName(name: String?): ChannelType = entries.firstOrNull { it.name == name } ?: BARK
    }
}

/**
 * One push destination. Fields are deliberately flat so the configuration can
 * be persisted as JSON and edited without a polymorphic serializer.
 *
 * Bark keeps its credentials in the existing AppSettings fields for backward
 * compatibility; other channel types carry their own values here.
 */
data class PushChannel(
    val id: String,
    val type: ChannelType,
    val name: String,
    val enabled: Boolean = true,
    val serverUrl: String = "",
    val topic: String = "",
    val token: String = "",
    val chatId: String = "",
    val botToken: String = "",
    val userKey: String = "",
    val appToken: String = "",
    val priority: String = "",
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val smtpSecurity: String = "STARTTLS",
    val smtpUsername: String = "",
    val smtpPassword: String = "",
    val recipient: String = "",
) {
    fun isConfigured(): Boolean = when (type) {
        ChannelType.BARK -> true // legacy AppSettings hold the credentials
        ChannelType.NTFY -> serverUrl.isNotBlank() && topic.isNotBlank()
        ChannelType.TELEGRAM -> botToken.isNotBlank() && chatId.isNotBlank()
        ChannelType.PUSHOVER -> userKey.isNotBlank() && appToken.isNotBlank()
        ChannelType.EMAIL -> smtpHost.isNotBlank() && smtpUsername.isNotBlank() &&
            smtpPassword.isNotBlank() && recipient.isNotBlank()
    }

    fun copyWithId(newId: String = id): PushChannel = copy(id = newId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("name", name)
        put("enabled", enabled)
        put("serverUrl", serverUrl)
        put("topic", topic)
        put("token", token)
        put("chatId", chatId)
        put("botToken", botToken)
        put("userKey", userKey)
        put("appToken", appToken)
        put("priority", priority)
        put("smtpHost", smtpHost)
        put("smtpPort", smtpPort)
        put("smtpSecurity", smtpSecurity)
        put("smtpUsername", smtpUsername)
        put("smtpPassword", smtpPassword)
        put("recipient", recipient)
    }

    companion object {
        fun bark(): PushChannel = PushChannel(
            id = "bark",
            type = ChannelType.BARK,
            name = "Bark",
            enabled = true,
        )

        fun create(type: ChannelType): PushChannel {
            val id = "${type.name.lowercase()}-${UUID.randomUUID().toString().take(8)}"
            return when (type) {
                ChannelType.BARK -> bark()
                ChannelType.NTFY -> PushChannel(
                    id = id,
                    type = type,
                    name = "ntfy",
                    serverUrl = "https://ntfy.sh",
                )
                ChannelType.TELEGRAM -> PushChannel(id = id, type = type, name = "Telegram")
                ChannelType.PUSHOVER -> PushChannel(id = id, type = type, name = "Pushover")
                ChannelType.EMAIL -> PushChannel(id = id, type = type, name = "Email")
            }
        }

        fun fromJson(json: JSONObject): PushChannel = PushChannel(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
            type = ChannelType.fromName(json.optString("type")),
            name = json.optString("name").ifBlank { "Channel" },
            enabled = json.optBoolean("enabled", true),
            serverUrl = json.optString("serverUrl"),
            topic = json.optString("topic"),
            token = json.optString("token"),
            chatId = json.optString("chatId"),
            botToken = json.optString("botToken"),
            userKey = json.optString("userKey"),
            appToken = json.optString("appToken"),
            priority = json.optString("priority"),
            smtpHost = json.optString("smtpHost"),
            smtpPort = json.optInt("smtpPort", 587),
            smtpSecurity = json.optString("smtpSecurity", "STARTTLS"),
            smtpUsername = json.optString("smtpUsername"),
            smtpPassword = json.optString("smtpPassword"),
            recipient = json.optString("recipient"),
        )
    }
}
