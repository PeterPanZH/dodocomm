package com.dodocomm.app.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses headings bullets and paragraphs`() {
        val blocks = MarkdownParser.parse(
            """
            ### 修复
            - 修复记录筛选栏
            - 修复英文裁切

            普通段落
            第二行
            """.trimIndent(),
        )
        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Heading(3, "修复"), blocks[0])
        assertEquals(MarkdownBlock.Bullet("修复记录筛选栏"), blocks[1])
        assertEquals(MarkdownBlock.Bullet("修复英文裁切"), blocks[2])
        assertEquals(MarkdownBlock.Paragraph("普通段落 第二行"), blocks[3])
    }

    @Test
    fun `parses numbered lists`() {
        val blocks = MarkdownParser.parse("1. 第一项\n2. 第二项")
        assertEquals(MarkdownBlock.Numbered("1.", "第一项"), blocks[0])
        assertEquals(MarkdownBlock.Numbered("2.", "第二项"), blocks[1])
    }

    @Test
    fun `blank markdown yields no blocks`() {
        assertTrue(MarkdownParser.parse(" \n\n ").isEmpty())
    }
}
