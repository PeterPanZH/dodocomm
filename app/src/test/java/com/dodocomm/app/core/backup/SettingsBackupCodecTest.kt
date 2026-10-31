package com.dodocomm.app.core.backup

import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.settings.PushLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackupCodecTest {

    private val base = AppSettings(appLockEnabled = true, appLockPinHash = "local-only")
    private val settings = base.copy(
        serverUrl = "https://bark.example.com",
        deviceKey = "secret-device-key",
        titleTemplate = "{device} · {sender}",
        pushGroup = "验证码",
        pushLevel = PushLevel.TIME_SENSITIVE,
        copyMode = CopyMode.CODE,
        senderWhitelist = listOf("95588"),
        keywordBlacklist = listOf("广告"),
        retentionDays = 30,
        maxRecords = 1000,
    )

    @Test
    fun `merge adds backup channels and rules without dropping current ones`() {
        val current = base.copy(
            channels = listOf(com.dodocomm.app.core.channel.PushChannel(id = "current", type = com.dodocomm.app.core.channel.ChannelType.NTFY, name = "current")),
            routingRules = listOf(
                com.dodocomm.app.core.routing.RoutingRule(id = "current-rule", name = "current"),
            ),
        )
        val restored = base.copy(
            channels = listOf(com.dodocomm.app.core.channel.PushChannel(id = "restored", type = com.dodocomm.app.core.channel.ChannelType.TELEGRAM, name = "restored")),
            routingRules = listOf(
                com.dodocomm.app.core.routing.RoutingRule(id = "restored-rule", name = "restored"),
            ),
            defaultChannelIds = listOf("restored"),
        )
        val merged = SettingsBackupCodec.merge(restored, current)
        assertEquals(2, merged.channels.size)
        assertEquals(2, merged.routingRules.size)
        assertEquals(listOf("restored"), merged.defaultChannelIds)
    }

    @Test
    fun `configuration round trips and preserves local app lock`() {
        val raw = SettingsBackupCodec.toJson(settings)
        val restored = SettingsBackupCodec.fromJson(raw, base)
        assertEquals(settings.serverUrl, restored.serverUrl)
        assertEquals(settings.deviceKey, restored.deviceKey)
        assertEquals(settings.titleTemplate, restored.titleTemplate)
        assertEquals(settings.pushLevel, restored.pushLevel)
        assertEquals(settings.copyMode, restored.copyMode)
        assertEquals(settings.senderWhitelist, restored.senderWhitelist)
        assertEquals(settings.keywordBlacklist, restored.keywordBlacklist)
        assertEquals(30, restored.retentionDays)
        assertEquals(1000, restored.maxRecords)
        assertEquals("local-only", restored.appLockPinHash)
        assertEquals(true, restored.appLockEnabled)
    }
}
