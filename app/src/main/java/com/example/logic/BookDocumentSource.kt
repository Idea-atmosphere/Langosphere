package com.example.logic

import android.content.Context
import android.net.Uri
import com.example.model.BookSentence
import com.example.model.DocumentSlice
import com.example.model.SliceKind

/**
 * The Android-facing edge of the book chunking engine.
 *
 * Everything that needs a [Context] or a [Uri] lives here, and nothing else
 * does: extraction happens in this file, while segmentation and slicing stay
 * in [SentenceSegmenter] and [DocumentChunker] as pure Kotlin. That split is
 * what makes the interesting logic testable on the JVM instead of requiring an
 * instrumented or Robolectric run for every boundary case.
 *
 * All functions here do disk and parsing work and must be called off the main
 * thread.
 */
object BookDocumentSource {

    /** A document reduced to cleaned units: PDF pages, or EPUB chapters. */
    data class Units(
        val kind: SliceKind,
        val docName: String,
        val units: List<String>,
        val unitTitles: List<String> = emptyList(),
    ) {
        val unitCount: Int get() = units.size
        val isEmpty: Boolean get() = units.none { it.isNotBlank() }

        /** Human label for a 1-based unit number, used in range pickers. */
        fun unitLabel(unitNumber: Int): String {
            val title = unitTitles.getOrNull(unitNumber - 1)?.trim().orEmpty()
            val prefix = when (kind) {
                SliceKind.PDF_PAGES -> "p. $unitNumber"
                SliceKind.EPUB_SPINE, SliceKind.EPUB_PARAGRAPHS -> "Ch. $unitNumber"
            }
            return if (title.isEmpty()) prefix else "$prefix — $title"
        }
    }

    /**
     * Extracts a PDF page by page, then cleans each page.
     *
     * Running headers and footers are detected across the whole document
     * before cleaning, because a repeated line can only be recognised as
     * furniture by seeing it repeat — and if it is left in, it lands in the
     * middle of the sentence stream and gets translated as if it were prose.
     */
    fun loadPdf(context: Context, uri: Uri, docName: String = ""): Units {
        val rawPages = PdfTextExtractor.extractPages(context, uri)
        val running = SentenceSegmenter.detectRunningLines(rawPages)
        val cleaned = rawPages.map { page -> SentenceSegmenter.clean(page, running) }
        return Units(kind = SliceKind.PDF_PAGES, docName = docName, units = cleaned)
    }

    /** Extracts an EPUB chapter by chapter. Returns null when it cannot be parsed. */
    fun loadEpub(context: Context, uri: Uri, docName: String = ""): Units? {
        val book = EpubParser.parse(context, uri) ?: return null
        val rawChapters = book.chapters.map { chapter -> chapter.text }
        val running = SentenceSegmenter.detectRunningLines(rawChapters)
        val cleaned = rawChapters.map { chapter -> SentenceSegmenter.clean(chapter, running) }
        return Units(
            kind = SliceKind.EPUB_SPINE,
            docName = docName.ifBlank { book.title.toString() },
            units = cleaned,
            unitTitles = book.chapters.map { chapter -> chapter.title.toString() },
        )
    }

    /** Wraps plain text as a single-unit document, so the Text reader uses the same path. */
    fun loadPlainText(text: String, docName: String = ""): Units = Units(
        kind = SliceKind.PDF_PAGES,
        docName = docName,
        units = listOf(SentenceSegmenter.clean(text)),
    )

    /** Splits a unit into logical paragraphs, for EPUB paragraph-threshold slicing. */
    fun paragraphsOf(unitText: String): List<String> =
        unitText.split(Regex("\\n{2,}"))
            .map { paragraph -> paragraph.trim() }
            .filter { paragraph -> paragraph.isNotEmpty() }

    /** "Next N sentences", continuing exactly where the last batch stopped. */
    fun sliceNext(
        source: Units,
        cursor: DocumentChunker.Cursor,
        maxSentences: Int,
    ): DocumentSlice = DocumentChunker.sliceFrom(
        units = source.units,
        cursor = cursor,
        maxSentences = maxSentences,
        kind = source.kind,
    )

    /**
     * "Next N pages" or "page X to Y" — unit numbers are 1-based here because
     * that is what the user sees and types.
     */
    fun sliceUnitRange(
        source: Units,
        fromUnitNumber: Int,
        toUnitNumber: Int,
    ): DocumentSlice = DocumentChunker.sliceRange(
        units = source.units,
        fromUnitIndex = fromUnitNumber - 1,
        toUnitIndex = toUnitNumber - 1,
        kind = source.kind,
    )

    /** "Next N pages" expressed from a cursor, for the one-tap case. */
    fun sliceNextUnits(
        source: Units,
        cursor: DocumentChunker.Cursor,
        unitCount: Int,
    ): DocumentSlice {
        val from = cursor.unitIndex + 1
        val to = (cursor.unitIndex + unitCount).coerceAtMost(source.unitCount)
        return sliceUnitRange(source, from, to)
    }

    /** EPUB slicing by paragraph threshold inside one chapter (1-based inputs). */
    fun sliceParagraphs(
        source: Units,
        chapterNumber: Int,
        fromParagraphNumber: Int,
        paragraphThreshold: Int,
        maxSentences: Int,
    ): DocumentSlice {
        val chapter = source.units.getOrNull(chapterNumber - 1).orEmpty()
        return DocumentChunker.sliceEpubParagraphs(
            paragraphs = paragraphsOf(chapter),
            chapterIndex = chapterNumber - 1,
            fromParagraph = fromParagraphNumber - 1,
            paragraphThreshold = paragraphThreshold,
            maxSentences = maxSentences,
        )
    }

    /**
     * Finds an enriched sentence back in the source text.
     *
     * The model was asked to clean the text up, so the stored sentence is
     * never byte-identical to the extracted page: hyphenation was joined, OCR
     * noise was dropped, quotes were normalised. [FuzzyTextAligner] is
     * therefore the only reliable way to recover the character range, which is
     * what a highlight or a "show me this on the page" action needs.
     */
    fun locate(source: Units, sentence: BookSentence): IntRange? {
        val cursor = DocumentChunker.cursorFromLocation(sentence.location)
        val unit = source.units.getOrNull(cursor.unitIndex) ?: return null
        if (sentence.english.isBlank()) return null
        return FuzzyTextAligner.locate(sentence.english, unit)
    }

    /** The 1-based unit number a sentence belongs to, for jump-to-page. */
    fun unitNumberOf(sentence: BookSentence): Int =
        DocumentChunker.cursorFromLocation(sentence.location).unitIndex + 1
}
