package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt contract is the only thing standing between the app and a model
 * that happily returns paragraphs, six words per entry, or respelled
 * pronunciations. These tests assert the contract for every book mode rather
 * than for one sampled mode.
 */
class BookPromptTemplatesTest {

    @Test
    fun `every mode carries the dynamic CEFR placeholder before resolution`() {
        for (mode in BookPromptTemplates.BookPromptMode.values()) {
            val template = BookPromptTemplates.buildTemplate(
                mode = mode,
                targetLanguage = "Persian",
                chunkSize = 20,
                sourceLanguage = "English",
            )
            assertTrue(
                mode.name,
                template.contains(BookPromptTemplates.CEFR_PLACEHOLDER),
            )
        }
    }

    @Test
    fun `resolving a level removes every placeholder`() {
        for (mode in BookPromptTemplates.BookPromptMode.values()) {
            for (level in BookPromptTemplates.LEVELS) {
                val resolved = BookPromptTemplates.resolveLevel(
                    BookPromptTemplates.buildTemplate(
                        mode = mode,
                        targetLanguage = "Persian",
                        chunkSize = 20,
                        sourceLanguage = "English",
                    ),
                    level,
                )
                assertFalse(
                    "$mode/$level",
                    resolved.contains(BookPromptTemplates.CEFR_PLACEHOLDER),
                )
                assertFalse(
                    "$mode/$level",
                    resolved.contains(BookPromptTemplates.CEFR_GUIDANCE_PLACEHOLDER),
                )
                assertTrue("$mode/$level", resolved.contains(level))
            }
        }
    }

    @Test
    fun `no book prompt asks for paragraph level entries`() {
        for (mode in BookPromptTemplates.BookPromptMode.values()) {
            val prompt = BookPromptTemplates.buildPrompt(
                level = "B1",
                mode = mode,
                targetLanguage = "Persian",
                chunkSize = 20,
                sourceLanguage = "English",
            ).lowercase()
            assertFalse(mode.name, prompt.contains("one source paragraph = one entry"))
            assertTrue(mode.name, prompt.contains("sentence"))
        }
    }

    @Test
    fun `json modes state the one sentence one entry rule and the word ceiling`() {
        val jsonModes = BookPromptTemplates.BookPromptMode.values()
            .filter { mode -> BookPromptTemplates.producesJsonPackage(mode) }
        assertTrue(jsonModes.isNotEmpty())
        for (mode in jsonModes) {
            val prompt = BookPromptTemplates.buildPrompt(
                level = "B2",
                mode = mode,
                targetLanguage = "Persian",
                chunkSize = 20,
                sourceLanguage = "English",
            )
            assertTrue(mode.name, prompt.contains("one entry"))
            assertTrue(
                mode.name,
                prompt.contains(BookPromptTemplates.MAX_WORDS_PER_SENTENCE.toString()),
            )
            assertTrue(mode.name, prompt.contains("IPA"))
            assertTrue(mode.name, prompt.contains("formatVersion"))
            assertTrue(mode.name, prompt.contains("subtitles"))
        }
    }

    @Test
    fun `front matter is one entry per line in every json mode`() {
        val jsonModes = BookPromptTemplates.BookPromptMode.values()
            .filter { mode -> BookPromptTemplates.producesJsonPackage(mode) }
        assertTrue(jsonModes.isNotEmpty())
        for (mode in jsonModes) {
            val prompt = BookPromptTemplates.buildPrompt(
                level = "B1",
                mode = mode,
                targetLanguage = "Persian",
                chunkSize = 15,
                sourceLanguage = "English",
            )
            // The reported sample put a testimonial signature, the title block
            // and the publisher's blurb in ONE entry, and no single line of the
            // page can match it. The rule has to be stated where the anchors are
            // explained and again among the checks before answering.
            assertTrue(mode.name, prompt.contains("Do not merge front matter"))
            assertTrue(mode.name, prompt.contains("Front matter is NOT an exception"))
            assertTrue(mode.name, prompt.contains("A merged entry cannot be placed on any single line"))
        }
    }

    @Test
    fun `book cleaning rules spell out de-hyphenation`() {
        for (mode in BookPromptTemplates.BookPromptMode.values()) {
            val prompt = BookPromptTemplates.buildPrompt(
                level = "B1",
                mode = mode,
                targetLanguage = "Persian",
                chunkSize = 15,
                sourceLanguage = "English",
            )
            assertTrue(mode.name, prompt.contains("HYPHENATION FIX"))
            assertTrue(mode.name, prompt.contains("Never leave broken hyphens inside"))
            assertTrue(mode.name, prompt.contains("WHITESPACE"))
            assertTrue(mode.name, prompt.contains("STRUCTURAL LINES"))
            // The field the reader character-matches against must stay verbatim.
            assertTrue(mode.name, prompt.contains("MUST remain identical to the original English sentence"))
        }
    }

    @Test
    fun `multi-page chunks carry the strict page boundary and 1-to-1 parity rules`() {
        for (mode in BookPromptTemplates.BookPromptMode.values()) {
            val prompt = BookPromptTemplates.buildPrompt(
                level = "B1",
                mode = mode,
                targetLanguage = "Persian",
                chunkSize = 15,
                sourceLanguage = "English",
            )
            // Once in the anchor contract, once in the anchoring rules: a model
            // that misses the page transition is what puts a translation on the
            // wrong page, so the rule is stated where the markers are explained
            // and again where the anchors are validated.
            assertEquals(
                mode.name,
                2,
                Regex(Regex.escape("MULTI-PAGE BOUNDARY STRICTNESS")).findAll(prompt).count(),
            )
            assertEquals(
                mode.name,
                2,
                Regex(Regex.escape("1-TO-1 PARITY")).findAll(prompt).count(),
            )
            assertTrue(mode.name, prompt.contains("reset `sentenceInPage: 1`"))
            assertTrue(mode.name, prompt.contains("Never shift a sentence to the wrong page"))
        }
    }

    @Test
    fun `chunk sizes stay inside the token safe band`() {
        assertEquals(
            BookPromptTemplates.MIN_CHUNK_SENTENCES,
            BookPromptTemplates.normalizeChunkSize(1),
        )
        assertEquals(
            BookPromptTemplates.MAX_CHUNK_SENTENCES,
            BookPromptTemplates.normalizeChunkSize(500),
        )
        assertEquals(20, BookPromptTemplates.normalizeChunkSize(20))
        for (size in BookPromptTemplates.CHUNK_SIZES) {
            assertTrue(size >= BookPromptTemplates.MIN_CHUNK_SENTENCES)
            assertTrue(size <= BookPromptTemplates.MAX_CHUNK_SENTENCES)
        }
    }

    @Test
    fun `unknown levels fall back instead of leaking into the prompt`() {
        assertEquals(BookPromptTemplates.DEFAULT_LEVEL, BookPromptTemplates.safeLevel(""))
        assertEquals(BookPromptTemplates.DEFAULT_LEVEL, BookPromptTemplates.safeLevel("Z9"))
        assertEquals("C2", BookPromptTemplates.safeLevel("c2"))
    }

    @Test
    fun `mode names and descriptions exist in both languages`() {
        for (mode in BookPromptTemplates.BookPromptMode.values()) {
            assertTrue(mode.name, BookPromptTemplates.modeName(mode, true).isNotBlank())
            assertTrue(mode.name, BookPromptTemplates.modeName(mode, false).isNotBlank())
            assertTrue(mode.name, BookPromptTemplates.modeDescription(mode, true).isNotBlank())
            assertTrue(mode.name, BookPromptTemplates.modeDescription(mode, false).isNotBlank())
        }
    }
}
