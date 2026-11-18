package com.example.zonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The opts document sent with every reader generate: language follows the caller, pace follows the speed slider. */
class ReaderOptsTest {

    private val table = listOf(
        -4f to 2.881f, -2f to 2.212f, -1f to 1.451f, 0f to 1.0f,
        1f to 0.779f, 2f to 0.63f, 4f to 0.549f
    )

    @Test
    fun languageIsForwarded() {
        val opts = readerOptsJson(1.0f, table, -2f, 2f, "de")
        assertTrue(opts, opts.contains("\"language\":\"de\""))
        val withDesign = readerOptsJsonWithDesign(1.0f, table, -2f, 2f, null, emptyList(), false, "fr_fr")
        assertTrue(withDesign, withDesign.contains("\"language\":\"fr_fr\""))
        assertTrue(withDesign, withDesign.contains("\"speaker_mode\":\"embedding\""))
    }

    @Test
    fun defaultLanguageIsEnglish() {
        assertTrue(readerOptsJson(1.0f, table, -2f, 2f).contains("\"language\":\"en_us\""))
    }

    @Test
    fun fasterSpeedRaisesTheTempoDose() {
        val fast = readerOptsJsonWithDesign(1.5f, table, -2f, 2f, null, emptyList(), false)
        assertTrue(fast, fast.contains("delivery.tempo"))
        val slow = readerOptsJsonWithDesign(0.7f, table, -2f, 2f, null, emptyList(), false)
        assertTrue(slow, slow.contains("delivery.tempo\":-"))
    }

    @Test
    fun designSourceDefaultsWithoutAStudioDocument() {
        val (design, source) = resolveReaderDesign(null, "default_940eaff39a929da0", null)
        assertEquals(null, design)
        assertEquals(DesignSource.Default, source)
    }

    @Test
    fun studioLanguageNormalizes() {
        assertEquals("en", normalizeStudioLang(null))
        assertEquals("fa", normalizeStudioLang("fa"))
        assertEquals("fa", normalizeStudioLang(null, "fa"))
        assertEquals("en", normalizeStudioLang("xx", "en"))
    }

    @Test
    fun jsStringResultsUnwrap() {
        assertEquals("{\"a\":1}", unwrapJsStringResult("\"{\\\"a\\\":1}\""))
        assertEquals("null", unwrapJsStringResult("null"))
    }
}
