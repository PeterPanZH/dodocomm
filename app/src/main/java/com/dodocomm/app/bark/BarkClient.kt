package com.dodocomm.app.bark

import com.dodocomm.app.core.model.BarkResult
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.util.OtpExtractor
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * Thin, dependency-free client for the Bark push API.
 *
 * Two API shapes are supported:
 *  - V2 (current): `POST {base}/push` with a JSON body, including the device key.
 *  - V1 (legacy / some self-hosted builds): `GET {base}/{key}/{title}/{body}?...`,
 *    used once as a fallback when the POST endpoint answers 404/405.
 *
 * The client never throws: every failure is mapped to a [BarkResult] whose
 * `message` is Chinese and user-displayable (it is stored in the history row).
 */
class BarkClient(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * Pushes one notification. Blocking work runs on [Dispatchers.IO]; the
     * surrounding coroutine is cooperatively cancelled if the caller times out
     * (the in-flight HTTP call is cancelled too).
     */
    suspend fun send(settings: AppSettings, title: String, body: String): BarkResult =
        withContext(Dispatchers.IO) { sendInternal(settings, title, body) }

    private suspend fun sendInternal(settings: AppSettings, title: String, body: String): BarkResult {
        if (!settings.isConfigured) {
            return BarkResult.failure(ERROR_NOT_CONFIGURED)
        }
        if (settings.normalizedServerBase().toHttpUrlOrNull() == null) {
            return BarkResult.failure(
                "Bark 服务器地址无效，请在设置中填写完整地址（例如 https://api.day.app）",
            )
        }

        val postResult = try {
            val payload = buildPayload(settings, title, body)
            val request = Request.Builder()
                .url(settings.pushEndpoint())
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            interpret(execute(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            networkFailure(e)
        } catch (e: Exception) {
            BarkResult.failure("请求 Bark 服务器失败：${e.friendlyDetail()}")
        }

        // Only legacy servers that reject the /push route get the GET fallback.
        if (postResult.success || (postResult.code != 404 && postResult.code != 405)) {
            return postResult
        }
        val fallbackUrl = legacyGetUrl(settings, title, body) ?: return postResult
        return try {
            val request = Request.Builder().url(fallbackUrl).get().build()
            interpret(execute(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            networkFailure(e)
        } catch (e: Exception) {
            BarkResult.failure("请求 Bark 服务器失败：${e.friendlyDetail()}")
        }
    }

    /** Bark V2 JSON body; empty/disabled style fields are omitted on purpose. */
    private fun buildPayload(settings: AppSettings, title: String, body: String): JSONObject =
        JSONObject().apply {
            put("device_key", settings.deviceKey)
            put("title", title)
            put("body", body)
            if (settings.pushGroup.isNotBlank()) put("group", settings.pushGroup)
            if (settings.pushSound.isNotBlank()) put("sound", settings.pushSound)
            put("level", settings.pushLevel.apiValue)
            if (settings.pushIcon.isNotBlank()) put("icon", settings.pushIcon)
            if (settings.pushUrl.isNotBlank()) put("url", settings.pushUrl)
            if (settings.isArchive) put("isArchive", "1")
            when (settings.copyMode) {
                CopyMode.FULL -> put("autoCopy", "1")
                CopyMode.CODE -> OtpExtractor.extract(body)?.let { put("copy", it) }
                CopyMode.OFF -> Unit
            }
            if (settings.badgeEnabled) put("badge", 1)
        }

    /**
     * Legacy GET route: `{base}/{deviceKey}/{title}/{body}?group=&sound=&level=...`.
     * [HttpUrl] takes care of percent-encoding every path segment (Chinese included).
     */
    private fun legacyGetUrl(settings: AppSettings, title: String, body: String): HttpUrl? {
        val base = settings.normalizedServerBase().toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
            .addPathSegment(settings.deviceKey)
            .addPathSegment(title)
            .addPathSegment(body)
        if (settings.pushGroup.isNotBlank()) builder.addQueryParameter("group", settings.pushGroup)
        if (settings.pushSound.isNotBlank()) builder.addQueryParameter("sound", settings.pushSound)
        if (settings.pushLevel.apiValue.isNotBlank()) {
            builder.addQueryParameter("level", settings.pushLevel.apiValue)
        }
        if (settings.isArchive) builder.addQueryParameter("isArchive", "1")
        when (settings.copyMode) {
            CopyMode.FULL -> builder.addQueryParameter("autoCopy", "1")
            CopyMode.CODE -> OtpExtractor.extract(body)?.let { builder.addQueryParameter("copy", it) }
            CopyMode.OFF -> Unit
        }
        return builder.build()
    }

    /**
     * Executes [request] without blocking a thread; cancellation cancels the call.
     * OkHttp's own connect/read timeouts always bound the wait.
     */
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    /**
     * Success = HTTP 2xx AND (no JSON `code` field or `code == 200`).
     * Failure messages prefer the server's own `message` field.
     */
    private fun interpret(response: Response): BarkResult = response.use { resp ->
        val raw = runCatching { resp.body.string() }.getOrNull()
        val parsed = raw?.let { parseJsonObject(it) }
        val numericCode = parsed?.opt("code")?.let { value ->
            (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull()
        }
        val jsonMessage = parsed?.optString("message", "").orEmpty().trim()
        val success = resp.isSuccessful && (numericCode == null || numericCode == 200)
        if (success) {
            return BarkResult(
                success = true,
                code = numericCode ?: resp.code,
                message = "推送成功",
                rawBody = raw?.take(MAX_RAW_BODY_CHARS),
            )
        }
        val detail = if (jsonMessage.isNotEmpty()) {
            "推送失败：$jsonMessage"
        } else {
            friendlyHttpMessage(resp.code, resp.message)
        }
        BarkResult(
            success = false,
            code = numericCode ?: resp.code,
            message = detail,
            rawBody = raw?.take(MAX_RAW_BODY_CHARS),
        )
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    private fun friendlyHttpMessage(code: Int, statusText: String): String = when (code) {
        400 -> "推送被拒绝：请求参数不正确（HTTP 400）"
        401, 403 -> "推送被拒绝：Device Key 无效或无权限（HTTP $code）"
        404, 405 -> "推送失败：服务器不支持该推送接口（HTTP $code）"
        429 -> "推送失败：请求过于频繁，请稍后重试（HTTP 429）"
        in 500..599 -> "推送失败：Bark 服务器内部错误（HTTP $code），请稍后重试"
        else -> "推送失败：HTTP $code ${statusText.trim()}".trim()
    }

    private fun networkFailure(e: IOException): BarkResult = when (e) {
        is SocketTimeoutException ->
            BarkResult.failure("连接 Bark 服务器超时，请检查服务器地址与网络")
        is UnknownHostException ->
            BarkResult.failure("无法解析 Bark 服务器地址，请检查服务器地址是否正确")
        is SSLException ->
            BarkResult.failure("与 Bark 服务器的安全连接失败，请检查 HTTPS 配置")
        is ConnectException ->
            BarkResult.failure("无法连接 Bark 服务器，请检查服务器地址与端口")
        else ->
            BarkResult.failure("网络请求失败：${e.friendlyDetail()}")
    }

    private fun Throwable.friendlyDetail(): String {
        val reason = localizedMessage?.trim().orEmpty()
        return if (reason.isEmpty()) javaClass.simpleName else "$reason（${javaClass.simpleName}）"
    }

    companion object {
        /** Kept in sync with the engine so history rows read consistently. */
        const val ERROR_NOT_CONFIGURED =
            "未配置 Bark 服务器，请先在设置中填写服务器地址和 Device Key"

        private const val MAX_RAW_BODY_CHARS = 2048
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * One shared client: connection pool + dispatcher are reused and only the
         * two configured timeouts are short enough for an SMS pipeline.
         */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }
}
