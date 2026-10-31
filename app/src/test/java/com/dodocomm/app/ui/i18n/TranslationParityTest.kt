package com.dodocomm.app.ui.i18n

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranslationParityTest {
    private fun assetsDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/i18n")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("找不到 i18n assets 目录")
    }

    private fun load(file: File): Map<String, String> {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    @Test
    fun `English and Japanese dictionaries have the same non-empty keys`() {
        val en = load(File(assetsDir(), "en.json"))
        val ja = load(File(assetsDir(), "ja.json"))
        assertEquals(en.keys.sorted(), ja.keys.sorted())
        assertTrue(en.values.none { it.isBlank() })
        assertTrue(ja.values.none { it.isBlank() })
    }

    @Test
    fun `pattern files compile and reference valid placeholders`() {
        listOf("patterns_en.json", "patterns_ja.json").forEach { name ->
            val array = JSONArray(File(assetsDir(), name).readText(Charsets.UTF_8))
            for (index in 0 until array.length()) {
                val entry = array.getJSONObject(index)
                val regex = entry.getString("regex")
                val format = entry.getString("format")
                Regex(regex)
                val groups = Regex(regex).matchEntire("")?.groupValues?.size ?: 0
                // Every {n} placeholder must refer to a capture group.
                Regex("\\{(\\d+)\\}").findAll(format).forEach { match ->
                    val placeholder = match.groupValues[1].toInt()
                    assertTrue("$name: {$placeholder} has no capture group", groups == 0 || placeholder < groups)
                }
            }
        }
    }
}
