package com.dodocomm.app.ui.components

import com.dodocomm.app.core.i18n.LanguageState
import com.dodocomm.app.core.i18n.LanguageTag
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "+8613800138000" -> "13800138000"; blank -> "未知号码". */
fun displaySender(sender: String): String {
    val trimmed = sender.trim()
    if (trimmed.isEmpty()) return when (LanguageState.tag) {
        LanguageTag.ZH_TW -> "未知號碼"
        LanguageTag.EN -> "Unknown sender"
        LanguageTag.JA -> "不明な番号"
        LanguageTag.KO -> "알 수 없는 번호"
        LanguageTag.ES -> "Remitente desconocido"
        LanguageTag.FR -> "Expéditeur inconnu"
        LanguageTag.ZH -> "未知号码"
    }
    val withoutCountryCode = if (trimmed.startsWith("+86")) trimmed.removePrefix("+86") else trimmed
    return withoutCountryCode.ifBlank { trimmed }
}

/**
 * Avatar text for a sender: CJK / letter senders use their first character,
 * numeric service numbers use the last two digits so they stay distinguishable.
 */
fun senderInitial(sender: String): String {
    val label = displaySender(sender)
    val first = label.firstOrNull() ?: return "?"
    return when {
        first.isDigit() -> label.takeLast(2).ifBlank { first.toString() }
        first.isLetter() -> first.uppercase(Locale.getDefault())
        else -> first.toString()
    }
}

/** "10月29日 周四" for the home header. */
fun todayLabel(): String {
    val date = LocalDate.now()
    return when (LanguageState.tag) {
        LanguageTag.ZH_TW -> "${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date)}"
        LanguageTag.EN -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))
        LanguageTag.JA -> date.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
        LanguageTag.KO -> date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
        LanguageTag.ES -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.forLanguageTag("es")))
        LanguageTag.FR -> date.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.forLanguageTag("fr")))
        LanguageTag.ZH -> "${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date)}"
    }
}

fun weekdayLabel(date: LocalDate): String = when (LanguageState.tag) {
    LanguageTag.ZH_TW -> listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")[date.dayOfWeek.value - 1]
    LanguageTag.EN -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[date.dayOfWeek.value - 1]
    LanguageTag.JA -> listOf("月", "火", "水", "木", "金", "土", "日")[date.dayOfWeek.value - 1]
    LanguageTag.KO -> listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
    LanguageTag.ES -> listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")[date.dayOfWeek.value - 1]
    LanguageTag.FR -> listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")[date.dayOfWeek.value - 1]
    LanguageTag.ZH -> when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}

fun greeting(): String = when (LanguageState.tag) {
    LanguageTag.ZH_TW -> when (LocalTime.now().hour) {
        in 5..11 -> "早安"
        in 12..17 -> "午安"
        else -> "晚安"
    }
    LanguageTag.KO -> when (LocalTime.now().hour) {
        in 5..11 -> "좋은 아침입니다"
        in 12..17 -> "안녕하세요"
        else -> "좋은 저녁입니다"
    }
    LanguageTag.ES -> when (LocalTime.now().hour) {
        in 5..11 -> "Buenos días"
        in 12..17 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    LanguageTag.FR -> when (LocalTime.now().hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    LanguageTag.EN -> when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    LanguageTag.JA -> when (LocalTime.now().hour) {
        in 5..11 -> "おはようございます"
        in 12..17 -> "こんにちは"
        else -> "こんばんは"
    }
    LanguageTag.ZH -> when (LocalTime.now().hour) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
}
