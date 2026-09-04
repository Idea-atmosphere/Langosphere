package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
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
 * The app's single theme entry point. It serves the four design languages
 * described in DesignStyle.kt:
 *
 *  - [AppDesignStyle.LANGOSPHERE] (default): Material 3 Expressive —
 *    MaterialExpressiveTheme + MotionScheme.expressive() plus the app's own
 *    very round shape scale (AppShapes) and bold type scale (Typography).
 *  - [AppDesignStyle.MATERIAL3]: the Material Design 3 baseline — plain
 *    MaterialTheme with the official M3 shape scale (Material3Shapes),
 *    the official M3 type scale (Material3Typography) and standard
 *    Material motion, so components look exactly like the spec.
 *  - [AppDesignStyle.MATERIAL_YOU]: the Material You (M3 Expressive)
 *    experience from the material-3-skill — MaterialExpressiveTheme with
 *    spring motion, the rounder expressive shape scale
 *    (MaterialYouShapes) and the emphasized type scale
 *    (MaterialYouTypography). Its primary navigation is the adaptive M3
 *    NavigationBar / NavigationRail with the Material Symbols icon set.
 *    (Only this design adapts; the Material Design 3 baseline keeps the M3
 *    top TabRow, per the user's request.)
 *  - [AppDesignStyle.NEOBRUTALISM]: the neobrutalist skin — plain
 *    MaterialTheme with the square NeoBrutalismShapes and the heavy
 *    NeoTypography over the fixed cream/ink palette. Wallpaper dynamic
 *    color and the custom accent override are skipped for this design: its
 *    identity IS its fixed loud palette (see NeoBrutalismLightColors /
 *    NeoBrutalismDarkColors), and the chunky border/shadow treatment comes
 *    from the shared components in ui/components/LangosphereUi.kt.
 *
 * In all designs except NEOBRUTALISM, [dynamicColor] (on by default) takes
 * the palette from the wallpaper on Android 12+; otherwise the design's own
 * fallback palette is used (Color.kt for Langosphere, the M3 baseline
 * purple for Material 3, and the teal/green Material You palette for
 * Material You). The user's manual Light / Dark / System mode is respected
 * either way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    // Which of the four design languages to render (Settings ▸ Theme).
    designStyle: AppDesignStyle = AppDesignStyleState.style,
    // Material You dynamic colors are the default (Android 12+); each
    // design's own palette is used on older devices or when this is false.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        // Neobrutalism never follows the wallpaper: its identity is the
        // fixed cream-and-ink palette with loud accent blocks.
        designStyle == AppDesignStyle.NEOBRUTALISM ->
            if (isDark) NeoBrutalismDarkColors else NeoBrutalismLightColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        designStyle == AppDesignStyle.MATERIAL_YOU ->
            if (isDark) MaterialYouDarkColors else MaterialYouLightColors
        designStyle == AppDesignStyle.MATERIAL3 ->
            if (isDark) Material3DarkColors else Material3LightColors
        isDark -> DarkColors
        else -> LightColors
    }

    // If the user picked a custom accent color from the app's settings, apply
    // it on top of the base scheme so it takes effect everywhere immediately.
    // (Not for neobrutalism — a stray accent would break its fixed palette.)
    val customAccent = AppAccentColorState.color
    val colorScheme = if (customAccent != null && designStyle != AppDesignStyle.NEOBRUTALISM) {
        baseColorScheme.copy(
            primary = customAccent,
            onPrimary = contrastingOnColor(customAccent),
            primaryContainer = lerp(customAccent, if (isDark) Color.Black else Color.White, 0.55f),
            onPrimaryContainer = if (isDark) Color.White else Color(0xFF1A1A1A),
        )
    } else baseColorScheme

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalDesignStyle provides designStyle,
    ) {
        when (designStyle) {
            AppDesignStyle.LANGOSPHERE -> {
                // The app's own expressive skin: the app's very round shapes
                // and bold type scale on top of the expressive Material
                // motion scheme.
                val motionScheme = remember { MotionScheme.expressive() }
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    motionScheme = motionScheme,
                    shapes = AppShapes,
                    typography = Typography,
                    content = content
                )
            }
            AppDesignStyle.MATERIAL3 -> {
                // Material Design 3 baseline: spec shapes, spec type scale and
                // the standard (non-expressive) Material motion.
                MaterialTheme(
                    colorScheme = colorScheme,
                    shapes = Material3Shapes,
                    typography = Material3Typography,
                    content = content
                )
            }
            AppDesignStyle.MATERIAL_YOU -> {
                // Material You (M3 Expressive): spring-based expressive
                // motion, the rounder expressive shape scale and the
                // *emphasized* M3 type scale, exactly as the
                // material-3-skill guidance specifies.
                val motionScheme = remember { MotionScheme.expressive() }
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    motionScheme = motionScheme,
                    shapes = MaterialYouShapes,
                    typography = MaterialYouTypography,
                    content = content
                )
            }
            AppDesignStyle.NEOBRUTALISM -> {
                // Neobrutalism: square shapes + heavy type over the fixed
                // cream/ink palette. The signature depth (2-4dp ink borders
                // and hard zero-blur offset shadows) is painted by the shared
                // components (LangosphereUi.kt) rather than by MaterialTheme.
                MaterialTheme(
                    colorScheme = colorScheme,
                    shapes = NeoBrutalismShapes,
                    typography = NeoTypography,
                    content = content
                )
            }
        }
    }
}
