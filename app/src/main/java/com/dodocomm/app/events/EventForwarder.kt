package com.dodocomm.app.events

import android.content.Context
import android.util.Log
import com.dodocomm.app.AppContainer
import com.dodocomm.app.R
import com.dodocomm.app.core.crypto.HmacDedupe
import com.dodocomm.app.core.forward.DeliveryPolicy
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardSource
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Forwards non-SMS events (incoming/missed calls and app notifications) through
 * the same configured push channels and local history as SMS. Kept separate
 * from ForwardEngine so SMS filtering/template semantics stay unchanged.
 */
class EventForwarder(
    context: Context,
    private val container: AppContainer,
) {
    private val appContext = context.applicationContext
    private val hmac = HmacDedupe(appContext)

    suspend fun forwardIncomingCall(
        number: String?,
        missed: Boolean,
        timestamp: Long = System.currentTimeMillis(),
    ): ForwardRecord? {
        val settings = container.settings.current()
        if (!settings.forwardingEnabled || settings.isPaused()) return null
        if (!settings.phoneForwardEnabled) return null
        if (missed && !settings.phoneForwardMissed) return null
        if (!missed && !settings.phoneForwardIncoming) return null
        val safeNumber = number?.trim().orEmpty()
        val displayNumber = safeNumber.ifBlank { appContext.getString(R.string.event_unknown_number) }
        val title = appContext.getString(
            if (missed) R.string.event_call_missed_title else R.string.event_call_incoming_title,
        )
        val body = appContext.getString(
            R.string.event_call_body,
            displayNumber,
            TimeFormatter.formatFull(timestamp),
        )
        return deliver(
            source = ForwardSource.CALL,
            sender = displayNumber,
            title = title,
            body = body,
            dedupe = "call|${if (missed) "missed" else "incoming"}|$safeNumber|$timestamp",
            timestamp = timestamp,
        )
    }

    suspend fun forwardAppNotification(
        appName: String,
        packageName: String,
        notificationTitle: String?,
        notificationText: String?,
        notificationKey: String?,
        timestamp: Long = System.currentTimeMillis(),
    ): ForwardRecord? {
        val settings = container.settings.current()
        if (!settings.forwardingEnabled || settings.isPaused()) return null
        if (!settings.notificationForwardEnabled) return null
        if (!settings.notificationAllApps && packageName !in settings.notificationSelectedPackages) return null
        val safeApp = appName.trim().ifBlank { packageName }
        val title = appContext.getString(R.string.event_notification_title, safeApp)
        val content = listOfNotNull(
            notificationTitle?.trim()?.takeIf { it.isNotEmpty() },
            notificationText?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString("\n").ifBlank { appContext.getString(R.string.event_notification_empty) }
        return deliver(
            source = ForwardSource.APP_NOTIFICATION,
            sender = safeApp,
            title = title,
            body = content,
            dedupe = "notif|$packageName|${notificationKey.orEmpty()}|$content",
            timestamp = timestamp,
        )
    }

    private suspend fun deliver(
        source: ForwardSource,
        sender: String,
        title: String,
        body: String,
        dedupe: String,
        timestamp: Long,
    ): ForwardRecord? {
        val settings = container.settings.current()
        val record = ForwardRecord(
            sender = sender,
            body = body,
            receivedAt = timestamp,
            createdAt = System.currentTimeMillis(),
            status = ForwardStatus.PENDING,
            title = title,
            source = source,
        )
        val id = container.history.insert(record, hmac.token(dedupe))
        if (id == -1L) return null
        if (!settings.hasAnyConfiguredChannel) {
            container.history.updateResult(
                id = id,
                status = ForwardStatus.FAILED,
                attempts = 0,
                error = appContext.getString(R.string.event_no_channel),
            )
            container.serviceController.refreshNotification()
            return record.copy(id = id, status = ForwardStatus.FAILED)
        }
        val dispatch = try {
            withContext(Dispatchers.IO) {
                container.pushDispatcher.dispatch(settings, title, body)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "事件推送失败: ${t.javaClass.simpleName}")
            container.history.updateResult(
                id = id,
                status = ForwardStatus.FAILED,
                attempts = 1,
                error = t.message ?: t.javaClass.simpleName,
            )
            return record.copy(id = id, status = ForwardStatus.FAILED)
        }
        val outcome = DeliveryPolicy.evaluate(dispatch.results)
        container.history.updateResult(
            id = id,
            status = if (outcome.sent) ForwardStatus.SENT else ForwardStatus.FAILED,
            attempts = 1,
            error = outcome.error,
            response = dispatch.results.firstOrNull()?.rawBody,
        )
        if (dispatch.results.isNotEmpty()) {
            container.history.updateTargetChannels(
                id,
                dispatch.results.joinToString("、") { it.channelName }.takeIf { it.isNotBlank() },
            )
        }
        container.serviceController.refreshNotification()
        return record.copy(
            id = id,
            status = if (outcome.sent) ForwardStatus.SENT else ForwardStatus.FAILED,
            error = outcome.error,
        )
    }

    private companion object {
        const val TAG = "SMS2BARK"
    }
}
