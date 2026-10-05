package com.example.logic

import com.example.model.BookSentence
import com.example.model.JsonLesson
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitleMetadata
import com.example.model.JsonSubtitlePackage
import com.example.model.JsonWord
import com.example.model.LeitnerCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the study features the reader and the film subtitles share: the
 * difficulty dot and reading-time estimate, the sentence card both surfaces
 * build, the smart page batching, and the Anki tag column.
 *
 * Everything here is pure logic on purpose — the same material has to behave
 * identically whether it arrived from a book or from a subtitle file, and the
 * only way to promise that is to test the shared code rather than either
 * screen.
 */
class StudyModeLogicTest {

    // ─────────────────────────────── difficulty + timing ───────────────────────────────

    @Test
    fun `difficulty text maps onto the three tones`() {
        assertEquals(DifficultyTone.EASY, SentenceMetrics.tone("easy", null))
        assertEquals(DifficultyTone.EASY, SentenceMetrics.tone("Easy to read", null))
        assertEquals(DifficultyTone.MEDIUM, SentenceMetrics.tone("medium", null))
        assertEquals(DifficultyTone.HARD, SentenceMetrics.tone("hard", null))
        // Persian labels the prompts also produce.
        assertEquals(DifficultyTone.EASY, SentenceMetrics.tone("آسان", null))
        assertEquals(DifficultyTone.HARD, SentenceMetrics.tone("دشوار", null))
    }

    @Test
    fun `cefr level is the fallback when difficulty says nothing`() {
        assertEquals(DifficultyTone.EASY, SentenceMetrics.tone(null, "A2"))
        assertEquals(DifficultyTone.MEDIUM, SentenceMetrics.tone("", "B1"))
        assertEquals(DifficultyTone.HARD, SentenceMetrics.tone(null, "C1"))
        // A level written into the difficulty field still counts.
        assertEquals(DifficultyTone.HARD, SentenceMetrics.tone("C2", null))
    }

    @Test
    fun `an unreadable label leaves the dot off instead of guessing`() {
        assertEquals(DifficultyTone.UNKNOWN, SentenceMetrics.tone(null, null))
        assertEquals(DifficultyTone.UNKNOWN, SentenceMetrics.tone("", ""))
        assertEquals(DifficultyTone.UNKNOWN, SentenceMetrics.tone(null, "Z9"))
        assertEquals("", SentenceMetrics.toneLabel(DifficultyTone.UNKNOWN, fa = true))
    }

    @Test
    fun `a line that holds several source lines is reported as a merge`() {
        // The model answered with the signature, the title block and the
        // publisher's blurb in one entry; the reader cannot cut it apart again,
        // so it says so instead of letting a line look lost.
        val merged = "-- Walt Jung, Former IC apps engineer, and author of IC Op Amp Cookbook " +
            "THIRD EDITION TH E A R T O F ELECTR O N ICS H O RO W ITZ H The Art of " +
            "Electronics Third Edition. At long last, here is the thoroughly revised " +
            "and updated, and long-anticipated, third edition of a hugely successful book."
        assertTrue(SentenceMetrics.isMergedEntry(merged))
        // One line, however long, is one line.
        val single = "At long last, here is the thoroughly revised and updated, and " +
            "long-anticipated, third edition of the hugely successful book, now with more " +
            "chapters, more figures and a companion site for readers and teachers alike."
        assertFalse(SentenceMetrics.isMergedEntry(single))
        // Short text stays quiet even with two sentences in it.
        assertFalse(SentenceMetrics.isMergedEntry("He came. He saw."))
        assertFalse(SentenceMetrics.isMergedEntry(""))
    }

    @Test
    fun `reading time uses 130 words a minute, rounded up, never zero for real text`() {
        assertEquals(130, SentenceMetrics.WORDS_PER_MINUTE)
        assertEquals(130, SentenceMetrics.wordCount("word ".repeat(130).trim()).toLong())
        assertEquals(1, SentenceMetrics.readingMinutes(130).toLong())
        assertEquals(2, SentenceMetrics.readingMinutes(131).toLong())
        assertEquals(1, SentenceMetrics.readingMinutes("just three tiny words").toLong())
        assertEquals(0, SentenceMetrics.readingMinutes("").toLong())
        // Persians digits are not required, but the wording is complete either way.
        assertTrue(SentenceMetrics.readingTimeLabel(0, fa = true).contains("کمتر از یک دقیقه"))
        assertTrue(SentenceMetrics.readingTimeLabel(3, fa = true).contains("3"))
    }

    // ─────────────────────────────── sentence cards ───────────────────────────────

    private fun bookSentence() = BookSentence(
        id = 7,
        start = 0,
        end = 1,
        location = "p. 12 s. 3",
        english = "The house had been empty for years.",
        translation = "خانه سال‌ها خالی مانده بود.",
        level = "B1",
        difficulty = "medium",
        pronunciation = "ðə haʊs",
        lesson = JsonLesson(
            explanation = "زمان حال کامل استمراری در گذشته.",
            grammar = "Past perfect",
            grammarTranslation = "ماضی نقلی",
            structure = "had + been + adjective",
        ),
        words = listOf(JsonWord(word = "empty", translation = "خالی", pronunciation = "ˈɛmpti")),
    )

    @Test
    fun `a book sentence becomes one card carrying the whole lesson`() {
        val draft = SentenceCardFactory.fromBook(bookSentence())
        assertEquals(LeitnerCard.SOURCE_BOOK, draft.source)
        assertEquals("The house had been empty for years.", draft.front)
        assertEquals("خانه سال‌ها خالی مانده بود.", draft.translation)
        // Both halves of the grammar line are kept, joined.
        assertTrue(draft.grammar.contains("Past perfect"))
        assertTrue(draft.grammar.contains("ماضی نقلی"))
        assertEquals("had + been + adjective", draft.structure)
        assertEquals("p. 12 s. 3", draft.location)
        assertTrue(draft.isValid)
        // The back anyone reads: translation first, then the lesson and the words.
        assertTrue(draft.definition.contains("خانه سال‌ها خالی مانده بود."))
        assertTrue(draft.lessonText.contains("Past perfect"))
        assertTrue(draft.lessonText.contains("had + been + adjective"))
        assertTrue(draft.wordsText.contains("empty"))
        assertTrue(draft.wordsText.contains("ˈɛmpti"))
        assertTrue(draft.definition.contains(draft.lessonText))
    }

    @Test
    fun `a film line becomes the same card, tagged to subtitles`() {
        val subtitle = JsonSubtitle(
            id = "42",
            start = 83.0,
            end = 85.0,
            english = "Take it personally.",
            translation = "به دل نگیر.",
            lesson = JsonLesson(grammar = "Imperative", explanation = "دستور"),
            words = listOf(JsonWord(word = "personally", translation = "شخصاً")),
        )
        val draft = SentenceCardFactory.fromSubtitle(subtitle, timeLabel = "00:01:23")
        assertEquals(LeitnerCard.SOURCE_MOVIE, draft.source)
        assertEquals("00:01:23", draft.location)
        assertEquals("به دل نگیر.", draft.translation)
        assertTrue(draft.lessonText.contains("Imperative"))
        assertTrue(draft.wordsText.contains("personally"))
    }

    @Test
    fun `a line with nothing to teach is not a card`() {
        val empty = SentenceCardFactory.fromSubtitle(JsonSubtitle(english = "   "), timeLabel = "")
        assertFalse(empty.isValid)
        val englishOnly = SentenceCardFactory.fromSubtitle(JsonSubtitle(english = "Hello."), timeLabel = "")
        assertFalse(englishOnly.isValid)
    }

    @Test
    fun `card keys collapse spacing so the same line is recognised everywhere`() {
        assertEquals(
            "s:movie:take it personally.",
            LeitnerBoxManager.keyFor(LeitnerCard.KIND_SENTENCE, LeitnerCard.SOURCE_MOVIE, "  Take   IT personally. "),
        )
        // Word keys keep the format earlier builds wrote.
        assertEquals("take", LeitnerBoxManager.keyFor(LeitnerCard.KIND_WORD, LeitnerCard.SOURCE_DICTIONARY, " Take "))
    }

    // ─────────────────────────────── smart page batching ───────────────────────────────

    @Test
    fun `the next batch starts after whatever was already handled`() {
        assertEquals(1, BookBatchCopier.nextStartPage(copiedPage = null, checkpointPage = null))
        assertEquals(6, BookBatchCopier.nextStartPage(copiedPage = 5, checkpointPage = null))
        // The imported answer is the better measure of progress, and the later
        // of the two wins so a copy is never repeated.
        assertEquals(8, BookBatchCopier.nextStartPage(copiedPage = 5, checkpointPage = 7))
        assertEquals(9, BookBatchCopier.nextStartPage(copiedPage = 8, checkpointPage = 3))
    }

    @Test
    fun `a batch is five pages wide and stops at the end of the document`() {
        assertEquals(BookBatchCopier.DEFAULT_PAGES, 5)
        assertEquals(6..10, BookBatchCopier.nextRange(unitCount = 40, startPage = 6))
        assertEquals(10..12, BookBatchCopier.nextRange(unitCount = 12, startPage = 10))
        assertEquals(1..5, BookBatchCopier.nextRange(unitCount = 40, startPage = 0))
        assertNull(BookBatchCopier.nextRange(unitCount = 12, startPage = 13))
        assertNull(BookBatchCopier.nextRange(unitCount = 0, startPage = 1))
    }

    @Test
    fun `a page is read out of the locator the reader stores`() {
        assertEquals(12, BookBatchCopier.unitOfLocation("p. 12 s. 3"))
        assertEquals(4, BookBatchCopier.unitOfLocation("Ch. 4 s. 12"))
        assertEquals(128, BookBatchCopier.unitOfLocation("page 128"))
        assertNull(BookBatchCopier.unitOfLocation("somewhere later"))
        assertNull(BookBatchCopier.unitOfLocation(null))
    }

    // ─────────────────────────────── anki export ───────────────────────────────

    private fun wordCard(id: Long, word: String) = LeitnerCard(
        id = id,
        word = word,
        definition = "meaning of $word",
        boxLevel = 1,
        nextReviewAt = 0L,
        createdAt = 0L,
    )

    private fun sentenceCard(id: Long, sentence: String, source: String) = LeitnerCard(
        id = id,
        word = sentence,
        definition = "ترجمه\n📘 درس",
        boxLevel = 1,
        nextReviewAt = 0L,
        createdAt = 0L,
        kind = LeitnerCard.KIND_SENTENCE,
        source = source,
        translation = "ترجمه",
        location = "p. 1 s. 1",
    )

    @Test
    fun `a vocabulary-only export stays exactly two columns`() {
        val text = AnkiExporter.buildExportText(listOf(wordCard(1, "house"), wordCard(2, "empty")))
        assertTrue(text.startsWith("#separator:tab"))
        assertFalse(text.contains("#tags column:3"))
        assertFalse(text.contains("\tLangosphere"))
    }

    @Test
    fun `a sentence card turns on the tag column and is tagged by its source`() {
        val text = AnkiExporter.buildExportText(
            listOf(
                wordCard(1, "house"),
                sentenceCard(2, "The house was empty.", LeitnerCard.SOURCE_BOOK),
                sentenceCard(3, "Take it personally.", LeitnerCard.SOURCE_MOVIE),
            ),
        )
        assertTrue(text.contains("#tags column:3"))
        assertTrue(text.contains(LeitnerCard.TAG_BOOK))
        assertTrue(text.contains(LeitnerCard.TAG_MOVIE))
        // Every row has three columns once the file says so, or Anki would
        // read a two-column row as a short line.
        val rows = text.lines().filter { it.contains("\t") && !it.startsWith("#") }
        assertEquals(3, rows.size)
        rows.forEach { row -> assertEquals(3, row.split("\t").size) }
        assertEquals(1 to 2, AnkiExporter.countByKind(
            listOf(
                wordCard(1, "house"),
                sentenceCard(2, "The house was empty.", LeitnerCard.SOURCE_BOOK),
                sentenceCard(3, "Take it personally.", LeitnerCard.SOURCE_MOVIE),
            ),
        ))
    }

    // ─────────────────────────────── quiz sources ───────────────────────────────

    /**
     * Three lines with three distinct words: enough material for a
     * multiple-choice question, which needs at least one wrong option.
     */
    private fun packageOf(prefix: String) = JsonSubtitlePackage(
        formatVersion = 1,
        metadata = JsonSubtitleMetadata(language = "English", targetLanguage = "Persian", level = "B1"),
        subtitles = (1..3).map { index ->
            JsonSubtitle(
                id = index.toString(),
                english = "$prefix line number $index.",
                translation = "$prefix ترجمهٔ $index",
                words = listOf(
                    JsonWord(word = "$prefix word $index", translation = "$prefix معنی $index"),
                ),
            )
        },
    )

    @Test
    fun `a filtered quiz uses only the chosen source and can blur its prompt`() {
        val materials = listOf(
            QuizMaterial(QuizSource.MOVIE, packageOf("Film")),
            QuizMaterial(QuizSource.BOOK, packageOf("Book")),
        )
        val bookOnly = JsonQuizBuilder.filter(materials, QuizSourceFilter.BOOK)
        assertEquals(1, bookOnly.size)
        assertTrue(bookOnly.all { it.source == QuizSource.BOOK })
        assertEquals(2, JsonQuizBuilder.filter(materials, QuizSourceFilter.BOTH).size)

        val blurred = JsonQuizBuilder.build(materials = bookOnly, seed = 7L, blurPeek = true)
        assertTrue(blurred.isNotEmpty())
        assertTrue(blurred.all { it.blurPrompt })
        assertTrue(blurred.all { it.source == QuizSource.BOOK })
        // Blur / fast guess is a pure sentence-meaning recall test: only
        // sentence → translation questions are dealt.
        assertTrue(blurred.all { it.type == JsonQuizType.SENTENCE_TO_TRANSLATION })

        val plain = JsonQuizBuilder.build(materials = bookOnly, seed = 7L)
        assertTrue(plain.all { !it.blurPrompt })
        // The normal mode still asks about the package's words as well.
        assertTrue(plain.any { it.type != JsonQuizType.SENTENCE_TO_TRANSLATION })
    }
}
