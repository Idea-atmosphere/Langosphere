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
