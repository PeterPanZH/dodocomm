package com.dodocomm.app.core.db

import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.model.TodayStats
import com.dodocomm.app.core.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Observable facade over [HistoryDao]. The forwarding pipeline writes through
 * this repository so the UI updates reactively; the UI reads [records]/[stats].
 */
class HistoryRepository(
    private val dao: HistoryDao,
    private val scope: CoroutineScope,
) {
    private val _records = MutableStateFlow<List<ForwardRecord>>(emptyList())
    val records: StateFlow<List<ForwardRecord>> = _records.asStateFlow()

    private val _stats = MutableStateFlow(TodayStats())
    val stats: StateFlow<TodayStats> = _stats.asStateFlow()

    @Volatile
    private var snapshot: List<ForwardRecord> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            val records = withContext(Dispatchers.IO) { dao.list() }
            val stats = withContext(Dispatchers.IO) {
                dao.todayStats(TimeFormatter.startOfToday())
            }
            snapshot = records
            _records.value = records
            _stats.value = stats
        }
    }

    /** @return inserted id, or -1 if [dedupeKey] was already present. */
    suspend fun insert(record: ForwardRecord, dedupeKey: String): Long {
        val id = withContext(Dispatchers.IO) { dao.insert(record, dedupeKey) }
        if (id != -1L) refresh()
        return id
    }

    suspend fun updateResult(
        id: Long,
        status: ForwardStatus,
        attempts: Int,
        error: String? = null,
        response: String? = null,
    ) {
        withContext(Dispatchers.IO) { dao.updateResult(id, status, attempts, error, response) }
        refresh()
    }

    suspend fun get(id: Long): ForwardRecord? = withContext(Dispatchers.IO) { dao.get(id) }

    suspend fun listForRetry(limit: Int = 10, maxAttempts: Int = 5): List<ForwardRecord> =
        withContext(Dispatchers.IO) { dao.listForRetry(limit, maxAttempts) }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) { dao.delete(id) }
        refresh()
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) { dao.clearAll() }
        refresh()
    }

    suspend fun prune(keep: Int = HistoryDao.MAX_HISTORY_ROWS) {
        withContext(Dispatchers.IO) { dao.prune(keep) }
    }

    suspend fun pruneBefore(before: Long) {
        withContext(Dispatchers.IO) { dao.pruneBefore(before) }
    }

    /** Latest known record (may be one refresh behind). */
    fun lastRecord(): ForwardRecord? = snapshot.firstOrNull()
}
