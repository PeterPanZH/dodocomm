package com.dodocomm.app.widget

import android.os.Build
import java.util.Locale

/** Host/launcher details that affect how a RemoteViews card should be drawn. */
object WidgetHost {
    fun isColorOS(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        if (manufacturer.contains("oppo") ||
            manufacturer.contains("oneplus") ||
            manufacturer.contains("realme")
        ) {
            return true
        }
        val oplusRom = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            (get.invoke(null, "ro.build.version.oplusrom") as? String).orEmpty()
        }.getOrDefault("")
        return oplusRom.isNotBlank()
    }
}
