package com.dodocomm.app.core.db

import android.content.ContentValues
import android.database.Cursor
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.model.TodayStats

/**
 * Plain SQLite DAO. All methods block; callers wrap them in Dispatchers.IO.
 */
class HistoryDao(private val database: HistoryDatabase) {

    /** @return the new row id, or -1 when the message was already recorded. */
    fun insert(record: ForwardRecord, dedupeKey: String): Long {
        val values = ContentValues().apply {
            put("sender", record.sender)
            put("body", record.body)
            put("received_at", record.receivedAt)
            put("created_at", record.createdAt)
            put("sim_label", record.simLabel)
            put("subscription_id", record.subscriptionId)
            put("status", record.status.name)
            put("attempts", record.attempts)
            put("error", record.error)
            put("title", record.title)
            put("bark_response", record.barkResponse)
            put("dedupe_key", dedupeKey)
        }
        return database.writableDatabase.insertWithOnConflict(
            HistoryDatabase.TABLE_RECORDS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun updateResult(
        id: Long,
        status: ForwardStatus,
        attempts: Int,
        error: String?,
        response: String?,
    ) {
        val values = ContentValues().apply {
            put("status", status.name)
            put("attempts", attempts)
            put("error", error)
            put("bark_response", response)
        }
        database.writableDatabase.update(
            HistoryDatabase.TABLE_RECORDS,
            values,
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun get(id: Long): ForwardRecord? {
        database.readableDatabase.query(
            HistoryDatabase.TABLE_RECORDS,
            COLUMNS,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toRecord() else null
        }
    }

    fun list(limit: Int = DEFAULT_HISTORY_LIMIT): List<ForwardRecord> {
        database.readableDatabase.query(
            HistoryDatabase.TABLE_RECORDS,
            COLUMNS,
            null,
            null,
            null,
            null,
            "created_at DESC",
            limit.toString(),
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toRecord())
            }
        }
    }

    fun listForRetry(limit: Int, maxAttempts: Int): List<ForwardRecord> {
        database.readableDatabase.query(
            HistoryDatabase.TABLE_RECORDS,
            COLUMNS,
            "status IN (?, ?) AND attempts < ?",
            arrayOf(ForwardStatus.PENDING.name, ForwardStatus.FAILED.name, maxAttempts.toString()),
            null,
            null,
            "created_at ASC",
            limit.toString(),
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.toRecord())
            }
        }
    }

    fun todayStats(startOfDayMillis: Long): TodayStats {
        val counts = mutableMapOf<ForwardStatus, Int>()
        database.readableDatabase.rawQuery(
            "SELECT status, COUNT(*) FROM ${HistoryDatabase.TABLE_RECORDS} WHERE received_at >= ? GROUP BY status",
            arrayOf(startOfDayMillis.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val status = ForwardStatus.fromStorage(cursor.getString(0))
                counts[status] = cursor.getInt(1)
            }
        }
        return TodayStats(
            sent = counts[ForwardStatus.SENT] ?: 0,
            failed = counts[ForwardStatus.FAILED] ?: 0,
            filtered = counts[ForwardStatus.FILTERED] ?: 0,
            pending = counts[ForwardStatus.PENDING] ?: 0,
        )
    }

    fun delete(id: Long): Boolean =
        database.writableDatabase.delete(HistoryDatabase.TABLE_RECORDS, "id = ?", arrayOf(id.toString())) > 0

    fun clearAll() {
        database.writableDatabase.delete(HistoryDatabase.TABLE_RECORDS, null, null)
    }

    fun pruneBefore(before: Long) {
        database.writableDatabase.delete(
            HistoryDatabase.TABLE_RECORDS,
            "received_at < ?",
            arrayOf(before.toString()),
        )
    }

    fun prune(keep: Int) {
        if (keep <= 0) return
        database.writableDatabase.execSQL(
            "DELETE FROM ${HistoryDatabase.TABLE_RECORDS} WHERE id NOT IN " +
                "(SELECT id FROM ${HistoryDatabase.TABLE_RECORDS} ORDER BY created_at DESC LIMIT ?)",
            arrayOf<Any>(keep),
        )
    }

    fun count(): Int {
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${HistoryDatabase.TABLE_RECORDS}", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun Cursor.toRecord(): ForwardRecord = ForwardRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        sender = getString(getColumnIndexOrThrow("sender")).orEmpty(),
        body = getString(getColumnIndexOrThrow("body")).orEmpty(),
        receivedAt = getLong(getColumnIndexOrThrow("received_at")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        simLabel = getString(getColumnIndexOrThrow("sim_label")),
        subscriptionId = getInt(getColumnIndexOrThrow("subscription_id")),
        status = ForwardStatus.fromStorage(getString(getColumnIndexOrThrow("status"))),
        attempts = getInt(getColumnIndexOrThrow("attempts")),
        error = getString(getColumnIndexOrThrow("error")),
        title = getString(getColumnIndexOrThrow("title")),
        barkResponse = getString(getColumnIndexOrThrow("bark_response")),
    )

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 300
        const val MAX_HISTORY_ROWS = 500

        private val COLUMNS = arrayOf(
            "id", "sender", "body", "received_at", "created_at", "sim_label",
            "subscription_id", "status", "attempts", "error", "title", "bark_response",
        )
    }
}
