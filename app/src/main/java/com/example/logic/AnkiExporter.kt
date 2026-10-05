package com.example.logic

import com.example.model.LeitnerCard

/**
 * Builds a plain-text file from the user's Leitner cards, in the format
 * Anki's "Notes in Plain Text" importer expects:
 *
 *   #separator:tab
 *   #html:true
 *   <front>\t<back>
 *   <front>\t<back>
 *   ...
 *
 * - The "#separator:tab" line tells Anki that fields are separated by a TAB
 *   character on each line.
 * - The "#html:true" line tells Anki the field text may contain HTML, so the
 *   "<br>" tags inserted below render as real line breaks inside the Anki
 *   card instead of showing up as literal text.
 * - Each note is one line: front field (the word, e.g. "Hello") then a TAB
 *   then the back field (its dictionary definition). A literal TAB or
 *   newline inside a field would break Anki's line-based parser, so any
 *   real line breaks in the stored definition are converted to "<br>" and
 *   any stray tabs are replaced with spaces.
 *
 * ## Sentence cards
 *
 * A sentence card carries far more than a definition: the translation, the
 * grammar lesson, the sentence structure, IPA and the key words. Packing all
 * of that into one column would bury it, so as soon as the export contains at
 * least one sentence card the file describes a THIRD column holding the note's
 * tags, and the back field is composed from the card's structured parts:
 *
 * ```
 * #separator:tab
 * #html:true
 * #tags column:3
 * The gate was locked.   <translation><br><br>📘 …<br>🔧 …<br>🧩 …   BookReader Langosphere
 * ```
 *
 * Anki reads `#tags column:3` itself, so the tags land on the notes; a
 * word-only export keeps the original two-column layout byte for byte, because
 * nothing about it needs to change.
 */
object AnkiExporter {

    private const val HEADER_BASIC = "#separator:tab\n#html:true\n"
    private const val HEADER_TAGGED = "#separator:tab\n#html:true\n#tags column:3\n"

    /** Section markers, shared with the Leitner screen's card rendering. */
    private const val LESSON_PREFIX = "📘"
    private const val GRAMMAR_PREFIX = "🔧"
    private const val STRUCTURE_PREFIX = "🧩"
    private const val IPA_PREFIX = "🔊"
    private const val WORDS_PREFIX = "•"

    fun buildExportText(cards: List<LeitnerCard>): String {
        if (cards.isEmpty()) return HEADER_BASIC
        val tagged = cards.any { it.isSentence }
        val header = if (tagged) HEADER_TAGGED else HEADER_BASIC
        val body = cards.joinToString("\n") { card ->
            val front = sanitizeField(card.word)
            val back = htmlEncodeDefinition(backFor(card))
            if (!tagged) {
                "$front\t$back"
            } else {
                // Every row carries three columns once the file declares the
                // tag column, so Anki never has to guess a field count.
                "$front\t$back\t${tagsFor(card)}"
            }
        }
        return header + body + "\n"
    }

    /**
     * The back of one note.
     *
     * A sentence card is rendered from its own fields, so the card in Anki
     * looks like the card in the app: translation first, then the lesson
     * block, then IPA, then the key words. Word cards keep the definition the
     * dictionary produced.
     */
    private fun backFor(card: LeitnerCard): String {
        if (!card.isSentence) return card.definition
        val sections = ArrayList<String>()
        card.translation.trim().takeIf { it.isNotBlank() }?.let { sections += it }
        card.lessonText.trim().takeIf { it.isNotBlank() }?.let { sections += it }
        card.pronunciation.trim().takeIf { it.isNotBlank() }?.let { sections += "$IPA_PREFIX $it" }
        card.wordsText.trim().takeIf { it.isNotBlank() }?.let { sections += it }
        if (card.location.isNotBlank()) sections += "<i>${card.location.trim()}</i>"
        // A sentence card always has something (isValid guaranteed that); the
        // stored definition is the last resort if a future field is missing.
        return sections.joinToString("\n\n").ifBlank { card.definition }
    }

    /**
     * Space-separated tags: every card is tagged with the app's own tag, and a
     * sentence card additionally names where it came from, which is what makes
     * "show me only my reading cards" possible inside Anki.
     */
    private fun tagsFor(card: LeitnerCard): String {
        if (!card.isSentence) return LeitnerCard.TAG_DICTIONARY
        return "${LeitnerCard.TAG_DICTIONARY} ${card.ankiTag}"
    }

    private fun sanitizeField(text: String): String {
        return text.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim()
    }

    /**
     * Turns the stored multi-line text into HTML for Anki.
     *
     * `<br>` is inserted by hand rather than by escaping the angle brackets of
     * the content, because `#html:true` is declared: the JSON's own text is
     * plain prose and must not be escaped. Tabs would break the row, so they
     * become spaces.
     */
    private fun htmlEncodeDefinition(definition: String): String {
        val normalized = definition.replace("\r\n", "\n").replace("\t", " ")
        return normalized.split("\n").joinToString("<br>") { it.trim() }
    }

    /**
     * Convenience for the export button: which sections a card file contains,
     * for the confirmation message ("۱۲ کارت واژه، ۵ کارت جمله").
     */
    fun countByKind(cards: List<LeitnerCard>): Pair<Int, Int> {
        val sentences = cards.count { it.isSentence }
        return (cards.size - sentences) to sentences
    }

    // Kept public for the Leitner screen, which renders the same markers.
    fun sectionPrefixes(): List<String> =
        listOf(LESSON_PREFIX, GRAMMAR_PREFIX, STRUCTURE_PREFIX, IPA_PREFIX, WORDS_PREFIX)
}
