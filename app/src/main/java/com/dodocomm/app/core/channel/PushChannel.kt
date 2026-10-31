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
    val deviceKey: String = "",
    val pushGroup: String = "短信",
    val pushSound: String = "",
    val pushLevel: String = "active",
    val pushIcon: String = "",
    val pushUrl: String = "",
    val isArchive: Boolean = true,
    val badgeEnabled: Boolean = false,
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
        ChannelType.BARK -> serverUrl.isNotBlank() && deviceKey.isNotBlank()
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
        put("deviceKey", deviceKey)
        put("pushGroup", pushGroup)
        put("pushSound", pushSound)
        put("pushLevel", pushLevel)
        put("pushIcon", pushIcon)
        put("pushUrl", pushUrl)
        put("isArchive", isArchive)
        put("badgeEnabled", badgeEnabled)
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
        fun bark(
            serverUrl: String = "",
            deviceKey: String = "",
            pushGroup: String = "短信",
            pushSound: String = "",
            pushLevel: String = "active",
            pushIcon: String = "",
            pushUrl: String = "",
            isArchive: Boolean = true,
            badgeEnabled: Boolean = false,
        ): PushChannel = PushChannel(
            id = "bark",
            type = ChannelType.BARK,
            name = "Bark",
            enabled = true,
            serverUrl = serverUrl,
            deviceKey = deviceKey,
            pushGroup = pushGroup,
            pushSound = pushSound,
            pushLevel = pushLevel,
            pushIcon = pushIcon,
            pushUrl = pushUrl,
            isArchive = isArchive,
            badgeEnabled = badgeEnabled,
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
            deviceKey = json.optString("deviceKey"),
            pushGroup = json.optString("pushGroup", "短信"),
            pushSound = json.optString("pushSound"),
            pushLevel = json.optString("pushLevel", "active"),
            pushIcon = json.optString("pushIcon"),
            pushUrl = json.optString("pushUrl"),
            isArchive = json.optBoolean("isArchive", true),
            badgeEnabled = json.optBoolean("badgeEnabled", false),
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
