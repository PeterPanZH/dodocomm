package com.dodocomm.app.core.model

/** Subscription id used when the platform does not report one. */
const val INVALID_SUBSCRIPTION_ID: Int = -1

/**
 * A received SMS that is a candidate for forwarding. Immutable and free of
 * Android framework types so the forwarding rules stay unit-testable.
 */
data class SmsPayload(
    val sender: String,
    val body: String,
    val smsTimestamp: Long,
    val receivedAt: Long,
    val subscriptionId: Int = INVALID_SUBSCRIPTION_ID,
    val simLabel: String? = null,
    /** Friendly device name, e.g. the user-set phone name or Build.MODEL. */
    val deviceName: String? = null,
    /** The receiving SIM's phone number, when the platform/permission exposes it. */
    val receiverNumber: String? = null,
)

enum class ForwardStatus {
    /** Accepted, waiting for the network / retry queue. */
    PENDING,

    /** Bark server acknowledged the push. */
    SENT,

    /** Last attempt failed; may still be retried. */
    FAILED,

    /** Deliberately not forwarded because a forwarding rule matched. */
    FILTERED,

    /** Deliberately ignored (e.g. forwarding disabled when it arrived). */
    SKIPPED;

    companion object {
        fun fromStorage(value: String?): ForwardStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

/** One row in the local forwarding history. */
data class ForwardRecord(
    val id: Long = 0L,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val createdAt: Long,
    val simLabel: String? = null,
    val subscriptionId: Int = INVALID_SUBSCRIPTION_ID,
    val status: ForwardStatus = ForwardStatus.PENDING,
    val attempts: Int = 0,
    val error: String? = null,
    val title: String? = null,
    /** Rendered Bark body, stored so retries keep the template metadata. */
    val barkBody: String? = null,
    val barkResponse: String? = null,
)

/** Aggregated counters for the dashboard. */
data class TodayStats(
    val sent: Int = 0,
    val failed: Int = 0,
    val filtered: Int = 0,
    val pending: Int = 0,
) {
    val total: Int get() = sent + failed + filtered + pending
}

/** Result of one Bark HTTP push. */
data class BarkResult(
    val success: Boolean,
    val code: Int?,
    val message: String,
    val rawBody: String? = null,
) {
    companion object {
        fun ok(code: Int = 200, raw: String? = null): BarkResult =
            BarkResult(success = true, code = code, message = "推送成功", rawBody = raw)

        fun failure(message: String, code: Int? = null, raw: String? = null): BarkResult =
            BarkResult(success = false, code = code, message = message, rawBody = raw)
    }
}

/** Outcome of applying the user's forwarding rules to one SMS. */
data class FilterDecision(val forward: Boolean, val reason: String? = null) {
    companion object {
        val Pass: FilterDecision = FilterDecision(true)

        fun filtered(reason: String): FilterDecision = FilterDecision(false, reason)
    }
}
