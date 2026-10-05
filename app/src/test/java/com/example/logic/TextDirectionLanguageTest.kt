package com.example.logic

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The declared-language direction rules of the lesson popup: a learning
 * JSON's metadata.language / targetLanguage say which languages the content
 * is in, and a DECLARED language overrides the per-text script guess — so a
 * Persian translation line that opens with a Latin word still reads as a
 * whole right-to-left, and an English line in an RTL app still reads LTR.
 */
class TextDirectionLanguageTest {

    // ── isRtlLanguageName: what counts as an RTL language ──

    @Test
    fun `native-script names are rtl`() {
        assertTrue(isRtlLanguageName("فارسی"))
        assertTrue(isRtlLanguageName("العربية"))
        assertTrue(isRtlLanguageName("עברית"))
    }

    @Test
    fun `english names and iso codes of rtl languages`() {
        for (name in listOf("Persian", "Farsi", "Dari", "Arabic", "Hebrew", "Urdu", "Pashto", "Sorani", "Uyghur")) {
            assertTrue("expected RTL: $name", isRtlLanguageName(name))
        }
        for (code in listOf("fa", "ar", "he", "ur", "ps", "sd", "ug", "yi", "dv", "ckb")) {
            assertTrue("expected RTL: $code", isRtlLanguageName(code))
        }
    }

    @Test
    fun `compound names and region tags`() {
        assertTrue(isRtlLanguageName("fa-IR"))
        assertTrue(isRtlLanguageName("Persian (Farsi)"))
        assertTrue(isRtlLanguageName("ar-SA"))
    }

    @Test
    fun `ltr languages stay ltr`() {
        for (name in listOf("English", "German", "Spanish", "Kurmanji", "en", "de", "es", "tr", "zh", "ja")) {
            assertFalse("expected LTR: $name", isRtlLanguageName(name))
        }
    }

    @Test
    fun `blank names are not languages`() {
        assertFalse(isRtlLanguageName(""))
        assertFalse(isRtlLanguageName("   "))
    }

    // ── declaredLanguageRtl: null means "nothing declared" ──

    @Test
    fun `undeclared fields return null`() {
        assertNull(declaredLanguageRtl(null))
        assertNull(declaredLanguageRtl(""))
        assertNull(declaredLanguageRtl("   "))
    }

    @Test
    fun `declared fields resolve`() {
        assertEquals(java.lang.Boolean.TRUE, declaredLanguageRtl("Persian"))
        assertEquals(java.lang.Boolean.FALSE, declaredLanguageRtl("English"))
        assertEquals(java.lang.Boolean.TRUE, declaredLanguageRtl("فارسی"))
    }

    // ── resolveDirection / resolveAlign: the declaration wins ──

    @Test
    fun `a declared rtl language keeps mixed lines rtl`() {
        // First strong character is Latin, but the JSON declares Persian —
        // the whole line must read right-to-left.
        assertEquals(TextDirection.Rtl, resolveDirection("JSON 3 is imported", true))
        assertEquals(TextAlign.Right, resolveAlign("JSON 3 is imported", true))
    }

    @Test
    fun `a declared ltr language keeps rtl-script lines ltr`() {
        // A Persian-looking line in a package whose target language is
        // declared English reads left-to-right.
        assertEquals(TextDirection.Ltr, resolveDirection("سلام دنیا", false))
        assertEquals(TextAlign.Left, resolveAlign("سلام دنیا", false))
    }

    @Test
    fun `without a declaration the text's own script decides`() {
        assertEquals(TextDirection.Rtl, resolveDirection("سلام دنیا", null))
        assertEquals(TextDirection.Ltr, resolveDirection("Hello world", null))
        assertEquals(TextAlign.Right, resolveAlign("سلام", null))
        assertEquals(TextAlign.Left, resolveAlign("Hello", null))
    }
}
