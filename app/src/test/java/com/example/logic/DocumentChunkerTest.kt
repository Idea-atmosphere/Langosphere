package com.example.logic

import com.example.model.SliceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentChunkerTest {

    private val pages = listOf(
        "Page one first sentence. Page one second sentence. Page one third sentence.",
        "Page two first sentence. Page two second sentence.",
        "Page three only sentence.",
    )

    @Test
    fun `slice range covers exactly the requested pages`() {
        val slice = DocumentChunker.sliceRange(pages, 0, 1, SliceKind.PDF_PAGES)
        assertEquals(5, slice.sentenceCount)
        assertEquals(0, slice.firstUnitIndex)
        assertEquals(1, slice.lastUnitIndex)
        assertTrue(slice.locationHeader, slice.locationHeader.contains("1"))
        assertFalse(slice.isEmpty)
    }

    @Test
    fun `sentence locations are one based and per unit`() {
        val slice = DocumentChunker.sliceRange(pages, 0, 0, SliceKind.PDF_PAGES)
        assertEquals("p. 1 s. 1", slice.sentences.first().location)
        assertEquals("p. 1 s. 3", slice.sentences.last().location)
    }

    @Test
    fun `slice from a cursor stops at the sentence budget`() {
        val slice = DocumentChunker.sliceFrom(
            units = pages,
            cursor = DocumentChunker.Cursor.START,
            maxSentences = 4,
            kind = SliceKind.PDF_PAGES,
        )
        assertEquals(4, slice.sentenceCount)
    }

    @Test
    fun `consecutive slices continue without gaps or repeats`() {
        val first = DocumentChunker.sliceFrom(
            units = pages,
            cursor = DocumentChunker.Cursor.START,
            maxSentences = 4,
            kind = SliceKind.PDF_PAGES,
        )
        val last = first.sentences.last()
        val second = DocumentChunker.sliceFrom(
            units = pages,
            cursor = DocumentChunker.Cursor(
                unitIndex = last.unitIndex,
                sentenceInUnit = last.sentenceInUnit + 1,
            ),
            maxSentences = 4,
            kind = SliceKind.PDF_PAGES,
        )

        val all = first.sentences.map { it.text } + second.sentences.map { it.text }
        val expected = pages.flatMap { page -> SentenceSegmenter.sentences(page) }
        assertEquals(expected.size, all.size)
        assertEquals(expected, all)
        assertTrue(second.isLastSlice)
    }

    @Test
    fun `epub paragraph slicing respects the threshold`() {
        val paragraphs = List(10) { index -> "Paragraph $index has one sentence." }
        val slice = DocumentChunker.sliceEpubParagraphs(
            paragraphs = paragraphs,
            chapterIndex = 2,
            fromParagraph = 0,
            paragraphThreshold = 4,
            maxSentences = 25,
        )
        assertEquals(4, slice.sentenceCount)
        assertTrue(slice.locationHeader, slice.locationHeader.contains("chapter 3"))
    }

    @Test
    fun `cursor advances beyond a location label`() {
        val label = DocumentChunker.locationLabel(SliceKind.PDF_PAGES, 127, 3)
        val cursor = DocumentChunker.cursorFromLocation(label)
        assertEquals(127, cursor.unitIndex)
        assertEquals(4, cursor.sentenceInUnit)
    }

    @Test
    fun `unparseable locations fall back to the start`() {
        val cursor = DocumentChunker.cursorFromLocation("somewhere in the book")
        assertEquals(DocumentChunker.Cursor.START, cursor)
    }

    @Test
    fun `numbered text starts at the requested id`() {
        val slice = DocumentChunker.sliceRange(pages, 0, 0, SliceKind.PDF_PAGES)
        val numbered = slice.numberedText(startId = 41)
        assertTrue(numbered, numbered.trimStart().startsWith("41"))
        assertTrue(numbered, numbered.contains("43"))
    }

    @Test
    fun `payload carries the location header and id range`() {
        val slice = DocumentChunker.sliceRange(pages, 1, 1, SliceKind.PDF_PAGES)
        val payload = BookPayloadSynthesizer.synthesize(
            slice = slice,
            prompt = "SYSTEM PROMPT BODY",
            startId = 11,
            bookName = "Test Book",
        )
        assertFalse(payload.isEmpty)
        assertEquals(11, payload.firstId)
        assertEquals(12, payload.lastId)
        assertEquals(2, payload.sentenceCount)
        assertTrue(payload.text, payload.text.contains("SYSTEM PROMPT BODY"))
        assertTrue(payload.text, payload.text.contains("LOCATION:"))
        assertTrue(payload.text, payload.text.contains("Page two first sentence."))
        assertTrue(payload.fileName, payload.fileName.endsWith(".txt"))
    }

    @Test
    fun `empty documents produce an empty slice rather than throwing`() {
        val slice = DocumentChunker.sliceRange(listOf("", "   "), 0, 1, SliceKind.PDF_PAGES)
        assertTrue(slice.isEmpty)
        assertEquals(0, slice.sentenceCount)
    }

    // The reported bleed, production path: pages are cleaned first (as
    // BookDocumentSource.loadPdf does), then sliced as a range. Page 1 ends
    // with a signature plus title block and no terminator, page 2 is blank,
    // page 3 opens the book.
    private val bleedPages = listOf(
        SentenceSegmenter.clean(
            "-- Walt Jung, Former IC apps engineer, and author of IC Op Amp Cookbook\n" +
                "THIRD EDITION\n" +
                "TH E A R T O F ELECTR O N ICS"
        ),
        SentenceSegmenter.clean(""),
        SentenceSegmenter.clean(
            "At long last, here is the thoroughly revised and updated, and " +
                "long-anticipated, third edition of the hugely successful The Art of Electronics."
        ),
    )

    @Test
    fun `page three keeps its real first sentence under its own marker`() {
        val slice = DocumentChunker.sliceRange(bleedPages, 0, 2, SliceKind.PDF_PAGES)
        val page3 = slice.sentences.filter { it.unitIndex == 2 }
        assertEquals(1, page3.size)
        assertEquals("p. 3 s. 1", page3.first().location)
        assertEquals(1, page3.first().sentenceInUnit)
        assertTrue(page3.first().text.startsWith("At long last"))
        // Page 1 ends on page 1: signature, then the reconstructed title block.
        val page1 = slice.sentences.filter { it.unitIndex == 0 }
        assertEquals(2, page1.size)
        assertEquals("p. 1 s. 2", page1.last().location)
        assertEquals("THIRD EDITION THE ART OF ELECTRONICS", page1.last().text)
    }

    @Test
    fun `no sentence carries words from two pages`() {
        val slice = DocumentChunker.sliceRange(bleedPages, 0, 2, SliceKind.PDF_PAGES)
        for (sentence in slice.sentences) {
            assertTrue(
                "sentence leaked across pages: ${sentence.location} ${sentence.text}",
                when (sentence.unitIndex) {
                    0 -> !sentence.text.contains("At long last")
                    2 -> !sentence.text.contains("Walt Jung") && !sentence.text.contains("THIRD EDITION")
                    else -> false
                },
            )
        }
    }

    @Test
    fun `page three opens with its own marker in the copy payload`() {
        val slice = DocumentChunker.sliceRange(bleedPages, 0, 2, SliceKind.PDF_PAGES)
        val payload = BookSlicePayload.build(
            slice = slice,
            firstId = 1,
            bookTitle = "",
            sourceLanguage = "",
            targetLanguage = "",
            level = "",
        )
        // id 1 is page 1's signature, id 2 is the unified title block; page 3 opens at 3.
        assertTrue(payload, payload.contains("[3|p3s1] At long last"))
        assertTrue(payload, payload.contains("[1|p1s1] -- Walt Jung"))
        assertTrue(payload, payload.contains("[2|p1s2] THIRD EDITION THE ART OF ELECTRONICS"))
    }

    @Test
    fun `copy payload unifies 6-line broken title page fragments into one entry`() {
        val page1 = SentenceSegmenter.clean("THIRD\nEDITION\nTH\nE OF\nELEC\nTRONIC")
        val slice = DocumentChunker.sliceRange(listOf(page1), 0, 0, SliceKind.PDF_PAGES)
        assertEquals(1, slice.sentenceCount)
        assertEquals("THIRD EDITION THE ART OF ELECTRONICS", slice.sentences.first().text)
        val payload = BookSlicePayload.build(
            slice = slice,
            firstId = 1,
            bookTitle = "",
            sourceLanguage = "",
            targetLanguage = "",
            level = "",
        )
        assertTrue(payload, payload.contains("[1|p1s1] THIRD EDITION THE ART OF ELECTRONICS"))
    }
}
