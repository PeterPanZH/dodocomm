package com.dodocomm.app.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.dodocomm.app.core.crypto.RecordCipher

/** Small, dependency-free SQLite store for the local forwarding history. */
class HistoryDatabase(
    context: Context,
    internal val cipher: RecordCipher = RecordCipher(context),
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECORDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                sim_label TEXT,
                subscription_id INTEGER NOT NULL DEFAULT -1,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                error TEXT,
                title TEXT,
                bark_body TEXT,
                bark_response TEXT,
                dedupe_key TEXT NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_received ON $TABLE_RECORDS (received_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_status ON $TABLE_RECORDS (status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_created ON $TABLE_RECORDS (created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Preserve existing history when adding the rendered Bark body column.
            db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN bark_body TEXT")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        runCatching { encryptLegacyRows(db) }
            .onFailure { Log.w("DODOCOMM_CRYPTO", "本地记录加密迁移失败: ${it.javaClass.simpleName}") }
    }

    /** Idempotently encrypts rows written before local encryption existed. */
    private fun encryptLegacyRows(db: SQLiteDatabase) {
        val fields = listOf("sender", "body", "title", "bark_body", "error", "bark_response")
        var migrated = 0
        db.beginTransaction()
        try {
            db.query(
                TABLE_RECORDS,
                arrayOf("id") + fields,
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val values = ContentValues()
                    var changed = false
                    fields.forEachIndexed { index, field ->
                        val value = cursor.getString(index + 1)
                        if (!value.isNullOrEmpty() && !cipher.isEncrypted(value)) {
                            values.put(field, cipher.encrypt(value))
                            changed = true
                        }
                    }
                    if (changed) {
                        db.update(TABLE_RECORDS, values, "id = ?", arrayOf(id.toString()))
                        migrated++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (migrated > 0) Log.i("DODOCOMM_CRYPTO", "已加密 $migrated 条本地记录")
    }

    companion object {
        const val DATABASE_NAME = "sms2bark.db"
        const val DATABASE_VERSION = 2
        const val TABLE_RECORDS = "records"
    }
}
