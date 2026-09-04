package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app ships FOUR complete, deliberately different design languages. This
 * is not a color switch: every layer of the visual system (color roles,
 * shape scale, type scale, motion, navigation pattern and the shared
 * component vocabulary in ui/components/LangosphereUi.kt) is swapped.
 *
 *  - [AppDesignStyle.LANGOSPHERE] — the app's own expressive skin: frosted
 *    glass panels, brand gradients, a liquid blob tab bar, very round
 *    corners (8/12/18/24/32dp), bold headings and spring-based expressive
 *    motion.
 *  - [AppDesignStyle.MATERIAL3] — Google's Material Design 3 baseline:
 *    flat tonal surfaces, real Material components (Button,
 *    FilledTonalIconButton, TabRow, SegmentedButton, CircularProgress...),
 *    the official M3 shape scale (4/8/12/16/28dp), the official M3 type
 *    scale and standard Material motion. Depth comes from tonal surface
 *    colors, never from gradients or shadows.
 *  - [AppDesignStyle.MATERIAL_YOU] — the Material You / Material 3
 *    **Expressive** experience from the material-3-skill guidance: dynamic
 *    color, spring-based motion, the expressive (rounder) shape scale
 *    (4/8/16/24/32dp), an *emphasized* type scale, and the adaptive M3
 *    navigation (bottom NavigationBar on phones, NavigationRail on wider
 *    windows) with the Material Symbols icon set.
 *  - [AppDesignStyle.NEOBRUTALISM] — the neobrutalist skin (from the
 *    neubrutalism design scale / neubrutalism.com visual anatomy): a warm
 *    cream canvas, ink-black structural lines, loud color blocks (yellow
 *    #FDC800 in [NeoBrutalismAccent], indigo #432DD7 as the semantic M3
 *    `primary`, pink #FF6B6B as `secondary`), 0dp corners, 2-4dp ink
 *    borders with hard offset shadows (zero blur) for depth, and heavy
 *    block type. Icons are drawn in flat ink on color blocks instead of
 *    tinted glass circles.
 *
 * The choice is picked in Settings (top-right) ▸ Theme and persisted in
 * `app_prefs.design_style`, so the app relaunches in the chosen design.
 */
enum class AppDesignStyle { LANGOSPHERE, MATERIAL3, MATERIAL_YOU, NEOBRUTALISM }

/** The design language the current composition is rendering with. */
val LocalDesignStyle = staticCompositionLocalOf { AppDesignStyle.LANGOSPHERE }

/** Shorthand every shared component uses to pick its Material 3 variant. */
@Composable
@ReadOnlyComposable
fun isMaterial3Design(): Boolean =
    LocalDesignStyle.current == AppDesignStyle.MATERIAL3 ||
        LocalDesignStyle.current == AppDesignStyle.MATERIAL_YOU

/** True only for the Material You design, which uses the adaptive M3
 *  NavigationBar (phone) / NavigationRail (wider windows). The Material
 *  Design 3 baseline keeps the M3 top TabRow instead. */
@Composable
@ReadOnlyComposable
fun isMaterialYouDesign(): Boolean =
    LocalDesignStyle.current == AppDesignStyle.MATERIAL_YOU

/** True only for the neobrutalist design, which re-skis every shared
 *  component with ink borders, hard offset shadows and loud color blocks
 *  (see ui/components/LangosphereUi.kt and ui/components/LiquidTabBar.kt). */
@Composable
@ReadOnlyComposable
fun isNeobrutalismDesign(): Boolean =
    LocalDesignStyle.current == AppDesignStyle.NEOBRUTALISM

/**
 * Process-wide holder for the selected design, backed by Compose state and
 * mirrored into SharedPreferences — the same pattern already used by
 * [AppAccentColorState], so any screen can read/change it and the whole app
 * re-themes immediately.
 */
object AppDesignStyleState {
    private const val PREFS_NAME = "app_prefs"
    private const val PREF_KEY = "design_style"

    var style: AppDesignStyle by mutableStateOf(AppDesignStyle.LANGOSPHERE)

    /** Called before the first composition (MainActivity.onCreate). */
    fun restore(prefs: SharedPreferences) {
        val stored = prefs.getInt(PREF_KEY, AppDesignStyle.LANGOSPHERE.ordinal)
        style = AppDesignStyle.entries.getOrElse(stored) { AppDesignStyle.LANGOSPHERE }
    }

    fun set(context: Context, newStyle: AppDesignStyle) {
        style = newStyle
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_KEY, newStyle.ordinal)
            .apply()
    }
}

// ── Material 3 shape scale ──
// The official md.sys.shape tokens: extra-small 4, small 8, medium 12,
// large 16, extra-large 28. Much crisper than the app's own 8/12/18/24/32.
val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ── Material 3 type scale ──
// The baseline md.sys.typescale values (Roboto = FontFamily.Default),
// with the spec's own weights: regular headings instead of the app's bold
// ones, which is one of the most visible differences between the two
// designs. Persian right-alignment/RTL is layered on top by
// Typography.forAppLanguage() in Type.kt.
val Material3Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ── Material 3 baseline color schemes ──
// The reference M3 palette (the "Material You" purple), including the full
// surface-container ramp so tonal elevation works properly. Used whenever
// wallpaper-based dynamic color is unavailable (or disabled).
val Material3LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF4F378B),
    inversePrimary = Color(0xFFD0BCFF),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF633B48),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF6750A4),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    surfaceDim = Color(0xFFDED8E1),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF8C1D18),
)

val Material3DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF6750A4),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFFD0BCFF),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

// ── Material You (M3 Expressive) shape scale ──
// The expressive corner tokens from the material-3-skill: cards get the
// "large-increased" 24dp, dialogs/sheets the "extra-large-increased" 32dp.
// Rounder than the baseline M3 (12/16/28dp), but still a token-driven M3
// system rather than the app's own 8/12/18/24/32 glass scale.
val MaterialYouShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// ── Material You (M3 Expressive) type scale ──
// The official M3 type scale but with the *emphasized* weights that the
// M3 Expressive update specifies: display/headline/title styles carry more
// weight so headings feel alive, while body/label stay legible at the
// builder's regular weight.
val MaterialYouTypography = Material3Typography.copy(
    displayLarge = Material3Typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
    displayMedium = Material3Typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
    displaySmall = Material3Typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Material3Typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Material3Typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Material3Typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Material3Typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Material3Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Material3Typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

// ── Material You (M3 Expressive) color schemes ──
// A Material You palette generated from a teal/green seed — the "personal
// color" half of Material You. Distinct from the app's own indigo/teal
// palette (Color.kt) and from the M3-baseline purple above, so even on
// devices without wallpaper dynamic color the Material You design reads
// clearly different. The full surface-container ramp is included so tonal
// elevation works the way the spec intends. On Android 12+ the wallpaper
// palette overrides this (Material You is dynamic-color-first).
val MaterialYouLightColors = lightColorScheme(
    primary = Color(0xFF3F6B4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC0EECB),
    onPrimaryContainer = Color(0xFF00210D),
    inversePrimary = Color(0xFFA5D2B0),
    secondary = Color(0xFF52634E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CE),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF3A646C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDEAF3),
    onTertiaryContainer = Color(0xFF001F25),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF41483F),
    surfaceTint = Color(0xFF3F6B4E),
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEEF2EB),
    surfaceDim = Color(0xFFD6DCD2),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EC),
    surfaceContainer = Color(0xFFEAF0E7),
    surfaceContainerHigh = Color(0xFFE4EAE1),
    surfaceContainerHighest = Color(0xFFDEE4DC),
    outline = Color(0xFF71796F),
    outlineVariant = Color(0xFFC1C9BE),
    scrim = Color(0xFF000000),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val MaterialYouDarkColors = darkColorScheme(
    primary = Color(0xFFA5D2B0),
    onPrimary = Color(0xFF0B381D),
    primaryContainer = Color(0xFF285137),
    onPrimaryContainer = Color(0xFFC0EECB),
    inversePrimary = Color(0xFF3F6B4E),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243525),
    secondaryContainer = Color(0xFF3A4B3B),
    onSecondaryContainer = Color(0xFFD5E8CE),
    tertiary = Color(0xFFA1CED8),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF204E56),
    onTertiaryContainer = Color(0xFFBDEAF3),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE0E4DC),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE0E4DC),
    surfaceVariant = Color(0xFF41483F),
    onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceTint = Color(0xFFA5D2B0),
    inverseSurface = Color(0xFFE0E4DC),
    inverseOnSurface = Color(0xFF2D322C),
    surfaceDim = Color(0xFF10140F),
    surfaceBright = Color(0xFF353A34),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF272C26),
    surfaceContainerHighest = Color(0xFF323731),
    outline = Color(0xFF8B9388),
    outlineVariant = Color(0xFF41483F),
    scrim = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ── Neobrutalism shape scale ──
// Zero-radius everywhere. Square corners, 2-4dp ink borders and hard offset
// shadows (zero blur) are the core of the neubrutalist visual anatomy
// (neubrutalism.com: `--radius: 0; border: 3px solid #000;
// box-shadow: 5px 5px 0 0 #000`).
val NeoBrutalismShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

// ── Neobrutalism type scale ──
// Built on the app's bold Langosphere scale (so Persian readability is
// kept) but pushed blockier: Black/ExtraBold display + headline weights,
// Bold titles/labels and tighter line heights, echoing the guide's
// oversized, confident headings (Syne 800 / Space Grotesk 700 on the site;
// we map to the platform font weights since no font files are bundled).
val NeoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 45.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    ),
)

// ── Neobrutalism color schemes ──
// The palette fuses the two design scales:
//  • neubrutalism DESIGN.md tokens: primary #FDC800, secondary #432DD7,
//    success #16A34A, warning #D97706, danger #DC2626, surface #FBFBF9,
//    text #1C293C;
//  • neubrutalism.com "cyber-brutalism" dark tokens: bg #14131A, raised
//    #1F1E28, structural ink #F3F3F6, plus the loud accent blocks
//    (#FF6B6B pink, #FDC800 yellow...).
//
// M3 role mapping (important for legibility): the app's shared components
// and screens use `primary` for text/icons/selection accents everywhere, so
// neobrutalism's *indigo* fills the `primary` role (readable on cream at
// AA; dark mode gets a lighter indigo). The signature yellow lives both as
// `tertiary` and in [NeoBrutalismAccent], which the chunky components use
// for their loud flat blocks (CTA buttons, active tabs, selected rows...).
//
// Neobrutalism is deliberately NOT dynamic-color driven: its identity is
// the fixed cream/ink palette, so wallpaper dynamic color is skipped for
// this design in Theme.kt.

/** The neubrutalist loud yellow used by the chunky block components. */
val NeoBrutalismAccent = Color(0xFFFDC800)

val NeoBrutalismLightColors = lightColorScheme(
    primary = Color(0xFF432DD7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E3FC),
    onPrimaryContainer = Color(0xFF221A5C),
    inversePrimary = Color(0xFFB9ADFF),
    secondary = Color(0xFFFF6B6B),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFFFE0E0),
    onSecondaryContainer = Color(0xFF501111),
    tertiary = Color(0xFFFDC800),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFFFF3C4),
    onTertiaryContainer = Color(0xFF1C293C),
    background = Color(0xFFFFFDF5),
    onBackground = Color(0xFF1C293C),
    surface = Color(0xFFFFFDF5),
    onSurface = Color(0xFF1C293C),
    surfaceVariant = Color(0xFFF1EBDD),
    onSurfaceVariant = Color(0xFF4A4F59),
    surfaceTint = Color(0xFF432DD7),
    inverseSurface = Color(0xFF1C293C),
    inverseOnSurface = Color(0xFFFFFDF5),
    surfaceDim = Color(0xFFE6DFCF),
    surfaceBright = Color(0xFFFFFDF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F1E3),
    surfaceContainerHigh = Color(0xFFF0E9DA),
    surfaceContainerHighest = Color(0xFFE9E1D1),
    outline = Color(0xFF000000),
    outlineVariant = Color(0xFFD6CEC0),
    scrim = Color(0xFF000000),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
)

val NeoBrutalismDarkColors = darkColorScheme(
    // "Cyber-brutalism" (neubrutalism.com dark tokens): page #14131A, raised
    // #1F1E28, structural ink #F3F3F6. The surface ramp is tinted slightly
    // indigo-violet so dark cards read as a family, and the loud accent
    // blocks (yellow/pink/indigo-light) stay vivid against it.
    primary = Color(0xFFB9ADFF),
    onPrimary = Color(0xFF160F4A),
    primaryContainer = Color(0xFF2B2356),
    onPrimaryContainer = Color(0xFFE1DBFF),
    inversePrimary = Color(0xFF432DD7),
    secondary = Color(0xFFFF6B6B),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF3A1F28),
    onSecondaryContainer = Color(0xFFFFC9C2),
    tertiary = Color(0xFFFDC800),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2E2710),
    onTertiaryContainer = Color(0xFFFFDF66),
    background = Color(0xFF14131A),
    onBackground = Color(0xFFEFEDF5),
    surface = Color(0xFF14131A),
    onSurface = Color(0xFFEFEDF5),
    surfaceVariant = Color(0xFF2E2B3A),
    onSurfaceVariant = Color(0xFFBDB9C9),
    surfaceTint = Color(0xFFB9ADFF),
    inverseSurface = Color(0xFFEFEDF5),
    inverseOnSurface = Color(0xFF14131A),
    surfaceDim = Color(0xFF0D0C13),
    surfaceBright = Color(0xFF27242F),
    surfaceContainerLowest = Color(0xFF1D1B26),
    surfaceContainerLow = Color(0xFF221F2B),
    surfaceContainer = Color(0xFF282430),
    surfaceContainerHigh = Color(0xFF2F2B38),
    surfaceContainerHighest = Color(0xFF373341),
    outline = Color(0xFFF3F3F6),
    outlineVariant = Color(0xFF4A4655),
    scrim = Color(0xFF000000),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF421417),
    onErrorContainer = Color(0xFFFFB4A8),
)

/**
 * Copy for the Settings ▸ Theme ▸ design picker. Kept next to the design
 * system itself (instead of in the big AppStrings class) so the whole
 * feature lives in one file.
 */
class DesignStyleStrings(private val isEn: Boolean) {
    private fun t(fa: String, en: String) = if (isEn) en else fa

    val sectionTitle = t("طراحی کل برنامه", "App design")
    val sectionDesc = t(
        "چهار طراحی کامل و کاملاً متفاوت. با تغییر این گزینه فقط رنگ‌ها عوض نمی‌شود؛ شکل‌ها، فونت‌ها، دکمه‌ها، کارت‌ها، نوار تب‌ها و حتی آیکون‌ها همه با هم تغییر می‌کنند.",
        "Four complete, deliberately different designs. This changes far more than colors: shapes, type, buttons, cards, the tab bar, the icons and the way every option is shown all change together."
    )
    val langosphereTitle = t("لنگوسفر", "Langosphere")
    )
    val material3Title = t("متریال ۳ تب بالا", "M3 top bar")
    )
    val materialYouTitle = t("متریال ۳ تب پایین", "M3 down bar")
    )
    val neobrutalismTitle = t("نئوبروتالیسم", "Neubrutalism")
    )
    val applyNote = t(
        "با انتخاب هر گزینه، کل برنامه فوراً با طراحی جدید ساخته می‌شود و انتخاب شما ذخیره می‌ماند.",
        "Picking an option rebuilds the whole app in that design right away, and your choice is remembered."
    )
    val selectedLabel = t("فعال", "Active")
}
