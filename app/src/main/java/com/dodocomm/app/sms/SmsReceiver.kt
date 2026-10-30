package com.dodocomm.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.dodocomm.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Manifest receiver for `android.provider.Telephony.SMS_RECEIVED` (protected by
 * the system `BROADCAST_SMS` permission). Being a manifest receiver it still
 * works when the app process was killed — that is the core of the keep-alive
 * design, no accessibility/audio hacks required.
 *
 * The broadcast is handled with [goAsync] on an IO coroutine so the main thread
 * never blocks. Total work is bounded to [RECEIVE_TIMEOUT_MS]; if the network
 * is too slow the row stays PENDING and the retry queue finishes the job. The
 * broadcast is always finished in a `finally` block.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVE_TIMEOUT_MS) {
                    val payload = SmsParser.parse(appContext, intent) ?: return@withTimeout
                    Log.i(
                        TAG,
                        "收到短信 sender=${payload.sender} subId=${payload.subscriptionId} " +
                            "sim=${payload.simLabel} length=${payload.body.length}",
                    )
                    val record = appContext.appContainer.forwardEngine.handleIncoming(payload)
                    if (record == null) {
                        Log.i(TAG, "短信未转发（重复/关闭/过滤且不记录）")
                    } else {
                        Log.i(TAG, "短信已入库 id=${record.id} status=${record.status}")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "短信转发超过 ${RECEIVE_TIMEOUT_MS}ms，已交给重试队列")
            } catch (t: Throwable) {
                Log.e(TAG, "短信转发异常: ${t.javaClass.simpleName}", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        private const val TAG = "SMS2BARK"

        /** Broadcasts get ~10s; leave headroom so the pending result always finishes. */
        private const val RECEIVE_TIMEOUT_MS = 8_000L
    }
}
