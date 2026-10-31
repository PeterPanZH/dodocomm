package com.dodocomm.app.core.export

import android.content.Context
import android.net.Uri
import com.dodocomm.app.core.model.ForwardRecord
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
}

enum class ExportRange(val label: String) {
    ALL("全部"),
    TODAY("今天"),
    LAST_7_DAYS("近 7 天"),
    LAST_30_DAYS("近 30 天");

    fun startMillis(now: Long = System.currentTimeMillis()): Long = when (this) {
        ALL -> 0L
        TODAY -> startOfToday(now)
        LAST_7_DAYS -> startOfToday(now) - 6L * 86_400_000L
        LAST_30_DAYS -> startOfToday(now) - 29L * 86_400_000L
    }

    private fun startOfToday(now: Long): Long =
        java.time.Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}

/**
 * Exports local history through the Storage Access Framework. The file is
 * written to a user-picked [Uri], so the app needs no storage permission.
 */
class RecordExporter(private val context: Context) {

    suspend fun export(
        uri: Uri,
        format: ExportFormat,
        range: ExportRange,
        masked: Boolean,
        records: List<ForwardRecord>,
    ): Int = withContext(Dispatchers.IO) {
        val selected = filter(records, range)
        val content = if (format == ExportFormat.CSV) toCsv(selected, masked) else toJson(selected, masked)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, StandardCharsets.UTF_8).use { it.write(content) }
        } ?: error("无法打开导出文件")
        selected.size
    }

    companion object {
        private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun filter(records: List<ForwardRecord>, range: ExportRange): List<ForwardRecord> {
            val from = range.startMillis()
            return if (from == 0L) records else records.filter { it.receivedAt >= from }
        }

        /** CSV with UTF-8 BOM and spreadsheet-formula protection. */
        fun toCsv(records: List<ForwardRecord>, masked: Boolean): String {
            val header = "received_at,sender,sim,body,status,attempts,error,title"
            val rows = records.map { record ->
                listOf(
                    iso(record.receivedAt),
                    safeCell(if (masked) maskSender(record.sender) else record.sender),
                    safeCell(record.simLabel.orEmpty()),
                    safeCell(if (masked) maskBody(record.body) else record.body),
                    record.status.name,
                    record.attempts.toString(),
                    safeCell(if (masked) maskedError(record.error) else record.error.orEmpty()),
                    safeCell(if (masked) maskedTitle(record.title) else record.title.orEmpty()),
                ).joinToString(",")
            }
            return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n")
        }

        fun toJson(records: List<ForwardRecord>, masked: Boolean): String {
            val array = JSONArray()
            records.forEach { record ->
                array.put(
                    JSONObject().apply {
                        put("receivedAt", iso(record.receivedAt))
                        put("sender", if (masked) maskSender(record.sender) else record.sender)
                        put("simLabel", record.simLabel)
                        put("body", if (masked) maskBody(record.body) else record.body)
                        put("status", record.status.name)
                        put("attempts", record.attempts)
                        put("error", if (masked) maskedError(record.error) else record.error)
                        put("title", if (masked) maskedTitle(record.title) else record.title)
                    },
                )
            }
            return JSONObject().put("exportedAt", iso(System.currentTimeMillis()))
                .put("masked", masked)
                .put("records", array)
                .toString(2)
        }

        /** Masks a sender while keeping enough context to identify the source. */
        fun maskSender(sender: String): String {
            val trimmed = sender.trim()
            if (trimmed.length <= 4) return "*".repeat(trimmed.length)
            val visiblePrefix = trimmed.take(3)
            val visibleSuffix = trimmed.takeLast(2)
            return "$visiblePrefix${"*".repeat((trimmed.length - 5).coerceAtLeast(1))}$visibleSuffix"
        }

        /**
         * Masks phone numbers and long card-like numbers in the body, but keeps
         * verification codes (they are what the user usually needs).
         */
        fun maskBody(body: String): String {
            val phone = Regex("(?<!\\d)(1[3-9]\\d{9})(?!\\d)")
            val card = Regex("(?<!\\d)(\\d{16,19})(?!\\d)")
            return body
                .replace(phone) { maskDigits(it.value) }
                .replace(card) { maskDigits(it.value) }
        }

        private fun maskDigits(value: String): String =
            if (value.length <= 7) value else value.take(3) + "*".repeat(value.length - 7) + value.takeLast(4)

        private fun maskedTitle(title: String?): String =
            title?.let { maskBody(it).replace(Regex("(?<!\\d)\\d{5,}(?!\\d)")) { match -> maskDigits(match.value) } }.orEmpty()

        private fun maskedError(error: String?): String =
            if (error.isNullOrBlank()) "" else "（原因已隐藏）"

        private fun safeCell(value: String): String {
            val protected = if (value.startsWith("=") || value.startsWith("+") ||
                value.startsWith("-") || value.startsWith("@")
            ) {
                "'$value"
            } else {
                value
            }
            return "\"" + protected.replace("\"", "\"\"") + "\""
        }

        private fun iso(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(ISO)
    }
}
