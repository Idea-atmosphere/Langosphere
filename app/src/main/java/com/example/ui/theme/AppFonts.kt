package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * One font choice: either one of the built-in platform families ("default",
 * "serif", "sansserif", "monospace", "cursive") or a user-imported font file
 * ("custom" + the imported file's absolute path). The resolved Compose
 * [FontFamily] is built once at construction, so recompositions never
 * re-read the file from disk.
 */
data class FontChoice(val key: String = "default", val customPath: String? = null) {

    /** The Compose family for this choice, or null for "default". */
    val family: FontFamily? = when (key) {
        "serif" -> FontFamily.Serif
        "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "custom" -> customPath?.let { path ->
            try {
                if (File(path).exists()) FontFamily(Font(File(path))) else null
            } catch (e: Exception) {
                null
            }
        }
        else -> null
    }

    companion object {
        /** The neutral "leave it to the design / inherit" choice. */
        val DEFAULT = FontChoice("default")
    }
}

/**
 * Where a font choice applies (Settings ▸ Theme ▸ Font). Every scope keeps
 * TWO independent choices — one for English/general content and one for
 * Persian content — persisted under separate pref keys:
 *
 *  - [APP] — the whole app: the choice is layered over every design's type
 *    scale (see `Typography.withFontFamily` in Type.kt). The app UI language
 *    picks which of the two is active (FA UI → the Persian choice, EN UI →
 *    the English one).
 *  - [READER] — only the reading files (PDF/text pages) in ReaderScreen;
 *    each page picks per its own content language.
 *  - [LEITNER] — only the Leitner flashcards; the word and the definition
 *    each pick per their own content language.
 *
 * The fourth scope, "subtitles only", keeps living in the player prefs
 * (`PlayerPrefs.fontEn` / `fontFa`, EN and FA separately) since it was the
 * original font feature and the video player already renders from it.
 */
enum class AppFontScope(
    val keyPref: String,
    val pathPref: String,
    val fileName: String,
    val keyPrefFa: String,
    val pathPrefFa: String,
    val fileNameFa: String,
) {
    APP(
        "app_font_key", "app_font_custom_path", "custom_app_font.ttf",
        "app_font_fa_key", "app_font_fa_custom_path", "custom_app_font_fa.ttf",
    ),
    READER(
        "reader_font_key", "reader_font_custom_path", "custom_reader_font.ttf",
        "reader_font_fa_key", "reader_font_fa_custom_path", "custom_reader_font_fa.ttf",
    ),
    LEITNER(
        "leitner_font_key", "leitner_font_custom_path", "custom_leitner_font.ttf",
        "leitner_font_fa_key", "leitner_font_fa_custom_path", "custom_leitner_font_fa.ttf",
    );
}

/**
 * Process-wide holder for the per-scope font choices, backed by Compose
 * state and mirrored into SharedPreferences — the same pattern as
 * [AppPaletteState], so any screen reads the choice reactively and the
 * change is visible immediately without prop drilling.
 *
 * Inheritance rule (per the user's request): the WHOLE-APP font applies to
 * every scope (reader pages, subtitles, Leitner cards) as the base, and a
 * scope only deviates where the user explicitly picked a font for THAT
 * scope (and language). [resolvedFamily] encodes that chain:
 *
 *   scope(language) → scope(English) → app(Persian) → app(English)
 *
 * (The Persian links fall through to the English choice so a single
 * "general" font pick still covers everything, while a Persian-only pick
 * never hijacks Latin text.)
 */
object AppFontState {
    private const val PREFS_NAME = "app_prefs"

    /** Whole-app font — English/general content. */
    var app: FontChoice by mutableStateOf(FontChoice.DEFAULT)
    /** Whole-app font — Persian content (FA UI). */
    var appFa: FontChoice by mutableStateOf(FontChoice.DEFAULT)
    var reader: FontChoice by mutableStateOf(FontChoice.DEFAULT)
    var readerFa: FontChoice by mutableStateOf(FontChoice.DEFAULT)
    var leitner: FontChoice by mutableStateOf(FontChoice.DEFAULT)
    var leitnerFa: FontChoice by mutableStateOf(FontChoice.DEFAULT)

    fun choice(scope: AppFontScope, fa: Boolean = false): FontChoice = when (scope) {
        AppFontScope.APP -> if (fa) appFa else app
        AppFontScope.READER -> if (fa) readerFa else reader
        AppFontScope.LEITNER -> if (fa) leitnerFa else leitner
    }

    /** Called before the first composition (MainActivity.onCreate). */
    fun restore(prefs: SharedPreferences) {
        AppFontScope.entries.forEach { scope ->
            restoreLanguage(prefs, scope, fa = false)
            restoreLanguage(prefs, scope, fa = true)
        }
    }

    private fun restoreLanguage(prefs: SharedPreferences, scope: AppFontScope, fa: Boolean) {
        val keyPref = if (fa) scope.keyPrefFa else scope.keyPref
        val pathPref = if (fa) scope.pathPrefFa else scope.pathPref
        val storedKey = prefs.getString(keyPref, "default") ?: "default"
        val storedPath = prefs.getString(pathPref, null)
        // A stale "custom" key (deleted/moved file) falls back to default.
        val choice = if (storedKey == "custom" &&
            (storedPath == null || !File(storedPath).exists())
        ) {
            FontChoice.DEFAULT
        } else {
            FontChoice(storedKey, storedPath)
        }
        when {
            scope == AppFontScope.APP && !fa -> app = choice
            scope == AppFontScope.APP -> appFa = choice
            scope == AppFontScope.READER && !fa -> reader = choice
            scope == AppFontScope.READER -> readerFa = choice
            scope == AppFontScope.LEITNER && !fa -> leitner = choice
            scope == AppFontScope.LEITNER -> leitnerFa = choice
        }
    }

    fun set(context: Context, scope: AppFontScope, fa: Boolean = false, choice: FontChoice) {
        when {
            scope == AppFontScope.APP && !fa -> app = choice
            scope == AppFontScope.APP -> appFa = choice
            scope == AppFontScope.READER && !fa -> reader = choice
            scope == AppFontScope.READER -> readerFa = choice
            scope == AppFontScope.LEITNER && !fa -> leitner = choice
            scope == AppFontScope.LEITNER -> leitnerFa = choice
        }
        val keyPref = if (fa) scope.keyPrefFa else scope.keyPref
        val pathPref = if (fa) scope.pathPrefFa else scope.pathPref
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyPref, choice.key)
        if (choice.customPath == null) editor.remove(pathPref)
        else editor.putString(pathPref, choice.customPath)
        editor.apply()
    }

    /** The whole-app family for a content language (FA falls through to EN). */
    fun resolvedAppFamily(fa: Boolean): FontFamily? =
        if (fa) appFa.family ?: app.family else app.family

    /**
     * The family a scope renders a given content language with: the scope's
     * own choice for that language, else the scope's English choice, else
     * the whole-app choice (Persian first, then English). Null = follow the
     * design's own type scale.
     */
    fun resolvedFamily(scope: AppFontScope, fa: Boolean): FontFamily? {
        choice(scope, fa).family?.let { return it }
        if (fa) {
            choice(scope, fa = false).family?.let { return it }
        }
        return resolvedAppFamily(fa)
    }
}

/**
 * The family a subtitle language renders with (the video overlay and the
 * subtitle list): the player's own EN/FA choice (Settings ▸ Theme ▸ Font ▸
 * Subtitles), falling back to the whole-app font for that language when it
 * is left on "default" — so the app font reaches the subtitles too unless
 * the user defined a separate subtitle font.
 */
fun resolvedSubtitleFamily(key: String, customPath: String?, fa: Boolean): FontFamily? {
    FontChoice(key, customPath).family?.let { return it }
    return AppFontState.resolvedAppFamily(fa)
}
