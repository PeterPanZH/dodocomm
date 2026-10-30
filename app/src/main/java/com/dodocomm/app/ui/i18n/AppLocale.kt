package com.dodocomm.app.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dodocomm.app.core.settings.AppLanguage
import java.util.Locale

enum class UiLanguage { ZH, EN, JA }

/**
 * Lightweight in-app localization. Dictionaries live in assets/i18n and are
 * loaded once; Compose observes [current] so switching language recomposes.
 */
object AppLocale {
    var current: UiLanguage by mutableStateOf(resolve(AppLanguage.SYSTEM, Locale.getDefault()))
        private set

    fun apply(language: AppLanguage, systemLocale: Locale = Locale.getDefault()) {
        current = resolve(language, systemLocale)
    }

    fun resolve(language: AppLanguage, systemLocale: Locale): UiLanguage = when (language) {
        AppLanguage.ZH -> UiLanguage.ZH
        AppLanguage.EN -> UiLanguage.EN
        AppLanguage.JA -> UiLanguage.JA
        // 1.7.1 will follow the system locale once zh/en/ja dictionaries are complete.
        AppLanguage.SYSTEM -> UiLanguage.ZH
    }
}

/** Access to the bundled dictionaries. */
object AppStrings {
    private var en: Map<String, String> = emptyMap()
    private var ja: Map<String, String> = emptyMap()

    fun init(context: android.content.Context) {
        if (en.isNotEmpty() || ja.isNotEmpty()) return
        en = load(context, "i18n/en.json")
        ja = load(context, "i18n/ja.json")
    }

    fun translate(source: String, language: UiLanguage = AppLocale.current): String {
        if (language == UiLanguage.ZH) return source
        val map = if (language == UiLanguage.EN) en else ja
        return map[source] ?: source
    }

    private fun load(context: android.content.Context, path: String): Map<String, String> = runCatching {
        val raw = context.assets.open(path).bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(raw)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optString(key, key))
            }
        }
    }.getOrDefault(emptyMap())
}
