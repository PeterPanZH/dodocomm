package com.dodocomm.app.core.i18n

/** Current UI language for non-Compose helpers such as time formatting. */
enum class LanguageTag { ZH, EN, JA }

object LanguageState {
    @Volatile
    var tag: LanguageTag = LanguageTag.ZH
}
