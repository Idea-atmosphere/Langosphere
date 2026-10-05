package com.example.logic

import com.example.model.DocumentSlice
import com.example.model.SliceKind
import com.example.model.SourceSentence

/**
 * Cuts a document into sentence slices that are safe to send to an AI.
 *
 * The hard requirement is that a sentence must never cross a page boundary.
 * Each unit in a requested range is therefore segmented on its own: when page
 * N ends, its sentence buffer is force-closed and flushed, and page N+1
 * starts a brand-new buffer. Joining pages into one stream first used to glue
 * the end of one page onto the start of the next (a title-page signature plus
 * `THIRD EDITION` plus the next chapter's opening line became a single giant
 * "sentence"), which cost the following page its real first sentence and
 * misaligned every translation after it.
 *
 * The accepted trade-off is that a page ending mid-sentence yields an edge
 * fragment on each side instead of one stitched sentence. Fragments stay
 * anchored to their own page (`p12s7` never carries page-13 words), so the
 * drift guard, the id continuity and the highlight anchors all keep working -
 * on the page each fragment truly belongs to.
 *
 * Pure Kotlin: no Context, no Uri. Android-facing extraction lives in
 * [BookDocumentSource], which keeps this file unit-testable.
 */
object DocumentChunker {

    /** Where reading resumes: the next sentence to be sent. */
    data class Cursor(
        /** Zero-based page or chapter index. */
        val unitIndex: Int,
        /** One-based ordinal of the next sentence inside that unit. */
        val sentenceInUnit: Int,
    ) {
        companion object {
            val START = Cursor(unitIndex = 0, sentenceInUnit = 1)
        }
    }

    /** Units scanned in one `sliceFrom` call before giving up on filling it. */
    private const val DEFAULT_MAX_UNITS_TO_SCAN = 12

    /**
     * Segments the units in an inclusive range into sentences, one unit at a
     * time. The page boundary is hard: a sentence can never span two units,
     * blank units contribute nothing, and every unit numbers its own
     * sentences from 1 - so `p3s1` is always the first sentence *of page 3*.
     * Offsets are relative to the owning unit's own text, exactly what a
     * highlight needs, with no cross-unit clamping.
     *
     * @param startIndex drops this many leading sentences, used to resume in
     *   the middle of a page without re-sending what was already enriched.
     */
    fun sentencesIn(
        units: List<String>,
        fromUnitIndex: Int,
        toUnitIndex: Int,
        kind: SliceKind,
        startIndex: Int = 0,
    ): List<SourceSentence> {
        if (units.isEmpty()) return emptyList()
        val from = fromUnitIndex.coerceIn(0, units.lastIndex)
        val to = toUnitIndex.coerceIn(from, units.lastIndex)

        val result = ArrayList<SourceSentence>()
        for (unitIndex in from..to) {
            val text = units[unitIndex]
            if (text.isBlank()) continue
            val spans = SentenceSegmenter.splitSentences(text)
            spans.forEachIndexed { ordinal0, span ->
                result += SourceSentence(
                    index = result.size,
                    text = span.text,
                    location = locationLabel(kind, unitIndex, ordinal0 + 1),
                    unitIndex = unitIndex,
                    sentenceInUnit = ordinal0 + 1,
                    charStart = span.startOffset,
                    charEnd = span.endOffset,
                )
            }
        }
        if (result.isEmpty()) return emptyList()

        val dropped = if (startIndex > 0) result.drop(startIndex) else result
        return dropped.mapIndexed { index, sentence -> sentence.copy(index = index) }
    }

    /**
     * The "next N sentences" slice, starting exactly at [cursor].
     *
     * Scanning several units ahead is deliberate: a request for 20 sentences
     * from the last line of a page has to reach into the following pages to be
     * filled, and a sparse page (an illustration plate, a chapter title page)
     * must not end the slice early.
     */
    fun sliceFrom(
        units: List<String>,
        cursor: Cursor,
        maxSentences: Int,
        kind: SliceKind,
        maxUnitsToScan: Int = DEFAULT_MAX_UNITS_TO_SCAN,
    ): DocumentSlice {
        if (units.isEmpty() || maxSentences <= 0) return emptySlice(kind)
        val startUnit = cursor.unitIndex.coerceIn(0, units.lastIndex)
        val lastScanned = (startUnit + maxUnitsToScan - 1).coerceAtMost(units.lastIndex)

        val candidates = sentencesIn(units, startUnit, lastScanned, kind)
            .filterNot { it.unitIndex == startUnit && it.sentenceInUnit < cursor.sentenceInUnit.coerceAtLeast(1) }

        if (candidates.isEmpty()) {
            // Nothing left in the scanned window; if the document continues,
            // skip ahead so an empty run of pages cannot stall the reader.
            return if (lastScanned < units.lastIndex) {
                sliceFrom(units, Cursor(lastScanned + 1, 1), maxSentences, kind, maxUnitsToScan)
            } else {
                emptySlice(kind)
            }
        }

        val taken = candidates.take(maxSentences).mapIndexed { index, sentence ->
            sentence.copy(index = index)
        }
        val reachedEnd = taken.size == candidates.size && lastScanned == units.lastIndex

        return DocumentSlice(
            kind = kind,
            sentences = taken,
            firstUnitIndex = taken.first().unitIndex,
            lastUnitIndex = taken.last().unitIndex,
            locationHeader = locationHeader(
                kind = kind,
                firstUnit = taken.first().unitIndex,
                lastUnit = taken.last().unitIndex,
                firstSentence = taken.first().sentenceInUnit,
            ),
            isLastSlice = reachedEnd,
        )
    }

    /**
     * An explicit unit range: "page X to Y" for PDFs, a spine/chapter range for
     * EPUBs. Every sentence in the range is returned, since the user asked for
     * that range specifically.
     */
    fun sliceRange(
        units: List<String>,
        fromUnitIndex: Int,
        toUnitIndex: Int,
        kind: SliceKind,
    ): DocumentSlice {
        if (units.isEmpty()) return emptySlice(kind)
        val from = fromUnitIndex.coerceIn(0, units.lastIndex)
        val to = toUnitIndex.coerceIn(from, units.lastIndex)
        val sentences = sentencesIn(units, from, to, kind)
        if (sentences.isEmpty()) return emptySlice(kind)

        return DocumentSlice(
            kind = kind,
            sentences = sentences,
            firstUnitIndex = sentences.first().unitIndex,
            lastUnitIndex = sentences.last().unitIndex,
            locationHeader = locationHeader(
                kind = kind,
                firstUnit = from,
                lastUnit = to,
                firstSentence = sentences.first().sentenceInUnit,
            ),
            isLastSlice = to >= units.lastIndex && isLastSentenceOfUnit(units, sentences.last()),
        )
    }

    /**
     * EPUB slicing by logical paragraph threshold inside one chapter, for books
     * whose chapters are far too long to send whole.
     *
     * The ordinals of preceding paragraphs are counted first so sentence
     * numbering stays chapter-relative and continues correctly across calls.
     */
    fun sliceEpubParagraphs(
        paragraphs: List<String>,
        chapterIndex: Int,
        fromParagraph: Int,
        paragraphThreshold: Int,
        maxSentences: Int,
    ): DocumentSlice {
        if (paragraphs.isEmpty() || paragraphThreshold <= 0 || maxSentences <= 0) {
            return emptySlice(SliceKind.EPUB_PARAGRAPHS)
        }
        val start = fromParagraph.coerceIn(0, paragraphs.lastIndex)
        val end = (start + paragraphThreshold - 1).coerceAtMost(paragraphs.lastIndex)

        val precedingOrdinal = if (start == 0) {
            0
        } else {
            SentenceSegmenter.sentences(paragraphs.take(start).joinToString("\n\n")).size
        }

        val body = paragraphs.subList(start, end + 1).joinToString("\n\n")
        val spans = SentenceSegmenter.splitSentences(body)
        if (spans.isEmpty()) return emptySlice(SliceKind.EPUB_PARAGRAPHS)

        val sentences = spans.take(maxSentences).mapIndexed { index, span ->
            val ordinal = precedingOrdinal + index + 1
            SourceSentence(
                index = index,
                text = span.text,
                location = locationLabel(SliceKind.EPUB_PARAGRAPHS, chapterIndex, ordinal),
                unitIndex = chapterIndex,
                sentenceInUnit = ordinal,
                charStart = span.startOffset,
                charEnd = span.endOffset,
            )
        }

        val header = "LOCATION: chapter ${chapterIndex + 1}, paragraphs ${start + 1}-${end + 1}"
        return DocumentSlice(
            kind = SliceKind.EPUB_PARAGRAPHS,
            sentences = sentences,
            firstUnitIndex = chapterIndex,
            lastUnitIndex = chapterIndex,
            locationHeader = header,
            isLastSlice = end >= paragraphs.lastIndex && sentences.size == spans.size,
        )
    }

    /**
     * Parses a `location` string back into the cursor for the NEXT sentence.
     *
     * This is the hinge of auto-continuation: the last enriched sentence says
     * `p. 128 s. 7`, so reading resumes at page 128, sentence 8 — without
     * needing any state that could be lost when the app is killed.
     *
     * Accepts the forms this app emits (`p. 128 s. 7`, `Ch. 4 s. 12`) and the
     * looser forms a model sometimes returns (`page 128`, `chapter 4`).
     */
    fun cursorFromLocation(location: String, fallback: Cursor = Cursor.START): Cursor {
        if (location.isBlank()) return fallback
        val text = location.lowercase()

        val unitMatch = Regex("(?:p|page|pp|ch|chap|chapter)\\.?\\s*(\\d+)").find(text)
        val sentenceMatch = Regex("(?:s|sent|sentence)\\.?\\s*(\\d+)").find(text)

        val unitNumber = unitMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val sentenceNumber = sentenceMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (unitNumber == null && sentenceNumber == null) {
            // Bare number, e.g. "128".
            val bare = Regex("(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return fallback
            return Cursor(unitIndex = (bare - 1).coerceAtLeast(0), sentenceInUnit = 1)
        }

        val unitIndex = ((unitNumber ?: 1) - 1).coerceAtLeast(0)
        val next = (sentenceNumber ?: 0) + 1
        return Cursor(unitIndex = unitIndex, sentenceInUnit = next.coerceAtLeast(1))
    }

    /** The `LOCATION:` line for a slice. */
    fun locationHeader(
        kind: SliceKind,
        firstUnit: Int,
        lastUnit: Int,
        firstSentence: Int,
    ): String {
        val first = firstUnit + 1
        val last = lastUnit + 1
        val body = when (kind) {
            SliceKind.PDF_PAGES ->
                if (first == last) "page $first" else "pages $first-$last"
            SliceKind.EPUB_SPINE, SliceKind.EPUB_PARAGRAPHS ->
                if (first == last) "chapter $first" else "chapters $first-$last"
        }
        // A slice that resumes mid-page must say so, otherwise the model
        // renumbers sentences from 1 and the locations it returns are wrong.
        val suffix = if (firstSentence > 1) ", from sentence $firstSentence" else ""
        return "LOCATION: $body$suffix"
    }

    /** Per-sentence label stored on each entry, e.g. `p. 128 s. 1`. */
    fun locationLabel(kind: SliceKind, unitIndex: Int, sentenceInUnit: Int): String =
        when (kind) {
            SliceKind.PDF_PAGES -> "p. ${unitIndex + 1} s. $sentenceInUnit"
            SliceKind.EPUB_SPINE, SliceKind.EPUB_PARAGRAPHS -> "Ch. ${unitIndex + 1} s. $sentenceInUnit"
        }

    private fun emptySlice(kind: SliceKind) = DocumentSlice(
        kind = kind,
        sentences = emptyList(),
        firstUnitIndex = 0,
        lastUnitIndex = 0,
        locationHeader = "",
        isLastSlice = true,
    )

    private fun isLastSentenceOfUnit(units: List<String>, sentence: SourceSentence): Boolean {
        val unit = units.getOrNull(sentence.unitIndex) ?: return true
        return sentence.charEnd >= unit.trimEnd().length
    }
}
