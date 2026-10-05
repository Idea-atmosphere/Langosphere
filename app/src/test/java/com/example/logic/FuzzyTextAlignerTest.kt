package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTextAlignerTest {

    @Test
    fun `identical strings score one`() {
        assertEquals(1.0, FuzzyTextAligner.similarity("same text", "same text"), 0.0001)
    }

    @Test
    fun `similarity survives ocr noise`() {
        val score = FuzzyTextAligner.similarity(
            "The quick brown fox jumps over the lazy dog",
            "The quick brovvn fox jumps over the 1azy dog",
        )
        assertTrue(score.toString(), score > FuzzyTextAligner.DEFAULT_THRESHOLD)
    }

    @Test
    fun `unrelated strings score low`() {
        val score = FuzzyTextAligner.similarity(
            "A treatise on marine biology",
            "Interest rates rose again",
        )
        assertTrue(score.toString(), score < FuzzyTextAligner.DEFAULT_THRESHOLD)
    }

    @Test
    fun `locates a cleaned sentence inside hyphenated page text`() {
        val page = "chapter opening\nHe could not under-\nstand the map at all.\nnext line"
        val range = FuzzyTextAligner.locate(
            "He could not understand the map at all.",
            page,
            FuzzyTextAligner.DEFAULT_THRESHOLD,
        )
        assertNotNull(range)
        val matched = page.substring(range!!.first, range.last + 1)
        assertTrue(matched, matched.contains("under"))
        assertTrue(matched, matched.contains("map"))
    }

    @Test
    fun `locate returns null when the sentence is absent`() {
        val range = FuzzyTextAligner.locate(
            "A sentence that never appeared in the source document at all.",
            "Completely different page content about shipping schedules.",
            FuzzyTextAligner.DEFAULT_THRESHOLD,
        )
        assertNull(range)
    }

    @Test
    fun `best match picks the closest candidate`() {
        val candidates = listOf(
            "The dog barked loudly",
            "The cat sat on the mat",
            "Interest rates rose",
        )
        val match = FuzzyTextAligner.bestMatch(
            "The cat sat on the  mat",
            candidates,
            FuzzyTextAligner.DEFAULT_THRESHOLD,
        )
        assertEquals(1, match.index)
    }
}
