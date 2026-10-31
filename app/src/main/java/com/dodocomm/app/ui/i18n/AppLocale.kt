package com.dodocomm.app.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dodocomm.app.core.i18n.LanguageState
import com.dodocomm.app.core.i18n.LanguageTag
import com.dodocomm.app.core.settings.AppLanguage
import java.util.Locale

enum class UiLanguage { ZH, ZH_TW, EN, JA, KO, ES, FR }

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
            UiLanguage.ZH_TW -> LanguageTag.ZH_TW
            UiLanguage.EN -> LanguageTag.EN
            UiLanguage.JA -> LanguageTag.JA
            UiLanguage.KO -> LanguageTag.KO
            UiLanguage.ES -> LanguageTag.ES
            UiLanguage.FR -> LanguageTag.FR
        }
    }

    fun resolve(language: AppLanguage, systemLocale: Locale): UiLanguage = when (language) {
        AppLanguage.ZH -> UiLanguage.ZH
        AppLanguage.ZH_TW -> UiLanguage.ZH_TW
        AppLanguage.EN -> UiLanguage.EN
        AppLanguage.JA -> UiLanguage.JA
        AppLanguage.KO -> UiLanguage.KO
        AppLanguage.ES -> UiLanguage.ES
        AppLanguage.FR -> UiLanguage.FR
        AppLanguage.SYSTEM -> when (systemLocale.language) {
            "zh" -> if (systemLocale.country in setOf("TW", "HK", "MO") ||
                systemLocale.script == "Hant"
            ) UiLanguage.ZH_TW else UiLanguage.ZH
            "ja" -> UiLanguage.JA
            "ko" -> UiLanguage.KO
            "es" -> UiLanguage.ES
            "fr" -> UiLanguage.FR
            else -> UiLanguage.EN
        }
    }
}

/** Access to the bundled dictionaries. */
object AppStrings {
    private data class PatternEntry(val regex: Regex, val format: String)

    private val maps = mutableMapOf<UiLanguage, Map<String, String>>()
    private val patterns = mutableMapOf<UiLanguage, List<PatternEntry>>()

    private val assetNames = mapOf(
        UiLanguage.ZH_TW to "zh-TW",
        UiLanguage.EN to "en",
        UiLanguage.JA to "ja",
        UiLanguage.KO to "ko",
        UiLanguage.ES to "es",
        UiLanguage.FR to "fr",
    )

    fun init(context: android.content.Context) {
        if (maps.isNotEmpty()) return
        assetNames.forEach { (language, name) ->
            maps[language] = load(context, "i18n/$name.json")
            patterns[language] = loadPatterns(context, "i18n/patterns_$name.json")
        }
    }

    fun translate(source: String, language: UiLanguage = AppLocale.current): String {
        if (language == UiLanguage.ZH) return source
        val map = maps[language].orEmpty()
        map[source]?.let { return it }
        val languagePatterns = patterns[language].orEmpty()
        for (entry in languagePatterns) {
            val match = entry.regex.matchEntire(source) ?: continue
            var result = entry.format
            match.groupValues.drop(1).forEachIndexed { index, value ->
                val translatedValue = if (value == source) value else translate(value, language)
                result = result.replace("{$index}", translatedValue)
            }
            return result
        }
        val english = maps[UiLanguage.EN].orEmpty()[source]
        if (english != null) return english
        for (entry in patterns[UiLanguage.EN].orEmpty()) {
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
