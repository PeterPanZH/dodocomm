package com.dodocomm.app.core.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Opens official system settings screens; every call is best-effort and safe. */
object SystemNavigator {
    fun deviceLabel(): String = "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: ""}".trim()

    fun openAppDetails(context: Context): Boolean = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    fun openNotificationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (launch(context, intent)) return true
        return openAppDetails(context)
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean = launch(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    /**
     * Shows the system "allow background activity" dialog for this app. Falls
     * back to the battery-optimisation list when the OEM blocks the dialog.
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (launch(context, direct)) return true
        return openBatteryOptimizationSettings(context)
    }

    fun openBatterySettings(context: Context): Boolean {
        if (launch(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) return true
        if (openAppDetails(context)) return true
        return openBatteryOptimizationSettings(context)
    }

    /** Tries known OEM auto-start pages, then falls back to app details. */
    fun openAutoStartSettings(context: Context): Boolean {
        for (component in AUTO_START_COMPONENTS) {
            val componentName = ComponentName.unflattenFromString(component) ?: continue
            val intent = Intent().apply {
                this.component = componentName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ExternalNavigation.suppressNextLeave()
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next candidate.
            } catch (_: SecurityException) {
                // Try the next candidate.
            } catch (_: Exception) {
                // Try the next candidate.
            }
        }
        return openAppDetails(context)
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        return try {
            ExternalNavigation.suppressNextLeave()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private val AUTO_START_COMPONENTS = listOf(
        "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.coloros.safecenter/.startupapp.StartupAppListActivity",
        "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
        "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
    )
}
