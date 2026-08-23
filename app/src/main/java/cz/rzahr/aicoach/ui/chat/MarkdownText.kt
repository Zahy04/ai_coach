package cz.rzahr.aicoach.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private val numberedLineRegex = Regex("^(\\d+)[.)]\\s+(.*)$")

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified
) {
    val lines = remember(markdown) { markdown.lines() }

    Column(modifier) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))

                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(3)
                    val text = annotatedInline(line.dropWhile { it == '#' }.trim())
                    Text(
                        text,
                        style = when (level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }

                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                    Row(Modifier.padding(start = 4.dp)) {
                        Text("•  ", style = style, color = color, fontWeight = FontWeight.Bold)
                        Text(annotatedInline(line.substring(2).trim()), style = style, color = color)
                    }
                }

                numberedLineRegex.containsMatchIn(line) -> {
                    val match = numberedLineRegex.find(line)
                    if (match != null) {
                        Row(Modifier.padding(start = 4.dp)) {
                            Text("${match.groupValues[1]}.  ", style = style, color = color, fontWeight = FontWeight.Bold)
                            Text(annotatedInline(match.groupValues[2]), style = style, color = color)
                        }
                    } else {
                        Text(annotatedInline(line), style = style, color = color)
                    }
                }

                else -> Text(annotatedInline(line), style = style, color = color)
            }
        }
    }
}

private fun annotatedInline(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end == -1) {
                    append(text.substring(index))
                    index = text.length
                } else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                }
            }

            text[index] == '*' && index + 1 < text.length -> {
                val end = text.indexOf('*', index + 1)
                if (end == -1) {
                    append(text.substring(index))
                    index = text.length
                } else {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                }
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}
