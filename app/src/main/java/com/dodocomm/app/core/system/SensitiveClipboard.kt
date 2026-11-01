package com.dodocomm.app.core.system

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copies sensitive values and marks them as sensitive to the system UI. */
object SensitiveClipboard {
    fun copy(context: Context, label: String, text: String) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CoroutineScope(Dispatchers.Main).launch {
                delay(60_000)
                if (manager.primaryClipDescription?.label == label) {
                    manager.clearPrimaryClip()
                }
            }
        }
    }
}
