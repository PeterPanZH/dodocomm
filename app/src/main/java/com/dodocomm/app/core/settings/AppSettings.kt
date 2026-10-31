package com.dodocomm.app.core.settings

import com.dodocomm.app.core.channel.ChannelType
import com.dodocomm.app.core.routing.RoutingRule
import com.dodocomm.app.core.channel.PushChannel

enum class PushLevel(val apiValue: String, val label: String) {
    ACTIVE("active", "即时"),
    TIME_SENSITIVE("timeSensitive", "时效性"),
    PASSIVE("passive", "静默"),
    CRITICAL("critical", "重要（持续响铃）");

    companion object {
        fun fromName(name: String?): PushLevel = entries.firstOrNull { it.name == name } ?: ACTIVE
    }
}

enum class AppLanguage(val label: String) {
    SYSTEM("跟随系统"),
    ZH("简体中文"),
    EN("English"),
    JA("日本語");

    companion object {
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class CopyMode(val label: String) {
    OFF("不复制"),
    FULL("复制全文"),
    CODE("仅复制验证码");

    companion object {
        fun fromName(name: String?): CopyMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * All user-visible settings, persisted in SharedPreferences.
 *
 * This class is deliberately a plain data class so previews, tests and the UI
 * can reason about configuration without touching storage.
 */
data class AppSettings(
    val forwardingEnabled: Boolean = true,
    val serverUrl: String = "https://api.day.app",
    val deviceKey: String = "",
    /** Enabled push destinations. Bark credentials remain in serverUrl/deviceKey. */
    val channels: List<PushChannel> = listOf(PushChannel.bark()),
    val routingRules: List<RoutingRule> = emptyList(),
    /** Optional custom name used by the {device} template token. */
    val deviceName: String = "",
    val titleTemplate: String = DEFAULT_TITLE_TEMPLATE,
    /** Bark notification body template; `{body}` is the original SMS text. */
    val bodyTemplate: String = DEFAULT_BODY_TEMPLATE,
    val pushGroup: String = "短信",
    val pushSound: String = "",
    val pushLevel: PushLevel = PushLevel.ACTIVE,
    val pushIcon: String = "",
    val pushUrl: String = "",
    val isArchive: Boolean = true,
    val copyMode: CopyMode = CopyMode.OFF,
    val badgeEnabled: Boolean = false,
    val foregroundServiceEnabled: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    /** Remove the app from the recent-tasks list when the user leaves it. */
    val hideFromRecents: Boolean = false,
    /** App lock: 6-digit PIN + optional biometric unlock. */
    val appLockEnabled: Boolean = false,
    val appLockPinSalt: String = "",
    val appLockPinHash: String = "",
    /** 0 = lock immediately when leaving; otherwise minutes of grace. */
    val autoLockMinutes: Int = 1,
    val biometricUnlock: Boolean = true,
    val senderWhitelist: List<String> = emptyList(),
    val senderBlacklist: List<String> = emptyList(),
    val keywordBlacklist: List<String> = emptyList(),
    val dynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val recordFilteredMessages: Boolean = true,
    /** 0 = keep forever; otherwise delete records older than this many days. */
    val retentionDays: Int = 0,
    /** Hard cap on locally stored rows (maintenance enforces it). */
    val maxRecords: Int = 500,
    /** Check GitHub Releases for a new version at most once per 24h. */
    val autoUpdateCheck: Boolean = true,
    /** Temporary pause from the ongoing notification; 0 = not paused. */
    val pauseUntil: Long = 0L,
    /** Retry policy for failed pushes. */
    val maxRetryAttempts: Int = 5,
    val retryBackoffSeconds: Int = 15,
    val retryOnlyWifi: Boolean = false,
    val retryMaxHours: Int = 24,
    /** Broadcast automation is off by default; commands require this token. */
    val automationEnabled: Boolean = false,
    val automationToken: String = "",
) {
    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = pauseUntil > now

    /** True once the user has configured a Bark server and a device key. */
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && deviceKey.isNotBlank()

    /** Channels that are enabled and have enough credentials to send. */
    fun activePushChannels(): List<PushChannel> = channels.filter { channel ->
        channel.enabled && channel.isConfigured()
    }

    val hasAnyConfiguredChannel: Boolean get() = activePushChannels().isNotEmpty()

    val hasFilters: Boolean
        get() = senderWhitelist.isNotEmpty() || senderBlacklist.isNotEmpty() || keywordBlacklist.isNotEmpty()

    /**
     * Normalised Bark base URL, e.g. "https://api.day.app" or
     * "http://10.0.0.8:8080". Accepts a pasted ".../push" endpoint too.
     */
    fun normalizedServerBase(): String {
        var base = serverUrl.trim()
        if (base.isEmpty()) return ""
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://$base"
        }
        base = base.trimEnd('/')
        if (base.endsWith("/push")) base = base.removeSuffix("/push").trimEnd('/')
        return base
    }

    /** Full Bark V2 push endpoint, e.g. "https://api.day.app/push". */
    fun pushEndpoint(): String {
        val base = normalizedServerBase()
        return if (base.isEmpty()) "" else "$base/push"
    }

    companion object {
        /** Old default kept for one-time migration to [DEFAULT_TITLE_TEMPLATE]. */
        const val LEGACY_TITLE_TEMPLATE = "来自 {sender}"
        const val DEFAULT_TITLE_TEMPLATE = "{device} · {sender}"
        const val DEFAULT_BODY_TEMPLATE = "{body}\n\n{datetime} · {sim} · {number}"
    }
}
