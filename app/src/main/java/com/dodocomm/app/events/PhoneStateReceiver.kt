package com.dodocomm.app.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.dodocomm.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Best-effort call state bridge. Number availability depends on READ_CALL_LOG /
 * OEM dialer restrictions; call state itself only needs READ_PHONE_STATE.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                ringing = true
                answered = false
                lastNumber = number
                lastRingingAt = System.currentTimeMillis()
                if (!context.appContainer.settings.current().phoneForwardEnabled) return
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runCatching {
                        appContext.appContainer.eventForwarder.forwardIncomingCall(
                            number = number,
                            missed = false,
                            timestamp = lastRingingAt,
                        )
                    }.onFailure { Log.w(TAG, "来电事件转发失败: ${it.javaClass.simpleName}") }
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (ringing) answered = true
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasRinging = ringing
                val wasAnswered = answered
                val missedNumber = lastNumber
                val missedAt = lastRingingAt
                ringing = false
                answered = false
                lastNumber = null
                if (!wasRinging || wasAnswered || missedAt <= 0L) return
                val settings = context.appContainer.settings.current()
                if (!settings.phoneForwardEnabled || !settings.phoneForwardMissed) return
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runCatching {
                        appContext.appContainer.eventForwarder.forwardIncomingCall(
                            number = missedNumber,
                            missed = true,
                            timestamp = missedAt,
                        )
                    }.onFailure { Log.w(TAG, "未接来电转发失败: ${it.javaClass.simpleName}") }
                }
            }
        }
    }

    private companion object {
        const val TAG = "SMS2BARK"
        @Volatile var ringing = false
        @Volatile var answered = false
        @Volatile var lastNumber: String? = null
        @Volatile var lastRingingAt: Long = 0L
    }
}
