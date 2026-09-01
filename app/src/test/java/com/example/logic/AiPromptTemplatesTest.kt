package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Tutorial & AI Learning prompt templates: every level and
 * mode must produce a non-empty, ready-to-copy prompt that targets the
 * app's JSON parser format.
 */
class AiPromptTemplatesTest {

    @Test
    fun `all levels are the six cefr levels`() {
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), AiPromptTemplates.LEVELS)
    }

    @Test
    fun `every level and mode produces a prompt`() {
        for (level in AiPromptTemplates.LEVELS) {
            for (mode in AiPromptTemplates.PromptMode.entries) {
                val prompt = AiPromptTemplates.buildPrompt(level, mode)
                assertTrue("prompt for $level/$mode must not be blank", prompt.isNotBlank())
                assertTrue("prompt for $level/$mode must mention the level", prompt.contains(level))
            }
        }
    }

    @Test
    fun `translation only prompt mentions original and translation but not lessons`() {
        val prompt = AiPromptTemplates.buildPrompt("B1", AiPromptTemplates.PromptMode.TRANSLATION_ONLY)
        assertTrue(prompt.contains("english", ignoreCase = true))
        assertTrue(prompt.contains("translation", ignoreCase = true))
        assertFalse(prompt.contains("grammar", ignoreCase = true))
        assertFalse(prompt.contains("words", ignoreCase = true))
    }

    @Test
    fun `translation plus learning prompt covers grammar vocabulary and structure`() {
        val prompt = AiPromptTemplates.buildPrompt("A2", AiPromptTemplates.PromptMode.TRANSLATION_LEARNING)
        assertTrue(prompt.contains("grammar", ignoreCase = true))
        assertTrue(prompt.contains("vocabulary", ignoreCase = true))
        assertTrue(prompt.contains("structure", ignoreCase = true))
        assertTrue(prompt.contains("partOfSpeech"))
        assertTrue(prompt.contains("pronunciation", ignoreCase = true))
        assertTrue(prompt.contains("difficulty", ignoreCase = true))
    }

    @Test
    fun `word analysis prompt covers word role and context`() {
        val prompt = AiPromptTemplates.buildPrompt("C1", AiPromptTemplates.PromptMode.WORD_ANALYSIS)
        assertTrue(prompt.contains("partOfSpeech"))
        assertTrue(prompt.contains("meaningInContext"))
        assertTrue(prompt.contains("examples"))
        assertTrue(prompt.contains("extraExplanation"))
    }

    @Test
    fun `every mode embeds the parser compatible schema except word analysis`() {
        // Translation modes must target the app's JSON package format.
        for (mode in listOf(
            AiPromptTemplates.PromptMode.TRANSLATION_ONLY,
            AiPromptTemplates.PromptMode.TRANSLATION_LEARNING
        )) {
            val prompt = AiPromptTemplates.buildPrompt("B2", mode)
            assertTrue(prompt.contains("formatVersion"))
            assertTrue(prompt.contains("metadata"))
            assertTrue(prompt.contains("subtitles"))
        }
        // Word analysis returns a single word object (used for word clicks).
        val wordPrompt = AiPromptTemplates.buildPrompt("B2", AiPromptTemplates.PromptMode.WORD_ANALYSIS)
        assertTrue(wordPrompt.contains("word"))
    }

    @Test
    fun `level descriptions are readable`() {
        assertEquals("A1 Beginner", AiPromptTemplates.levelDescription("A1"))
        assertEquals("C2 Native-like", AiPromptTemplates.levelDescription("C2"))
        assertEquals("B1", AiPromptTemplates.levelDescription("b1"))
    }
}
