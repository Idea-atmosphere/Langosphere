package com.example.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.ui.theme.AppAccentColorState
import com.example.ui.theme.SubtitleColorState
import java.io.File

/**
 * Single source of truth for every player preference.
 *
 * Before this, VideoPlayerScreen read ~20 values out of SharedPreferences
 * into separate `remember { mutableStateOf(...) }` variables and re-wrote
 * them by hand at every call site (easy to forget, easy to desync). Here
 * each property is state-backed AND persists itself on assignment, so the
 * UI stays reactive and storage always matches what is on screen.
 */
class PlayerPrefs(val raw: SharedPreferences) {

    // ── Appearance ──
    private val glassState = mutableStateOf(raw.getBoolean("glassmorphism_enabled", true))
    var glassmorphism: Boolean
        get() = glassState.value
        set(value) {
            glassState.value = value
            raw.edit().putBoolean("glassmorphism_enabled", value).apply()
        }

    // ── Subtitles ──
    private val subtitlesState = mutableStateOf(raw.getBoolean("subtitles_enabled", true))
    var subtitlesEnabled: Boolean
        get() = subtitlesState.value
        set(value) {
            subtitlesState.value = value
            raw.edit().putBoolean("subtitles_enabled", value).apply()
        }

    private val fontSizeState = mutableStateOf(raw.getFloat("subtitle_font_size_factor", 1.0f))
    var fontSizeFactor: Float
        get() = fontSizeState.value
        set(value) {
            val clamped = value.coerceIn(0.6f, 2.0f)
            fontSizeState.value = clamped
            raw.edit().putFloat("subtitle_font_size_factor", clamped).apply()
        }

    private val bottomPaddingState = mutableStateOf(raw.getFloat("subtitle_bottom_padding", 64f))
    var bottomPadding: Float
        get() = bottomPaddingState.value
        set(value) {
            val clamped = value.coerceIn(16f, 250f)
            bottomPaddingState.value = clamped
            raw.edit().putFloat("subtitle_bottom_padding", clamped).apply()
        }

    private val fontEnState = mutableStateOf(raw.getString("subtitle_font_en", "default") ?: "default")
    var fontEn: String
        get() = fontEnState.value
        set(value) {
            fontEnState.value = value
            raw.edit().putString("subtitle_font_en", value).apply()
        }

    private val fontFaState = mutableStateOf(raw.getString("subtitle_font_fa", "default") ?: "default")
    var fontFa: String
        get() = fontFaState.value
        set(value) {
            fontFaState.value = value
            raw.edit().putString("subtitle_font_fa", value).apply()
        }

    private val customFontEnState = mutableStateOf(raw.getString("subtitle_custom_font_path_en", null))
    var customFontPathEn: String?
        get() = customFontEnState.value
        set(value) {
            customFontEnState.value = value
            if (value == null) raw.edit().remove("subtitle_custom_font_path_en").apply()
            else raw.edit().putString("subtitle_custom_font_path_en", value).apply()
        }

    private val customFontFaState = mutableStateOf(raw.getString("subtitle_custom_font_path_fa", null))
    var customFontPathFa: String?
        get() = customFontFaState.value
        set(value) {
            customFontFaState.value = value
            if (value == null) raw.edit().remove("subtitle_custom_font_path_fa").apply()
            else raw.edit().putString("subtitle_custom_font_path_fa", value).apply()
        }

    // ── Smart pause ──
    private val smartPauseState = mutableStateOf(raw.getBoolean("smart_pause_enabled", true))
    var smartPause: Boolean
        get() = smartPauseState.value
        set(value) {
            smartPauseState.value = value
            raw.edit().putBoolean("smart_pause_enabled", value).apply()
        }

    private val pauseDimState = mutableStateOf(raw.getBoolean("smart_pause_dim_enabled", true))
    var pauseDim: Boolean
        get() = pauseDimState.value
        set(value) {
            pauseDimState.value = value
            raw.edit().putBoolean("smart_pause_dim_enabled", value).apply()
        }

    private val pauseHideUiState = mutableStateOf(raw.getBoolean("smart_pause_hide_ui_enabled", false))
    var pauseHideUi: Boolean
        get() = pauseHideUiState.value
        set(value) {
            pauseHideUiState.value = value
            raw.edit().putBoolean("smart_pause_hide_ui_enabled", value).apply()
        }

    private val pauseRequireContinueState = mutableStateOf(raw.getBoolean("smart_pause_require_continue", false))
    var pauseRequireContinue: Boolean
        get() = pauseRequireContinueState.value
        set(value) {
            pauseRequireContinueState.value = value
            raw.edit().putBoolean("smart_pause_require_continue", value).apply()
        }

    private val skipSecondsState = mutableStateOf(raw.getInt("skip_seconds", 10))
    var skipSeconds: Int
        get() = skipSecondsState.value
        set(value) {
            val clamped = value.coerceIn(2, 30)
            skipSecondsState.value = clamped
            raw.edit().putInt("skip_seconds", clamped).apply()
        }

    // ── Playback speed ──
    // Language learners live on this control: 0.75x to catch fast dialogue,
    // 1.25x+ to skim what they already understand. ExoPlayer time-stretches
    // the audio without shifting the pitch, so voices stay natural.
    private val playbackSpeedState = mutableStateOf(raw.getFloat("playback_speed", 1.0f))
    var playbackSpeed: Float
        get() = playbackSpeedState.value
        set(value) {
            val clamped = value.coerceIn(0.5f, 2.0f)
            playbackSpeedState.value = clamped
            raw.edit().putFloat("playback_speed", clamped).apply()
        }

    // ── Layout: how much height the video takes in split mode ──
    // New in the Langosphere player: the divider between the video and the
    // subtitle list is draggable, and the chosen ratio is remembered.
    private val videoWeightState = mutableStateOf(raw.getFloat("player_video_weight", 0.38f))
    var videoWeight: Float
        get() = videoWeightState.value
        set(value) {
            val clamped = value.coerceIn(0.22f, 0.78f)
            videoWeightState.value = clamped
            raw.edit().putFloat("player_video_weight", clamped).apply()
        }

    // ── Colors (kept in the process-wide theme singletons) ──
    fun setAccentColor(color: Color?) {
        AppAccentColorState.color = color
        if (color == null) raw.edit().remove("app_accent_color").apply()
        else raw.edit().putInt("app_accent_color", color.toArgb()).apply()
    }

    fun setSubtitleColorEn(color: Color) {
        SubtitleColorState.colorEn = color
        raw.edit().putInt("subtitle_color_en", color.toArgb()).apply()
    }

    fun setSubtitleColorFa(color: Color) {
        SubtitleColorState.colorFa = color
        raw.edit().putInt("subtitle_color_fa", color.toArgb()).apply()
    }

    // ── Playback resume ──
    // Saved both on dispose AND periodically while playing, so the position
    // survives the process being killed in the background (previously it was
    // only written on dispose, which never runs on a kill).
    fun savePlayback(key: String, positionMs: Long, wasPlaying: Boolean) {
        raw.edit()
            .putLong("${key}_position", positionMs)
            .putBoolean("${key}_was_playing", wasPlaying)
            .apply()
    }

    fun savedPosition(key: String): Long = raw.getLong("${key}_position", 0L)

    fun savedWasPlaying(key: String): Boolean = raw.getBoolean("${key}_was_playing", true)
}

@Composable
fun rememberPlayerPrefs(): PlayerPrefs {
    val context = LocalContext.current
    return remember { PlayerPrefs(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)) }
}

/** Maps a stored font key to a real font family. */
fun fontFamilyFor(key: String, customFamily: FontFamily? = null): FontFamily = when (key) {
    "serif" -> FontFamily.Serif
    "sansserif" -> FontFamily.SansSerif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    "custom" -> customFamily ?: FontFamily.Default
    else -> FontFamily.Default
}

/** Loads a user-imported font file, or null when it is missing/unreadable. */
@Composable
fun rememberCustomFontFamily(path: String?): FontFamily? = remember(path) {
    path?.let { p ->
        try {
            if (File(p).exists()) FontFamily(Font(File(p))) else null
        } catch (e: Exception) {
            null
        }
    }
}
