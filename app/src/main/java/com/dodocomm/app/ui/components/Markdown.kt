package com.dodocomm.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dodocomm.app.core.markdown.MarkdownBlock
import com.dodocomm.app.core.markdown.MarkdownParser

/**
 * Small Material3 Markdown renderer for the release changelog. Supports
 * headings, bullets, numbered lists, **bold**, *italic*, `code` and links.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val blocks = MarkdownParser.parse(markdown)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val linkColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    if (index > 0) Spacer(Modifier.padding(top = 8.dp))
                    MaterialText(
                        text = inlineMarkdown(block.text, linkColor, codeBackground),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleMedium
                            2 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.labelLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.Bullet -> {
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                        MaterialText(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(14.dp),
                        )
                        MaterialText(
                            text = inlineMarkdown(block.text, linkColor, codeBackground),
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                        )
                    }
                }

                is MarkdownBlock.Numbered -> {
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                        MaterialText(
                            text = block.marker,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        MaterialText(
                            text = inlineMarkdown(block.text, linkColor, codeBackground),
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    MaterialText(
                        text = inlineMarkdown(block.text, linkColor, codeBackground),
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun inlineMarkdown(
    text: String,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.94f,
                        ),
                    )
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text.startsWith("[", index) -> {
                val closeBracket = text.indexOf(']', index + 1)
                val openParen = if (closeBracket > 0) text.indexOf('(', closeBracket + 1) else -1
                val closeParen = if (openParen > 0) text.indexOf(')', openParen + 1) else -1
                if (closeBracket > 0 && openParen == closeBracket + 1 && closeParen > openParen) {
                    pushStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    )
                    append(text.substring(index + 1, closeBracket))
                    pop()
                    index = closeParen + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}
