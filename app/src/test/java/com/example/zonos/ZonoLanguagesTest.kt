package com.example.zonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The learning pair stores the source language as free text; the TTS must map the usual spellings. */
class ZonoLanguagesTest {

    @Test
    fun englishSpellingsMapToEngineIds() {
        assertEquals("en_us", ZonoLanguages.guess("English"))
        assertEquals("de", ZonoLanguages.guess("german"))
        assertEquals("de", ZonoLanguages.guess("Deutsch"))
        assertEquals("fr_fr", ZonoLanguages.guess("  French "))
        assertEquals("pt_br", ZonoLanguages.guess("Portuguese"))
        assertEquals("cmn", ZonoLanguages.guess("Chinese (Mandarin)"))
        assertEquals("cmn", ZonoLanguages.guess("Mandarin"))
    }

    @Test
    fun persianSpellingsMapToEngineIds() {
        assertEquals("en_us", ZonoLanguages.guess("انگلیسی"))
        assertEquals("de", ZonoLanguages.guess("آلمانی"))
        assertEquals("ko", ZonoLanguages.guess("کره‌ای"))
        assertEquals("ko", ZonoLanguages.guess("کره ای"))
        assertEquals("ja", ZonoLanguages.guess("ژاپنی"))
    }

    @Test
    fun engineIdsPassThrough() {
        for (lang in ZonoLanguages.ALL) assertEquals(lang.id, ZonoLanguages.guess(lang.id))
    }

    @Test
    fun unknownLanguagesFallBackToEnglishOnlyInResolve() {
        assertNull(ZonoLanguages.guess("Persian"))
        assertNull(ZonoLanguages.guess(""))
        assertNull(ZonoLanguages.guess(null))
        assertEquals(ZonoLanguages.DEFAULT_ID, ZonoLanguages.resolve("Persian"))
        assertEquals(ZonoLanguages.DEFAULT_ID, ZonoLanguages.resolve(null))
    }

    @Test
    fun idsAreUniqueAndNamesPresent() {
        assertEquals(ZonoLanguages.ALL.size, ZonoLanguages.ALL.map { it.id }.toSet().size)
        for (lang in ZonoLanguages.ALL) {
            assert(lang.nameEn.isNotBlank() && lang.nameFa.isNotBlank()) { lang.id }
        }
    }
}
