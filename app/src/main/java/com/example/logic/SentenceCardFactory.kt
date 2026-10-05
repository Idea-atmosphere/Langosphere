package com.example.logic

import com.example.model.BookSentence
import com.example.model.JsonSubtitle
import com.example.model.JsonWord

/**
 * A sentence as a flashcard, in the shape both the Leitner box and the Anki
 * exporter need.
 *
 * The same draft is produced from a book sentence and from a subtitle line,
 * which is what makes "[+ لایتنر جمله]" behave identically in the reader and
 * in the player: front is the original line, back is the full teaching payload
 * the JSON carried for it (translation, lesson, key words with IPA).
 *
 * Everything is reduced to plain strings here on purpose. The Leitner database
 * stores text, the Anki exporter writes text, and both surfaces only display
 * it - so there is no second JSON parser to keep in sync with
 * [com.example.logic.SubtitleJsonParser], and an imported package that used an
 * older schema still produces a usable card.
 */
data class SentenceCardDraft(
    /** The original sentence, exactly as the reader shows it. */
    val front: String,
    /** Target-language translation of [front]. */
    val translation: String,
    val explanation: String = "",
    val grammar: String = "",
    val structure: String = "",
    val pronunciation: String = "",
    /** One rendered line per key word, e.g. `gate /ɡeɪt/ — دروازه: the entrance…`. */
    val words: List<String> = emptyList(),
    /** `p. 12 s. 3` for a book, `00:01:23` for a film. */
    val location: String = "",
    /** [com.example.model.LeitnerCard.SOURCE_BOOK] or `..._MOVIE`. */
    val source: String = com.example.model.LeitnerCard.SOURCE_BOOK,
) {
    /** A card without its front line or its translation cannot teach anything. */
    val isValid: Boolean get() = front.isNotBlank() && (translation.isNotBlank() || words.isNotEmpty())

    /** The lesson block on its own, `""` when the entry carried no lesson. */
    val lessonText: String
        get() = buildList {
            explanation.takeIf { it.isNotBlank() }?.let { add("📘 $it") }
            grammar.takeIf { it.isNotBlank() }?.let { add("🔧 $it") }
            structure.takeIf { it.isNotBlank() }?.let { add("🧩 $it") }
        }.joinToString("\n")

    val wordsText: String get() = words.joinToString("\n") { "• $it" }

    /**
     * The plain-text back of the card.
     *
     * This is what the Leitner screen renders and what the quiz and the
     * dictionary-style lookups read, so it always leads with the translation
     * and then adds whatever else the entry had.
     */
    val definition: String
        get() = buildList {
            translation.takeIf { it.isNotBlank() }?.let { add(it) }
            lessonText.takeIf { it.isNotBlank() }?.let { add(it) }
            pronunciation.takeIf { it.isNotBlank() }?.let { add("🔊 $it") }
            wordsText.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("\n\n")
}

/**
 * Builds [SentenceCardDraft]s out of the two places learning sentences come
 * from. Kept here rather than in a ViewModel so both the reader and the player
 * share one definition of what a sentence card contains.
 */
object SentenceCardFactory {

    /** From a book reader sentence (the reader's own JSON-backed model). */
    fun fromBook(sentence: BookSentence): SentenceCardDraft {
        val lesson = sentence.lesson
        return SentenceCardDraft(
            front = sentence.english.trim(),
            translation = sentence.translation?.trim().orEmpty(),
            explanation = lesson?.explanation?.trim().orEmpty(),
            grammar = listOfNotNull(
                lesson?.grammar?.trim()?.takeIf { it.isNotBlank() },
                lesson?.grammarTranslation?.trim()?.takeIf { it.isNotBlank() },
            ).distinct().joinToString(" — "),
            structure = lesson?.structure?.trim().orEmpty(),
            pronunciation = sentence.pronunciation?.trim().orEmpty(),
            words = renderWords(sentence.words),
            location = sentence.location.trim(),
            source = com.example.model.LeitnerCard.SOURCE_BOOK,
        )
    }

    /**
     * From one line of a JSON learning package.
     *
     * @param sub the parsed line (film subtitle or book entry - the envelope
     *   is the same for both).
     * @param timeLabel the line's clock position, when it has one. Book entries
     *   have no timestamps, so they pass their page locator instead.
     */
    fun fromSubtitle(sub: JsonSubtitle, timeLabel: String = ""): SentenceCardDraft {
        val lesson = sub.lesson
        return SentenceCardDraft(
            front = sub.english.trim(),
            translation = sub.translation?.trim().orEmpty(),
            explanation = lesson?.explanation?.trim().orEmpty()
                .ifBlank { sub.notes?.trim().orEmpty() },
            grammar = listOfNotNull(
                lesson?.grammar?.trim()?.takeIf { it.isNotBlank() },
                lesson?.grammarTranslation?.trim()?.takeIf { it.isNotBlank() },
            ).distinct().joinToString(" — "),
            structure = lesson?.structure?.trim().orEmpty(),
            pronunciation = sub.pronunciation?.trim().orEmpty(),
            words = renderWords(sub.words),
            location = timeLabel.trim(),
            source = com.example.model.LeitnerCard.SOURCE_MOVIE,
        )
    }

    /**
     * One line per word: the word, its IPA in brackets, its translation, and
     * the meaning it has in this sentence. Empty parts are dropped instead of
     * leaving `— :` scaffolding behind.
     */
    fun renderWords(words: List<JsonWord>): List<String> =
        words.mapNotNull { word ->
            val text = word.word.trim()
            if (text.isEmpty()) return@mapNotNull null
            buildString {
                append(text)
                word.pronunciation?.trim()?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                val meaning = word.translation?.trim().orEmpty()
                if (meaning.isNotBlank()) append(" — ").append(meaning)
                word.meaningInContext?.trim()?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            }
        }
}
