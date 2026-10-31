package com.dodocomm.app.core.crash

import android.content.Context
import android.os.Build
import android.os.Process
import com.dodocomm.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class CrashEntry(
    val time: Long,
    val versionName: String,
    val thread: String,
    val stackTrace: String,
)

/**
 * Stores the last few uncaught exceptions locally. Nothing is uploaded and the
 * log is capped at [MAX_ENTRIES]; users can inspect, copy or clear it from the
 * diagnostics screen.
 */
object CrashLogger {
    private const val PREFS = "dodocomm_crash_logs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 10

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(appContext, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun entries(context: Context): List<CrashEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        CrashEntry(
                            time = item.optLong("time"),
                            versionName = item.optString("versionName"),
                            thread = item.optString("thread"),
                            stackTrace = item.optString("stackTrace"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ENTRIES)
            .apply()
    }

    fun toText(entries: List<CrashEntry>): String = buildString {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        entries.forEachIndexed { index, entry ->
            if (index > 0) appendLine()
            appendLine("### ${formatter.format(Date(entry.time))} · v${entry.versionName}")
            appendLine("thread: ${entry.thread}")
            appendLine(entry.stackTrace)
        }
    }.trim()

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val trace = writer.toString().take(MAX_STACK_CHARS)
        val current = entries(context).toMutableList()
        current.add(
            0,
            CrashEntry(
                time = System.currentTimeMillis(),
                versionName = BuildConfig.VERSION_NAME,
                thread = thread.name,
                stackTrace = trace,
            ),
        )
        val array = JSONArray()
        current.take(MAX_ENTRIES).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("time", entry.time)
                    put("versionName", entry.versionName)
                    put("thread", entry.thread)
                    put("stackTrace", entry.stackTrace)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, array.toString())
            .commit()
    }

    private const val MAX_STACK_CHARS = 16_000
}
