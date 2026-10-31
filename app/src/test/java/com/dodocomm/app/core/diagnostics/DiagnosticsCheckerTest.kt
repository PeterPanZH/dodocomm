package com.dodocomm.app.core.diagnostics

import com.dodocomm.app.core.model.TodayStats
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.ui.model.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsCheckerTest {

    private val healthySettings = AppSettings(
        forwardingEnabled = true,
        channels = listOf(
            com.dodocomm.app.core.channel.PushChannel(
                id = "ntfy-1",
                type = com.dodocomm.app.core.channel.ChannelType.NTFY,
                name = "ntfy",
                enabled = true,
                serverUrl = "https://ntfy.sh",
                topic = "sms",
            ),
        ),
        foregroundServiceEnabled = true,
        autoStartOnBoot = true,
    )

    @Test
    fun `healthy configuration passes critical checks`() {
        val report = DiagnosticsChecker.check(
            DiagnosticsInput(
                settings = healthySettings,
                permissions = PermissionState(
                    receiveSms = true,
                    postNotifications = true,
                    notificationsEnabled = true,
                    batteryOptimizationsIgnored = true,
                    readPhoneState = true,
                ),
                stats = TodayStats(),
                serviceRunning = true,
                networkAvailable = true,
            ),
        )
        assertEquals(0, report.failed)
        assertTrue(report.passed >= 7)
    }

    @Test
    fun `missing sms permission and channels are failures`() {
        val report = DiagnosticsChecker.check(
            DiagnosticsInput(
                settings = AppSettings(),
                permissions = PermissionState(),
                stats = TodayStats(failed = 2, pending = 1),
                serviceRunning = false,
                networkAvailable = false,
            ),
        )
        assertEquals(2, report.failed)
        assertTrue(report.items.any { it.id == "sms_permission" && it.status == DiagnosticStatus.FAIL })
        assertTrue(report.items.any { it.id == "channels_configured" && it.status == DiagnosticStatus.FAIL })
    }

    @Test
    fun `share text has no credential fields`() {
        val report = DiagnosticsChecker.check(
            DiagnosticsInput(
                settings = healthySettings,
                permissions = PermissionState(receiveSms = true),
                stats = TodayStats(),
                serviceRunning = true,
                networkAvailable = true,
            ),
        )
        val text = report.toShareText()
        assertTrue(text.contains("DodoComm 运行诊断"))
        assertTrue(!text.contains("https://ntfy.sh"))
        assertTrue(!text.contains("token", ignoreCase = true))
    }
}
