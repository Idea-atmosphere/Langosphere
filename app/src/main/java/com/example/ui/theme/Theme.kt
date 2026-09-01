package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

// ── Theme mode state (manual toggle) ──
enum class AppThemeMode { LIGHT, DARK, SYSTEM }

val LocalThemeMode = staticCompositionLocalOf { AppThemeMode.SYSTEM }

// ── User-customizable app accent color ──
// A simple app-wide holder (instead of prop-drilling through every screen) so
// any screen can let the user pick a custom accent color for the whole app,
// and MyApplicationTheme picks it up immediately since it's backed by
// Compose state.
object AppAccentColorState {
    var color: Color? by mutableStateOf(null)
}

// ── User-customizable subtitle colors (EN/FA) ──
// Backed by the SAME process-wide Compose-state-singleton pattern as
// AppAccentColorState above (rather than a per-composable `remember`).
// VideoPlayerScreen is only composed while its tab is selected in
// MainScreen — switching tabs disposes/recreates it — so keeping this color
// choice at the process level (instead of inside that composable's own
// `remember`) guarantees it is always the single live source of truth and
// updates immediately, exactly like the app accent color already does.
object SubtitleColorState {
    var colorEn: Color? by mutableStateOf(null)
    var colorFa: Color? by mutableStateOf(null)
}

// ── User-customizable Agent chat bubble colors (sent/received) ──
// Same reasoning as SubtitleColorState: the Agent tab's screen is also
// disposed/recreated on tab switches, so this lives at the process level.
object MessageColorState {
    var sentColor: Color? by mutableStateOf(null)
    var receivedColor: Color? by mutableStateOf(null)
}

private fun contrastingOnColor(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF1A1A1A) else Color.White

// ── Color Schemes ──
// The app's default palette (Color.kt) — calm, modern blue/teal/violet
// tones used whenever Material You dynamic colors are unavailable.
private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

/**
 * The app's single theme entry point, built on Material 3 Expressive
 * (Material You):
 *  - MaterialExpressiveTheme + MotionScheme.expressive() give every
 *    Material component the expressive spring-based motion system and the
 *    expressive shape defaults (pill buttons, softer corners).
 *  - [dynamicColor] (on by default): on Android 12+ the color scheme comes
 *    from the user's wallpaper (Material You dynamic colors); on older
 *    devices — or when disabled — the app falls back to the hand-tuned
 *    low-glare palette in Color.kt.
 *  - The user's manual Light / Dark / System mode is respected either way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    // Material You dynamic colors are the default (Android 12+); the custom
    // palette in Color.kt is used on older devices or when this is false.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColors
        else -> LightColors
    }

    // If the user picked a custom accent color from the app's settings, apply
    // it on top of the base scheme so it takes effect everywhere immediately.
    val customAccent = AppAccentColorState.color
    val colorScheme = if (customAccent != null) {
        baseColorScheme.copy(
            primary = customAccent,
            onPrimary = contrastingOnColor(customAccent),
            primaryContainer = lerp(customAccent, if (isDark) Color.Black else Color.White, 0.55f),
            onPrimaryContainer = if (isDark) Color.White else Color(0xFF1A1A1A),
        )
    } else baseColorScheme

    val motionScheme = remember { MotionScheme.expressive() }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = AppShapes,
            typography = Typography,
            content = content
        )
    }
}
