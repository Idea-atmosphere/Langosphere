package com.example.logic

import com.example.model.SentenceHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightSpanMergerTest {

    private val yellow = 0x33FFEB3B
    private val green = 0x334CAF50

    private fun span(
        start: Int,
        end: Int,
        color: Int = yellow,
        sentenceId: Int = 1,
        createdAt: Long = 1_000L,
    ) = SentenceHighlight(
        sentenceId = sentenceId,
        charStart = start,
        charEnd = end,
        colorArgb = color,
        createdAt = createdAt,
    )

    @Test
    fun `same colour overlap merges into one span`() {
        val result = HighlightSpanMerger.add(listOf(span(0, 10)), span(5, 20), 40)
        assertEquals(1, result.size)
        assertEquals(0, result.first().charStart)
        assertEquals(20, result.first().charEnd)
        assertTrue(HighlightSpanMerger.isCanonical(result))
    }

    @Test
    fun `adjacent same colour spans merge`() {
        val result = HighlightSpanMerger.add(listOf(span(0, 5)), span(5, 9), 40)
        assertEquals(1, result.size)
        assertEquals(0..9, result.first().charStart..result.first().charEnd)
    }

    @Test
    fun `merging keeps the earliest creation time`() {
        val result = HighlightSpanMerger.add(
            listOf(span(0, 10, createdAt = 500L)),
            span(5, 20, createdAt = 9_000L),
            40,
        )
        assertEquals(500L, result.first().createdAt)
    }

    @Test
    fun `re-selecting the same range does not duplicate`() {
        var result = HighlightSpanMerger.add(emptyList(), span(3, 12), 40)
        result = HighlightSpanMerger.add(result, span(3, 12), 40)
        result = HighlightSpanMerger.add(result, span(3, 12), 40)
        assertEquals(1, result.size)
    }

    @Test
    fun `different colour overlap trims the older span`() {
        val result = HighlightSpanMerger.add(listOf(span(0, 20)), span(5, 10, color = green), 40)
        assertEquals(3, result.size)
        assertTrue(HighlightSpanMerger.isCanonical(result))
        assertEquals(listOf(0, 5, 10), result.map { it.charStart })
        assertEquals(green, result[1].colorArgb)
    }

    @Test
    fun `a fully covered older span disappears instead of orphaning`() {
        val result = HighlightSpanMerger.add(listOf(span(5, 10)), span(0, 20, color = green), 40)
        assertEquals(1, result.size)
        assertEquals(green, result.first().colorArgb)
    }

    @Test
    fun `spans from other sentences are left alone`() {
        val other = span(0, 5, sentenceId = 2)
        val result = HighlightSpanMerger.add(listOf(other), span(0, 5, sentenceId = 1), 40)
        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), result.map { it.sentenceId })
    }

    @Test
    fun `spans are clamped to the text and invalid ones dropped`() {
        val result = HighlightSpanMerger.add(emptyList(), span(30, 500), 40)
        assertEquals(1, result.size)
        assertEquals(40, result.first().charEnd)

        val empty = HighlightSpanMerger.add(emptyList(), span(80, 90), 40)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `find resolves a tapped offset and respects the exclusive end`() {
        val spans = listOf(span(4, 9))
        assertNotNull(HighlightSpanMerger.findAt(spans, 1, 4))
        assertNotNull(HighlightSpanMerger.findAt(spans, 1, 8))
        assertNull(HighlightSpanMerger.findAt(spans, 1, 9))
        assertNull(HighlightSpanMerger.findAt(spans, 1, 3))
        assertNull(HighlightSpanMerger.findAt(spans, 2, 5))
    }

    @Test
    fun `remove deletes exactly one span`() {
        val spans = listOf(span(0, 5), span(10, 15))
        val result = HighlightSpanMerger.remove(spans, spans.first())
        assertEquals(1, result.size)
        assertEquals(10, result.first().charStart)
    }

    @Test
    fun `normalize repairs a legacy overlapping set`() {
        val messy = listOf(
            span(0, 10, createdAt = 1L),
            span(4, 14, createdAt = 2L),
            span(12, 20, createdAt = 3L),
            span(0, 10, createdAt = 4L),
        )
        val result = HighlightSpanMerger.normalize(messy) { 40 }
        assertTrue(HighlightSpanMerger.isCanonical(result))
        assertEquals(1, result.size)
        assertEquals(0, result.first().charStart)
        assertEquals(20, result.first().charEnd)
    }

    @Test
    fun `isCanonical rejects an overlapping set`() {
        assertFalse(HighlightSpanMerger.isCanonical(listOf(span(0, 10), span(5, 12))))
    }
}
