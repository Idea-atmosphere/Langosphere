package com.example.logic

import org.jsoup.Jsoup

/**
 * Shared HTML -> readable plain text conversion.
 *
 * Used anywhere a dictionary entry's raw HTML needs to become a clean,
 * properly separated string: the offline dictionary's stored `def` field
 * (built at import time by DictionaryParser/BinaryMdictParser/SqliteDictParser),
 * live MDX lookups and the Leitner box definition (AppViewModel), and the Anki
 * export / AI "learn from dictionary" memory notes.
 *
 * Unlike a plain `Regex("<[^>]*>")` tag strip (the previous approach), this
 * converts line/paragraph-level tags into real newlines *before* removing
 * the remaining tags, so multiple definitions/senses stay visually
 * separated instead of being jammed into one unreadable blob.
 */
object HtmlTextUtils {
    private val liTagRegex = Regex("(?i)<li[^>]*>")
    private val brTagRegex = Regex("(?i)<br\\s*/?>")
    private val blockCloseTagRegex = Regex("(?i)</(p|div|li|h[1-6]|tr|table|ul|ol)>")
    private val anyTagRegex = Regex("<[^>]*>")
    private val extraBlankLinesRegex = Regex("\n{3,}")

    fun htmlToReadableText(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""
        var text = rawHtml
        text = liTagRegex.replace(text, "• ")
        text = brTagRegex.replace(text, "\n")
        text = blockCloseTagRegex.replace(text, "\n\n")
        text = anyTagRegex.replace(text, "")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "\u2019")
            .replace("&lsquo;", "\u2018")
        text = text.lines().joinToString("\n") { it.trim() }
        text = extraBlankLinesRegex.replace(text, "\n\n")
        return text.trim()
    }

    /** One `&name;` or `&#123;` or `&#x1F;` occurrence. */
    private val entityRegex = Regex("&(#x?[0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,9});")

    /** The named entities a book's text layer or an AI reply actually produces. */
    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "sbquo" to "\u201A", "bdquo" to "\u201E", "hellip" to "\u2026", "bull" to "\u2022",
        "ndash" to "\u2013", "mdash" to "\u2014", "minus" to "\u2212", "times" to "\u00D7",
        "deg" to "\u00B0", "middot" to "\u00B7", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "eacute" to "\u00E9", "egrave" to "\u00E8", "agrave" to "\u00E0", "ccedil" to "\u00E7",
        "uuml" to "\u00FC", "ouml" to "\u00F6", "auml" to "\u00E4", "szlig" to "\u00DF",
        "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122", "sect" to "\u00A7",
        "para" to "\u00B6", "dagger" to "\u2020", "prime" to "\u2032", "Prime" to "\u2033",
        "frac12" to "\u00BD", "frac14" to "\u00BC", "frac34" to "\u00BE", "permil" to "\u2030",
    )

    /**
     * Turns HTML escapes back into the characters they stand for.
     *
     * A page that prints `H&H` reaches an AI through an HTML-aware copy path
     * often enough that the reply carries `H&amp;H` - and a reply that escaped
     * the entity once more comes back as `&amp;amp;H`. The book reader matches
     * the reply's `english` against the document's own text, so an undecoded
     * entity is a line that never binds to its page and a translation that
     * appears nowhere. Decoding here, at import, keeps every reader of
     * `BookSentence` (the merged page, the Leitner card, the Anki export) on
     * the text the page actually shows.
     *
     * Escaped input is decoded repeatedly, because `&amp;amp;` only becomes
     * `&` on the second pass. An entity this table does not know is left
     * exactly as it arrived: guessing would corrupt the author's words, and
     * printing it unchanged is what the page shows anyway.
     */
    fun decodeEntities(text: String): String {
        if (text.isEmpty() || '&' !in text) return text
        var current = text
        // A bounded loop: three passes cover `&amp;amp;amp;`, which no real
        // reply has ever contained, and it can never spin on unknown text.
        repeat(3) {
            if (!entityRegex.containsMatchIn(current)) return current
            current = entityRegex.replace(current) { match ->
                decodeEntity(match.groupValues[1]) ?: match.value
            }
        }
        return current
    }

    private fun decodeEntity(body: String): String? {
        if (body.startsWith("#")) {
            val code = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
                body.substring(2).toIntOrNull(16)
            } else {
                body.substring(1).toIntOrNull()
            } ?: return null
            // Reject the surrogate range and anything past the last code point:
            // String(Character.toChars(..)) would throw on both.
            if (code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) return null
            return String(Character.toChars(code))
        }
        return namedEntities[body] ?: namedEntities[body.lowercase()]
    }

    /**
     * Extracts usage-example markup already embedded in a dictionary entry's
     * HTML, following the same `.enex` (original/English sentence) and
     * `.faex` (its Persian meaning) convention the offline dictionary reader
     * already styles (see ui/components/DictionaryBottomSheet.kt's
     * WebViewPart CSS). Returns (originalExample, translatedExample) as
     * plain readable text, or a pair of empty strings when neither class is
     * present. Safe to call on plain (non-HTML) text too.
     */
    fun extractHtmlExamples(rawHtml: String): Pair<String, String> {
        if (rawHtml.isBlank()) return "" to ""
        return try {
            val doc = Jsoup.parseBodyFragment(rawHtml)
            val original = doc.select(".enex").joinToString("\n") { htmlToReadableText(it.html()) }.trim()
            val translation = doc.select(".faex").joinToString("\n") { htmlToReadableText(it.html()) }.trim()
            original to translation
        } catch (e: Exception) {
            "" to ""
        }
    }
}
