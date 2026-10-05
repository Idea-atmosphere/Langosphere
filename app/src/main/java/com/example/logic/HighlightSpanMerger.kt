package com.example.logic

import com.example.model.SentenceHighlight

/**
 * Keeps a document's highlight spans in a canonical shape.
 *
 * ## Why this exists
 *
 * Re-selecting text that is already highlighted is the normal case, not the
 * exceptional one: the user drags a handle a few characters, lifts, and drags
 * again. If every gesture simply appended a span, the store would fill up with
 * duplicates and partial overlaps, the rendered background would double-darken
 * where they overlapped, and removing "the" highlight would leave orphan
 * fragments behind.
 *
 * So overlap is resolved once, here, before anything is persisted or drawn:
 *
 * - Same colour, touching or overlapping → merged into one span.
 * - Different colour, overlapping → the new span wins, and the old one is
 *   trimmed to whatever is left outside it (dropped if nothing is).
 * - The result is always clamped to the text, non-overlapping, and sorted by
 *   sentence then offset.
 *
 * All of it is pure Kotlin on character offsets, so it is unit-testable and
 * independent of fonts, layout and screen size.
 */
object HighlightSpanMerger {

    /** Adds a span, resolving any overlap it creates. */
    fun add(
        existing: List<SentenceHighlight>,
        added: SentenceHighlight,
        textLength: Int,
    ): List<SentenceHighlight> {
        val incoming = added.clampedTo(textLength)?.takeIf { it.isValid }
            ?: return sorted(valid(existing, textLength))

        val untouched = existing.filter { it.sentenceId != incoming.sentenceId }
        val sameSentence = valid(
            existing.filter { it.sentenceId == incoming.sentenceId },
            textLength,
        )

        var merged = incoming
        val kept = ArrayList<SentenceHighlight>()

        for (span in sameSentence) {
            val disjoint = span.charEnd < merged.charStart || span.charStart > merged.charEnd
            if (disjoint) {
                kept += span
                continue
            }
            if (span.colorArgb == merged.colorArgb) {
                merged = merged.copy(
                    charStart = minOf(span.charStart, merged.charStart),
                    charEnd = maxOf(span.charEnd, merged.charEnd),
                    // The earliest creation time wins, so merging does not make
                    // an old highlight look newly added.
                    createdAt = minOf(span.createdAt, merged.createdAt),
                    note = merged.note.ifBlank { span.note },
                )
            } else {
                if (span.charStart < merged.charStart) {
                    kept += span.copy(charEnd = merged.charStart)
                }
                if (span.charEnd > merged.charEnd) {
                    kept += span.copy(charStart = merged.charEnd)
                }
            }
        }

        kept += merged
        return sorted(untouched + kept.filter { it.isValid })
    }

    /** Re-runs the overlap rules over a whole set, e.g. after loading from storage. */
    fun normalize(
        highlights: List<SentenceHighlight>,
        textLengthOf: (Int) -> Int,
    ): List<SentenceHighlight> {
        var result = emptyList<SentenceHighlight>()
        for (span in highlights.sortedBy { it.createdAt }) {
            result = add(result, span, textLengthOf(span.sentenceId))
        }
        return result
    }

    /** The span covering a tapped character offset, if any. */
    fun findAt(
        highlights: List<SentenceHighlight>,
        sentenceId: Int,
        offset: Int,
    ): SentenceHighlight? = highlights.firstOrNull { span ->
        span.sentenceId == sentenceId && offset >= span.charStart && offset < span.charEnd
    }

    /** Removes one span by identity (sentence plus offsets), leaving no fragments. */
    fun remove(
        highlights: List<SentenceHighlight>,
        target: SentenceHighlight,
    ): List<SentenceHighlight> = highlights.filterNot { span ->
        span.sentenceId == target.sentenceId &&
            span.charStart == target.charStart &&
            span.charEnd == target.charEnd
    }

    /** True when the span set contains no overlaps — the invariant [add] maintains. */
    fun isCanonical(highlights: List<SentenceHighlight>): Boolean {
        val bySentence = highlights.groupBy { it.sentenceId }
        for ((_, spans) in bySentence) {
            val ordered = spans.sortedBy { it.charStart }
            for (index in 1 until ordered.size) {
                if (ordered[index].charStart < ordered[index - 1].charEnd) return false
            }
        }
        return highlights.all { it.isValid }
    }

    private fun valid(
        highlights: List<SentenceHighlight>,
        textLength: Int,
    ): List<SentenceHighlight> = highlights
        .mapNotNull { span -> span.clampedTo(textLength) }
        .filter { span -> span.isValid }

    private fun sorted(highlights: List<SentenceHighlight>): List<SentenceHighlight> =
        highlights.sortedWith(compareBy({ it.sentenceId }, { it.charStart }))
}
