package com.dodocomm.app.core.i18n

/** Current UI language for non-Compose helpers such as time formatting. */
enum class LanguageTag { ZH, ZH_TW, EN, JA, KO, ES, FR }

object LanguageState {
    @Volatile
    var tag: LanguageTag = LanguageTag.ZH
}
