package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// App-wide type scale (the Langosphere design).
//
// Two deliberate differences from the Material defaults:
//  1. Letter spacing is tightened almost everywhere. The default positive
//     tracking on body/label styles looks loose in Latin text and actively
//     breaks Persian, where letters have to stay connected.
//  2. Line heights are taller than Material's, because Persian glyphs carry
//     tall ascenders/descenders and this app is mostly long-form reading
//     (documents, subtitles, dictionary entries, AI answers).
//
// The Material Design 3 design uses the official spec scale instead — see
// Material3Typography in DesignStyle.kt, which forAppLanguage() below picks
// up automatically.
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * Resolves the type scale for the current design language and UI language.
 *
 *  - The Material Design 3 design always uses the official M3 type scale
 *    (regular headings, spec line heights), whatever the receiver is.
 *  - The Material You design uses the official M3 type scale with the
 *    emphasized heading weights (MaterialYouTypography).
 *  - The composition is laid out LTR in every UI language (see MainScreen's
 *    LocalLayoutDirection), so rows, start/end paddings and alignments never
 *    mirror. Only the paragraph direction is auto-detected per-text
 *    (Content), so Persian reads RTL and English LTR inside their own line;
 *    letter spacing is zeroed for Persian because its letters have to stay
 *    connected.
 */
fun Typography.forAppLanguage(language: AppLanguage): Typography {
    val base = when (AppDesignStyleState.style) {
        AppDesignStyle.MATERIAL3 -> Material3Typography
        AppDesignStyle.MATERIAL_YOU -> MaterialYouTypography
        AppDesignStyle.NEOBRUTALISM -> NeoTypography
        AppDesignStyle.ANIME -> animeTypography()
        else -> this
    }
    return if (language == AppLanguage.FA) base.forPersianUi() else base
}

private fun Typography.forPersianUi(): Typography = copy(
    displayLarge = displayLarge.asPersianUiText(),
    displayMedium = displayMedium.asPersianUiText(),
    displaySmall = displaySmall.asPersianUiText(),
    headlineLarge = headlineLarge.asPersianUiText(),
    headlineMedium = headlineMedium.asPersianUiText(),
    headlineSmall = headlineSmall.asPersianUiText(),
    titleLarge = titleLarge.asPersianUiText(),
    titleMedium = titleMedium.asPersianUiText(),
    titleSmall = titleSmall.asPersianUiText(),
    bodyLarge = bodyLarge.asPersianUiText(),
    bodyMedium = bodyMedium.asPersianUiText(),
    bodySmall = bodySmall.asPersianUiText(),
    labelLarge = labelLarge.asPersianUiText(),
    labelMedium = labelMedium.asPersianUiText(),
    labelSmall = labelSmall.asPersianUiText(),
)

private fun TextStyle.asPersianUiText(): TextStyle = copy(
    // Content = auto per-paragraph direction (Persian RTL, English LTR)
    // so mixed Fa/En sentences keep correct order. Start alignment still
    // follows the composition's LayoutDirection (Rtl when FA) — content
    // that needs per-language alignment (subtitle lines, document text)
    // overrides textAlign with Right/Left per autoTextDirection() at the
    // call site. Tracking zeroed for Persian connectivity.
    textAlign = TextAlign.Start,
    textDirection = TextDirection.Content,
    letterSpacing = 0.sp,
)

/**
 * Replaces the font family of every style in the scale — this is how the
 * user's "whole app" font choice (Settings ▸ Theme ▸ Font, AppFontState) is
 * layered over whichever design's type scale is currently active. Null keeps
 * the design's own families (including the toon skin's bundled display
 * fonts), which is the default.
 */
fun Typography.withFontFamily(family: FontFamily?): Typography {
    if (family == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}

// ── ANIME (toon) type scale ──
//
// Fonts. The toon skin is designed around two display families:
//   • Latin  — "Baloo 2" (rounded, chunky, very cartoon-friendly)
//   • Persian — "Vazirmatn" (the de-facto modern Persian UI face)
// Drop the files into `app/src/main/res/font` using the names below and the
// app picks them up automatically at runtime — no code change needed:
//
//   baloo2_medium.ttf · baloo2_semibold.ttf · baloo2_bold.ttf ·
//   baloo2_extrabold.ttf
//   vazirmatn_regular.ttf · vazirmatn_medium.ttf · vazirmatn_bold.ttf ·
//   vazirmatn_black.ttf
//
// The families are resolved *by resource name* through
// [animeFontFamilyOrNull] instead of a compile-time R.font.* reference, so
// the design keeps working (falling back to the platform font at the same
// weights) on a checkout where the font binaries have not been added yet.
// When both families are present the Persian face is registered *after* the
// Latin one in the same [FontFamily], which makes it the fallback for every
// glyph Baloo 2 does not cover — i.e. mixed FA/EN sentences render correctly
// inside a single Text.

private val AnimeLatinFonts = listOf(
    "baloo2_medium" to FontWeight.Medium,
    "baloo2_semibold" to FontWeight.SemiBold,
    "baloo2_bold" to FontWeight.Bold,
    "baloo2_extrabold" to FontWeight.ExtraBold,
)

private val AnimePersianFonts = listOf(
    "vazirmatn_regular" to FontWeight.Normal,
    "vazirmatn_medium" to FontWeight.Medium,
    "vazirmatn_bold" to FontWeight.Bold,
    "vazirmatn_black" to FontWeight.Black,
)

/**
 * Builds the toon [FontFamily] from whichever of the bundled font files
 * actually exist in `res/font`, or returns null when none do (so callers can
 * fall back to [FontFamily.Default]).
 */
private fun animeFontFamilyOrNull(context: Context): FontFamily? {
    val fonts = (AnimeLatinFonts + AnimePersianFonts).mapNotNull { (name, weight) ->
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        if (id == 0) null else Font(id, weight)
    }
    return if (fonts.isEmpty()) null else FontFamily(fonts)
}

/**
 * Process-wide cache for the resolved toon font family. Resolved once in
 * MainActivity.onCreate (via [AnimeFonts.load]) so composition never touches
 * the resource table.
 */
object AnimeFonts {
    /** Null until [load] finds bundled toon fonts; then the merged family. */
    var family: FontFamily? = null
        private set

    fun load(context: Context) {
        if (family == null) family = animeFontFamilyOrNull(context)
    }

    /** The family to render the toon skin with (platform font as fallback). */
    fun resolved(): FontFamily = family ?: FontFamily.Default
}

/**
 * The ANIME type scale: chunky, high-contrast display weights over
 * comfortable 500-weight body copy. Line heights stay generous because the
 * app is long-form reading and Persian glyphs are tall.
 *
 * Only [labelLarge] carries positive tracking (0.5sp) — it is a Latin-only
 * button/label style, and Persian zeroes it again through
 * [Typography.forAppLanguage].
 */
fun animeTypography(family: FontFamily = AnimeFonts.resolved()): Typography = Typography(
    displayLarge = toonStyle(family, FontWeight.ExtraBold, 40.sp, 48.sp, (-0.5).sp),
    displayMedium = toonStyle(family, FontWeight.ExtraBold, 34.sp, 42.sp, (-0.25).sp),
    displaySmall = toonStyle(family, FontWeight.ExtraBold, 30.sp, 38.sp, 0.sp),
    headlineLarge = toonStyle(family, FontWeight.ExtraBold, 30.sp, 38.sp, (-0.25).sp),
    headlineMedium = toonStyle(family, FontWeight.ExtraBold, 24.sp, 32.sp, 0.sp),
    headlineSmall = toonStyle(family, FontWeight.Bold, 21.sp, 29.sp, 0.sp),
    titleLarge = toonStyle(family, FontWeight.Bold, 20.sp, 28.sp, 0.sp),
    titleMedium = toonStyle(family, FontWeight.Bold, 17.sp, 25.sp, 0.sp),
    titleSmall = toonStyle(family, FontWeight.Bold, 15.sp, 22.sp, 0.sp),
    bodyLarge = toonStyle(family, FontWeight.Medium, 16.sp, 26.sp, 0.sp),
    bodyMedium = toonStyle(family, FontWeight.Medium, 14.sp, 23.sp, 0.sp),
    bodySmall = toonStyle(family, FontWeight.Medium, 12.sp, 19.sp, 0.sp),
    labelLarge = toonStyle(family, FontWeight.Bold, 14.sp, 20.sp, 0.5.sp),
    labelMedium = toonStyle(family, FontWeight.Bold, 12.sp, 17.sp, 0.25.sp),
    labelSmall = toonStyle(family, FontWeight.Bold, 11.sp, 16.sp, 0.sp),
)

private fun toonStyle(
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    tracking: TextUnit,
): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
)

/**
 * The "outlined title" effect the toon skin uses for hero/app-bar headings:
 * the glyphs are stroked in ink underneath the fill, exactly like a manga
 * logotype. Applied by drawing the same [Text] twice — see
 * ui/components/anime/ToonPrimitives.kt `ToonOutlinedTitle`.
 */
fun TextStyle.toonOutlineStroke(ink: Color, width: Float = 6f): TextStyle = copy(
    color = ink,
    drawStyle = Stroke(width = width, join = StrokeJoin.Round, cap = StrokeCap.Round),
)
