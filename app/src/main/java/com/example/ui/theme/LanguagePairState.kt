package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The learner's "source → target" language pair, edited inside
 * Settings ▸ Tutorial & AI Learning (the two fields right above the prompt
 * generator) and used everywhere the app used to say "English" / "Persian" as
 * if they were the only option:
 *
 *  - the AI prompt templates ([com.example.logic.AiPromptTemplates.buildPrompt]
 *    takes both names, so the generated prompt teaches the right pair),
 *  - the import labels and player labels ("German subtitle", "زیرنویس آلمانی"),
 *  - the AI assistant / translation screens, which already stored their target
 *    language under the same `ai_prefs.target_lang` key — this object is the
 *    single place that reads and writes it now.
 *
 * Whatever the learner types is what the app uses, character for character:
 * there is no table that renames "آلمانی" to "German" or "de" to "German". How
 * a language is spelled is the learner's own choice, and the AI on the other
 * end of the prompt understands both.
 *
 * It follows the same observable-singleton pattern as [MessageColorState] and
 * `SubtitleColorState`: the raw text is a Compose `mutableStateOf`, so every
 * label that reads it recomposes the moment the user types a new language.
 * The values live in the `ai_prefs` SharedPreferences file.
 */
object LanguagePairState {

    private const val PREFS_NAME = "ai_prefs"
    private const val KEY_SOURCE = "source_lang"
    private const val KEY_TARGET = "target_lang"

    /** Defaults: the pair the app was built around before this setting existed. */
    const val DEFAULT_SOURCE = "English"
    const val DEFAULT_TARGET = "Persian"

    /**
     * The AI screens used to default their target field to "فارسی". A stored
     * copy of that literal is migrated to [DEFAULT_TARGET] on restore, so the
     * field and the prompts both read "Persian" from now on.
     */
    private const val LEGACY_TARGET = "فارسی"

    /**
     * The smart-translation sheet used to store the ISO-style code of a
     * quick-pick chip ("fa", "de", ...) under the very same key. Those chips
     * are gone — the field is typed by hand now — so a stored code is turned
     * into the language name it meant, once, on restore. Anything that is not a
     * known old code is taken as the name the learner typed.
     *
     * Arabic is deliberately absent: it was removed from the app, so its old
     * code falls back to the default target instead of being spelled out.
     */
    private val LEGACY_CODES = mapOf(
        "fa" to DEFAULT_TARGET,
        "ar" to DEFAULT_TARGET,
        "tr" to "Turkish",
        "fr" to "French",
        "de" to "German",
        "es" to "Spanish",
        "ja" to "Japanese",
        "ko" to "Korean"
    )

    /** Source language exactly as typed by the user (e.g. "Persian", "German", "آلمانی"). */
    var sourceRaw by mutableStateOf(DEFAULT_SOURCE)
        private set

    /** Target language exactly as typed by the user. See [sourceRaw]. */
    var targetRaw by mutableStateOf(DEFAULT_TARGET)
        private set

    /**
     * The source language for prompts and labels: exactly what the learner
     * typed, trimmed, and never blank (blank falls back to [DEFAULT_SOURCE]).
     */
    val source: String
        get() = sourceRaw.trim().ifEmpty { DEFAULT_SOURCE }

    /** The target language for prompts and labels. See [source]. */
    val target: String
        get() = targetRaw.trim().ifEmpty { DEFAULT_TARGET }

    /** Reads the saved pair. Called from MainActivity before the first composition. */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sourceRaw = prefs.getString(KEY_SOURCE, DEFAULT_SOURCE)?.trim().orEmpty()
            .ifEmpty { DEFAULT_SOURCE }
        val storedTarget = prefs.getString(KEY_TARGET, DEFAULT_TARGET)?.trim().orEmpty()
            .ifEmpty { DEFAULT_TARGET }
        val migratedTarget =
            if (storedTarget == LEGACY_TARGET) DEFAULT_TARGET
            else LEGACY_CODES[storedTarget.lowercase()] ?: storedTarget
        targetRaw = migratedTarget
        if (migratedTarget != storedTarget) {
            // Write the migrated name back once, so the field and every screen
            // that reads `ai_prefs` directly all show the same language.
            prefs.edit().putString(KEY_TARGET, migratedTarget).apply()
        }
    }

    /**
     * Saves a new source language (free text, used exactly as typed).
     *
     * The observable keeps what the learner typed — including a blank field
     * mid-edit, so the text box is never rewritten from under them — while the
     * persisted value falls back to the default, so the screens that read
     * `ai_prefs` directly always see a usable language name.
     */
    fun setSource(context: Context, value: String) {
        sourceRaw = value.trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, sourceRaw.ifEmpty { DEFAULT_SOURCE })
            .apply()
    }

    /** Saves a new target language (free text, used exactly as typed). See [setSource]. */
    fun setTarget(context: Context, value: String) {
        targetRaw = value.trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET, targetRaw.ifEmpty { DEFAULT_TARGET })
            .apply()
    }

    /** Saves both at once. */
    fun update(context: Context, source: String, target: String) {
        setSource(context, source)
        setTarget(context, target)
    }

    /** Back to the built-in English → Persian pair. */
    fun reset(context: Context) {
        update(context, DEFAULT_SOURCE, DEFAULT_TARGET)
    }
}
