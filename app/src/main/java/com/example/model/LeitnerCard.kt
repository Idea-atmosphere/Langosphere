package com.example.model

/**
 * A single flashcard in the app's built-in Leitner box (spaced repetition).
 *
 * [word] and [definition] are filled in from the offline dictionary when the
 * user taps "افزودن به جعبهٔ لایتنر" ("Add to Leitner box") in the dictionary
 * bottom sheet — see AppViewModel.addActiveWordToLeitner().
 *
 * [boxLevel] is 1..5 (Leitner's classic 5-box system): higher boxes are
 * reviewed less often. [nextReviewAt] is a millisecond epoch timestamp; the
 * card is "due" once the current time passes it. See LeitnerBoxManager for
 * the exact interval per box and how cards move between boxes.
 *
 * ## Sentence cards
 *
 * The box holds two kinds of card, told apart by [kind]:
 *
 *  - [KIND_WORD] — one word, one definition. This is what every earlier build
 *    stored, and a card read from such a database defaults to it.
 *  - [KIND_SENTENCE] — a whole line from a book or a film. [word] is then the
 *    original sentence and [definition] the rendered back of the card; the
 *    structured fields ([translation], [lessonText], [wordsText],
 *    [pronunciation], [location]) carry the same content in pieces, which is
 *    what the Anki export needs to fill one column per topic.
 *
 * Everything is plain text: the Leitner screen, the quiz and the exporter all
 * display it as-is, so a card never depends on a JSON parser being able to
 * read the package it came from.
 */
data class LeitnerCard(
    val id: Long,
    val word: String,
    val definition: String,
    val boxLevel: Int,
    val nextReviewAt: Long,
    val createdAt: Long,
    /** [KIND_WORD] or [KIND_SENTENCE]. */
    val kind: String = KIND_WORD,
    /** [SOURCE_DICTIONARY], [SOURCE_BOOK] or [SOURCE_MOVIE]. */
    val source: String = SOURCE_DICTIONARY,
    /** Target-language translation of a sentence card. */
    val translation: String = "",
    /** IPA of the whole sentence, when the JSON carried it. */
    val pronunciation: String = "",
    /** Rendered lesson: explanation / grammar / structure, one per line. */
    val lessonText: String = "",
    /** One rendered line per key word, with IPA and meaning. */
    val wordsText: String = "",
    /** `p. 12 s. 3` for a book line, `00:01:23` for a film line. */
    val location: String = "",
) {
    val isSentence: Boolean get() = kind == KIND_SENTENCE

    /** Short source name for the badge on a card row. */
    fun sourceLabel(fa: Boolean): String = when (source) {
        SOURCE_BOOK -> if (fa) "کتاب" else "Book"
        SOURCE_MOVIE -> if (fa) "فیلم" else "Film"
        else -> if (fa) "دیکشنری" else "Dictionary"
    }

    /** The tag this card is exported to Anki under. */
    val ankiTag: String
        get() = when (source) {
            SOURCE_BOOK -> TAG_BOOK
            SOURCE_MOVIE -> TAG_MOVIE
            else -> TAG_DICTIONARY
        }

    companion object {
        /**
         * The canonical form of a card's text.
         *
         * Both the box's lookup keys and the "already saved" checks in the
         * reader build their keys through this one function, so a sentence
         * saved from the film list is recognised in the book reader (and the
         * other way round) even if the two sides spaced it differently.
         */
        fun normalizeFront(text: String): String =
            text.lowercase().trim().replace(Regex("\\s+"), " ")

        const val KIND_WORD = "word"
        const val KIND_SENTENCE = "sentence"

        const val SOURCE_DICTIONARY = "dictionary"
        const val SOURCE_BOOK = "book"
        const val SOURCE_MOVIE = "movie"

        /** Anki tags, spelled exactly as the project asked for them. */
        const val TAG_BOOK = "BookReader"
        const val TAG_MOVIE = "MovieSubtitle"
        const val TAG_DICTIONARY = "Langosphere"
    }
}
