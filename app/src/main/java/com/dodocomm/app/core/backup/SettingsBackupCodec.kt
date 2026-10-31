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
        put("automationEnabled", settings.automationEnabled)
        put("automationToken", settings.automationToken)
        put("remoteEnabled", settings.remoteEnabled)
        put("remoteSecret", settings.remoteSecret)
        put("remoteTrustedNumber", settings.remoteTrustedNumber)
        put("remoteLastNonce", settings.remoteLastNonce)
        put("senderWhitelist", JSONArray(settings.senderWhitelist))
        put("senderBlacklist", JSONArray(settings.senderBlacklist))
        put("keywordBlacklist", JSONArray(settings.keywordBlacklist))
    }.toString()

    /** Restores only the backed-up configuration; lock/PIN fields stay local. */
    fun fromJson(raw: String, base: AppSettings): AppSettings {
        val json = JSONObject(raw)
        require(json.optInt("version", VERSION) <= VERSION) { "备份版本过新" }
        val legacyServer = json.optString("serverUrl", base.serverUrl)
        val legacyDeviceKey = json.optString("deviceKey", base.deviceKey)
        val legacyGroup = json.optString("pushGroup", base.pushGroup)
        val legacySound = json.optString("pushSound", base.pushSound)
        val legacyLevel = json.optString("pushLevel", base.pushLevel.name)
        val legacyIcon = json.optString("pushIcon", base.pushIcon)
        val legacyUrl = json.optString("pushUrl", base.pushUrl)
        val legacyArchive = json.optBoolean("isArchive", base.isArchive)
        val legacyBadge = json.optBoolean("badgeEnabled", base.badgeEnabled)
        val legacyCopyMode = json.optString("copyMode", base.copyMode.name)
        return base.copy(
            forwardingEnabled = json.optBoolean("forwardingEnabled", base.forwardingEnabled),
            serverUrl = json.optString("serverUrl", base.serverUrl),
            deviceKey = json.optString("deviceKey", base.deviceKey),
            channels = migrateBarkChannel(
                channels = json.optJSONArray("channels")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            add(PushChannel.fromJson(array.getJSONObject(index)))
                        }
                    }
                } ?: base.channels,
                serverUrl = legacyServer,
                deviceKey = legacyDeviceKey,
                group = legacyGroup,
                sound = legacySound,
                level = legacyLevel,
                icon = legacyIcon,
                url = legacyUrl,
                archive = legacyArchive,
                badge = legacyBadge,
                copyMode = legacyCopyMode,
            ),
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
            automationEnabled = json.optBoolean("automationEnabled", base.automationEnabled),
            automationToken = json.optString("automationToken", base.automationToken),
            remoteEnabled = json.optBoolean("remoteEnabled", base.remoteEnabled),
            remoteSecret = json.optString("remoteSecret", base.remoteSecret),
            remoteTrustedNumber = json.optString("remoteTrustedNumber", base.remoteTrustedNumber),
            remoteLastNonce = json.optLong("remoteLastNonce", base.remoteLastNonce),
            senderWhitelist = json.optJSONArray("senderWhitelist").toStringList(),
            senderBlacklist = json.optJSONArray("senderBlacklist").toStringList(),
            keywordBlacklist = json.optJSONArray("keywordBlacklist").toStringList(),
        )
    }

    /** Additive merge used by the import preview: backup wins for matching ids. */
    fun merge(restored: AppSettings, current: AppSettings): AppSettings = restored.copy(
        channels = (current.channels + restored.channels)
            .distinctBy { "${'$'}{it.type}:${'$'}{it.id}" },
        routingRules = (current.routingRules + restored.routingRules)
            .distinctBy { it.id },
        senderWhitelist = (current.senderWhitelist + restored.senderWhitelist).distinct(),
        senderBlacklist = (current.senderBlacklist + restored.senderBlacklist).distinct(),
        keywordBlacklist = (current.keywordBlacklist + restored.keywordBlacklist).distinct(),
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private fun migrateBarkChannel(
        channels: List<PushChannel>,
        serverUrl: String,
        deviceKey: String,
        group: String,
        sound: String,
        level: String,
        icon: String,
        url: String,
        archive: Boolean,
        badge: Boolean,
        copyMode: String,
    ): List<PushChannel> {
        val base = if (channels.any { it.type == com.dodocomm.app.core.channel.ChannelType.BARK }) {
            channels
        } else {
            listOf(PushChannel.bark()) + channels
        }
        return base.map { channel ->
            if (channel.type != com.dodocomm.app.core.channel.ChannelType.BARK || channel.serverUrl.isNotBlank()) {
                channel
            } else {
                channel.copy(
                    serverUrl = serverUrl,
                    deviceKey = deviceKey,
                    pushGroup = group,
                    pushSound = sound,
                    pushLevel = level,
                    pushIcon = icon,
                    pushUrl = url,
                    isArchive = archive,
                    badgeEnabled = badge,
                    copyMode = copyMode,
                )
            }
        }
    }
}
