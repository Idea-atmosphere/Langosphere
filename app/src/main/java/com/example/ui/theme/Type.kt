package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
 *  - Persian UI copy is RTL: when the app language is FA the whole
 *    composition uses LayoutDirection.Rtl (except the tab bar/pager which
 *    stay LTR), so rows, start/end paddings and alignments mirror. The
 *    paragraph direction is auto-detected per-text (Content) so Persian
 *    is RTL and English is LTR; letter spacing is zeroed because
 *    Persian letters have to stay connected.
 */
fun Typography.forAppLanguage(language: AppLanguage): Typography {
    val base = when (AppDesignStyleState.style) {
        AppDesignStyle.MATERIAL3 -> Material3Typography
        AppDesignStyle.MATERIAL_YOU -> MaterialYouTypography
        AppDesignStyle.NEOBRUTALISM -> NeoTypography
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
