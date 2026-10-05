package com.example.logic

import com.example.model.JsonLesson
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitlePackage
import com.example.model.JsonWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robustness tests for the quiz data pipeline: the parser must never lose
 * half of a pasted AI answer silently, and the question count the setup
 * screen shows must be the number of questions a run can really produce.
 */
// Robolectric gives the JVM a real org.json (the android.jar stub throws
// "not mocked" for every JSONObject/JSONArray call).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuizPipelineRobustnessTest {

    private fun chunkJson(id: Int, english: String): String =
        "{ \"subtitles\": [ { \"id\": $id, \"english\": \"$english\", \"translation\": \"ترجمه $id\" } ] }"

    // ── parser: partial AI answers ──

    @Test
    fun `a truncated chunk after a complete chunk is counted as skipped`() {
        val paste = "CHUNK 1-2\n" + chunkJson(1, "First line.") +
            "\nCHUNK 3-4\n{ \"subtitles\": [ { \"id\": 3, \"english\": \"cut off"
        val outcome = SubtitleJsonParser.parseWithReport(paste)
        assertNotNull(outcome.pkg)
        assertEquals(1, outcome.skippedDocuments)
        assertEquals(1, outcome.pkg.subtitles.size)
    }

    @Test
    fun `a single truncated answer still fails loudly`() {
        val paste = "CHUNK 1-2\n{ \"subtitles\": [ { \"id\": 1, \"english\": \"cut off"
        var threw = false
        try {
            SubtitleJsonParser.parse(paste)
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            threw = true
        }
        assertTrue("a truncated-only answer must not import", threw)
    }

    @Test
    fun `prose after a complete document does not become a phantom skip`() {
        val paste = chunkJson(1, "First line.") + "\nThanks for reading, enjoy the film!"
        val outcome = SubtitleJsonParser.parseWithReport(paste)
        assertEquals(0, outcome.skippedDocuments)
        assertEquals(1, outcome.pkg.subtitles.size)
    }

    @Test
    fun `a structural value where text was expected is treated as missing`() {
        // "english" as a nested object instead of a string must not leak its
        // raw JSON dump into the subtitle list; the parser reports the missing
        // english text instead.
        val json = "{ \"subtitles\": [ { \"id\": 1, " +
            "\"english\": { \"text\": \"hello\" }, \"translation\": \"سلام\" } ] }"
        var message: String? = null
        try {
            SubtitleJsonParser.parse(json)
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            message = e.message
        }
        assertNotNull(message)
        assertTrue(message!!.contains("english", ignoreCase = true))
    }

    // ── builder: the offered count is the real count ──

    @Test
    fun `a single word package offers no questions`() {
        // One word alone has no possible distractors: the old theoretical
        // count said 3 (two word directions + the sentence), the honest
        // count says 0 — and the setup screen can disable the start button.
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(
                    id = "1",
                    english = "Only line.",
                    translation = "تنها خط.",
                    words = listOf(JsonWord(word = "only", translation = "فقط"))
                )
            )
        )
        assertEquals(0, JsonQuizBuilder.availableQuestions(pkg))
        assertEquals(0, JsonQuizBuilder.build(pkg, maxQuestions = 10, seed = 1L).size)
    }

    @Test
    fun `available questions matches the built pool for a rich package`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(
                    id = "1", english = "Hello there.", translation = "سلام.",
                    words = listOf(JsonWord(word = "hello", translation = "سلام"))
                ),
                JsonSubtitle(
                    id = "2", english = "Good morning.", translation = "صبح بخیر.",
                    words = listOf(JsonWord(word = "morning", translation = "صبح"))
                ),
                JsonSubtitle(
                    id = "3", english = "Good night.", translation = "شب بخیر.",
                    words = listOf(JsonWord(word = "night", translation = "شب"))
                )
            )
        )
        val available = JsonQuizBuilder.availableQuestions(pkg)
        assertTrue("expected questions, got none", available > 0)
        assertEquals(available, JsonQuizBuilder.build(pkg, maxQuestions = 100, seed = 9L).size)
    }

    // ── the teachings + the running-deck persistence ──

    @Test
    fun `a sentence question carries the teachings of its json`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(
                    id = "1",
                    english = "I have been working here.",
                    translation = "من اینجا کار می‌کنم.",
                    notes = "Common in workplace small talk.",
                    lesson = JsonLesson(
                        explanation = "Present perfect continuous for ongoing actions.",
                        grammar = "Present Perfect Continuous",
                        grammarTranslation = "حال کامل استمراری",
                        structure = "Subject + have been + verb-ing"
                    )
                ),
                JsonSubtitle(id = "2", english = "Another line.", translation = "خط دیگر."),
                JsonSubtitle(id = "3", english = "A third line.", translation = "خط سوم.")
            )
        )
        val question = JsonQuizBuilder.build(pkg, maxQuestions = 50, seed = 1L)
            .first { it.type == JsonQuizType.SENTENCE_TO_TRANSLATION && it.subtitleId == "1" }
        // The note is what both the feedback dock shows and a wrong answer
        // ships to the Leitner box: notes + explanation + grammar, in Persian
        // where the JSON gave the Persian term.
        assertTrue(question.note!!.contains("workplace"))
        assertTrue(question.note!!.contains("Present perfect continuous"))
        assertTrue(question.note!!.contains("حال کامل استمراری"))
    }

    @Test
    fun `a dealt deck round-trips through the state encoder`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(
                    id = "1", english = "Hello there.", translation = "سلام.",
                    lesson = JsonLesson(grammar = "Greeting", grammarTranslation = "احوال‌پرسی"),
                    words = listOf(JsonWord(word = "hello", translation = "سلام"))
                ),
                JsonSubtitle(id = "2", english = "Good morning.", translation = "صبح بخیر."),
                JsonSubtitle(id = "3", english = "Good night.", translation = "شب بخیر.")
            )
        )
        val deck = JsonQuizBuilder.build(
            materials = listOf(QuizMaterial(QuizSource.MOVIE, pkg)),
            maxQuestions = 8,
            seed = 42L,
            blurPeek = true
        )
        assertTrue(deck.isNotEmpty())
        // What the dialog keeps in rememberSaveable must come back identical,
        // so a trip to Recents restores the exact quiz, not a reshuffle.
        val restored = JsonQuizBuilder.decodeState(JsonQuizBuilder.encodeState(deck))
        assertEquals(deck, restored)
    }

    @Test
    fun `the blur quiz deals only sentence-meaning questions`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(
                    id = "1", english = "Hello there.", translation = "سلام.",
                    words = listOf(JsonWord(word = "hello", translation = "سلام"))
                ),
                JsonSubtitle(
                    id = "2", english = "Good morning.", translation = "صبح بخیر.",
                    words = listOf(JsonWord(word = "morning", translation = "صبح"))
                ),
                JsonSubtitle(
                    id = "3", english = "Good night.", translation = "شب بخیر.",
                    words = listOf(JsonWord(word = "night", translation = "شب"))
                )
            )
        )
        val materials = listOf(QuizMaterial(QuizSource.MOVIE, pkg))
        // Blur / fast guess is a sentence-meaning recall test only: no word,
        // no grammar questions are dealt, and the count says so too.
        val blurDeck = JsonQuizBuilder.build(materials, maxQuestions = 50, seed = 3L, blurPeek = true)
        assertTrue(blurDeck.isNotEmpty())
        assertTrue(blurDeck.all { it.type == JsonQuizType.SENTENCE_TO_TRANSLATION })
        assertEquals(blurDeck.size, JsonQuizBuilder.availableQuestions(materials, blurPeek = true))
        // The normal mode still asks about words as well.
        val normalDeck = JsonQuizBuilder.build(materials, maxQuestions = 50, seed = 3L)
        assertTrue(normalDeck.any { it.type == JsonQuizType.WORD_TO_TRANSLATION })
    }

    @Test
    fun `the online source filter selects only online materials`() {
        fun pkg(prefix: String) = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(id = "1", english = "$prefix line one.", translation = "$prefix خط یک."),
                JsonSubtitle(id = "2", english = "$prefix line two.", translation = "$prefix خط دو."),
                JsonSubtitle(id = "3", english = "$prefix line three.", translation = "$prefix خط سه.")
            )
        )
        val materials = listOf(
            QuizMaterial(QuizSource.MOVIE, pkg("Film")),
            QuizMaterial(QuizSource.ONLINE, pkg("Online")),
            QuizMaterial(QuizSource.BOOK, pkg("Book")),
        )
        val onlyOnline = JsonQuizBuilder.filter(materials, QuizSourceFilter.ONLINE)
        assertEquals(1, onlyOnline.size)
        assertTrue(onlyOnline.all { it.source == QuizSource.ONLINE })
        // "All sources" now means all three.
        assertEquals(3, JsonQuizBuilder.filter(materials, QuizSourceFilter.BOTH).size)
        val deck = JsonQuizBuilder.build(onlyOnline, maxQuestions = 10, seed = 1L)
        assertTrue(deck.isNotEmpty())
        assertTrue(deck.all { it.source == QuizSource.ONLINE })
    }
}
