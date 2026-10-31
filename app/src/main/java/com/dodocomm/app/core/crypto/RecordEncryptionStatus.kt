package com.dodocomm.app.core.crypto

import android.content.Context

enum class RecordEncryptionState { PENDING, DONE, FAILED }

/** One-time migration state for encrypted local history. */
object RecordEncryptionStatus {
    private const val PREFS = "dodocomm_record_encryption"
    private const val KEY_STATE = "state"

    fun state(context: Context): RecordEncryptionState {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null) ?: return RecordEncryptionState.PENDING
        return runCatching { RecordEncryptionState.valueOf(raw) }
            .getOrDefault(RecordEncryptionState.PENDING)
    }

    fun mark(context: Context, state: RecordEncryptionState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state.name)
            .commit()
    }
}
