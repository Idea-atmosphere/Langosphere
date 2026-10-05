package com.example.model

/**
 * Data model for the sentence-level book pipeline.
 *
 * The book reader and the video reader share the `subtitles[]` JSON envelope,
 * so they share [JsonLesson] and [JsonWord] from `JsonSubtitleModels.kt`.
 * Everything that is specific to reading a document lives here, because a book
 * needs concepts a subtitle track never does: a page/chapter locator instead of
 * a timestamp, a checkpoint to resume from, a drift report for the batch that
 * was just ingested, and highlights anchored to character offsets.
 */

private val LOCATION_UNIT = Regex("(?:p|page|ch|chap|chapter)\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val LOCATION_SENTENCE = Regex("(?:s|sent|sentence)\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)

/**
 * One enriched sentence: the unit the reader renders, the AI returns, and the
 * store persists.
 *
 * [start] and [end] keep the subtitle envelope valid. For a book they are
 * sentence ordinals rather than seconds, which keeps every entry in document
 * order with no special-casing.
 */
data class BookSentence(
    val id: Int,
    val start: Int,
    val end: Int,
    /** Human-readable source position, e.g. `p. 128 s. 1` or `Ch. 4 s. 12`. */
    val location: String = "",
    val english: String,
    val translation: String? = null,
    val level: String? = null,
    val difficulty: String? = null,
    /** IPA transcription; the prompts forbid ad-hoc respellings. */
    val pronunciation: String? = null,
    val notes: String? = null,
    val lesson: JsonLesson? = null,
    val words: List<JsonWord> = emptyList(),
    /** Present only for the simplify/graded-reader prompt mode. */
    val simplified: String? = null,
) {
    val hasLesson: Boolean
        get() = lesson?.let {
            !it.explanation.isNullOrBlank() || !it.grammar.isNullOrBlank() ||
                !it.grammarTranslation.isNullOrBlank() || !it.structure.isNullOrBlank()
        } == true

    /**
     * True when the sentence drawer has anything at all to show.
     *
     * This deliberately includes a bare translation. The lesson button used to
     * require a `lesson` object or a word list, so a translation-only import
     * (the lightest and most common prompt mode) produced sentences whose
     * button never appeared — which read as "the sentence lesson is broken".
     * The drawer itself always has the original, the translation, the level and
     * the location to show, so anything non-empty is enough.
     */
    val hasStudyMaterial: Boolean
        get() = hasLesson || words.isNotEmpty() || !notes.isNullOrBlank() ||
            !translation.isNullOrBlank() || !pronunciation.isNullOrBlank() ||
            !simplified.isNullOrBlank()

    /** One-based page or chapter number parsed out of [location], when present. */
    val unitNumber: Int?
        get() = LOCATION_UNIT.find(location)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /** One-based sentence ordinal inside that page or chapter. */
    val sentenceInUnit: Int?
        get() = LOCATION_SENTENCE.find(location)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /** Short label for lists and the lesson drawer, e.g. `#42 - p. 12 s. 3`. */
    val displayLabel: String
        get() = if (location.isBlank()) "#$id" else "#$id - $location"
}

/**
 * A sentence as it was extracted from the document, before the AI saw it.
 *
 * These are kept for a round trip so the drift guard can compare what was sent
 * with what came back, and so a returned entry can be re-anchored to the exact
 * page, ordinal and character range it came from.
 */
data class SourceSentence(
    /** Zero-based position within the slice that was sent. */
    val index: Int,
    val text: String,
    val location: String,
    /** PDF page index or EPUB chapter index, zero-based. */
    val unitIndex: Int,
    /** One-based ordinal of this sentence within its page/chapter. */
    val sentenceInUnit: Int,
    /** Character offsets inside the cleaned text of [unitIndex]. */
    val charStart: Int,
    val charEnd: Int,
)

/** How a slice was addressed, which decides how its location header reads. */
enum class SliceKind {
    PDF_PAGES,
    EPUB_SPINE,
    EPUB_PARAGRAPHS,
}

/**
 * A contiguous run of source sentences ready to be wrapped in a prompt.
 *
 * A slice records the units it spans: a request for "the next 20 sentences"
 * routinely crosses a page boundary, and the header has to say `pages 128-129`
 * rather than pretend the text came from one page.
 */
data class DocumentSlice(
    val kind: SliceKind,
    val sentences: List<SourceSentence>,
    val firstUnitIndex: Int,
    val lastUnitIndex: Int,
    /** Pre-rendered `LOCATION: ...` line for this slice. */
    val locationHeader: String,
    /** True when the document has no further sentences after this slice. */
    val isLastSlice: Boolean,
) {
    val sentenceCount: Int get() = sentences.size

    val isEmpty: Boolean get() = sentences.isEmpty()

    /** The slice as a numbered list, which is what the prompt sends. */
    fun numberedText(startId: Int = 1): String =
        sentences.mapIndexed { offset, sentence -> "${startId + offset}. ${sentence.text}" }
            .joinToString("\n")

    /** The slice as running text, for preview surfaces. */
    val plainText: String get() = sentences.joinToString(" ") { it.text }
}

/**
 * The resume point of a document, derived from ingested JSON rather than from
 * in-memory reading state, so continuation survives an app restart.
 */
data class BookCheckpoint(
    val lastId: Int,
    val lastStart: Int,
    val lastLocation: String,
    /** One-based page or chapter number parsed out of [lastLocation]. */
    val lastUnitNumber: Int? = null,
    /** One-based sentence ordinal within that page or chapter. */
    val lastSentenceInUnit: Int? = null,
) {
    val isEmpty: Boolean get() = lastId <= 0 && lastLocation.isBlank()

    /** The id the next batch must start at, so ids stay continuous. */
    val nextId: Int get() = if (lastId <= 0) 1 else lastId + 1

    companion object {
        val EMPTY = BookCheckpoint(lastId = 0, lastStart = -1, lastLocation = "")
    }
}

/** One returned sentence that does not match the sentence sent under its id. */
data class SentenceMismatch(
    val id: Int,
    val sent: String,
    val received: String,
    /** 0.0 to 1.0, from FuzzyTextAligner. */
    val similarity: Double,
)

/**
 * The result of checking a batch against what was actually sent.
 *
 * Counting alone would miss the common failure: a model that merges two
 * sentences and splits another returns the correct total. So gaps, repeats,
 * extras and per-id text mismatches are all reported, together with
 * [firstDriftId] so the UI can point at where the batch stopped being
 * trustworthy.
 */
data class DriftReport(
    val expectedCount: Int,
    val receivedCount: Int,
    val missingIds: List<Int> = emptyList(),
    val duplicatedIds: List<Int> = emptyList(),
    val unexpectedIds: List<Int> = emptyList(),
    val mismatches: List<SentenceMismatch> = emptyList(),
    val firstDriftId: Int? = null,
) {
    val isClean: Boolean
        get() = missingIds.isEmpty() && duplicatedIds.isEmpty() &&
            unexpectedIds.isEmpty() && mismatches.isEmpty() &&
            (expectedCount == 0 || expectedCount == receivedCount)

    /** Received minus expected: negative means the model skipped or merged. */
    val delta: Int get() = receivedCount - expectedCount

    companion object {
        val CLEAN = DriftReport(expectedCount = 0, receivedCount = 0)
    }
}

/**
 * A highlight anchored to a sentence id and character offsets, so no layout
 * change can move it.
 */
data class SentenceHighlight(
    val sentenceId: Int,
    val charStart: Int,
    val charEnd: Int,
    val colorArgb: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isValid: Boolean get() = sentenceId > 0 && charStart >= 0 && charEnd > charStart

    /**
     * Clamps the span to a text length. Re-ingesting a chunk in another mode
     * can shorten a sentence, and an out-of-range span would crash layout.
     */
    fun clampedTo(length: Int): SentenceHighlight {
        if (length <= 0) return copy(charStart = 0, charEnd = 0)
        val start = charStart.coerceIn(0, length)
        val end = charEnd.coerceIn(start, length)
        return copy(charStart = start, charEnd = end)
    }
}

/**
 * The full persisted state of one document: everything needed to reopen a book
 * exactly where the user left it, including the batches already enriched.
 */
data class BookSession(
    /** Matches BookReaderState's `docKey`. */
    val docKey: String,
    val docName: String,
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val level: String = "",
    val sentences: List<BookSentence> = emptyList(),
    val checkpoint: BookCheckpoint = BookCheckpoint.EMPTY,
    val highlights: List<SentenceHighlight> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val sentenceCount: Int get() = sentences.size

    /** Highlights grouped for the reader, which looks them up per sentence. */
    fun highlightsBySentence(): Map<Int, List<SentenceHighlight>> =
        highlights.groupBy { it.sentenceId }
}
