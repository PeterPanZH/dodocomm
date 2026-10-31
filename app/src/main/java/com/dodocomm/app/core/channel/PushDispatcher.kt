package com.dodocomm.app.core.channel

import com.dodocomm.app.bark.BarkClient
import com.dodocomm.app.core.settings.AppSettings

/** Sends one rendered SMS to every enabled, configured channel. */
class PushDispatcher(
    private val barkClient: BarkClient,
    private val ntfyClient: NtfyClient = NtfyClient(),
    private val telegramClient: TelegramClient = TelegramClient(),
    private val pushoverClient: PushoverClient = PushoverClient(),
    private val emailClient: EmailClient = EmailClient(),
) {
    suspend fun sendTo(
        settings: AppSettings,
        channel: PushChannel,
        title: String,
        body: String,
    ): ChannelSendResult = when (channel.type) {
        ChannelType.BARK -> {
            if (!settings.isConfigured) {
                ChannelSendResult(channel.id, channel.name, channel.type, false, "Bark 服务器或 Device Key 未配置")
            } else {
                val result = barkClient.send(settings, title, body)
                ChannelSendResult(
                    channelId = channel.id,
                    channelName = channel.name,
                    type = channel.type,
                    success = result.success,
                    message = result.message,
                    rawBody = result.rawBody,
                )
            }
        }
        ChannelType.NTFY -> ntfyClient.send(channel, title, body)
        ChannelType.TELEGRAM -> telegramClient.send(channel, title, body)
        ChannelType.PUSHOVER -> pushoverClient.send(channel, title, body)
        ChannelType.EMAIL -> emailClient.send(channel, title, body)
    }

    suspend fun dispatch(settings: AppSettings, title: String, body: String): DispatchResult {
        val channels = settings.activePushChannels()
        if (channels.isEmpty()) {
            return DispatchResult(
                listOf(
                    ChannelSendResult(
                        channelId = "none",
                        channelName = "推送通道",
                        type = ChannelType.BARK,
                        success = false,
                        message = "没有可用的推送通道，请先配置至少一个通道",
                    ),
                ),
            )
        }
        val results = channels.map { channel ->
            sendTo(settings, channel, title, body)
        }
        return DispatchResult(results)
    }
}
