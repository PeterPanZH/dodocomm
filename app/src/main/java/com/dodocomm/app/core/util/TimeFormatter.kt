package com.dodocomm.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chinese-friendly time formatting shared by the UI and notifications. */
object TimeFormatter {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val yearMonthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val fullFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

    fun startOfToday(now: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatClock(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(timeFormatter)

    fun formatFull(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(fullFormatter)

    /** "14:32" today, "昨天 14:32", "3月8日 14:32" this year, otherwise full date. */
    fun formatRecordTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val target = Instant.ofEpochMilli(timestamp).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val targetDate = target.toLocalDate()
        return when {
            targetDate == today -> target.format(timeFormatter)
            targetDate == today.minusDays(1) -> "昨天 ${target.format(timeFormatter)}"
            targetDate.year == today.year -> "${target.format(monthDayFormatter)} ${target.format(timeFormatter)}"
            else -> target.format(fullFormatter)
        }
    }

    /** Date header for grouped history: "今天", "昨天", "3月8日", "2025年12月1日". */
    fun formatDateHeader(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> date.format(monthDayFormatter)
            else -> date.format(yearMonthDayFormatter)
        }
    }

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
            else -> formatRecordTime(timestamp, now)
        }
    }

    fun isSameDay(a: Long, b: Long): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    fun todayDate(now: Long = System.currentTimeMillis()): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}
