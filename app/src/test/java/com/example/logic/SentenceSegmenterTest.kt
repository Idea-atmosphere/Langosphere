package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmenterTest {

    @Test
    fun `splits on terminators`() {
        val sentences = SentenceSegmenter.sentences(
            "The cat sat. The dog barked! Did it rain?",
        )
        assertEquals(3, sentences.size)
        assertEquals("The cat sat.", sentences[0])
        assertEquals("The dog barked!", sentences[1])
        assertEquals("Did it rain?", sentences[2])
    }

    @Test
    fun `does not split common abbreviations`() {
        val sentences = SentenceSegmenter.sentences(
            "Dr. Watson arrived early. He waited.",
        )
        assertEquals(2, sentences.size)
        assertTrue(sentences[0].contains("Dr. Watson"))
    }

    @Test
    fun `rejoins hyphenated line breaks`() {
        val cleaned = SentenceSegmenter.clean("He could not under-\nstand the map.")
        assertTrue(cleaned, cleaned.contains("understand"))
    }

    @Test
    fun `spans point back at the original text`() {
        val text = "One sentence here. Another one follows."
        val spans = SentenceSegmenter.splitSentences(text)
        assertEquals(2, spans.size)
        for (span in spans) {
            assertEquals(span.text, text.substring(span.startOffset, span.endOffset).trim())
        }
    }

    @Test
    fun `detects running headers repeated across pages`() {
        val pages = List(6) { index ->
            "A HISTORY OF EVERYTHING\nPage body number $index continues here."
        }
        val running = SentenceSegmenter.detectRunningLines(pages)
        assertTrue(running.toString(), running.isNotEmpty())

        val cleaned = SentenceSegmenter.clean(pages.first(), running)
        assertTrue(cleaned, !cleaned.contains("A HISTORY OF EVERYTHING"))
    }

    @Test
    fun `blank input yields no sentences`() {
        assertTrue(SentenceSegmenter.sentences("   \n\n ").isEmpty())
    }

    @Test
    fun `signatures and title blocks stand alone after cleaning`() {
        val cleaned = SentenceSegmenter.clean(
            "Body line one ends.\n-- Walt Jung, engineer\nTHIRD EDITION"
        )
        assertEquals(
            listOf("Body line one ends.", "-- Walt Jung, engineer", "THIRD EDITION"),
            SentenceSegmenter.sentences(cleaned),
        )
    }

    @Test
    fun `page furniture stands alone instead of gluing into prose`() {
        val cleaned = SentenceSegmenter.clean("Body text here.\n27 / 312\nMore body text.")
        assertEquals(
            listOf("Body text here.", "27 / 312", "More body text."),
            SentenceSegmenter.sentences(cleaned),
        )
    }

    @Test
    fun `raw display text segments structural lines identically`() {
        // The reader displays the page's raw lines while the copy payload is
        // built from cleaned pages; both must cut the same standalone items
        // or translations stop matching their lines.
        val raw = "Body line one ends.\n-- Walt Jung, engineer\nTHIRD EDITION\n128\nMore body."
        assertEquals(
            listOf(
                "Body line one ends.",
                "-- Walt Jung, engineer",
                "THIRD EDITION",
                "128",
                "More body.",
            ),
            SentenceSegmenter.sentences(raw),
        )
    }

    @Test
    fun `bracketed directions stand alone`() {
        val sentences = SentenceSegmenter.sentences(
            "The play begins.\n[Thunder and lightning.]\nThe king enters."
        )
        assertEquals(3, sentences.size)
        assertEquals("[Thunder and lightning.]", sentences[1])
    }

    @Test
    fun `wrapped prose lines still glue across single breaks`() {
        val sentences = SentenceSegmenter.sentences(
            "The first part of a wrapped\nline continues here. A second sentence."
        )
        assertEquals(2, sentences.size)
        assertEquals("The first part of a wrapped line continues here.", sentences[0])
    }

    @Test
    fun `mixed lines with capitalised words do not split`() {
        val sentences = SentenceSegmenter.sentences(
            "He met the KING\nof birds in May. They talked."
        )
        assertEquals(2, sentences.size)
        assertEquals("He met the KING of birds in May.", sentences[0])
    }

    @Test
    fun `a lone wrapped article is prose, not a heading`() {
        val sentences = SentenceSegmenter.sentences("He bought\na book. She smiled.")
        assertEquals(2, sentences.size)
        assertEquals("He bought a book.", sentences[0])
    }

    @Test
    fun `reconstructs multi-line stylized title block into a single unified sentence`() {
        val raw = "THIRD\nEDITION\nTH\nE OF\nELEC\nTRONIC"
        val cleaned = SentenceSegmenter.clean(raw)
        assertEquals("THIRD EDITION THE ART OF ELECTRONICS", cleaned)
        val sentences = SentenceSegmenter.sentences(cleaned)
        assertEquals(listOf("THIRD EDITION THE ART OF ELECTRONICS"), sentences)
    }

    @Test
    fun `dehyphenates words across line breaks seamlessly`() {
        val raw = "The inves-\ntigation revealed unexpected results."
        val cleaned = SentenceSegmenter.clean(raw)
        assertTrue(cleaned.contains("investigation"))
        assertEquals("The investigation revealed unexpected results.", cleaned)
    }

    @Test
    fun `unwraps soft line breaks mid-sentence into complete continuous sentences`() {
        val raw = "Normal paragraphs flow as\ncomplete, continuous English sentences\nwithout mid-sentence chops."
        val cleaned = SentenceSegmenter.clean(raw)
        assertEquals(
            "Normal paragraphs flow as complete, continuous English sentences without mid-sentence chops.",
            cleaned,
        )
    }

    @Test
    fun `filters out standalone 1-2 character non-word OCR artefacts`() {
        val raw = "|\n_\nx\nTHIRD EDITION\n•\nValid sentence here."
        val cleaned = SentenceSegmenter.clean(raw)
        assertFalse(cleaned.contains("|"))
        assertFalse(cleaned.contains("_"))
        assertTrue(cleaned.contains("THIRD EDITION"))
        assertTrue(cleaned.contains("Valid sentence here."))
    }

    @Test
    fun `quoted dialogues stay intact as complete sentence units`() {
        val raw = '"' + "Wow." + '"' + " He smiled. " + '“' + "First of all... read it." + '”'
        val sentences = SentenceSegmenter.sentences(raw)
        assertEquals(3, sentences.size)
        assertEquals('"' + "Wow." + '"', sentences[0])
        assertEquals("He smiled.", sentences[1])
        assertEquals('“' + "First of all... read it." + '”', sentences[2])
    }

}
