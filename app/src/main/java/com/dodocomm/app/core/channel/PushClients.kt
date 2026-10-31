package com.dodocomm.app.core.channel

import com.dodocomm.app.core.settings.AppSettings
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** One channel delivery result, kept together for history/diagnostics. */
data class ChannelSendResult(
    val channelId: String,
    val channelName: String,
    val type: ChannelType,
    val success: Boolean,
    val message: String,
    val rawBody: String? = null,
)

data class DispatchResult(
    val results: List<ChannelSendResult>,
) {
    val success: Boolean get() = results.isNotEmpty() && results.all { it.success }
    val message: String
        get() = if (results.isEmpty()) {
            "没有可用的推送通道，请先配置至少一个通道"
        } else if (success) {
            "推送成功"
        } else {
            results.filterNot { it.success }
                .joinToString("；") { "${it.channelName}：${it.message}" }
        }
    val rawBody: String? get() = results.joinToString("\n") { "${it.channelName}: ${it.message}" }
}

private val JSON = "application/json; charset=utf-8".toMediaType()

/** ntfy JSON publishing (supports UTF-8 titles without header encoding limits). */
class NtfyClient(private val client: OkHttpClient = defaultClient()) {
    suspend fun send(channel: PushChannel, title: String, body: String): ChannelSendResult =
        withContext(Dispatchers.IO) {
            if (!channel.isConfigured()) return@withContext failure(channel, "ntfy 服务器或 Topic 未配置")
            val base = channel.serverUrl.trim().trimEnd('/')
            val payload = JSONObject().apply {
                put("topic", channel.topic.trim())
                put("title", title)
                put("message", body)
                channel.priority.trim().toIntOrNull()?.let { put("priority", it) }
                channel.token.trim().takeIf { it.isNotEmpty() }?.let { put("token", it) }
            }
            val request = Request.Builder()
                .url(base)
                .post(payload.toString().toRequestBody(JSON))
                .apply {
                    channel.token.trim().takeIf { it.isNotEmpty() }?.let {
                        header("Authorization", "Bearer $it")
                    }
                }
                .build()
            client.execute(channel, request)
        }
}

/** Telegram Bot API sendMessage with plain text (avoids parse-mode escaping issues). */
class TelegramClient(private val client: OkHttpClient = defaultClient()) {
    suspend fun send(channel: PushChannel, title: String, body: String): ChannelSendResult =
        withContext(Dispatchers.IO) {
            if (!channel.isConfigured()) return@withContext failure(channel, "Telegram Bot Token 或 Chat ID 未配置")
            val payload = JSONObject().apply {
                put("chat_id", channel.chatId.trim())
                put("text", listOf(title, body).filter { it.isNotBlank() }.joinToString("\n"))
                put("disable_web_page_preview", true)
            }
            val request = Request.Builder()
                .url("https://api.telegram.org/bot${channel.botToken.trim()}/sendMessage")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.execute(channel, request)
        }
}

/** Pushover messages API (form encoded). */
class PushoverClient(private val client: OkHttpClient = defaultClient()) {
    suspend fun send(channel: PushChannel, title: String, body: String): ChannelSendResult =
        withContext(Dispatchers.IO) {
            if (!channel.isConfigured()) return@withContext failure(channel, "Pushover User Key 或 App Token 未配置")
            val form = FormBody.Builder()
                .add("token", channel.appToken.trim())
                .add("user", channel.userKey.trim())
                .add("title", title)
                .add("message", body)
                .apply {
                    channel.priority.trim().toIntOrNull()?.let { add("priority", it.toString()) }
                }
                .build()
            val request = Request.Builder()
                .url("https://api.pushover.net/1/messages.json")
                .post(form)
                .build()
            client.execute(channel, request)
        }
}

/** SMTP email through Android's JavaMail port. */
class EmailClient {
    suspend fun send(channel: PushChannel, title: String, body: String): ChannelSendResult =
        withContext(Dispatchers.IO) {
            if (!channel.isConfigured()) {
                return@withContext failure(channel, "SMTP 服务器、账号、授权码或收件人未配置")
            }
            runCatching {
                val props = Properties().apply {
                    put("mail.smtp.host", channel.smtpHost.trim())
                    put("mail.smtp.port", channel.smtpPort.toString())
                    put("mail.smtp.auth", "true")
                    when (channel.smtpSecurity.uppercase()) {
                        "SSL", "SMTPS" -> put("mail.smtp.ssl.enable", "true")
                        "NONE", "PLAIN" -> Unit
                        else -> put("mail.smtp.starttls.enable", "true")
                    }
                }
                val session = Session.getInstance(
                    props,
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(channel.smtpUsername.trim(), channel.smtpPassword)
                    },
                )
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(channel.smtpUsername.trim()))
                    setRecipients(
                        Message.RecipientType.TO,
                        InternetAddress.parse(channel.recipient.trim()),
                    )
                    setSubject(title, "UTF-8")
                    setText(body, "UTF-8")
                }
                Transport.send(message)
            }.fold(
                onSuccess = { ChannelSendResult(channel.id, channel.name, channel.type, true, "邮件已发送") },
                onFailure = { error ->
                    ChannelSendResult(
                        channel.id,
                        channel.name,
                        channel.type,
                        false,
                        "邮件发送失败：${error.message ?: error.javaClass.simpleName}",
                    )
                },
            )
        }
}

private fun failure(channel: PushChannel, message: String) =
    ChannelSendResult(channel.id, channel.name, channel.type, false, message)

private fun OkHttpClient.execute(channel: PushChannel, request: Request): ChannelSendResult {
    return try {
        newCall(request).execute().use { response ->
            val raw = runCatching { response.body.string() }.getOrNull()?.take(2_000)
            val jsonMessage = raw?.let {
                runCatching { JSONObject(it).optString("error", "") }.getOrNull()
                    ?: runCatching { JSONObject(it).optString("message", "") }.getOrNull()
            }.orEmpty()
            val success = response.isSuccessful
            ChannelSendResult(
                channelId = channel.id,
                channelName = channel.name,
                type = channel.type,
                success = success,
                message = if (success) "推送成功" else {
                    jsonMessage.ifBlank { "HTTP ${response.code}" }
                },
                rawBody = raw,
            )
        }
    } catch (e: IOException) {
        ChannelSendResult(
            channelId = channel.id,
            channelName = channel.name,
            type = channel.type,
            success = false,
            message = e.message?.takeIf { it.isNotBlank() } ?: "网络请求失败",
        )
    }
}

private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
