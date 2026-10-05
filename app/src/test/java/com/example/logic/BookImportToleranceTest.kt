package com.example.logic

import com.example.model.BookSentence
import com.example.model.SliceKind
import com.example.model.SourceSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import tolerance for the book pipeline.
 *
 * The failure these tests pin down: a reply that the app cannot line up with
 * the copied range used to be reported as "N items out of range" - one
 * complaint per line, for all N lines - and the entries stayed unusable
 * because their locator was never normalised. One mismatch between a batch and
 * its reference has to read as one problem, with the ids named, while the
 * lines that CAN be placed are still placed.
 *
 * Pure JVM: [BookLessonAligner] and [BookJsonIngest] take no Android
 * dependency, so every case below runs in a plain unit test.
 */
class BookImportToleranceTest {

    // ──────── the locator normaliser ────────

    @Test
    fun `marker style locations become the app label`() {
        assertEquals("p. 12 s. 1", BookLessonAligner.canonicalLocation("p12s1"))
        assertEquals("p. 12 s. 1", BookLessonAligner.canonicalLocation("[21|p12s1]"))
        assertEquals("Ch. 4 s. 12", BookLessonAligner.canonicalLocation("c4s12"))
        assertEquals("p. 12 s. 3", BookLessonAligner.canonicalLocation("P. 12 S. 3"))
    }

    @Test
    fun `worded locations are read too`() {
        assertEquals("p. 12 s. 3", BookLessonAligner.canonicalLocation("page 12, sentence 3"))
        assertEquals("p. 7 s. 1", BookLessonAligner.canonicalLocation("p. 7"))
        assertEquals("Ch. 4 s. 12", BookLessonAligner.canonicalLocation("Chapter 4, sentence 12"))
    }

    @Test
    fun `the two schema numbers build a label when location is unusable`() {
        assertEquals("p. 12 s. 3", BookLessonAligner.canonicalLocation("", page = 12, sentenceInPage = 3))
        assertEquals("p. 12 s. 1", BookLessonAligner.canonicalLocation(null, page = 12, sentenceInPage = 1))
        // Garbage in `location` must not beat the structured numbers.
        assertEquals("p. 9 s. 2", BookLessonAligner.canonicalLocation("???", page = 9, sentenceInPage = 2))
    }

    @Test
    fun `nothing to build a label from answers null`() {
        assertNull(BookLessonAligner.canonicalLocation(null))
        assertNull(BookLessonAligner.canonicalLocation("   "))
        assertNull(BookLessonAligner.canonicalLocation("unknown place"))
        assertNull(BookLessonAligner.canonicalLocation("", page = 0, sentenceInPage = 0))
    }

    // ──────── the ingest keeps the locator usable ────────

    @Test
    fun `ingest builds the label from page and sentenceInPage`() {
        val raw = """
            {
              "subtitles": [
                {
                  "id": 21, "english": "The house was empty.", "translation": "خانه خالی بود.",
                  "page": 12, "sentenceInPage": 3, "location": ""
                }
              ]
            }
        """.trimIndent()
        val result = BookJsonIngest.ingest(raw)
        assertTrue(result.errorKey.orEmpty(), result.isSuccess)
        val sentence = result.sentences.single()
        assertEquals("p. 12 s. 3", sentence.location)
        assertEquals(12, sentence.unitNumber)
        assertEquals(3, sentence.sentenceInUnit)
    }

    @Test
    fun `ingest decodes html escapes and a doubly escaped ampersand`() {
        val raw = """
            {
              "subtitles": [
                {
                  "id": 26, "english": "I tip my hat to H&amp;amp;H!",
                  "translation": "كلاه از سر برمی‌دارم برای H&amp;amp;H!", "location": "p1s26"
                },
                {
                  "id": 27, "english": "A &quot;quote&quot; and a dash -- here.",
                  "translation": "یک &laquo;نقل‌قول&raquo;", "location": "p1s27"
                }
              ]
            }
        """.trimIndent()
        val result = BookJsonIngest.ingest(raw)
        assertTrue(result.errorKey.orEmpty(), result.isSuccess)

        // The ampersand the page prints, not the escape it travelled in.
        assertEquals("I tip my hat to H&H!", result.sentences[0].english)
        assertEquals("كلاه از سر برمی‌دارم برای H&H!", result.sentences[0].translation)
        assertEquals("A \"quote\" and a dash -- here.", result.sentences[1].english)
        assertEquals("یک \u00ABنقل‌قول\u00BB", result.sentences[1].translation)
    }

    @Test
    fun `the decoded text is what the aligner stores`() {
        val sent = slice(firstId = 26, texts = listOf("I tip my hat to H&H!"))
        val received = listOf(
            sentence(26, "I tip my hat to H&amp;amp;H!", "p. 1 s. 26"),
        )

        val result = BookLessonAligner.align(received, sent)

        // The reference text is restored, so the reply's escape never reaches
        // the reader - and the entry stays anchored rather than going off-range.
        assertEquals("I tip my hat to H&H!", result.sentences.single().english)
        assertTrue(result.report.unanchoredIds.isEmpty())
        assertFalse(result.report.referenceMismatch)
    }

    @Test
    fun `ingest normalises a marker echoed as the location`() {
        val raw = """
            {
              "subtitles": [
                {
                  "id": 22, "english": "No one remembered it.", "translation": "کسی به یادش نبود.",
                  "location": "p12s1"
                }
              ]
            }
        """.trimIndent()
        val sentence = BookJsonIngest.ingest(raw).sentences.single()
        assertEquals("p. 12 s. 1", sentence.location)
    }

    // ──────── the aligner tolerates, then explains ────────

    @Test
    fun `a batch that does not belong to the copied range reads as one mismatch`() {
        val sent = slice(firstId = 1, texts = listOf("One.", "Two.", "Three."))
        // The reply answers a later chunk: ids 20..22 with their own labels.
        val received = listOf(
            sentence(20, "Ten.", "p. 10 s. 1"),
            sentence(21, "Eleven.", "p. 10 s. 2"),
            sentence(22, "Twelve.", "p. 10 s. 3"),
        )

        val result = BookLessonAligner.align(received, sent)
        val report = result.report

        assertEquals(3, report.offRangeIds.size)
        assertEquals(3, report.unanchoredIds.size)
        assertTrue(report.referenceMismatch)
        assertFalse(report.isClean)
        // Nothing is thrown away, and every line keeps a locator the reader can
        // read a page number out of.
        assertEquals(3, result.sentences.size)
        assertEquals("p. 10 s. 2", result.sentences[1].location)
        assertEquals(10, result.sentences[1].unitNumber)
    }

    @Test
    fun `the summary names the lines that failed instead of counting them twice`() {
        val sent = slice(firstId = 1, texts = listOf("One.", "Two."))
        val received = listOf(
            sentence(20, "Ten.", "p. 10 s. 1"),
            sentence(21, "Eleven.", "p. 10 s. 2"),
        )
        val summary = BookLessonAligner.summaryFa(BookLessonAligner.align(received, sent).report)

        // One sentence about the mismatch, naming the ids, plus the instruction
        // for how to fix it - not one complaint per line.
        assertTrue(summary, summary.contains("id: 20، 21"))
        assertTrue(summary, summary.contains("هم‌خوان نیست"))
        assertFalse(summary, summary.contains("2 خط بیرون از محدوده"))
    }

    @Test
    fun `a couple of odd lines never condemn the rest of the page`() {
        val sent = slice(firstId = 1, texts = listOf("One.", "Two.", "Three.", "Four."))
        val received = listOf(
            sentence(1, "One."),
            // Two entries the reference text does not resemble at all: a merge
            // and a line the model rewrote. Their id still anchors them.
            sentence(2, "Something else entirely, a paragraph the model merged."),
            sentence(3, ""),
            sentence(4, "Four."),
        )

        val report = BookLessonAligner.align(received, sent).report
        assertTrue(report.unanchoredIds.isEmpty())
        assertEquals(4, report.aligned)
        assertFalse(report.referenceMismatch)
    }

    @Test
    fun `off-range lines still get a label when the reference covers a neighbour`() {
        val sent = slice(firstId = 10, texts = listOf("Ten.", "Eleven."))
        val received = listOf(
            sentence(10, "Ten."),
            sentence(99, "Ninety nine.", "p. 42 s. 7"),
        )

        val result = BookLessonAligner.align(received, sent)
        assertEquals(listOf(99), result.report.offRangeIds)
        assertEquals("p. 42 s. 7", result.sentences.single { it.id == 99 }.location)
    }

    // ──────── fixtures ────────

    private fun slice(firstId: Int, texts: List<String>): BookSlicePayload.SentSlice =
        BookSlicePayload.SentSlice(
            docKey = "test",
            kind = SliceKind.PDF_PAGES,
            firstId = firstId,
            sentences = texts.mapIndexed { index, text ->
                SourceSentence(
                    index = index,
                    unitIndex = index,
                    sentenceInUnit = index + 1,
                    charStart = 0,
                    charEnd = text.length,
                    location = DocumentChunker.locationLabel(SliceKind.PDF_PAGES, index, index + 1),
                    text = text,
                )
            },
        )

    private fun sentence(id: Int, english: String, location: String = ""): BookSentence =
        BookSentence(
            id = id,
            start = id - 1,
            end = id,
            location = location,
            english = english,
            translation = "ترجمه",
        )
}
