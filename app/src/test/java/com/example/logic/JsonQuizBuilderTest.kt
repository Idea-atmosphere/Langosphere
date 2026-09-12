package com.example.logic

import com.example.model.JsonLesson
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitleMetadata
import com.example.model.JsonSubtitlePackage
import com.example.model.JsonWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the beta "quiz from JSON" builder (Leitner tab): the questions must
 * come only from data the imported package actually carries, the correct answer
 * must always be one of the options, and a file without learning data must
 * produce no questions instead of broken ones.
 */
class JsonQuizBuilderTest {

    private fun packageWithWords(): JsonSubtitlePackage = JsonSubtitlePackage(
        formatVersion = 1,
        metadata = JsonSubtitleMetadata(language = "English", targetLanguage = "Persian", level = "B1"),
        subtitles = listOf(
            JsonSubtitle(
                id = "1",
                start = 1.0,
                end = 2.0,
                english = "I have been waiting here.",
                translation = "من اینجا منتظر بوده‌ام.",
                lesson = JsonLesson(
                    grammar = "Present perfect continuous",
                    grammarTranslation = "حال کامل استمراری",
                    explanation = "An action that started in the past and continues."
                ),
                words = listOf(
                    JsonWord(word = "waiting", translation = "منتظر بودن", meaningInContext = "staying until something happens"),
                    JsonWord(word = "here", translation = "اینجا")
                )
            ),
            JsonSubtitle(
                id = "2",
                start = 3.0,
                end = 4.0,
                english = "Take it personally.",
                translation = "به دل نگیر.",
                lesson = JsonLesson(grammar = "Adverb use", grammarTranslation = "قید"),
                words = listOf(
                    JsonWord(word = "take it personally", translation = "به دل گرفتن"),
                    JsonWord(word = "personally", translation = "شخصاً")
                )
            ),
            JsonSubtitle(
                id = "3",
                english = "She left early.",
                translation = "او زود رفت.",
                words = listOf(JsonWord(word = "early", translation = "زود"))
            )
        )
    )

    @Test
    fun `questions are built from words sentences and grammar`() {
        val pkg = packageWithWords()
        val questions = JsonQuizBuilder.build(pkg, maxQuestions = 100, seed = 1L)

        assertTrue("expected questions, got none", questions.isNotEmpty())
        assertTrue(questions.any { it.type == JsonQuizType.WORD_TO_TRANSLATION })
        assertTrue(questions.any { it.type == JsonQuizType.TRANSLATION_TO_WORD })
        assertTrue(questions.any { it.type == JsonQuizType.SENTENCE_TO_TRANSLATION })
        assertTrue(questions.any { it.type == JsonQuizType.SENTENCE_TO_GRAMMAR })
    }

    @Test
    fun `every question contains its own answer as an option`() {
        val questions = JsonQuizBuilder.build(packageWithWords(), maxQuestions = 100, seed = 7L)
        questions.forEach { question ->
            assertTrue(
                "answer missing from options: ${question.answer} / ${question.options}",
                question.options.contains(question.answer)
            )
            assertTrue(question.options.size >= 2)
            assertEquals(question.options.size, question.options.distinct().size)
        }
    }

    @Test
    fun `max questions is respected`() {
        val questions = JsonQuizBuilder.build(packageWithWords(), maxQuestions = 4, seed = 3L)
        assertEquals(4, questions.size)
    }

    @Test
    fun `the same seed deals the same quiz again`() {
        val pkg = packageWithWords()
        val first = JsonQuizBuilder.build(pkg, maxQuestions = 6, seed = 42L).map { it.id }
        val again = JsonQuizBuilder.build(pkg, maxQuestions = 6, seed = 42L).map { it.id }
        val other = JsonQuizBuilder.build(pkg, maxQuestions = 6, seed = 43L).map { it.id }

        // Deterministic: "retry the same quiz" has to reproduce it exactly.
        assertEquals(first, again)
        // Any seed still produces a playable quiz.
        assertEquals(first.size, other.size)
        assertTrue(other.isNotEmpty())
    }

    @Test
    fun `a translation-only package still produces sentence questions`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(id = "1", english = "Hello there.", translation = "سلام."),
                JsonSubtitle(id = "2", english = "See you tomorrow.", translation = "فردا می‌بینمت."),
                JsonSubtitle(id = "3", english = "It is late.", translation = "دیر شده.")
            )
        )
        val questions = JsonQuizBuilder.build(pkg, maxQuestions = 10, seed = 5L)
        assertTrue(questions.isNotEmpty())
        assertTrue(questions.all { it.type == JsonQuizType.SENTENCE_TO_TRANSLATION })
    }

    @Test
    fun `a package without translations produces no questions`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(id = "1", english = "Hello there."),
                JsonSubtitle(id = "2", english = "See you.")
            )
        )
        assertEquals(0, JsonQuizBuilder.build(pkg, maxQuestions = 10, seed = 5L).size)
        assertEquals(0, JsonQuizBuilder.availableQuestions(pkg))
    }

    @Test
    fun `word questions carry the sentence they came from`() {
        val questions = JsonQuizBuilder.build(packageWithWords(), maxQuestions = 100, seed = 11L)
        val wordQuestion = questions.first { it.type == JsonQuizType.WORD_TO_TRANSLATION && it.word == "waiting" }
        assertEquals("I have been waiting here.", wordQuestion.contextSentence)
        assertEquals("staying until something happens", wordQuestion.note)
        assertEquals("1", wordQuestion.subtitleId)
    }

    @Test
    fun `the same word in two lines is only asked once`() {
        val pkg = JsonSubtitlePackage(
            subtitles = listOf(
                JsonSubtitle(id = "1", english = "One.", translation = "یک.", words = listOf(JsonWord(word = "waiting", translation = "منتظر بودن"))),
                JsonSubtitle(id = "2", english = "Two.", translation = "دو.", words = listOf(JsonWord(word = "Waiting", translation = "منتظر بودن"))),
                JsonSubtitle(id = "3", english = "Three.", translation = "سه.", words = listOf(JsonWord(word = "early", translation = "زود")))
            )
        )
        val questions = JsonQuizBuilder.build(pkg, maxQuestions = 100, seed = 2L)
        val waitingQuestions = questions.filter { it.word?.lowercase() == "waiting" }
        assertTrue("expected at most two directions for one word", waitingQuestions.size <= 2)
    }
}
