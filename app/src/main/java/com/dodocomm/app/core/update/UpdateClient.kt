package com.dodocomm.app.core.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches the update manifest and downloads/verifies the APK. */
class UpdateClient(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun fetch(manifestUrl: String, currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(manifestUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("更新服务返回 HTTP ${response.code}")
                }
                val body = response.body.string()
                UpdateManifest.parse(body, currentVersionCode)
            }
        }

    /**
     * Streams [info.apkUrl] into [destination], calling [onProgress] with 0..100.
     * The file is deleted and [IOException] is thrown when the SHA-256 or size
     * does not match the manifest.
     */
    suspend fun download(
        info: UpdateInfo,
        destination: File,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        val request = Request.Builder().url(info.apkUrl).get().build()
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载更新失败：HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0L } ?: info.size
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var lastPercent = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            val percent = if (total > 0) {
                                ((written * 100) / total).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                throw IOException("更新包校验失败（SHA-256 不匹配）")
            }
            if (info.size > 0 && written != info.size) {
                throw IOException("更新包大小不匹配（$written != ${info.size}）")
            }
            onProgress(100)
            destination
        } catch (t: Throwable) {
            destination.delete()
            throw t
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
