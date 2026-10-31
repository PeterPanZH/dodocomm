package com.dodocomm.app.core.util

import com.dodocomm.app.core.i18n.LanguageState
import com.dodocomm.app.core.i18n.LanguageTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Language-aware time formatting shared by the UI and notifications. */
object TimeFormatter {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    private fun monthDay(): DateTimeFormatter = when (LanguageState.tag) {
        LanguageTag.EN -> DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        LanguageTag.JA, LanguageTag.ZH, LanguageTag.ZH_TW -> DateTimeFormatter.ofPattern("M月d日", Locale.JAPANESE)
        LanguageTag.KO -> DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        LanguageTag.ES -> DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
        LanguageTag.FR -> DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("fr"))
    }

    private fun yearMonthDay(): DateTimeFormatter = when (LanguageState.tag) {
        LanguageTag.EN -> DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
        LanguageTag.JA, LanguageTag.ZH, LanguageTag.ZH_TW -> DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPANESE)
        LanguageTag.KO -> DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
        LanguageTag.ES -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))
        LanguageTag.FR -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("fr"))
    }

    private fun full(): DateTimeFormatter = when (LanguageState.tag) {
        LanguageTag.EN -> DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH)
        LanguageTag.JA, LanguageTag.ZH, LanguageTag.ZH_TW -> DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.JAPANESE)
        LanguageTag.KO -> DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREAN)
        LanguageTag.ES -> DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("es"))
        LanguageTag.FR -> DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("fr"))
    }

    fun startOfToday(now: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatClock(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(clockFormatter)

    fun formatFull(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(full())

    /** "14:32" today, "昨天 14:32", "3月8日 14:32" this year, otherwise full date. */
    fun formatRecordTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val target = Instant.ofEpochMilli(timestamp).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val targetDate = target.toLocalDate()
        return when {
            targetDate == today -> target.format(clockFormatter)
            targetDate == today.minusDays(1) -> yesterdayLabel() + " " + target.format(clockFormatter)
            targetDate.year == today.year -> "${target.format(monthDay())} ${target.format(clockFormatter)}"
            else -> target.format(full())
        }
    }

    /** Date header for grouped history: "今天", "昨天", "3月8日", "2025年12月1日". */
    fun formatDateHeader(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today -> todayLabel()
            date == today.minusDays(1) -> yesterdayLabel()
            date.year == today.year -> date.format(monthDay())
            else -> date.format(yearMonthDay())
        }
    }

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> justNowLabel()
            diff < 3_600_000L -> minutesAgo(diff / 60_000L)
            diff < 86_400_000L -> hoursAgo(diff / 3_600_000L)
            else -> formatRecordTime(timestamp, now)
        }
    }

    fun isSameDay(a: Long, b: Long): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    fun todayDate(now: Long = System.currentTimeMillis()): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    private fun todayLabel(): String = when (LanguageState.tag) {
        LanguageTag.EN -> "Today"
        LanguageTag.JA -> "今日"
        LanguageTag.ZH -> "今天"
        LanguageTag.ZH_TW -> "今天"
        LanguageTag.KO -> "오늘"
        LanguageTag.ES -> "Hoy"
        LanguageTag.FR -> "Aujourd’hui"
    }

    private fun yesterdayLabel(): String = when (LanguageState.tag) {
        LanguageTag.EN -> "Yesterday"
        LanguageTag.JA -> "昨日"
        LanguageTag.ZH -> "昨天"
        LanguageTag.ZH_TW -> "昨天"
        LanguageTag.KO -> "어제"
        LanguageTag.ES -> "Ayer"
        LanguageTag.FR -> "Hier"
    }

    private fun justNowLabel(): String = when (LanguageState.tag) {
        LanguageTag.EN -> "just now"
        LanguageTag.JA -> "たった今"
        LanguageTag.ZH -> "刚刚"
        LanguageTag.ZH_TW -> "剛剛"
        LanguageTag.KO -> "방금"
        LanguageTag.ES -> "ahora mismo"
        LanguageTag.FR -> "à l’instant"
    }

    private fun minutesAgo(minutes: Long): String = when (LanguageState.tag) {
        LanguageTag.EN -> "$minutes min ago"
        LanguageTag.JA -> "${minutes}分前"
        LanguageTag.ZH -> "$minutes 分钟前"
        LanguageTag.ZH_TW -> "$minutes 分鐘前"
        LanguageTag.KO -> "${minutes}분 전"
        LanguageTag.ES -> "hace $minutes min"
        LanguageTag.FR -> "il y a $minutes min"
    }

    private fun hoursAgo(hours: Long): String = when (LanguageState.tag) {
        LanguageTag.EN -> "$hours hr ago"
        LanguageTag.JA -> "${hours}時間前"
        LanguageTag.ZH -> "$hours 小时前"
        LanguageTag.ZH_TW -> "$hours 小時前"
        LanguageTag.KO -> "${hours}시간 전"
        LanguageTag.ES -> "hace $hours h"
        LanguageTag.FR -> "il y a $hours h"
    }
}
