package com.example.logic

import android.content.Context
import com.example.model.DocumentSlice

/**
 * The "copy the next five pages for the AI" button.
 *
 * ## Why this exists next to the extraction dialog
 *
 * [com.example.ui.components.BookChunkExporterPanel] is the full control room:
 * it can slice by sentence count, by page count, by an explicit page range or
 * by paragraphs, preview the payload and save it to a file. That is the right
 * tool for setting a book up, and the wrong one for the reading loop a learner
 * is actually in: read five pages, paste the answer, read five more.
 *
 * This object is that loop, one tap wide. It remembers how far the document
 * has been processed and produces the next page window as a payload the app's
 * own importer understands - the exact `@LANGO v3` header plus `[id|pXsY]`
 * markers [BookSlicePayload] builds everywhere else, so the JSON that comes
 * back re-anchors to the right pages with no special casing.
 *
 * ## What counts as "processed"
 *
 * Two things, and the later one wins:
 *  1. the page the reader has already handed to the AI (stored here, so the
 *     button never repeats itself), and
 *  2. the page the last imported answer reached (the document checkpoint),
 *     which is the real measure of progress.
 *
 * Taking the maximum means importing an answer and immediately pressing the
 * button continues after the imported pages rather than after the copied ones.
 */
object BookBatchCopier {

    /** How many pages one tap hands over. Five keeps a reply inside one answer. */
    const val DEFAULT_PAGES = 5

    private const val PREFS = "book_batch_copy"
    private const val KEY_PREFIX = "last_page:"

    /** One prepared batch: the text to copy plus what it covers. */
    data class Batch(
        val text: String,
        val firstPage: Int,
        val lastPage: Int,
        val firstId: Int,
        val lastId: Int,
        val sentenceCount: Int,
        /** True when this window reaches the end of the document. */
        val isLast: Boolean,
    ) {
        val idRange: IntRange get() = firstId..lastId
        val pageRange: IntRange get() = firstPage..lastPage
    }

    // ── memory ──

    /** The last page already copied for this document, or null when none was. */
    fun lastPage(context: Context, docKey: String): Int? {
        if (docKey.isBlank()) return null
        val stored = prefs(context).getInt(KEY_PREFIX + docKey, 0)
        return stored.takeIf { it > 0 }
    }

    fun rememberPage(context: Context, docKey: String, page: Int) {
        if (docKey.isBlank() || page <= 0) return
        prefs(context).edit().putInt(KEY_PREFIX + docKey, page).apply()
    }

    fun clear(context: Context, docKey: String) {
        if (docKey.isBlank()) return
        prefs(context).edit().remove(KEY_PREFIX + docKey).apply()
    }

    /** The page the next tap starts from, given everything the app knows. */
    fun nextStartPage(copiedPage: Int?, checkpointPage: Int?): Int =
        (maxOf(copiedPage ?: 0, checkpointPage ?: 0) + 1).coerceAtLeast(1)

    /**
     * The page (PDF) or chapter (EPUB) a stored `location` points at.
     *
     * `location` is the reader's own label - `p. 12 s. 3` or `Ch. 4 s. 12` -
     * and it is the only page number the imported answer carries, so it is
     * what "the AI already reached this page" is read from. Null when the
     * label has no number in it, which simply leaves the copy checkpoint in
     * charge.
     */
    fun unitOfLocation(location: String?): Int? {
        if (location.isNullOrBlank()) return null
        val match = LOCATION_NUMBER.find(location) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    /** `p. 12 s. 3` / `Ch. 4 s. 12` - the number after the page/chapter word. */
    private val LOCATION_NUMBER = Regex("(?:p\\.|page|Ch\\.|chapter)\\s*(\\d+)", RegexOption.IGNORE_CASE)

    /**
     * The window [pageCount] pages wide that starts at [startPage], or null
     * when the document has no pages left to offer.
     *
     * Pure arithmetic, so the label above the button can show the same window
     * it will produce without building the payload first.
     */
    fun nextRange(unitCount: Int, startPage: Int, pageCount: Int = DEFAULT_PAGES): IntRange? {
        if (unitCount <= 0) return null
        val from = startPage.coerceIn(1, unitCount + 1)
        if (from > unitCount) return null
        val to = (from + pageCount.coerceAtLeast(1) - 1).coerceAtMost(unitCount)
        return from..to
    }

    /**
     * Builds the payload for the next window and records it, so the document's
     * slice and the clipboard agree with what the reader will import next.
     *
     * Returns null when there is nothing left, when the window holds no text,
     * or when no document key is known yet (a copy that cannot be re-anchored
     * on import would be worse than no copy at all).
     */
    fun buildNext(
        context: Context,
        docKey: String,
        source: BookDocumentSource.Units,
        startPage: Int,
        pageCount: Int = DEFAULT_PAGES,
        firstId: Int,
        bookTitle: String = "",
        sourceLanguage: String = "",
        targetLanguage: String = "",
        level: String = "",
    ): Batch? {
        if (docKey.isBlank() || source.isEmpty) return null
        val range = nextRange(source.unitCount, startPage, pageCount) ?: return null
        val slice: DocumentSlice = BookDocumentSource.sliceUnitRange(source, range.first, range.last)
        if (slice.isEmpty) return null

        val text = BookSlicePayload.build(
            slice = slice,
            firstId = firstId,
            bookTitle = bookTitle,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            level = level,
        )
        if (text.isBlank()) return null

        // Persist the slice under the same key the importer reads, so the
        // answer to this copy lands on the right pages automatically.
        BookSlicePayload.save(context, docKey, slice, firstId)
        rememberPage(context, docKey, range.last)

        return Batch(
            text = text,
            firstPage = range.first,
            lastPage = range.last,
            firstId = firstId,
            lastId = firstId + slice.sentenceCount - 1,
            sentenceCount = slice.sentenceCount,
            isLast = range.last >= source.unitCount,
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
