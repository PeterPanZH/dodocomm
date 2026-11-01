package com.dodocomm.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RemoteViews layouts cannot be previewed in JVM tests. Guard the contracts
 * that matter for small cells: every id used by WidgetUpdater exists, action
 * labels are single-line/ellipsized, and localized widget copy stays short.
 */
class WidgetLayoutContractTest {
    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/layout").isDirectory) return dir
            dir = dir.parentFile
        }
        error("project root not found")
    }

    private val layouts = listOf(
        "widget_dodocomm.xml",
        "widget_dodocomm_wide.xml",
        "widget_dodocomm_bar.xml",
        "widget_dodocomm_tall.xml",
        "widget_dodocomm_dashboard.xml",
    )

    @Test
    fun `all layouts expose the RemoteViews ids used by the updater`() {
        val ids = listOf(
            "widget_root", "widget_card", "widget_title", "widget_status",
            "widget_count", "widget_count_label", "widget_open", "widget_toggle",
        )
        layouts.forEach { name ->
            val text = File(root(), "app/src/main/res/layout/$name").readText()
            // RemoteViews cannot inflate a raw <View>; only framework view subclasses.
            assertFalse("$name uses a raw <View>", text.contains("<View\n"))
            ids.forEach { id ->
                assertTrue("$name missing @id/$id", text.contains("@+id/$id"))
            }
        }
    }

    @Test
    fun `widget action labels are single-line and ellipsized`() {
        layouts.forEach { name ->
            val text = File(root(), "app/src/main/res/layout/$name").readText()
            listOf("widget_open", "widget_toggle", "widget_status").forEach { id ->
                val block = text.substringAfter("@+id/$id").substringBefore("</TextView>")
                assertTrue("$name/$id must be maxLines=1", block.contains("android:maxLines=\"1\""))
                assertTrue("$name/$id must ellipsize", block.contains("android:ellipsize="))
            }
        }
    }

    @Test
    fun `localized widget button labels stay short enough for a 2x2 cell`() {
        val folders = listOf("values", "values-en", "values-ja", "values-zh", "values-zh-rTW", "values-ko", "values-es", "values-fr")
        folders.forEach { folder ->
            val text = File(root(), "app/src/main/res/$folder/strings.xml").readText()
            listOf("widget_action_open", "widget_action_pause", "widget_action_resume").forEach { key ->
                val value = Regex("<string name=\"$key\">(.*?)</string>").find(text)?.groupValues?.get(1).orEmpty()
                assertTrue("$folder/$key missing", value.isNotBlank())
                assertTrue("$folder/$key too long: $value", value.length <= 12)
            }
        }
    }

    @Test
    fun `compact layout hides the brand title by default to avoid ellipsis`() {
        val text = File(root(), "app/src/main/res/layout/widget_dodocomm.xml").readText()
        val block = text.substringAfter("@+id/widget_title").substringBefore("</TextView>")
        assertTrue(block.contains("android:visibility=\"gone\""))
    }
}
