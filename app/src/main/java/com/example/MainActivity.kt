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
import com.example.ui.screens.MainScreen
import com.example.ui.theme.AppDesignStyleState
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The Android window itself stays LTR for system chrome. The app's
        // Compose layout mirrors to RTL when the app language is FA via
        // LocalLayoutDirection in MainScreen (only the tab bar/pager stay LTR).
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR

        // Keep the system status bar (clock, battery, signal icons) hidden at
        // all times while using the app — not only during fullscreen video —
        // since it was still showing on every other tab/screen. Swiping down
        // from the top edge still reveals it transiently if the user needs it.
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Crash logger - writes to crash_log.txt in app files dir
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashLog = java.io.File(filesDir, "crash_log.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                crashLog.appendText("""
                    |=== CRASH $timestamp ===
                    |Thread: ${thread.name}
                    |Exception: ${throwable.javaClass.simpleName}
                    |Message: ${throwable.message}
                    |${throwable.stackTraceToString()}
                    |=== END ===
                    |
                """.trimMargin())
                android.util.Log.e("CrashLogger", "Crash logged to ${crashLog.absolutePath}", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Restore which of the four design languages (Langosphere /
        // Material Design 3 / Material You / Neubrutalism) the user picked
        // in Settings ▸ Theme, before the first composition, so the app
        // launches directly in that design — shapes, type scale, components
        // and navigation included.
        AppDesignStyleState.restore(sharedPrefs)

        // Restore the user's custom app accent color (if any) before the first
        // composition so the whole app launches already using their chosen color.
        val storedAccentArgb = sharedPrefs.getInt("app_accent_color", 0)
        if (storedAccentArgb != 0) {
            com.example.ui.theme.AppAccentColorState.color = androidx.compose.ui.graphics.Color(storedAccentArgb)
        }

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

        setContent {
            val themeModeOrdinal = sharedPrefs.getInt("theme_mode", 2) // default: SYSTEM
            // Guard against stale/out-of-range persisted ordinals (e.g. after an
            // app update changes the enum) which would otherwise crash on launch.
            var themeMode by remember {
                mutableStateOf(AppThemeMode.entries.getOrElse(themeModeOrdinal) { AppThemeMode.SYSTEM })
            }

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
                    MainScreen(
                        onThemeToggle = { saveThemeMode(it) },
                        currentThemeMode = themeMode
                    )
                }
            }
        }
    }
}
