package com.example.ui.tutorial

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.AppLanguage

/**
 * The interactive, on-screen tutorial («آموزش تعاملی»).
 *
 * Unlike the static App guide, this one runs on top of the real UI: every
 * step points at (cuts a spotlight around) the actual control it talks
 * about. Screens register the controls they want to be explainable with
 * [tutorialTarget]; the overlay ([TutorialOverlay]) reads those positions and
 * draws the scrim, the hole and the explanation card. Nothing else about the
 * screens changes, and when no tutorial is running the registry is a
 * handful of rectangles kept up to date for free.
 */

/** Window-space bounds of every registered control, keyed by [TutorialStep.target]. */
class TutorialTargets {
    private val rects = mutableStateMapOf<String, Rect>()

    operator fun get(key: String): Rect? = rects[key]

    fun set(key: String, rect: Rect) {
        // Layout passes report the same rectangle again and again; only a
        // real change should invalidate the overlay.
        if (rects[key] != rect) rects[key] = rect
    }

    fun remove(key: String) {
        rects.remove(key)
    }
}

/**
 * Progress of a running tutorial: which topics were queued (one per section,
 * or «Full tour» = the basics followed by every tab), which step is showing,
 * and the language the tutorial speaks — chosen separately from the app
 * language and remembered across runs.
 */
class TutorialController(
    languageOverride: AppLanguage?,
    private val persistLanguage: (AppLanguage?) -> Unit = {},
) {
    val targets = TutorialTargets()

    /** The language picked for the tutorial, or null = follow the app language. */
    var languageOverride: AppLanguage? by mutableStateOf(languageOverride)
        private set

    var topics: List<TutorialTopic> by mutableStateOf(emptyList())
        private set
    var topicIndex: Int by mutableIntStateOf(0)
        private set
    var stepIndex: Int by mutableIntStateOf(0)
        private set

    /** Bumped on every [start], so hosts can re-run their preparation even for the same topic. */
    var session: Int by mutableIntStateOf(0)
        private set

    val isActive: Boolean get() = topics.isNotEmpty()
    val currentTopic: TutorialTopic? get() = topics.getOrNull(topicIndex)
    val currentSteps: List<TutorialStep> get() = currentTopic?.steps ?: emptyList()
    val currentStep: TutorialStep? get() = currentSteps.getOrNull(stepIndex)
    val currentStepId: String? get() = currentStep?.id
    val isFirstStep: Boolean get() = topicIndex == 0 && stepIndex == 0
    val isLastStep: Boolean get() = topicIndex >= topics.lastIndex && stepIndex >= currentSteps.lastIndex

    fun languageFor(appLanguage: AppLanguage): AppLanguage = languageOverride ?: appLanguage

    fun setLanguage(language: AppLanguage) {
        languageOverride = language
        persistLanguage(language)
    }

    fun start(topics: List<TutorialTopic>) {
        if (topics.isEmpty()) return
        this.topics = topics
        topicIndex = 0
        stepIndex = 0
        session++
    }

    fun next() {
        when {
            stepIndex < currentSteps.lastIndex -> stepIndex++
            topicIndex < topics.lastIndex -> {
                topicIndex++
                stepIndex = 0
            }
            else -> finish()
        }
    }

    fun back() {
        when {
            stepIndex > 0 -> stepIndex--
            topicIndex > 0 -> {
                topicIndex--
                stepIndex = currentSteps.lastIndex.coerceAtLeast(0)
            }
        }
    }

    fun finish() {
        topics = emptyList()
        topicIndex = 0
        stepIndex = 0
    }
}

/** The app-wide controller; provided once at the root of the main screen. */
val LocalTutorial = staticCompositionLocalOf { TutorialController(languageOverride = null) }

private const val PREFS_NAME = "app_prefs"
private const val PREF_TUTORIAL_LANGUAGE = "tutorial_language"

/** One controller per main screen; the tutorial language is restored from (and saved to) app_prefs. */
@Composable
fun rememberTutorialController(): TutorialController {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_TUTORIAL_LANGUAGE, null)
        TutorialController(
            languageOverride = saved?.let { AppLanguage.fromCode(it) },
            persistLanguage = { language ->
                val editor = prefs.edit()
                if (language == null) editor.remove(PREF_TUTORIAL_LANGUAGE) else editor.putString(PREF_TUTORIAL_LANGUAGE, language.code)
                editor.apply()
            },
        )
    }
}
