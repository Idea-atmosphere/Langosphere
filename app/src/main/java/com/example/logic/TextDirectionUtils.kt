package com.example.logic

import androidx.compose.ui.text.style.TextDirection

/**
 * Automatic RTL/LTR detection for user-entered content (subtitles, reader
 * documents, dictionary lookups, chat messages, translated lines, ...).
 *
 * The app's chrome is deliberately kept left-to-right at the window level
 * (see [com.example.MainActivity] and the LTR providers in MainScreen), and
 * the menu language (FA/EN) only changes the translation of the UI labels.
 * Because of that, every piece of *user* text needs its own per-paragraph
 * direction: a paragraph whose first strong character is Arabic/Persian
 * (or any other RTL script) is rendered right-to-left, a Latin paragraph
 * left-to-right — regardless of which language the app menu is in.
 *
 * Detection follows the Unicode bidirectional algorithm's "first strong
 * directional character" rule: weak/neutral characters (digits,
 * punctuation, whitespace) are skipped, and the first LTR or RTL strong
 * character wins. Texts without any strong character (e.g. pure numbers
 * or punctuation) fall back to LTR, matching the app's default layout.
 */
object TextDirectionUtils {

    /**
     * @return true when the first strong-directional character in [text] is
     * an RTL script (Persian/Arabic/Hebrew etc.), following the Unicode
     * "first strong character" rule: weak/neutral characters (digits,
     * punctuation, whitespace) are skipped while looking for that first
     * strong character, and the FIRST strong character found (LTR or RTL)
     * decides the result — a leading Latin word keeps a mixed paragraph LTR
     * even when RTL text follows later, and vice versa. Falls back to LTR
     * when no strong character exists.
     */
    fun isRtl(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE -> return false

                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                // Persian and Arabic letters are classified by Unicode as
                // "Arabic Letter" (AL), a distinct bidi class from the plain
                // Hebrew-style "R" class above. Missing this case was why
                // pure Persian/Arabic text was silently treated as LTR.
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE -> return true

                else -> Unit
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    /** Paragraph direction for [text]: RTL for Persian/Arabic, LTR otherwise. */
    fun direction(text: String): TextDirection =
        if (isRtl(text)) TextDirection.Rtl else TextDirection.Ltr
}

/**
 * Convenience for call sites: merge the result into the `TextStyle`
 * (Material3 `Text` takes the paragraph direction through
 * [androidx.compose.ui.text.TextStyle.textDirection], usually together with
 * [androidx.compose.ui.text.style.TextAlign.Start], which then follows the
 * detected paragraph direction).
 */
fun String.autoTextDirection(): TextDirection = TextDirectionUtils.direction(this)

fun String.autoTextAlign(): androidx.compose.ui.text.style.TextAlign =
    if (TextDirectionUtils.isRtl(this)) androidx.compose.ui.text.style.TextAlign.Right
    else androidx.compose.ui.text.style.TextAlign.Left

/**
 * True when [TextDirectionUtils.isRtl] detects Persian/Arabic script — used
 * by the per-scope font system (Settings ▸ Theme ▸ Font) to pick the
 * Persian font for Persian CONTENT (a reader page, a Leitner card, ...)
 * and the English/general font otherwise, independent of the menu language.
 */
fun String.isPersianText(): Boolean = TextDirectionUtils.isRtl(this)
