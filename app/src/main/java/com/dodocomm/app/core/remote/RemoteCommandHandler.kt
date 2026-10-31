package com.dodocomm.app.core.remote

import android.util.Log
import com.dodocomm.app.AppContainer
import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.widget.WidgetUpdater

/**
 * Handles opt-in signed SMS commands before the normal forwarding pipeline.
 * Returns true when the message was a remote-control message and must not be
 * forwarded as ordinary SMS.
 */
class RemoteCommandHandler(private val container: AppContainer) {

    suspend fun handle(payload: SmsPayload): Boolean {
        val settings = container.settings.current()
        val isCommand = payload.body.trim().startsWith(COMMAND_PREFIX, ignoreCase = true)
        if (!settings.remoteEnabled || settings.remoteSecret.isBlank() || settings.remoteTrustedNumber.isBlank()) {
            return false
        }
        if (RemoteCommandParser.normalizeNumber(payload.sender) !=
            RemoteCommandParser.normalizeNumber(settings.remoteTrustedNumber)
        ) {
            return false
        }
        if (!isCommand) return false

        val command = RemoteCommandParser.parse(payload.body) ?: return true
        if (!RemoteCommandParser.nonceIsFresh(command.nonce)) return true
        if (command.nonce <= settings.remoteLastNonce) return true
        if (!RemoteCommandParser.verify(settings.remoteSecret, command)) return true

        container.settings.update { it.copy(remoteLastNonce = command.nonce) }
        val result = when (command.action) {
            RemoteAction.STATUS -> statusText(settings)
            RemoteAction.PAUSE -> {
                container.serviceController.pauseFor(command.minutes)
                WidgetUpdater.updateAll(container.context())
                "已暂停转发 ${command.minutes} 分钟"
            }
            RemoteAction.RESUME -> {
                container.serviceController.resumeNow()
                WidgetUpdater.updateAll(container.context())
                "已恢复转发"
            }
        }
        // Replies are best-effort and sent to configured channels. Never reply
        // on invalid commands (prevents SMS flooding).
        runCatching {
            val channels = settings.activePushChannels()
            if (channels.isNotEmpty()) {
                container.pushDispatcher.dispatch(
                    settings = settings,
                    title = "DodoComm 远程状态",
                    body = result,
                )
            }
        }.onFailure { Log.w(TAG, "远程指令回复失败: ${it.javaClass.simpleName}") }
        return true
    }

    private fun statusText(settings: com.dodocomm.app.core.settings.AppSettings): String {
        val paused = settings.isPaused()
        val pausedPart = if (paused) {
            val minutes = ((settings.pauseUntil - System.currentTimeMillis()) / 60_000L).toInt() + 1
            "，暂停剩余约 ${minutes.coerceAtLeast(1)} 分钟"
        } else {
            ""
        }
        val channels = settings.activePushChannels()
        val pending = container.history.stats.value.pending
        return buildString {
            append("转发：").append(if (settings.forwardingEnabled) "开启" else "关闭")
            append(pausedPart)
            append("；通道：").append(channels.size).append(" 个")
            append("；今日送达：").append(container.history.stats.value.sent)
            append("；待重试：").append(pending)
        }
    }

    private companion object {
        const val TAG = "DODOCOMM_REMOTE"
        const val COMMAND_PREFIX = "#DODO"
    }
}
