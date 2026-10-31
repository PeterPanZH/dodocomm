package com.dodocomm.app.core.markdown

/** Minimal block-level Markdown model used by the in-app update dialog. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val marker: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

/**
 * Parses the subset of Markdown used by CHANGELOG.md: headings, bullet lists,
 * numbered lists and paragraphs. Inline emphasis is handled by the renderer.
 */
object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^[-*+]\\s+(.*)$")
    private val numbered = Regex("^(\\d+[.)])\\s+(.*)$")

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.toString().trim())
                paragraph.clear()
            }
        }

        markdown.replace("\r\n", "\n").split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushParagraph()
                heading.matches(trimmed) -> {
                    flushParagraph()
                    val match = heading.matchEntire(trimmed) ?: return@forEach
                    blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
                }
                bullet.matches(trimmed) -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Bullet(bullet.matchEntire(trimmed)!!.groupValues[1].trim())
                }
                numbered.matches(trimmed) -> {
                    flushParagraph()
                    val match = numbered.matchEntire(trimmed)!!
                    blocks += MarkdownBlock.Numbered(match.groupValues[1], match.groupValues[2].trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        flushParagraph()
        return blocks
    }
}
