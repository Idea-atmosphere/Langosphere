package com.example.logic

import com.example.model.LeitnerCard

/**
 * Builds a plain-text file from the user's Leitner cards, in the format
 * Anki's "Notes in Plain Text" importer expects:
 *
 *   #separator:tab
 *   #html:true
 *   <word>\t<definition>
 *   <word>\t<definition>
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
 */
object AnkiExporter {

    fun buildExportText(cards: List<LeitnerCard>): String {
        val header = "#separator:tab\n#html:true\n"
        if (cards.isEmpty()) return header
        val body = cards.joinToString("\n") { card ->
            val front = sanitizeField(card.word)
            val back = htmlEncodeDefinition(card.definition)
            "$front\t$back"
        }
        return header + body + "\n"
    }

    private fun sanitizeField(text: String): String {
        return text.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim()
    }

    private fun htmlEncodeDefinition(definition: String): String {
        val normalized = definition.replace("\r\n", "\n").replace("\t", " ")
        return normalized.split("\n").joinToString("<br>") { it.trim() }
    }
}
