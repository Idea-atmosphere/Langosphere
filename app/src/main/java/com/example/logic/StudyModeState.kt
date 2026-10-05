package com.example.logic

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How a sentence lesson opens. The learner picks one and the choice follows
 * the book reader and the video subtitles alike, because both surfaces render
 * the same lesson content.
 */
enum class LessonDisplay(val key: String) {
    /** Accordion: the lesson unfolds inside the line it belongs to. */
    INLINE("inline"),

    /** Modal: the lesson opens as a sheet over the reading surface. */
    POPUP("popup");

    companion object {
        fun from(key: String?): LessonDisplay =
            values().firstOrNull { it.key == key } ?: INLINE

        fun label(value: LessonDisplay, fa: Boolean): String = when (value) {
            INLINE -> if (fa) "درون متن" else "Inline"
            POPUP -> if (fa) "پنجرهٔ شناور" else "Floating window"
        }
    }
}

/**
 * The study switches that are shared by every reading surface in the app: the
 * book reader, the video subtitle list and the online clip list all read the
 * same values, so a learner who turns on challenge mode while reading a book
 * gets the same behaviour on a film.
 *
 * ## Why this is separate from [ReaderComfortState]
 *
 * Comfort is about the *page* (paper colour, type size, margins) and only the
 * book reader has one. These switches are about *learning* and are meaningless
 * on their own: challenge mode needs a translation to hide, focus mode needs a
 * list to dim, and the lesson choice needs a lesson. Keeping them in one small
 * object lets the subtitle side opt in without inheriting reader-only settings.
 *
 * ## Persistence
 *
 * [challengeMode] and [lessonDisplay] are stored in `study_mode_prefs` under
 * the keys the reading surfaces use (`reader-blur-enabled`, `reader-lesson`).
 * The old `reader-challenge` key remains a one-time fallback so existing
 * learners keep their chosen mode after the preference name migration, so
 * every reader continues to describe the same choice.
 * [focusKey] is deliberately NOT persisted: a reader who left one sentence
 * dimmed yesterday should not open the app to a half-faded page.
 */
object StudyModeState {

    private const val PREFS = "study_mode_prefs"

    /** Challenge/blur mode, shared with reader surfaces. */
    private const val KEY_CHALLENGE = "reader-blur-enabled"
    private const val LEGACY_KEY_CHALLENGE = "reader-challenge"

    /** Lesson presentation choice. */
    private const val KEY_LESSON = "reader-lesson"

    /** Peek radius in dp; the web surface uses the same 5px. */
    const val BLUR_DP = 5f

    /** Alpha applied to the lines around the focused one. */
    const val DIMMED_ALPHA = 0.35f

    private var appContext: Context? = null
    private var loaded = false

    /*
     * The switches follow the shape [ReaderComfortState] uses: a private
     * backing state, a read-only accessor for readers and a `setX` function
     * that also persists. A public `var` would give the property a JVM setter
     * with exactly the name and signature of that function, which the compiler
     * rejects as a platform declaration clash.
     */
    private var _challengeMode by mutableStateOf(false)

    /** True while challenge (blur/peek) mode is on. */
    val challengeMode: Boolean get() = _challengeMode

    private var _lessonDisplay by mutableStateOf(LessonDisplay.INLINE)

    /** How a lesson opens: the inline accordion or the floating window. */
    val lessonDisplay: LessonDisplay get() = _lessonDisplay

    /**
     * True while focus mode is on. Tapping a line focuses it; tapping it again
     * releases. Session-only, see the note above.
     */
    private var _focusMode by mutableStateOf(false)

    /** True while focus mode is on; see the note above. */
    val focusMode: Boolean get() = _focusMode

    private var _focusKey by mutableStateOf<String?>(null)

    /** Key of the focused line, or null when nothing is focused. */
    val focusKey: String? get() = _focusKey

    /** Reads the stored switches once; later calls are no-ops. */
    fun load(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val migratedChallenge = !p.contains(KEY_CHALLENGE)
        _challengeMode = if (!migratedChallenge) {
            p.getBoolean(KEY_CHALLENGE, false)
        } else {
            // Preserve the setting for users who enabled challenge mode before
            // the cross-reader preference was named reader-blur-enabled.
            p.getBoolean(LEGACY_KEY_CHALLENGE, false)
        }
        if (migratedChallenge) {
            // Write through immediately: subsequent launches now use the
            // reader-blur-enabled key even if the learner never toggles it.
            p.edit().putBoolean(KEY_CHALLENGE, _challengeMode).apply()
        }
        _lessonDisplay = LessonDisplay.from(p.getString(KEY_LESSON, null))
        loaded = true
    }

    fun setChallengeMode(value: Boolean) {
        _challengeMode = value
        persist(KEY_CHALLENGE, value)
    }

    /** One tap in a toolbar: the switch most learners reach for daily. */
    fun toggleChallengeMode() = setChallengeMode(!challengeMode)

    fun setLessonDisplay(value: LessonDisplay) {
        _lessonDisplay = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_LESSON, value.key)?.apply()
    }

    fun setFocusMode(value: Boolean) {
        _focusMode = value
        if (!value) _focusKey = null
    }

    fun toggleFocusMode() = setFocusMode(!focusMode)

    /** Tapping the focused line again releases the focus. */
    fun focus(key: String) {
        _focusKey = if (_focusKey == key) null else key
    }

    fun clearFocus() {
        _focusKey = null
    }

    /**
     * The alpha a line should be painted with.
     *
     * Answers 1f unless focus mode is on AND something else is focused, which
     * keeps every call site free of the condition - a surface just asks for
     * the alpha of its own key.
     */
    fun alphaFor(key: String?): Float =
        if (focusMode && focusKey != null && focusKey != key) DIMMED_ALPHA else 1f

    private fun persist(key: String, value: Boolean) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(key, value)?.apply()
    }
}
