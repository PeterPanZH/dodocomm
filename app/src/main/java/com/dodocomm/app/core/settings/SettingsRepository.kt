package com.dodocomm.app.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Single source of truth for [AppSettings].
 *
 * Thread-safe: the SMS receiver may call [current] on a binder thread while the
 * UI updates settings on the main thread. Writes use [SharedPreferences.commit]
 * so a process that is killed right after a toggle keeps its configuration.
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _settings = MutableStateFlow(readAll())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Cheap, synchronous snapshot used from receivers/workers. */
    fun current(): AppSettings = _settings.value

    /** Applies [transform], persists synchronously and publishes the new state. */
    fun update(transform: (AppSettings) -> AppSettings): AppSettings = synchronized(lock) {
        val updated = transform(_settings.value)
        writeAll(updated)
        _settings.value = updated
        updated
    }

    /** Replaces the whole configuration (used by tests / debug tooling). */
    fun replace(settings: AppSettings): AppSettings = synchronized(lock) {
        writeAll(settings)
        _settings.value = settings
        settings
    }

    private fun readAll(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            forwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, defaults.forwardingEnabled),
            serverUrl = prefs.getString(KEY_SERVER_URL, defaults.serverUrl).orEmpty(),
            deviceKey = prefs.getString(KEY_DEVICE_KEY, defaults.deviceKey).orEmpty(),
            titleTemplate = prefs.getString(KEY_TITLE_TEMPLATE, defaults.titleTemplate).orEmpty(),
            pushGroup = prefs.getString(KEY_PUSH_GROUP, defaults.pushGroup).orEmpty(),
            pushSound = prefs.getString(KEY_PUSH_SOUND, defaults.pushSound).orEmpty(),
            pushLevel = PushLevel.fromName(prefs.getString(KEY_PUSH_LEVEL, defaults.pushLevel.name)),
            pushIcon = prefs.getString(KEY_PUSH_ICON, defaults.pushIcon).orEmpty(),
            pushUrl = prefs.getString(KEY_PUSH_URL, defaults.pushUrl).orEmpty(),
            isArchive = prefs.getBoolean(KEY_IS_ARCHIVE, defaults.isArchive),
            copyMode = CopyMode.fromName(
                prefs.getString(
                    KEY_COPY_MODE,
                    if (prefs.getBoolean(KEY_AUTO_COPY, false)) CopyMode.FULL.name else defaults.copyMode.name,
                ),
            ),
            badgeEnabled = prefs.getBoolean(KEY_BADGE_ENABLED, defaults.badgeEnabled),
            foregroundServiceEnabled = prefs.getBoolean(KEY_FOREGROUND_SERVICE_ENABLED, defaults.foregroundServiceEnabled),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, defaults.autoStartOnBoot),
            hideFromRecents = prefs.getBoolean(KEY_HIDE_FROM_RECENTS, defaults.hideFromRecents),
            appLockEnabled = prefs.getBoolean(KEY_APP_LOCK_ENABLED, defaults.appLockEnabled),
            appLockPinSalt = prefs.getString(KEY_APP_LOCK_SALT, defaults.appLockPinSalt).orEmpty(),
            appLockPinHash = prefs.getString(KEY_APP_LOCK_HASH, defaults.appLockPinHash).orEmpty(),
            autoLockMinutes = prefs.getInt(KEY_AUTO_LOCK_MINUTES, defaults.autoLockMinutes),
            biometricUnlock = prefs.getBoolean(KEY_BIOMETRIC_UNLOCK, defaults.biometricUnlock),
            senderWhitelist = decodeList(prefs.getString(KEY_SENDER_WHITELIST, null)),
            senderBlacklist = decodeList(prefs.getString(KEY_SENDER_BLACKLIST, null)),
            keywordBlacklist = decodeList(prefs.getString(KEY_KEYWORD_BLACKLIST, null)),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
            themeMode = ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, defaults.themeMode.name)),
            recordFilteredMessages = prefs.getBoolean(KEY_RECORD_FILTERED, defaults.recordFilteredMessages),
            retentionDays = prefs.getInt(KEY_RETENTION_DAYS, defaults.retentionDays),
            maxRecords = prefs.getInt(KEY_MAX_RECORDS, defaults.maxRecords),
            autoUpdateCheck = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, defaults.autoUpdateCheck),
        )
    }

    private fun writeAll(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_FORWARDING_ENABLED, settings.forwardingEnabled)
            .putString(KEY_SERVER_URL, settings.serverUrl)
            .putString(KEY_DEVICE_KEY, settings.deviceKey)
            .putString(KEY_TITLE_TEMPLATE, settings.titleTemplate)
            .putString(KEY_PUSH_GROUP, settings.pushGroup)
            .putString(KEY_PUSH_SOUND, settings.pushSound)
            .putString(KEY_PUSH_LEVEL, settings.pushLevel.name)
            .putString(KEY_PUSH_ICON, settings.pushIcon)
            .putString(KEY_PUSH_URL, settings.pushUrl)
            .putBoolean(KEY_IS_ARCHIVE, settings.isArchive)
            .putString(KEY_COPY_MODE, settings.copyMode.name)
            .putBoolean(KEY_AUTO_COPY, settings.copyMode == CopyMode.FULL)
            .putBoolean(KEY_BADGE_ENABLED, settings.badgeEnabled)
            .putBoolean(KEY_FOREGROUND_SERVICE_ENABLED, settings.foregroundServiceEnabled)
            .putBoolean(KEY_AUTO_START_ON_BOOT, settings.autoStartOnBoot)
            .putBoolean(KEY_HIDE_FROM_RECENTS, settings.hideFromRecents)
            .putBoolean(KEY_APP_LOCK_ENABLED, settings.appLockEnabled)
            .putString(KEY_APP_LOCK_SALT, settings.appLockPinSalt)
            .putString(KEY_APP_LOCK_HASH, settings.appLockPinHash)
            .putInt(KEY_AUTO_LOCK_MINUTES, settings.autoLockMinutes)
            .putBoolean(KEY_BIOMETRIC_UNLOCK, settings.biometricUnlock)
            .putString(KEY_SENDER_WHITELIST, encodeList(settings.senderWhitelist))
            .putString(KEY_SENDER_BLACKLIST, encodeList(settings.senderBlacklist))
            .putString(KEY_KEYWORD_BLACKLIST, encodeList(settings.keywordBlacklist))
            .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_RECORD_FILTERED, settings.recordFilteredMessages)
            .putInt(KEY_RETENTION_DAYS, settings.retentionDays)
            .putInt(KEY_MAX_RECORDS, settings.maxRecords)
            .putBoolean(KEY_AUTO_UPDATE_CHECK, settings.autoUpdateCheck)
            .commit()
    }

    companion object {
        const val PREFS_NAME = "sms2bark_settings"

        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_KEY = "device_key"
        private const val KEY_TITLE_TEMPLATE = "title_template"
        private const val KEY_PUSH_GROUP = "push_group"
        private const val KEY_PUSH_SOUND = "push_sound"
        private const val KEY_PUSH_LEVEL = "push_level"
        private const val KEY_PUSH_ICON = "push_icon"
        private const val KEY_PUSH_URL = "push_url"
        private const val KEY_IS_ARCHIVE = "is_archive"
        private const val KEY_AUTO_COPY = "auto_copy"
        private const val KEY_COPY_MODE = "copy_mode"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_MAX_RECORDS = "max_records"
        private const val KEY_BADGE_ENABLED = "badge_enabled"
        private const val KEY_FOREGROUND_SERVICE_ENABLED = "foreground_service_enabled"
        private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_SALT = "app_lock_pin_salt"
        private const val KEY_APP_LOCK_HASH = "app_lock_pin_hash"
        private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
        private const val KEY_BIOMETRIC_UNLOCK = "biometric_unlock"
        private const val KEY_SENDER_WHITELIST = "sender_whitelist"
        private const val KEY_SENDER_BLACKLIST = "sender_blacklist"
        private const val KEY_KEYWORD_BLACKLIST = "keyword_blacklist"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_RECORD_FILTERED = "record_filtered"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"

        fun encodeList(values: List<String>): String {
            val array = JSONArray()
            values.forEach { value -> array.put(value) }
            return array.toString()
        }

        fun decodeList(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index, "").trim()
                        if (value.isNotEmpty()) add(value)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
