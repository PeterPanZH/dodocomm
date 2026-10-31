package com.dodocomm.app.core.channel

import com.dodocomm.app.bark.BarkClient
import com.dodocomm.app.core.routing.RoutingContext
import com.dodocomm.app.core.routing.RoutingEngine
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.dodocomm.app.core.settings.PushLevel

/** Sends one rendered SMS to every enabled, configured channel. */
class PushDispatcher(
    private val barkClient: BarkClient,
    private val ntfyClient: NtfyClient = NtfyClient(),
    private val telegramClient: TelegramClient = TelegramClient(),
    private val pushoverClient: PushoverClient = PushoverClient(),
    private val emailClient: EmailClient = EmailClient(),
    private val senderOverride: (suspend (PushChannel, String, String) -> ChannelSendResult)? = null,
) {
    suspend fun sendTo(
        settings: AppSettings,
        channel: PushChannel,
        title: String,
        body: String,
    ): ChannelSendResult = senderOverride?.invoke(channel, title, body) ?: when (channel.type) {
        ChannelType.BARK -> {
            if (!channel.isConfigured()) {
                ChannelSendResult(channel.id, channel.name, channel.type, false, "Bark 服务器或 Device Key 未配置")
            } else {
                val result = barkClient.send(settings.forBarkChannel(channel), title, body)
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

    suspend fun dispatch(
        settings: AppSettings,
        title: String,
        body: String,
        routing: RoutingContext? = null,
    ): DispatchResult {
        val active = settings.activePushChannels()
        val channels = if (routing == null) {
            active
        } else {
            RoutingEngine.targets(routing, settings.routingRules, active, settings.defaultChannelIds)
        }
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
        val results = coroutineScope {
            channels.map { channel ->
                async { sendTo(settings, channel, title, body) }
            }.map { it.await() }
        }
        return DispatchResult(results)
    }
}

private fun AppSettings.forBarkChannel(channel: PushChannel): AppSettings = copy(
    serverUrl = channel.serverUrl,
    deviceKey = channel.deviceKey,
    pushGroup = channel.pushGroup,
    pushSound = channel.pushSound,
    pushLevel = PushLevel.fromStorage(channel.pushLevel),
    pushIcon = channel.pushIcon,
    pushUrl = channel.pushUrl,
    isArchive = channel.isArchive,
    badgeEnabled = channel.badgeEnabled,
    copyMode = CopyMode.fromName(channel.copyMode),
)
