package com.dodocomm.app.core.system

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Resolves the user-visible phone name for the `{device}` template token.
 *
 * Prefers the system "device name" (Settings → About phone → Device name), then
 * the Bluetooth name exposed by Settings.Secure, and only falls back to the
 * model/device codename when the OS does not expose a name.
 */
object DeviceName {
    private const val TAG = "SMS2BARK"

    fun resolve(context: Context): String {
        val resolver = context.contentResolver
        val global = runCatching {
            Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME)
        }.onFailure { Log.w(TAG, "读取设备名称失败: ${it.javaClass.simpleName}") }.getOrNull()
            ?.trim()
            .orEmpty()
        if (global.isNotEmpty()) return global

        val secure = runCatching {
            Settings.Secure.getString(resolver, "bluetooth_name")
        }.getOrNull()?.trim().orEmpty()
        if (secure.isNotEmpty()) return secure

        val model = Build.MODEL.replace('_', ' ').trim()
        return model.ifEmpty { Build.DEVICE.trim() }
    }
}
