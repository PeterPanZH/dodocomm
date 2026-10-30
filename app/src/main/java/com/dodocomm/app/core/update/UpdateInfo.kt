package com.dodocomm.app.core.update

import org.json.JSONObject

/** Metadata for one published APK, read from the release `latest.json`. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val changelog: String,
)

/**
 * Pure parser for the update manifest. Kept free of Android/IO types so the
 * validation logic can be unit tested.
 */
object UpdateManifest {

    /**
     * Parses [raw] and returns an update only when it is strictly newer than
     * [currentVersionCode]. Returns null for "already up to date" and throws
     * [IllegalArgumentException] for malformed manifests.
     */
    fun parse(raw: String, currentVersionCode: Int): UpdateInfo? {
        val json = JSONObject(raw)
        val versionCode = json.optInt("versionCode", 0)
        val versionName = json.optString("versionName", "").trim()
        val apkUrl = json.optString("apkUrl", "").trim()
        val sha256 = json.optString("sha256", "").trim().lowercase()
        val size = json.optLong("size", 0L)
        val changelog = json.optString("changelog", "").trim()

        require(versionCode > 0) { "versionCode 无效" }
        require(versionName.isNotEmpty()) { "versionName 为空" }
        require(apkUrl.startsWith("https://") || apkUrl.startsWith("http://")) {
            "apkUrl 必须是 http(s) 地址"
        }
        require(sha256.length == 64 && sha256.all { it in "0123456789abcdef" }) {
            "sha256 格式无效"
        }
        require(size >= 0L) { "size 无效" }
        if (versionCode <= currentVersionCode) return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            size = size,
            changelog = changelog,
        )
    }
}
