package com.dodocomm.app.core.backup

import com.dodocomm.app.core.channel.PushChannel
import com.dodocomm.app.core.routing.RoutingRule
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.settings.PushLevel
import com.dodocomm.app.core.settings.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises user configuration (not history, not app-lock credentials) for the
 * encrypted backup file.
 */
object SettingsBackupCodec {
    const val VERSION = 1

    fun toJson(settings: AppSettings): String = JSONObject().apply {
        put("version", VERSION)
        put("forwardingEnabled", settings.forwardingEnabled)
        put("serverUrl", settings.serverUrl)
        put("deviceKey", settings.deviceKey)
        put("channels", JSONArray().also { array -> settings.channels.forEach { array.put(it.toJson()) } })
        put("routingRules", JSONArray().also { array -> settings.routingRules.forEach { array.put(it.toJson()) } })
        put("deviceName", settings.deviceName)
        put("titleTemplate", settings.titleTemplate)
        put("bodyTemplate", settings.bodyTemplate)
        put("pushGroup", settings.pushGroup)
        put("pushSound", settings.pushSound)
        put("pushLevel", settings.pushLevel.name)
        put("pushIcon", settings.pushIcon)
        put("pushUrl", settings.pushUrl)
        put("isArchive", settings.isArchive)
        put("copyMode", settings.copyMode.name)
        put("badgeEnabled", settings.badgeEnabled)
        put("foregroundServiceEnabled", settings.foregroundServiceEnabled)
        put("autoStartOnBoot", settings.autoStartOnBoot)
        put("hideFromRecents", settings.hideFromRecents)
        put("dynamicColor", settings.dynamicColor)
        put("themeMode", settings.themeMode.name)
        put("recordFilteredMessages", settings.recordFilteredMessages)
        put("retentionDays", settings.retentionDays)
        put("maxRecords", settings.maxRecords)
        put("autoUpdateCheck", settings.autoUpdateCheck)
        put("maxRetryAttempts", settings.maxRetryAttempts)
        put("retryBackoffSeconds", settings.retryBackoffSeconds)
        put("retryOnlyWifi", settings.retryOnlyWifi)
        put("retryMaxHours", settings.retryMaxHours)
        put("senderWhitelist", JSONArray(settings.senderWhitelist))
        put("senderBlacklist", JSONArray(settings.senderBlacklist))
        put("keywordBlacklist", JSONArray(settings.keywordBlacklist))
    }.toString()

    /** Restores only the backed-up configuration; lock/PIN fields stay local. */
    fun fromJson(raw: String, base: AppSettings): AppSettings {
        val json = JSONObject(raw)
        require(json.optInt("version", VERSION) <= VERSION) { "备份版本过新" }
        return base.copy(
            forwardingEnabled = json.optBoolean("forwardingEnabled", base.forwardingEnabled),
            serverUrl = json.optString("serverUrl", base.serverUrl),
            deviceKey = json.optString("deviceKey", base.deviceKey),
            channels = json.optJSONArray("channels")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        add(PushChannel.fromJson(array.getJSONObject(index)))
                    }
                }
            } ?: base.channels,
            routingRules = json.optJSONArray("routingRules")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) add(RoutingRule.fromJson(array.getJSONObject(index)))
                }
            } ?: base.routingRules,
            deviceName = json.optString("deviceName", base.deviceName),
            titleTemplate = json.optString("titleTemplate", base.titleTemplate),
            bodyTemplate = json.optString("bodyTemplate", base.bodyTemplate),
            pushGroup = json.optString("pushGroup", base.pushGroup),
            pushSound = json.optString("pushSound", base.pushSound),
            pushLevel = PushLevel.fromName(json.optString("pushLevel", base.pushLevel.name)),
            pushIcon = json.optString("pushIcon", base.pushIcon),
            pushUrl = json.optString("pushUrl", base.pushUrl),
            isArchive = json.optBoolean("isArchive", base.isArchive),
            copyMode = CopyMode.fromName(json.optString("copyMode", base.copyMode.name)),
            badgeEnabled = json.optBoolean("badgeEnabled", base.badgeEnabled),
            foregroundServiceEnabled = json.optBoolean("foregroundServiceEnabled", base.foregroundServiceEnabled),
            autoStartOnBoot = json.optBoolean("autoStartOnBoot", base.autoStartOnBoot),
            hideFromRecents = json.optBoolean("hideFromRecents", base.hideFromRecents),
            dynamicColor = json.optBoolean("dynamicColor", base.dynamicColor),
            themeMode = ThemeMode.fromName(json.optString("themeMode", base.themeMode.name)),
            recordFilteredMessages = json.optBoolean("recordFilteredMessages", base.recordFilteredMessages),
            retentionDays = json.optInt("retentionDays", base.retentionDays),
            maxRecords = json.optInt("maxRecords", base.maxRecords),
            autoUpdateCheck = json.optBoolean("autoUpdateCheck", base.autoUpdateCheck),
            maxRetryAttempts = json.optInt("maxRetryAttempts", base.maxRetryAttempts),
            retryBackoffSeconds = json.optInt("retryBackoffSeconds", base.retryBackoffSeconds),
            retryOnlyWifi = json.optBoolean("retryOnlyWifi", base.retryOnlyWifi),
            retryMaxHours = json.optInt("retryMaxHours", base.retryMaxHours),
            senderWhitelist = json.optJSONArray("senderWhitelist").toStringList(),
            senderBlacklist = json.optJSONArray("senderBlacklist").toStringList(),
            keywordBlacklist = json.optJSONArray("keywordBlacklist").toStringList(),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }
}
