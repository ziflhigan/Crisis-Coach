package com.cautious5.crisis_coach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser
import org.commonmark.node.Text as MdText

/**
 * A composable that renders markdown text using Material 3 styling
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val parser = remember { Parser.builder().build() }
    val document = remember(markdown) { parser.parse(markdown) }

    val annotatedString = remember(document) {
        buildAnnotatedString {
            val visitor = MarkdownAnnotatedStringVisitor(this)
            document.accept(visitor)
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = LocalTextStyle.current
    )
}

/**
 * Visitor that converts markdown nodes to AnnotatedString with styling
 */
private class MarkdownAnnotatedStringVisitor(
    private val builder: AnnotatedString.Builder
) : AbstractVisitor() {

    private var listLevel = 0

    override fun visit(text: MdText) {
        builder.append(text.literal)
    }

    override fun visit(softLineBreak: SoftLineBreak) {
        builder.append(" ")
    }

    override fun visit(strongEmphasis: StrongEmphasis) {
        val start = builder.length
        visitChildren(strongEmphasis)
        builder.addStyle(
            SpanStyle(fontWeight = FontWeight.Bold),
            start,
            builder.length
        )
    }

    override fun visit(emphasis: Emphasis) {
        val start = builder.length
        visitChildren(emphasis)
        builder.addStyle(
            SpanStyle(fontStyle = FontStyle.Italic),
            start,
            builder.length
        )
    }

    override fun visit(code: Code) {
        val start = builder.length
        builder.append(code.literal)
        builder.addStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f)
            ),
            start,
            builder.length
        )
    }

    override fun visit(indentedCodeBlock: IndentedCodeBlock) {
        builder.append("\n")
        val start = builder.length
        builder.append(indentedCodeBlock.literal)
        builder.addStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f)
            ),
            start,
            builder.length
        )
        builder.append("\n")
    }

    override fun visit(fencedCodeBlock: FencedCodeBlock) {
        builder.append("\n")
        val start = builder.length
        builder.append(fencedCodeBlock.literal)
        builder.addStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f)
            ),
            start,
            builder.length
        )
        builder.append("\n")
    }

    override fun visit(heading: Heading) {
        val start = builder.length
        visitChildren(heading)

        val style = when (heading.level) {
            1 -> SpanStyle(
                fontSize = 24.sp, // Large heading
                fontWeight = FontWeight.Bold
            )
            2 -> SpanStyle(
                fontSize = 20.sp, // Medium heading
                fontWeight = FontWeight.Bold
            )
            3 -> SpanStyle(
                fontSize = 18.sp, // Small heading
                fontWeight = FontWeight.Bold
            )
            4 -> SpanStyle(
                fontSize = 16.sp, // Smaller heading
                fontWeight = FontWeight.Bold
            )
            5 -> SpanStyle(
                fontSize = 14.sp, // Even smaller heading
                fontWeight = FontWeight.Bold
            )
            6 -> SpanStyle(
                fontSize = 12.sp, // Smallest heading
                fontWeight = FontWeight.Bold
            )
            else -> SpanStyle(fontWeight = FontWeight.Bold)
        }
        builder.addStyle(style, start, builder.length)
        builder.append("\n\n")
    }

    override fun visit(paragraph: Paragraph) {
        visitChildren(paragraph)
        builder.append("\n\n")
    }

    override fun visit(bulletList: BulletList) {
        listLevel++
        visitChildren(bulletList)
        listLevel--
        if (listLevel == 0) {
            builder.append("\n")
        }
    }

    override fun visit(orderedList: OrderedList) {
        listLevel++
        visitChildren(orderedList)
        listLevel--
        if (listLevel == 0) {
            builder.append("\n")
        }
    }

    override fun visit(listItem: ListItem) {
        val indent = "  ".repeat(listLevel - 1)
        builder.append("${indent}• ")
        visitChildren(listItem)
        builder.append("\n")
    }
}

/**
 * Enhanced markdown text with code block highlighting - SAFE VERSION
 */
@Composable
fun MarkdownTextWithCodeBlocks(
    markdown: String,
    modifier: Modifier = Modifier
) {
    // Add null/empty check to prevent crashes
    if (markdown.isBlank()) {
        return
    }

    val codeBlockRegex = remember { Regex("```([\\s\\S]*?)```") }
    val hasCodeBlocks = remember(markdown) {
        try {
            codeBlockRegex.containsMatchIn(markdown)
        } catch (e: Exception) {
            false // Fallback to simple rendering if regex fails
        }
    }

    if (hasCodeBlocks) {
        // Split content by code blocks and render separately
        val parts = remember(markdown) {
            try {
                val matches = codeBlockRegex.findAll(markdown).toList()
                val parts = mutableListOf<Pair<String, Boolean>>() // content, isCodeBlock
                var lastEnd = 0

                matches.forEach { match ->
                    if (match.range.first > lastEnd) {
                        parts.add(markdown.substring(lastEnd, match.range.first) to false)
                    }
                    parts.add(match.groupValues[1] to true)
                    lastEnd = match.range.last + 1
                }

                if (lastEnd < markdown.length) {
                    parts.add(markdown.substring(lastEnd) to false)
                }

                parts
            } catch (e: Exception) {
                // Fallback: treat entire content as regular markdown
                listOf(markdown to false)
            }
        }

        parts.forEach { (content, isCodeBlock) ->
            if (content.isNotBlank()) { // Skip empty content
                if (isCodeBlock) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = content.trim(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    MarkdownText(content, modifier = modifier)
                }
            }
        }
    } else {
        MarkdownText(markdown, modifier = modifier)
    }
}