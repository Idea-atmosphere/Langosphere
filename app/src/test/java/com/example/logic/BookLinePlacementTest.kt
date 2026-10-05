package com.example.logic

import com.example.model.BookSentence
import com.example.ui.screens.MergedEntry
import com.example.ui.screens.entriesForPage
import com.example.ui.screens.matchLinesToEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement of imported sentences on the lines of the page.
 *
 * The reported failure this pins down: a page can hold a couple of lines whose
 * imported `english` drifted too far from the extracted text - the sample that
 * came back merged the title block into the first sentence, which no single
 * line can ever match. Before the ordinal pass, one such entry also took the
 * line it did match away from a good entry, and the reader listed translations
 * as "did not fit under their line" while a line they belonged to sat empty.
 *
 * The rule under test: text similarity first, exactly; the sentence ordinal
 * second, which is the model's own `s. N` marker against the reader's own
 * segmentation; containment third, for an entry that holds several lines; the
 * blind in-order pairing last, and only when the number of entries and lines
 * agrees.
 */
class BookLinePlacementTest {

    @Test
    fun `a drifting entry does not strand the rest of the page`() {
        val lines = listOf(
            "The house on the hill had been empty for years.",
            "No one remembered who had built it.",
            "It stood above the village like a warning.",
        )
        val entries = listOf(
            // Merged title block + sentence: similarity against every line of
            // this page is far below the threshold.
            entry(1, "THIRD EDITION THE ART OF ELECTRONICS First of all, I sat down and read it.", "p. 1 s. 1"),
            entry(2, "No one remembered who had built it.", "p. 1 s. 2"),
            entry(3, "It stood above the village like a warning.", "p. 1 s. 3"),
        )

        val matches = matchLinesToEntries(lines, entries).matches

        // Every line with a matching entry is placed; nothing is left over.
        assertEquals(listOf(1, 2, 3), matches.map { it?.id })
    }

    @Test
    fun `the ordinal places a line whose wording drifted past similarity`() {
        val lines = listOf(
            "He nodded slowly, thinking about the letter.",
            "The window rattled in the wind.",
            "She said nothing at all.",
        )
        val entries = listOf(
            // `s. 1` is the first sentence of the page, whatever the text says.
            entry(7, "He nodded slowly - he was thinking about that letter again.", "p. 4 s. 1"),
            entry(8, "The window rattled in the wind.", "p. 4 s. 2"),
            entry(9, "She said nothing at all.", "p. 4 s. 3"),
        )

        val matches = matchLinesToEntries(lines, entries).matches

        assertEquals(7, matches[0]?.id)
        assertEquals(8, matches[1]?.id)
        assertEquals(9, matches[2]?.id)
    }

    @Test
    fun `the ordinal never steals a line from a text match`() {
        val lines = listOf("One.", "Two.")
        val entries = listOf(
            entry(1, "One.", "p. 2 s. 2"),
            entry(2, "Two.", "p. 2 s. 1"),
        )

        val matches = matchLinesToEntries(lines, entries).matches

        // Similarity wins: the labels are swapped and the lines still follow
        // their text, which is the more trustworthy of the two signals.
        assertEquals(1, matches[0]?.id)
        assertEquals(2, matches[1]?.id)
    }

    @Test
    fun `a page with more lines than entries keeps its unmatched lines empty`() {
        val lines = listOf("One.", "Two.", "Three.")
        val entries = listOf(entry(1, "One.", "p. 1 s. 1"))

        val matches = matchLinesToEntries(lines, entries).matches

        assertEquals(1, matches[0]?.id)
        assertNull(matches[1])
        assertNull(matches[2])
    }

    @Test
    fun `blank lines are not slots for an ordinal`() {
        val lines = listOf("One.", "", "Three.")
        val entries = listOf(
            entry(1, "One.", "p. 1 s. 1"),
            entry(2, "Three.", "p. 1 s. 3"),
        )

        val matches = matchLinesToEntries(lines, entries).matches

        assertEquals(1, matches[0]?.id)
        assertNull(matches[1])
        assertEquals(2, matches[2]?.id)
    }

    @Test
    fun `typographic quotes and dashes never stand between a line and its entry`() {
        // The reply tidies the page: curly quotes, an em dash where the book
        // prints a hyphen, an ellipsis character. None of that is a difference
        // the reader may punish - the matcher normalises punctuation away, so
        // the line is bound by its words.
        val lines = listOf("He said \"yes\" -- and then left.")
        val entries = listOf(
            entry(4, "He said \u201Cyes\u201D \u2014 and then left\u2026", "p. 6 s. 1"),
        )

        val placement = matchLinesToEntries(lines, entries)

        assertEquals(4, placement.matches[0]?.id)
        assertEquals(emptySet<Int>(), placement.mergedIds)
    }

    @Test
    fun `an imported chunk may skip pages and each page keeps its own lines`() {
        // The reported sample covers pages 1, 3 and 5: front matter, a table of
        // contents and a preface. Page scoping must not require contiguous
        // pages, and no page may borrow another page's translations.
        val all = listOf(
            sentence(1, "THE ART OF ELECTRONICS", "p. 1 s. 1"),
            sentence(26, "I tip my hat to H&H!", "p. 1 s. 26"),
            sentence(27, "At long last, here is the third edition.", "p. 1 s. 27"),
            sentence(28, "Contents", "p. 3 s. 1"),
            sentence(40, "Chapter 12: Voltage Regulators", "p. 3 s. 13"),
            sentence(41, "Preface to the third edition.", "p. 5 s. 1"),
        )

        val page1 = entriesForPage(all, currentPage = 1, currentChapter = 1, isEpubDoc = false)
        val page3 = entriesForPage(all, currentPage = 3, currentChapter = 1, isEpubDoc = false)
        val page5 = entriesForPage(all, currentPage = 5, currentChapter = 1, isEpubDoc = false)
        // A page the chunk never covered has nothing, and nothing from a
        // neighbour is offered as a substitute.
        val page2 = entriesForPage(all, currentPage = 2, currentChapter = 1, isEpubDoc = false)
        val page4 = entriesForPage(all, currentPage = 4, currentChapter = 1, isEpubDoc = false)

        assertEquals(listOf(1, 26, 27), page1.map { it.sentence.id })
        assertEquals(listOf(28, 40), page3.map { it.sentence.id })
        assertEquals(listOf(41), page5.map { it.sentence.id })
        assertTrue(page2.isEmpty())
        assertTrue(page4.isEmpty())
    }

    @Test
    fun `a page only ever offers its own lines`() {
        val all = listOf(
            sentence(1, "At long last, here is the third edition.", "p. 1 s. 1"),
            sentence(2, "Contents", "p. 3 s. 1"),
            sentence(3, "Chapter 12: Voltage Regulators", "p. 3 s. 13"),
        )

        val page1 = entriesForPage(all, currentPage = 1, currentChapter = 1, isEpubDoc = false)
        val page3 = entriesForPage(all, currentPage = 3, currentChapter = 1, isEpubDoc = false)

        // Page 3's list holds page 3's lines only, so the page 1 line with the
        // very same wording is not even a candidate for it.
        assertEquals(listOf(1), page1.map { it.sentence.id })
        assertEquals(listOf(2, 3), page3.map { it.sentence.id })
    }

    @Test
    fun `an entry built out of several lines lands on the line it opens`() {
        // Entry 27 of the reported sample: the model answered with the
        // testimonial signature, the title block and the publisher's blurb in
        // one `english`. Nothing it holds is similar to a whole line, and the
        // ordinal is the model's own numbering, so the entry used to be listed
        // as unplaceable while the line it opens sat empty.
        val lines = listOf(
            "Walt Jung, former IC apps engineer, and author of the IC Op Amp Cookbook",
            "THE ART OF ELECTRONICS Third Edition",
            "At long last, here is the thoroughly revised and updated, " +
                "and long-anticipated, third edition of the hugely successful book.",
        )
        val entries = listOf(
            entry(
                27,
                "-- Walt Jung, Former IC apps engineer, and author of IC Op Amp Cookbook " +
                    "THIRD EDITION TH E A R T O F ELECTR O N ICS H O RO W ITZ H " +
                    "The Art of Electronics Third Edition At long last, here is the " +
                    "thoroughly revised and updated, and long-anticipated, third edition " +
                    "of the hugely successful The Art of Electronics.",
                "p. 1 s. 27",
            ),
            entry(
                28,
                "At long last, here is the thoroughly revised and updated, " +
                    "and long-anticipated, third edition of the hugely successful book.",
                "p. 1 s. 29",
            ),
        )

        val placement = matchLinesToEntries(lines, entries)

        // It opens with the signature line, so that is the line it sits under.
        assertEquals(27, placement.matches[0]?.id)
        // The blurb keeps its own entry on its own line.
        assertEquals(28, placement.matches[2]?.id)
        // And the entry itself is flagged, so the reader can say why one
        // translation covers three lines instead of looking like a loss.
        assertEquals(setOf(27), placement.mergedIds)
        // The title line has no entry of its own here; its text sits inside
        // entry 27, and the line says so instead of looking lost.
        assertEquals(mapOf(1 to 27), placement.linesInsideMerged)
    }

    @Test
    fun `a line whose text lives inside a merged entry points at that entry`() {
        // Same page, but the model returned nothing for the title block and
        // the blurb: they exist only inside entry 27. Neither line may be left
        // looking as if the app dropped its translation.
        val lines = listOf(
            "Walt Jung, former IC apps engineer, and author of the IC Op Amp Cookbook",
            "THE ART OF ELECTRONICS Third Edition",
            "At long last, here is the thoroughly revised and updated, " +
                "and long-anticipated, third edition of the hugely successful book.",
        )
        val entries = listOf(
            entry(
                27,
                "-- Walt Jung, Former IC apps engineer, and author of IC Op Amp Cookbook " +
                    "THIRD EDITION TH E A R T O F ELECTR O N ICS H O RO W ITZ H The Art of " +
                    "Electronics Third Edition At long last, here is the thoroughly revised " +
                    "and updated, and long-anticipated, third edition of the hugely " +
                    "successful book.",
                "p. 1 s. 27",
            ),
        )

        val placement = matchLinesToEntries(lines, entries)

        assertEquals(27, placement.matches[0]?.id)
        assertEquals(setOf(27), placement.mergedIds)
        assertNull(placement.matches[1])
        assertNull(placement.matches[2])
        assertEquals(mapOf(1 to 27, 2 to 27), placement.linesInsideMerged)
    }

    @Test
    fun `an ordinary page is placed by its wording alone`() {
        val lines = listOf(
            "The house on the hill had been empty for years.",
            "No one remembered who had built it.",
            "It stood above the village like a warning.",
        )
        val entries = listOf(
            entry(1, "The house on the hill had been empty for years.", "p. 2 s. 1"),
            entry(2, "No one remembered who had built it.", "p. 2 s. 2"),
            entry(3, "It stood above the village like a warning.", "p. 2 s. 3"),
        )

        val placement = matchLinesToEntries(lines, entries)

        // Containment must never fire here: no line is swallowed by another
        // entry, and no entry is labelled as a merge.
        assertEquals(listOf(1, 2, 3), placement.matches.map { it?.id })
        assertEquals(emptySet<Int>(), placement.mergedIds)
        assertEquals(emptyMap<Int, Int>(), placement.linesInsideMerged)
    }

    @Test
    fun `one sentence under its own line is not called a merge`() {
        val lines = listOf("He nodded slowly, thinking about the letter.")
        val entries = listOf(entry(7, "He nodded slowly, thinking about the letter.", "p. 4 s. 1"))

        val placement = matchLinesToEntries(lines, entries)

        assertEquals(7, placement.matches[0]?.id)
        assertEquals(emptySet<Int>(), placement.mergedIds)
    }

    private fun sentence(id: Int, english: String, location: String): BookSentence = BookSentence(
        id = id,
        start = id - 1,
        end = id,
        location = location,
        english = english,
        translation = "ترجمه",
    )

    private fun entry(id: Int, english: String, location: String): MergedEntry = MergedEntry(
        sentence = BookSentence(
            id = id,
            start = id - 1,
            end = id,
            location = location,
            english = english,
            translation = "ترجمه",
        ),
        norm = FuzzyTextAligner.normalize(english),
        page = Regex("(?:p|page)\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(location)?.groupValues?.getOrNull(1)?.toIntOrNull(),
        chapter = null,
        ordinal = Regex("s\\.?\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
            .find(location)?.groupValues?.getOrNull(1)?.toIntOrNull(),
    )
}
