package com.example.logic

import com.example.model.BookSentence
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitlePackage

/**
 * Turns the book reader's stored sentences into quiz material.
 *
 * The quiz engine ([JsonQuizBuilder]) speaks one language: the app's JSON
 * learning package. The book reader keeps its sentences in its own store, with
 * the same learning payload but a page anchor instead of a timestamp. Bridging
 * the two here - one adapter, in the logic layer - is what lets the quiz offer
 * "book", "film" or "both" without the engine knowing anything about pages,
 * and without the reader's models leaking into the question builder.
 *
 * Nothing is re-parsed from JSON: the reader's sentences were already
 * validated on import, so a package built this way can never be malformed.
 */
object BookQuizMaterial {

    /** One document's worth of sentences, with the name to show in the quiz. */
    data class Document(val name: String, val sentences: List<BookSentence>)

    /**
     * Builds the book half of the quiz, or null when nothing has been read
     * yet. Empty translations and empty lines are dropped: a question whose
     * answer is blank would be unanswerable.
     */
    fun build(documents: List<Document>): QuizMaterial? {
        val usable = documents.flatMap { document ->
            document.sentences.filter { it.english.isNotBlank() && !it.translation.isNullOrBlank() }
        }
        if (usable.isEmpty()) return null
        return QuizMaterial(QuizSource.BOOK, packageFrom(usable))
    }

    /** One package out of the reader's sentences, in document order. */
    fun packageFrom(sentences: List<BookSentence>): JsonSubtitlePackage =
        JsonSubtitlePackage(
            formatVersion = 1,
            metadata = null,
            subtitles = sentences.map { it.toJsonSubtitle() },
        )

    private fun BookSentence.toJsonSubtitle(): JsonSubtitle = JsonSubtitle(
        id = id.toString(),
        start = null,
        end = null,
        english = english,
        translation = translation,
        level = level,
        difficulty = difficulty,
        pronunciation = pronunciation,
        notes = notes,
        lesson = lesson,
        words = words,
    )
}
