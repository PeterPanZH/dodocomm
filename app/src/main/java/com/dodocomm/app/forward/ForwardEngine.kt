package com.dodocomm.app.forward

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dodocomm.app.AppContainer
import com.dodocomm.app.bark.BarkClient
import com.dodocomm.app.core.model.BarkResult
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.system.DeviceName
import com.dodocomm.app.core.util.Hashing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * The SMS → Bark pipeline: filtering, title rendering, history persistence,
 * immediate delivery and the retry queue.
 *
 * All methods are safe to call from any dispatcher: database and HTTP work are
 * delegated to [HistoryRepository] / [BarkClient], which both run on IO. A
 * per-record in-flight guard plus a retry mutex prevent duplicate pushes when
 * the receiver, a retry worker and a user "resend" race each other.
 */
class ForwardEngine(
    context: Context,
    private val container: AppContainer,
) {
    private val appContext: Context = context.applicationContext

    /** Ids currently being delivered; prevents concurrent duplicate pushes. */
    private val inFlightIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Only one retry pass may scan/send at a time (worker + maintenance overlap). */
    private val retryMutex = Mutex()

    /**
     * Handles one parsed SMS.
     *
     * @return the created history row, or `null` when nothing was recorded
     *   (forwarding disabled, duplicate broadcast, or filtered-and-not-recorded).
     */
    suspend fun handleIncoming(payload: SmsPayload): ForwardRecord? {
        val settings = container.settings.current()
        if (!settings.forwardingEnabled) return null
        if (settings.isPaused()) {
            Log.i(TAG, "临时暂停中，跳过短信 sender=${payload.sender}")
            return null
        }

        val decision = FilterRules.evaluate(settings, payload)
        if (!decision.forward && !settings.recordFilteredMessages) {
            container.serviceController.refreshNotification()
            return null
        }

        val templatePayload = payload.copy(
            deviceName = settings.deviceName.trim().ifBlank { payload.deviceName },
        )
        val title = TitleTemplate.render(settings.titleTemplate, templatePayload)
        val barkBody = TitleTemplate.renderBody(settings.bodyTemplate, templatePayload)
        val baseRecord = ForwardRecord(
            sender = payload.sender.trim(),
            body = payload.body,
            receivedAt = payload.receivedAt,
            createdAt = System.currentTimeMillis(),
            simLabel = payload.simLabel?.trim()?.takeIf { it.isNotEmpty() },
            subscriptionId = payload.subscriptionId,
            status = if (decision.forward) ForwardStatus.PENDING else ForwardStatus.FILTERED,
            attempts = 0,
            error = if (decision.forward) null else decision.reason,
            title = title,
            barkBody = barkBody,
        )
        val dedupeKey = Hashing.sha256("${payload.sender}|${payload.smsTimestamp}|${payload.body}")
        val id = container.history.insert(baseRecord, dedupeKey)
        if (id == -1L) {
            // Duplicate broadcast / re-delivery: the original row already wins.
            Log.i(TAG, "重复短信，跳过 sender=${payload.sender} ts=${payload.smsTimestamp}")
            return null
        }
        val record = baseRecord.copy(id = id)

        if (!decision.forward) {
            Log.i(TAG, "短信被过滤 id=$id reason=${decision.reason}")
            container.serviceController.refreshNotification()
            return record
        }

        if (!settings.hasAnyConfiguredChannel) {
            Log.w(TAG, "已收到短信但没有可用推送通道 id=$id")
            container.history.updateResult(
                id = id,
                status = ForwardStatus.FAILED,
                attempts = 0,
                error = "没有可用的推送通道，请先配置至少一个通道",
            )
            // No retry enqueue: the periodic maintenance pass will pick this row
            // up once the user has configured a channel.
            container.serviceController.refreshNotification()
            return record.copy(
                status = ForwardStatus.FAILED,
                error = "没有可用的推送通道，请先配置至少一个通道",
            )
        }

        val result = try {
            deliver(record, settings, scheduleRetryOnFailure = true)
        } catch (e: CancellationException) {
            // The receiver's 8s guard (or a cancelled service scope) interrupted
            // the HTTP call. Keep the row retryable and make sure WorkManager
            // will pick it up.
            withContext(NonCancellable) {
                markInterrupted(id)
                enqueueRetry(settings)
                container.serviceController.refreshNotification()
            }
            throw e
        }

        Log.i(
            TAG,
            "短信处理完成 id=$id status=${if (result.success) "SENT" else "FAILED"} code=${result.code}",
        )
        return container.history.get(id) ?: record.copy(
            status = if (result.success) ForwardStatus.SENT else ForwardStatus.FAILED,
            attempts = record.attempts + 1,
            error = if (result.success) null else result.message,
            barkResponse = result.rawBody,
        )
    }

    /** Sends a one-off test push with the current settings; never touches history. */
    suspend fun sendTestPush(): BarkResult {
        val settings = container.settings.current()
        val clock = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA).format(LocalTime.now())
        return container.barkClient.send(
            settings = settings,
            title = "嘟嘟通讯 测试推送",
            body = "嘟嘟可报到！测试推送成功 ✅ 时间 $clock",
        )
    }

    /** Sends a sample SMS rendered with the user's title/body templates. */
    suspend fun sendTemplateTestPush(): BarkResult {
        val settings = container.settings.current()
        if (!settings.hasAnyConfiguredChannel) {
            return BarkResult.failure("没有可用的推送通道，请先配置至少一个通道")
        }
        val now = System.currentTimeMillis()
        val payload = SmsPayload(
            sender = "95588",
            body = "【测试】您的验证码是 123456，5 分钟内有效。",
            smsTimestamp = now,
            receivedAt = now,
            simLabel = "SIM 1 · 示例运营商",
            deviceName = settings.deviceName.trim().ifBlank { DeviceName.resolve(appContext) },
            receiverNumber = "13800138000",
        )
        val dispatch = container.pushDispatcher.dispatch(
            settings = settings,
            title = TitleTemplate.render(settings.titleTemplate, payload),
            body = TitleTemplate.renderBody(settings.bodyTemplate, payload),
        )
        return BarkResult(
            success = dispatch.success,
            code = null,
            message = dispatch.message,
            rawBody = dispatch.rawBody,
        )
    }

    /** Re-sends an existing history record (user action on the detail sheet). */
    suspend fun resend(recordId: Long): BarkResult {
        val settings = container.settings.current()
        if (!settings.hasAnyConfiguredChannel) {
            return BarkResult.failure("没有可用的推送通道，请先配置至少一个通道")
        }
        val record = container.history.get(recordId)
            ?: return BarkResult.failure("记录不存在或已被删除")
        return deliver(record, settings, scheduleRetryOnFailure = true)
    }

    /**
     * Delivers a batch of PENDING/FAILED rows.
     *
     * @return `true` when retryable rows remain (the caller should let
     *   WorkManager retry with backoff). Returns `false` while offline after
     *   enqueueing a network-constrained [WorkScheduler.enqueueRetry] instead,
     *   so no send attempts are wasted without a network.
     */
    suspend fun retryPending(): Boolean {
        if (!retryMutex.tryLock()) return false // another retry pass is already running
        try {
            val settings = container.settings.current()
            if (!settings.forwardingEnabled || !settings.hasAnyConfiguredChannel || settings.isPaused()) return false

            val maxAttempts = settings.maxRetryAttempts.coerceIn(1, 10)
            val maxAgeMillis = settings.retryMaxHours.coerceIn(1, 168) * 3_600_000L
            val candidates = container.history.listForRetry(
                limit = RETRY_BATCH_SIZE,
                maxAttempts = maxAttempts,
            ).filter { System.currentTimeMillis() - it.createdAt <= maxAgeMillis }
            if (candidates.isEmpty()) return false
            if (!hasNetworkConnection()) {
                enqueueRetry(settings)
                return false
            }

            for (candidate in candidates) {
                val record = container.history.get(candidate.id) ?: continue
                if (record.status != ForwardStatus.PENDING && record.status != ForwardStatus.FAILED) continue
                if (record.attempts >= maxAttempts) continue
                deliver(record, settings, scheduleRetryOnFailure = false)
            }

            return container.history
                .listForRetry(limit = 1, maxAttempts = maxAttempts)
                .isNotEmpty()
        } finally {
            retryMutex.unlock()
        }
    }

    // ------------------------------------------------------------------ internals

    /**
     * Performs exactly one Bark attempt for [record] and persists the outcome.
     * The per-record in-flight guard makes concurrent duplicate sends impossible.
     */
    private suspend fun deliver(
        record: ForwardRecord,
        settings: AppSettings,
        scheduleRetryOnFailure: Boolean,
    ): BarkResult {
        if (!inFlightIds.add(record.id)) {
            return BarkResult.failure("该短信正在发送中，请稍后")
        }
        try {
            val title = record.title?.takeIf { it.isNotBlank() }
                ?: TitleTemplate.render(settings.titleTemplate, record.toPayload())
            val body = record.barkBody?.takeIf { it.isNotBlank() } ?: record.body
            val dispatch = container.pushDispatcher.dispatch(
                settings = settings,
                title = title,
                body = body,
                routing = record.toRoutingContext(),
            )
            val result = BarkResult(
                success = dispatch.success,
                code = null,
                message = dispatch.message,
                rawBody = dispatch.rawBody,
            )
            val attempts = record.attempts + 1
            if (result.success) {
                container.history.updateResult(
                    id = record.id,
                    status = ForwardStatus.SENT,
                    attempts = attempts,
                    error = null,
                    response = result.rawBody,
                )
            } else {
                container.history.updateResult(
                    id = record.id,
                    status = ForwardStatus.FAILED,
                    attempts = attempts,
                    error = result.message,
                    response = result.rawBody,
                )
                if (scheduleRetryOnFailure) enqueueRetry(settings)
            }
            return result
        } finally {
            inFlightIds.remove(record.id)
            container.serviceController.refreshNotification()
            com.dodocomm.app.widget.WidgetUpdater.updateAll(appContext)
        }
    }

    /** Called under [NonCancellable] when an in-flight delivery was cancelled. */
    private suspend fun markInterrupted(recordId: Long) {
        val current = container.history.get(recordId) ?: return
        if (current.status != ForwardStatus.PENDING) return
        container.history.updateResult(
            id = recordId,
            status = ForwardStatus.PENDING,
            attempts = current.attempts,
            error = INTERRUPTED_ERROR,
        )
    }

    private fun enqueueRetry(settings: AppSettings) {
        container.workScheduler.enqueueRetry(
            backoffSeconds = settings.retryBackoffSeconds.toLong(),
            onlyWifi = settings.retryOnlyWifi,
        )
    }

    private fun ForwardRecord.toRoutingContext(): com.dodocomm.app.core.routing.RoutingContext {
        val zoned = java.time.Instant.ofEpochMilli(receivedAt)
            .atZone(java.time.ZoneId.systemDefault())
        return com.dodocomm.app.core.routing.RoutingContext(
            sender = sender,
            body = body,
            simLabel = simLabel,
            nowMinutes = zoned.hour * 60 + zoned.minute,
        )
    }

    private fun ForwardRecord.toPayload(): SmsPayload = SmsPayload(
        sender = sender,
        body = body,
        smsTimestamp = receivedAt,
        receivedAt = receivedAt,
        subscriptionId = subscriptionId,
        simLabel = simLabel,
    )

    /** Cheap network probe; used only to avoid burning retry attempts while offline. */
    private fun hasNetworkConnection(): Boolean = try {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        Log.w(TAG, "读取网络状态失败，按有网处理", e)
        true
    }

    companion object {
        /** A row is never attempted again after this many Bark calls. */
        const val DEFAULT_MAX_ATTEMPTS: Int = 5

        /** Rows handled per retry pass (WorkManager batch). */
        const val RETRY_BATCH_SIZE: Int = 10

        internal const val INTERRUPTED_ERROR = "发送被中断，已加入自动重试队列"

        private const val TAG = "SMS2BARK"
    }
}
