package com.dodocomm.app.core.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Small, dependency-free SQLite store for the local forwarding history. */
class HistoryDatabase(context: Context) : SQLiteOpenHelper(
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
        // History is a convenience cache; rebuilding is acceptable across schema bumps.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORDS")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        const val DATABASE_NAME = "sms2bark.db"
        const val DATABASE_VERSION = 1
        const val TABLE_RECORDS = "records"
    }
}
