package com.dodocomm.app.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/** Notification action that opens the system package installer. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL) return
        val path = intent.getStringExtra(EXTRA_APK_PATH) ?: return
        val file = File(path)
        if (!file.exists()) return
        runCatching { UpdateInstaller.install(context, file) }
    }

    companion object {
        const val ACTION_INSTALL = "com.dodocomm.app.action.INSTALL_UPDATE"
        const val EXTRA_APK_PATH = "apk_path"
    }
}
