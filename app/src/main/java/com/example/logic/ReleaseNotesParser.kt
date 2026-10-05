package com.example.logic

/**
 * A tiny Markdown parser for GitHub release notes ("Changelog"), enough to
 * render what GitHub actually generates for a release:
 *
 * - ATX headings (`#` .. `######`),
 * - bullet lists (`-`, `*`, `+`) and ordered lists (`1.`, `1)`),
 * - bold (`**text**`), italic (`*text*` / `_text_`), inline code (`` `code` ``),
 * - links (`[label](url)`) and bare `http(s)://…` URLs,
 * - fenced code blocks (``` ``` ```) and horizontal rules (`---`).
 *
 * It is deliberately line-based: every non-blank line becomes exactly one
 * block, which is how GitHub renders its auto-generated notes anyway and
 * avoids all paragraph-merging complexity. The output is a plain list of
 * [MarkdownBlock]s that the Compose layer maps to styled text — keeping the
 * parsing free of any Android/Compose dependency makes it directly
 * unit-testable (see ReleaseNotesParserTest).
 */
object ReleaseNotesParser {

    /** One styled run of text inside a block. */
    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val linkUrl: String? = null
    )

    /** A block-level element of the changelog document. */
    sealed class Block {
        data class Heading(val level: Int, val spans: List<Span>) : Block()
        data class Paragraph(val spans: List<Span>) : Block()
        data class ListItem(val spans: List<Span>, val bullet: Boolean) : Block()
        data class CodeBlock(val code: String) : Block()
        data object Divider : Block()
    }

    private val orderedItemRegex = Regex("""^(\d{1,9})[.)]\s+(.*)$""")
    private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
    private val dividerRegex = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

    /** Splits markdown text into its block-level elements, top to bottom. */
    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    // Fenced code block: collect until the matching fence
                    // (or the end of the text, for an unclosed fence).
                    val fence = trimmed.take(3)
                    val code = StringBuilder()
                    i++
                    var closed = false
                    while (i < lines.size) {
                        if (lines[i].trim().startsWith(fence)) {
                            closed = true
                            i++
                            break
                        }
                        code.appendLine(lines[i])
                        i++
                    }
                    if (!closed || code.isNotEmpty()) {
                        blocks.add(Block.CodeBlock(code.toString().trimEnd('\n')))
                    }
                }

                dividerRegex.matches(trimmed) -> {
                    blocks.add(Block.Divider)
                    i++
                }

                else -> {
                    val heading = headingRegex.matchEntire(trimmed)
                    if (heading != null) {
                        blocks.add(Block.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2])))
                        i++
                        continue
                    }
                    val bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
                    val ordered = orderedItemRegex.matchEntire(trimmed)
                    when {
                        bullet -> blocks.add(Block.ListItem(parseInline(trimmed.substring(2)), bullet = true))
                        ordered != null -> blocks.add(Block.ListItem(parseInline(ordered.groupValues[2]), bullet = false))
                        else -> blocks.add(Block.Paragraph(parseInline(trimmed)))
                    }
                    i++
                }
            }
        }
        return blocks
    }

    /**
     * Parses the inline styling of one line into [Span]s. Adjacent unstyled
     * characters are merged into one span, so a plain paragraph is a single
     * span. Nesting (a bold link, code inside bold…) is not supported —
     * GitHub release notes do not use it.
     */
    fun parseInline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var plain = StringBuilder()
        var i = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans.add(Span(plain.toString()))
                plain = StringBuilder()
            }
        }

        while (i < text.length) {
            val c = text[i]
            when {
                // **bold**
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", startIndex = i + 2)
                    if (end > i + 1) {
                        flushPlain()
                        spans.add(Span(text.substring(i + 2, end), bold = true))
                        i = end + 2
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                // `code`
                c == '`' -> {
                    val end = text.indexOf('`', startIndex = i + 1)
                    if (end > i) {
                        flushPlain()
                        spans.add(Span(text.substring(i + 1, end), code = true))
                        i = end + 1
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                // *italic* or _italic_ (a lone "* word" was already handled
                // as a list item by the block parser). GFM allows intraword
                // emphasis for "*" but not for "_", so an identifier like
                // "fix_json_parsing" stays literal.
                (c == '*' && i + 1 < text.length && text[i + 1] != ' ') ||
                    (c == '_' && i + 1 < text.length && text[i + 1] != ' ' &&
                        (i == 0 || !text[i - 1].isLetterOrDigit())) -> {
                    val end = text.indexOf(c, startIndex = i + 1)
                    if (end > i + 1) {
                        flushPlain()
                        spans.add(Span(text.substring(i + 1, end), italic = true))
                        i = end + 1
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                // [label](url)
                c == '[' -> {
                    var handled = false
                    val labelEnd = text.indexOf(']', startIndex = i + 1)
                    if (labelEnd > i && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                        val urlEnd = text.indexOf(')', startIndex = labelEnd + 2)
                        if (urlEnd > labelEnd + 1) {
                            val label = text.substring(i + 1, labelEnd)
                            val url = text.substring(labelEnd + 2, urlEnd)
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                flushPlain()
                                spans.add(Span(label, linkUrl = url))
                                i = urlEnd + 1
                                handled = true
                            }
                        }
                    }
                    // Not a link after all ("[x]", "[x] (y)", a relative URL…):
                    // keep the literal '[' and let the rest re-scan.
                    if (!handled) {
                        plain.append(c)
                        i++
                    }
                }

                // bare http://… or https://… URL
                c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i)) -> {
                    var end = i
                    while (end < text.length && !text[end].isWhitespace()) end++
                    var urlEnd = end
                    // Trailing punctuation is sentence punctuation, not part
                    // of the URL ("…compare/v1...v2." stays a link to …v2).
                    while (urlEnd > i && urlEnd > i + 7 && text[urlEnd - 1] in ".,;:!?)]}") urlEnd--
                    flushPlain()
                    spans.add(Span(text.substring(i, urlEnd), linkUrl = text.substring(i, urlEnd)))
                    i = urlEnd
                }

                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flushPlain()
        return spans
    }
}
