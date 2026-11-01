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
        val languages = listOf("en", "ja", "zh-TW", "ko", "es", "fr")
        val base = load(File(assetsDir(), "en.json"))
        languages.forEach { language ->
            val map = load(File(assetsDir(), "$language.json"))
            assertEquals("$language keys", base.keys.sorted(), map.keys.sorted())
            assertTrue("$language has empty values", map.values.none { it.isBlank() })
        }
    }

    @Test
    fun `no translator protection tokens leak into assets`() {
        listOf("en", "ja", "zh-TW", "ko", "es", "fr").forEach { language ->
            val dictionary = File(assetsDir(), "$language.json").readText()
            assertTrue("$language dictionary leaked ZXQ", !dictionary.contains("ZXQ", ignoreCase = true))
            val patterns = File(assetsDir(), "patterns_$language.json").readText()
            assertTrue("$language patterns leaked ZXQ", !patterns.contains("ZXQ", ignoreCase = true))
        }
    }

    @Test
    fun `dynamic count patterns are present in every language`() {
        val required = listOf(
            "^(\\d+) 条记录$",
            "^(\\d+) 个通道$",
            "^(\\d+) 个启用$",
            "^(\\d+) 分钟$",
            "^(\\d+) 秒$",
            "^(.+) (\\d+)$",
        )
        listOf("en", "ja", "zh-TW", "ko", "es", "fr").forEach { language ->
            val array = JSONArray(File(assetsDir(), "patterns_$language.json").readText(Charsets.UTF_8))
            val regexes = (0 until array.length())
                .map { array.getJSONObject(it).getString("regex") }
                .toSet()
            required.forEach { pattern ->
                assertTrue("$language missing dynamic pattern $pattern", pattern in regexes)
            }
        }
    }

    @Test
    fun `pattern files compile and reference valid placeholders`() {
        listOf(
            "patterns_en.json", "patterns_ja.json", "patterns_zh-TW.json",
            "patterns_ko.json", "patterns_es.json", "patterns_fr.json",
        ).forEach { name ->
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
