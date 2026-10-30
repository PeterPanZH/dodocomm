package com.dodocomm.app.sms

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.dodocomm.app.core.model.INVALID_SUBSCRIPTION_ID
import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.system.AppPermissions

/**
 * Turns an `SMS_RECEIVED` broadcast into a framework-free [SmsPayload].
 *
 * Multipart SMS: Android delivers all PDUs of one message in the same broadcast
 * (one broadcast per logical SMS), so bodies are concatenated in PDU order,
 * while sender/subscription/timestamp are taken from the first part.
 *
 * SIM label rendering is best-effort and requires READ_PHONE_STATE; without it
 * the payload simply keeps a null label.
 */
object SmsParser {

    private const val TAG = "SMS2BARK"

    /** Extras used by AOSP/OEM telephony stacks to report the receiving subscription. */
    private val SUBSCRIPTION_EXTRA_KEYS = listOf("subscription", "subscription_id", "sub_id")

    fun parse(context: Context, intent: Intent): SmsPayload? {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return null

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
                ?.filterNotNull()
                .orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "解析短信广播失败: ${t.javaClass.simpleName}", t)
            return null
        }
        if (messages.isEmpty()) {
            Log.w(TAG, "短信广播中没有 PDU")
            return null
        }

        val first = messages.first()
        val sender = (first.originatingAddress ?: first.displayOriginatingAddress)
            ?.trim()
            .orEmpty()
        val body = messages.joinToString(separator = "") { it.bodyOrEmpty() }
        val subscriptionId = extractSubscriptionId(intent)
        return SmsPayload(
            sender = sender,
            body = body,
            smsTimestamp = first.timestampMillis,
            receivedAt = System.currentTimeMillis(),
            subscriptionId = subscriptionId,
            simLabel = resolveSimLabel(context, subscriptionId),
            deviceName = resolveDeviceName(context),
            receiverNumber = resolveReceiverNumber(context, subscriptionId),
        )
    }

    private fun SmsMessage.bodyOrEmpty(): String =
        (messageBody ?: displayMessageBody).orEmpty()

    @Suppress("DEPRECATION") // Bundle.get() is the only type-agnostic accessor for OEM extras.
    private fun extractSubscriptionId(intent: Intent): Int {
        val extras = intent.extras ?: return INVALID_SUBSCRIPTION_ID
        for (key in SUBSCRIPTION_EXTRA_KEYS) {
            val value = extras.get(key) ?: continue
            val parsed = when (value) {
                is Int -> value
                is Long -> value.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
            // -1 means "no subscription"; a later key may still carry a valid id.
            if (parsed != null && parsed != INVALID_SUBSCRIPTION_ID) return parsed
        }
        return INVALID_SUBSCRIPTION_ID
    }

    /** Friendly phone name: user-set device name, else Build.MODEL / Build.DEVICE. */
    private fun resolveDeviceName(context: Context): String {
        val globalName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()
        if (!globalName.isNullOrEmpty()) return globalName
        val model = Build.MODEL.replace('_', ' ').trim()
        return model.ifEmpty { Build.DEVICE.trim() }
    }

    /**
     * The receiving SIM's own phone number. Android frequently returns null
     * (carrier/SIM dependent), so callers must treat `{number}` as optional.
     */
    private fun resolveReceiverNumber(context: Context, subscriptionId: Int): String? {
        if (subscriptionId == INVALID_SUBSCRIPTION_ID) return null
        if (!AppPermissions.hasReadPhoneState(context)) return null
        return try {
            val telephony = context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(subscriptionId)
            val raw = telephony?.line1Number?.trim().orEmpty()
            raw.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        } catch (e: SecurityException) {
            Log.w(TAG, "读取收信号码被拒绝（READ_PHONE_STATE）")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "读取收信号码失败: ${t.javaClass.simpleName}")
            null
        }
    }

    /**
     * Resolves e.g. `SIM 2 · 中国移动`. Returns null when the subscription is
     * unknown, permission is missing or the platform does not expose SIM data.
     */
    private fun resolveSimLabel(context: Context, subscriptionId: Int): String? {
        if (subscriptionId == INVALID_SUBSCRIPTION_ID) return null
        if (!AppPermissions.hasReadPhoneState(context)) return null
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return null
            val info = manager.getActiveSubscriptionInfo(subscriptionId) ?: return null
            val slotNumber = info.simSlotIndex + 1
            val name = info.displayName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: info.carrierName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            when {
                name == null -> if (info.simSlotIndex >= 0) "SIM $slotNumber" else null
                info.simSlotIndex >= 0 -> "SIM $slotNumber · $name"
                else -> name
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "读取 SIM 信息被拒绝（READ_PHONE_STATE）")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "读取 SIM 信息失败: ${t.javaClass.simpleName}")
            null
        }
    }
}
