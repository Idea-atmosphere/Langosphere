package com.example.logic

import androidx.compose.ui.text.style.TextDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TextDirectionUtils]: the first-strong-character rule used to
 * render user-entered text RTL (Persian/Arabic) or LTR (Latin, ...)
 * independently of the app's menu language.
 */
class TextDirectionUtilsTest {

    // ── pure scripts ──

    @Test
    fun `pure Persian text is RTL`() {
        assertTrue(TextDirectionUtils.isRtl("سلام دنیا"))
        assertEquals(TextDirection.Rtl, TextDirectionUtils.direction("در حال بارگذاری فایل زیرنویس"))
    }

    @Test
    fun `pure Arabic text is RTL`() {
        assertTrue(TextDirectionUtils.isRtl("مرحبا بك في البرنامج"))
    }

    @Test
    fun `pure English text is LTR`() {
        assertFalse(TextDirectionUtils.isRtl("Hello, world!"))
        assertEquals(TextDirection.Ltr, TextDirectionUtils.direction("The quick brown fox"))
    }

    @Test
    fun `CJK text is LTR`() {
        // CJK ideographs carry Unicode bidi class L, so they are treated
        // like any other LTR script.
        assertFalse(TextDirectionUtils.isRtl("你好世界"))
    }

    // ── mixed text: the first strong character decides ──

    @Test
    fun `leading Latin decides LTR even with Persian after it`() {
        // "hello دنیا" — the first strong character is 'h', so the
        // paragraph base direction is LTR (the Persian part still shapes
        // right-to-left internally).
        assertFalse(TextDirectionUtils.isRtl("hello دنیا"))
    }

    @Test
    fun `English with trailing Persian stays LTR`() {
        assertFalse(TextDirectionUtils.isRtl("Good morning دنیا"))
    }

    @Test
    fun `weak leading characters are skipped`() {
        // Digits, punctuation and whitespace are weak/neutral and must not
        // decide the direction.
        assertTrue(TextDirectionUtils.isRtl("123 - فارسی"))
        assertTrue(TextDirectionUtils.isRtl("«سلام»"))
        assertTrue(TextDirectionUtils.isRtl("... ۱۲۳ و "))
        assertFalse(TextDirectionUtils.isRtl("(404) error: "))
        assertFalse(TextDirectionUtils.isRtl("00:12 / 01:30"))
    }

    @Test
    fun `digits alone fall back to LTR`() {
        // Persian/Arabic-Indic digits (U+0660-0669, U+06F0-06F9) are WEAK
        // in the Unicode bidi classes, so a string of digits has no strong
        // character and uses the LTR fallback.
        assertFalse(TextDirectionUtils.isRtl("۱۲ ۴۵"))
        assertFalse(TextDirectionUtils.isRtl("١٢ ٤٥٦"))
    }

    @Test
    fun `empty and whitespace-only text falls back to LTR`() {
        assertFalse(TextDirectionUtils.isRtl(""))
        assertFalse(TextDirectionUtils.isRtl("   \n\t "))
    }

    // ── direction() helper ──

    @Test
    fun `direction maps isRtl to TextDirection`() {
        assertEquals(TextDirection.Rtl, "فارسی".autoTextDirection())
        assertEquals(TextDirection.Ltr, "English".autoTextDirection())
        assertEquals(TextDirection.Ltr, "".autoTextDirection())
        assertEquals(TextDirection.Rtl, "ترجمه: translation".autoTextDirection())
        assertEquals(TextDirection.Ltr, "translation: ترجمه".autoTextDirection())
    }
}
