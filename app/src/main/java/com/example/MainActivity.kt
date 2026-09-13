package com.example

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.logic.CrashReporter
import com.example.ui.screens.CrashReportScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.AnimeFonts
import com.example.ui.theme.AnimeMascotState
import com.example.ui.theme.AppDesignStyleState
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The Android window stays LTR for system chrome, and so does the
        // app's Compose layout: LocalLayoutDirection in MainScreen is LTR in
        // every UI language, and only the text inside resolves its own
        // direction per string, so Persian copy still reads right-to-left.
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR

        // Keep the system status bar (clock, battery, signal icons) hidden at
        // all times while using the app — not only during fullscreen video —
        // since it was still showing on every other tab/screen. Swiping down
        // from the top edge still reveals it transiently if the user needs it.
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // The crash handler itself is installed by LangosphereApp, before this
        // activity (or any background work) exists. Here we only pick up a
        // report left behind by the previous run, once, before any of the
        // restore work below can fail.
        val pendingCrashReport = CrashReporter.pendingReport(this)

        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // Same defaulting as AppViewModel: saved choice wins, otherwise the
        // system language decides (Persian devices start in Persian).
        val appLangCode = sharedPrefs.getString("app_language", null) ?: run {
            val systemLanguage = resources.configuration.locales.get(0).language
            if (systemLanguage == "fa") "fa" else "en"
        }

        // Restore which of the five design languages (Langosphere /
        // Material Design 3 / Material You / Neubrutalism / Anime) the user picked
        // in Settings ▸ Theme, before the first composition, so the app
        // launches directly in that design — shapes, type scale, components
        // and navigation included.
        AppDesignStyleState.restore(sharedPrefs)

        // The toon skin's mascot toggle, plus its bundled display fonts
        // (Baloo 2 / Vazirmatn). Both are resolved before the first
        // composition so the anime design launches fully formed and no
        // composable ever has to touch the resource table.
        AnimeMascotState.restore(sharedPrefs)
        AnimeFonts.load(this)

        // Restore the user's palette choice (Settings ▸ Theme ▸ App colors)
        // before the first composition so the whole app launches already
        // using it. A leftover single "app accent color" from the old player
        // settings is migrated into the palette's primary role.
        com.example.ui.theme.AppPaletteState.restore(sharedPrefs)

        // Restore the per-scope font choices (whole app / reading files /
        // Leitner cards) picked in Settings ▸ Theme ▸ Font. (The subtitle
        // fonts keep living in the player prefs and are read by the video
        // screen itself.)
        com.example.ui.theme.AppFontState.restore(sharedPrefs)

        // Restore the user's custom subtitle colors (EN/FA) into the process-wide
        // singleton BEFORE the first composition, so both the video overlay and
        // the settings dialog agree on the same live value from the very start,
        // regardless of which tab/screen gets composed first or is later
        // disposed and recreated when switching tabs.
        val storedSubtitleColorEnArgb = sharedPrefs.getInt("subtitle_color_en", 0)
        if (storedSubtitleColorEnArgb != 0) {
            com.example.ui.theme.SubtitleColorState.colorEn = androidx.compose.ui.graphics.Color(storedSubtitleColorEnArgb)
        }
        val storedSubtitleColorFaArgb = sharedPrefs.getInt("subtitle_color_fa", 0)
        if (storedSubtitleColorFaArgb != 0) {
            com.example.ui.theme.SubtitleColorState.colorFa = androidx.compose.ui.graphics.Color(storedSubtitleColorFaArgb)
        }

        // Restore the user's custom Agent chat bubble colors (sent/received).
        val aiPrefs = getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        val storedSentColorArgb = aiPrefs.getInt("chat_sent_color", 0)
        if (storedSentColorArgb != 0) {
            com.example.ui.theme.MessageColorState.sentColor = androidx.compose.ui.graphics.Color(storedSentColorArgb)
        }
        val storedReceivedColorArgb = aiPrefs.getInt("chat_received_color", 0)
        if (storedReceivedColorArgb != 0) {
            com.example.ui.theme.MessageColorState.receivedColor = androidx.compose.ui.graphics.Color(storedReceivedColorArgb)
        }

        // Restore the learner's "source → target" language pair (Settings ▸
        // Tutorial & AI Learning) before the first composition, so every label
        // and every AI prompt is built from it right away instead of defaulting
        // to English → Persian for one frame.
        com.example.ui.theme.LanguagePairState.restore(this)

        setContent {
            val themeModeOrdinal = sharedPrefs.getInt("theme_mode", 2) // default: SYSTEM
            // Guard against stale/out-of-range persisted ordinals (e.g. after an
            // app update changes the enum) which would otherwise crash on launch.
            var themeMode by remember {
                mutableStateOf(AppThemeMode.entries.getOrElse(themeModeOrdinal) { AppThemeMode.SYSTEM })
            }

            // Null once the user has read the report (or when there was none).
            var crashReport by remember { mutableStateOf(pendingCrashReport) }

            fun saveThemeMode(mode: AppThemeMode) {
                themeMode = mode
                sharedPrefs.edit().putInt("theme_mode", mode.ordinal).apply()
            }

            MyApplicationTheme(
                themeMode = themeMode,
                designStyle = AppDesignStyleState.style,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val report = crashReport
                    if (report != null) {
                        // MainScreen is deliberately not composed yet: a crash
                        // during startup would otherwise repeat immediately and
                        // the report would never be readable.
                        CrashReportScreen(
                            report = report,
                            appLanguage = AppLanguage.fromCode(appLangCode),
                            onShare = { CrashReporter.share(this@MainActivity) },
                            onDismiss = {
                                CrashReporter.markHandled(this@MainActivity)
                                crashReport = null
                            }
                        )
                    } else {
                        MainScreen(
                            onThemeToggle = { saveThemeMode(it) },
                            currentThemeMode = themeMode
                        )
                    }
                }
            }
        }
    }
}
