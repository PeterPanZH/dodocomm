package com.dodocomm.app.ui.components

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** "+8613800138000" -> "13800138000"; blank -> "未知号码". */
fun displaySender(sender: String): String {
    val trimmed = sender.trim()
    if (trimmed.isEmpty()) return "未知号码"
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
    return "${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date)}"
}

fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}
