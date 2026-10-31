package com.dodocomm.app.core.forward

import com.dodocomm.app.core.channel.ChannelSendResult

/** Pure result policy for one multi-channel delivery attempt. */
object DeliveryPolicy {
    data class Outcome(
        val sent: Boolean,
        val retry: Boolean,
        val error: String?,
    )

    fun evaluate(results: List<ChannelSendResult>): Outcome {
        if (results.isEmpty()) return Outcome(sent = false, retry = true, error = "没有可用的推送通道")
        val anySuccess = results.any { it.success }
        val failures = results.filterNot { it.success }
        return if (anySuccess) {
            Outcome(
                sent = true,
                retry = false,
                error = failures.takeIf { it.isNotEmpty() }
                    ?.joinToString("；") { "${it.channelName}: ${it.message}" },
            )
        } else {
            Outcome(
                sent = false,
                retry = true,
                error = failures.joinToString("；") { "${it.channelName}: ${it.message}" },
            )
        }
    }
}
