package com.example.logic

import com.example.model.BookSentence
import com.example.model.SourceSentence

/**
 * Re-anchors an imported batch to the slice that was actually sent.
 *
 * The reader places a translation under a line by matching text and page, so
 * the whole feature depends on the returned `english` and `location` being the
 * document's own text and the document's own locator. Models drift: they tidy
 * punctuation, drop a quotation mark, rewrite `p. 12 s. 3` as `page 12`, or
 * shift every id by one after merging two lines. Any of those is enough to
 * push a correct translation into the unmatched bucket.
 *
 * Rather than ask the model to be perfect, this pass throws away the parts of
 * its answer that we already know for certain. The id is trusted only as a
 * hint; the sentence text, the page and the ordinal are restored from the
 * stored slice, so downstream matching becomes exact instead of fuzzy. The
 * model's actual work — translation, lesson, words, pronunciation — is kept
 * untouched.
 */
object BookLessonAligner {

    /** Below this, an id-based pairing is treated as suspect and re-matched. */
    private const val ID_TRUST_THRESHOLD = 0.55

    /** Minimum similarity for rescuing an entry whose id is wrong. */
    private const val TEXT_RESCUE_THRESHOLD = 0.62

    data class Report(
        val expected: Int,
        val received: Int,
        val alignedById: Int,
        val alignedByText: Int,
        val textRestored: Int,
        val locationRestored: Int,
        val missingIds: List<Int>,
        val unanchoredIds: List<Int>,
        val withTranslation: Int,
        val withLesson: Int,
        /**
         * Entries whose id the stored slice does not contain at all.
         *
         * This is a different failure from "the text did not match": it means
         * the answer belongs to another copy (an older chunk, or a range the
         * user copied before this one). Saying so is the whole point - a batch
         * of 41 entries where 41 are off-range is not 41 mistakes, it is one
         * mismatch between the answer and the reference.
         */
        val offRangeIds: List<Int> = emptyList(),
        /** True when the reply answered a different range than the stored slice covers. */
        val referenceMismatch: Boolean = false,
    ) {
        val aligned: Int get() = alignedById + alignedByText

        /** Clean means every sent sentence got an entry and every entry found its place. */
        val isClean: Boolean get() = missingIds.isEmpty() && unanchoredIds.isEmpty()
    }

    data class Result(val sentences: List<BookSentence>, val report: Report)

    /**
     * @param received entries as parsed from the model's JSON
     * @param sent the slice that was copied for this document, if still known
     */
    fun align(received: List<BookSentence>, sent: BookSlicePayload.SentSlice?): Result {
        if (sent == null || sent.sentences.isEmpty()) {
            // Nothing to anchor against (e.g. a JSON file imported without
            // copying a chunk first). Keep the entries, but still make the
            // ordinals self-consistent so the reader can order them, and keep
            // every locator the reply carries in the app's own format.
            val normalized = received.map { entry ->
                entry.copy(
                    start = entry.id - 1,
                    end = entry.id,
                    location = canonicalLocation(entry.location) ?: entry.location,
                )
            }
            return Result(normalized, report(expected = 0, received = received.size, normalized = normalized))
        }

        val expectedById = sent.byId()
        val used = HashSet<Int>()
        val anchored = ArrayList<BookSentence>(received.size)
        val unanchored = ArrayList<Int>()
        val offRange = ArrayList<Int>()
        var byId = 0
        var byText = 0
        var textRestored = 0
        var locationRestored = 0

        for (entry in received) {
            var id = entry.id
            var source: SourceSentence? = expectedById[id]?.takeIf { id !in used }
            var rescued = false

            if (source == null || FuzzyTextAligner.similarity(source.text, entry.english) < ID_TRUST_THRESHOLD) {
                val candidate = bestMatch(entry.english, expectedById, used)
                if (candidate != null) {
                    id = candidate.first
                    source = candidate.second
                    rescued = true
                }
            }

            if (source == null) {
                // The slice does not cover this entry, so there is no canonical
                // text to restore - but the entry is NOT thrown away. Its own
                // label (or the `page` + `sentenceInPage` the reply carries) is
                // normalised instead, so the reader can still place the line
                // under the sentence it belongs to.
                unanchored += entry.id
                if (entry.id !in expectedById) offRange += entry.id
                anchored += entry.copy(
                    start = entry.id - 1,
                    end = entry.id,
                    location = canonicalLocation(entry.location) ?: entry.location,
                )
                continue
            }

            used += id
            if (rescued) byText++ else byId++

            val canonicalText = source.text.trim()
            val canonicalLocation = DocumentChunker.locationLabel(sent.kind, source.unitIndex, source.sentenceInUnit)
            if (canonicalText.isNotBlank() && canonicalText != entry.english.trim()) textRestored++
            if (canonicalLocation.isNotBlank() && canonicalLocation != entry.location.trim()) locationRestored++

            anchored += entry.copy(
                id = id,
                start = id - 1,
                end = id,
                english = canonicalText.ifBlank { entry.english },
                location = canonicalLocation.ifBlank { entry.location },
            )
        }

        val ordered = anchored.sortedBy { it.id }
        val missing = expectedById.keys.filter { it !in used }.sorted()
        return Result(
            sentences = ordered,
            report = Report(
                expected = expectedById.size,
                received = received.size,
                alignedById = byId,
                alignedByText = byText,
                textRestored = textRestored,
                locationRestored = locationRestored,
                missingIds = missing,
                unanchoredIds = unanchored,
                withTranslation = ordered.count { !it.translation.isNullOrBlank() },
                withLesson = ordered.count { it.hasLesson },
                offRangeIds = offRange.sorted(),
                referenceMismatch = byId + byText == 0 && received.isNotEmpty() && expectedById.isNotEmpty(),
            ),
        )
    }

    private fun report(expected: Int, received: Int, normalized: List<BookSentence>) = Report(
        expected = expected,
        received = received,
        alignedById = 0,
        alignedByText = 0,
        textRestored = 0,
        locationRestored = 0,
        missingIds = emptyList(),
        unanchoredIds = emptyList(),
        withTranslation = normalized.count { !it.translation.isNullOrBlank() },
        withLesson = normalized.count { it.hasLesson },
    )

    private fun bestMatch(
        text: String,
        expectedById: Map<Int, SourceSentence>,
        used: Set<Int>,
    ): Pair<Int, SourceSentence>? {
        if (text.isBlank()) return null
        var best: Pair<Int, SourceSentence>? = null
        var bestScore = TEXT_RESCUE_THRESHOLD
        for ((id, source) in expectedById) {
            if (id in used) continue
            val score = FuzzyTextAligner.similarity(source.text, text)
            if (score > bestScore) {
                bestScore = score
                best = id to source
            }
        }
        return best
    }

    /**
     * Canonical `p. 12 s. 3` / `Ch. 4 s. 12` label built from whatever a reply
     * carries: the anchor format the prompt asks for (`p12s1`), a loosely
     * worded locator (`page 12, sentence 3`), or the separate `page` and
     * `sentenceInPage` numbers the schema also requires.
     *
     * Returns null when nothing usable can be built, in which case the caller
     * keeps the raw text untouched. The label matters because the reader
     * groups lines by the page number inside it: an entry whose label is blank
     * or written in another format never appears under any page, which is what
     * "the whole batch is broken" looks like from the outside.
     *
     * @param location the reply's own `location`, if any
     * @param page the reply's `page` integer, if any
     * @param sentenceInPage the reply's `sentenceInPage` integer, if any
     */
    fun canonicalLocation(
        location: String?,
        page: Int? = null,
        sentenceInPage: Int? = null,
    ): String? {
        val raw = location?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            // `p12s1`, `P. 12 S. 1`, `p. 12 s. 1` - one marker, both numbers.
            MARKER.find(raw)?.let { match ->
                val chapter = match.groupValues[1].equals("c", ignoreCase = true)
                val unit = match.groupValues[2].toIntOrNull() ?: return@let
                val ordinal = match.groupValues[3].toIntOrNull() ?: return@let
                return label(chapter, unit, ordinal)
            }
            val chapter = CHAPTER.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (chapter != null) {
                val ordinal = ORDINAL.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
                return label(chapter = true, unit = chapter, ordinal = ordinal)
            }
            val unitPage = PAGE.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (unitPage != null) {
                val ordinal = ORDINAL.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
                return label(chapter = false, unit = unitPage, ordinal = ordinal)
            }
        }
        if (page != null && page > 0) {
            return label(chapter = false, unit = page, ordinal = sentenceInPage)
        }
        return null
    }

    private fun label(chapter: Boolean, unit: Int, ordinal: Int?): String {
        val number = ordinal?.takeIf { it > 0 } ?: 1
        return if (chapter) "Ch. $unit s. $number" else "p. $unit s. $number"
    }

    /** `p12s1` / `c4s12`, with the separators and spacing a model may add. */
    private val MARKER = Regex("\\b([pc])\\.?\\s?(\\d{1,5})\\s?s\\.?\\s?(\\d{1,4})\\b", RegexOption.IGNORE_CASE)

    private val CHAPTER = Regex("\\b(?:ch|chap|chapter)\\.?\\s*(\\d{1,5})\\b", RegexOption.IGNORE_CASE)

    private val PAGE = Regex("\\b(?:p|page|pp)\\.?\\s*(\\d{1,5})\\b", RegexOption.IGNORE_CASE)

    private val ORDINAL = Regex("\\b(?:s|sent|sentence)\\.?\\s*(\\d{1,4})\\b", RegexOption.IGNORE_CASE)

    /** Short Persian summary for the import status line. */
    fun summaryFa(report: Report): String {
        if (report.expected == 0) {
            return "${report.received} جمله وارد شد (بدون مرجع متن کپی‌شده)؛ " +
                "${report.withTranslation} ترجمه، ${report.withLesson} درس."
        }
        val parts = mutableListOf(
            "${report.aligned} از ${report.expected} جمله دقیق جاگذاری شد",
            "${report.withTranslation} ترجمه",
            "${report.withLesson} درس جمله",
        )
        if (report.alignedByText > 0) {
            parts += "${report.alignedByText} مورد با تطبیق متن تصحیح شد"
        }
        if (report.locationRestored > 0) {
            parts += "${report.locationRestored} شمارهٔ صفحه بازسازی شد"
        }
        if (report.missingIds.isNotEmpty()) {
            val tail = if (report.missingIds.size > 8) "…" else ""
            parts += "جاافتاده: ${report.missingIds.take(8).joinToString("، ")}$tail"
        }

        // The lines that still need a human. Naming them is the point: "41
        // lines out of range" reads as "the whole batch is broken", while the
        // same report with the ids on it reads as what it is - one mismatch
        // between an answer and the range it was supposed to answer.
        val ids = report.unanchoredIds.sorted()
        val idTail = if (ids.size > 8) "…" else ""
        val shown = ids.take(8).joinToString("، ") + idTail
        when {
            report.referenceMismatch ->
                parts += "این پاسخ با محدوده‌ای که کپی شده هم‌خوان نیست (id: $shown) — " +
                    "ممکن است جواب محدودهٔ دیگری باشد؛ بازهٔ درست را دوباره کپی کن و دوباره وارد کن."

            ids.isNotEmpty() && report.offRangeIds.size == ids.size ->
                parts += "${ids.size} خط بیرون از محدوده بود (id: $shown) — بقیه وارد شد."

            ids.isNotEmpty() ->
                parts += "${ids.size} خط نسبت به محدودهٔ کپی‌شده پیدا نشد (id: $shown) — بقیه وارد شد."
        }
        return parts.joinToString(" • ")
    }
}
