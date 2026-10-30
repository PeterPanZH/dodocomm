package com.dodocomm.app.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dodocomm.app.core.i18n.LanguageState
import com.dodocomm.app.core.i18n.LanguageTag
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
        LanguageState.tag = when (current) {
            UiLanguage.ZH -> LanguageTag.ZH
            UiLanguage.EN -> LanguageTag.EN
            UiLanguage.JA -> LanguageTag.JA
        }
    }

    fun resolve(language: AppLanguage, systemLocale: Locale): UiLanguage = when (language) {
        AppLanguage.ZH -> UiLanguage.ZH
        AppLanguage.EN -> UiLanguage.EN
        AppLanguage.JA -> UiLanguage.JA
        AppLanguage.SYSTEM -> when (systemLocale.language) {
            "zh" -> UiLanguage.ZH
            "ja" -> UiLanguage.JA
            else -> UiLanguage.EN
        }
    }
}

/** Access to the bundled dictionaries. */
object AppStrings {
    private data class PatternEntry(val regex: Regex, val format: String)

    private var en: Map<String, String> = emptyMap()
    private var ja: Map<String, String> = emptyMap()
    private var enPatterns: List<PatternEntry> = emptyList()
    private var jaPatterns: List<PatternEntry> = emptyList()

    fun init(context: android.content.Context) {
        if (en.isNotEmpty() || ja.isNotEmpty()) return
        en = load(context, "i18n/en.json")
        ja = load(context, "i18n/ja.json")
        enPatterns = loadPatterns(context, "i18n/patterns_en.json")
        jaPatterns = loadPatterns(context, "i18n/patterns_ja.json")
    }

    fun translate(source: String, language: UiLanguage = AppLocale.current): String {
        if (language == UiLanguage.ZH) return source
        val map = if (language == UiLanguage.EN) en else ja
        map[source]?.let { return it }
        val patterns = if (language == UiLanguage.EN) enPatterns else jaPatterns
        for (entry in patterns) {
            val match = entry.regex.matchEntire(source) ?: continue
            var result = entry.format
            match.groupValues.drop(1).forEachIndexed { index, value ->
                result = result.replace("{$index}", value)
            }
            return result
        }
        return source
    }

    private fun loadPatterns(context: android.content.Context, path: String): List<PatternEntry> = runCatching {
        val raw = context.assets.open(path).bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val regex = runCatching { Regex(item.getString("regex")) }.getOrNull() ?: continue
                add(PatternEntry(regex, item.getString("format")))
            }
        }
    }.getOrDefault(emptyList())

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
