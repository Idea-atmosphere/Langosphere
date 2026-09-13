package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Routing and contract tests for the ANIME design language.
 *
 * The whole toon skin hangs off [isAnimeDesign] being true for that style
 * and *only* that style: if it regresses, every anime branch in the shared
 * components silently stops rendering while the other four designs keep
 * working, which is exactly the kind of bug that survives a manual pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimeDesignStyleTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun animeIsTheFifthDesignLanguage() {
        assertEquals(5, AppDesignStyle.entries.size)
        // ANIME must stay last: the style is persisted as an enum ordinal, so
        // inserting it anywhere else would silently repaint existing installs.
        assertEquals(AppDesignStyle.ANIME, AppDesignStyle.entries.last())
        assertEquals(4, AppDesignStyle.ANIME.ordinal)
    }

    @Test
    fun designPredicatesAreMutuallyExclusive() {
        // One setContent for the whole matrix — the compose rule only allows
        // a single content root per test.
        val anime = mutableMapOf<AppDesignStyle, Boolean>()
        val material3 = mutableMapOf<AppDesignStyle, Boolean>()
        val materialYou = mutableMapOf<AppDesignStyle, Boolean>()
        val neo = mutableMapOf<AppDesignStyle, Boolean>()

        composeTestRule.setContent {
            AppDesignStyle.entries.forEach { style ->
                CompositionLocalProvider(LocalDesignStyle provides style) {
                    anime[style] = isAnimeDesign()
                    material3[style] = isMaterial3Design()
                    materialYou[style] = isMaterialYouDesign()
                    neo[style] = isNeobrutalismDesign()
                }
            }
        }
        composeTestRule.waitForIdle()

        AppDesignStyle.entries.forEach { style ->
            assertEquals(
                "isAnimeDesign() must be true only for ANIME (checked $style)",
                style == AppDesignStyle.ANIME,
                anime[style],
            )
        }
        // The toon skin must not also take a Material or neobrutalist branch —
        // those early-return before the anime one in several components and
        // would repaint its surfaces.
        assertFalse(material3[AppDesignStyle.ANIME]!!)
        assertFalse(materialYou[AppDesignStyle.ANIME]!!)
        assertFalse(neo[AppDesignStyle.ANIME]!!)
    }

    @Test
    fun animeShapesAreTheRoundestScale() {
        // 10/14/20/28/36dp — rounder than every other design, which is a big
        // part of the sticker look.
        assertEquals(RoundedCornerShape(10.dp), AnimeShapes.extraSmall)
        assertEquals(RoundedCornerShape(14.dp), AnimeShapes.small)
        assertEquals(RoundedCornerShape(20.dp), AnimeShapes.medium)
        assertEquals(RoundedCornerShape(28.dp), AnimeShapes.large)
        assertEquals(RoundedCornerShape(36.dp), AnimeShapes.extraLarge)
    }

    @Test
    fun animeSchemesCarryInkInTheOutlineRole() {
        // Every toon surface takes its border and hard shadow from
        // colorScheme.outline, so the mapping has to hold in both modes.
        assertEquals(AnimeColors.Ink, animeLightColors().outline)
        assertEquals(AnimeColors.InkDark, animeDarkColors().outline)
    }

    @Test
    fun animeNeverPutsWhiteTextOnAPastelFill() {
        // The skin's contrast contract: ink on every accent, never white.
        val light = animeLightColors()
        assertEquals(AnimeColors.Ink, light.onPrimary)
        assertEquals(AnimeColors.Ink, light.onSecondary)
        assertEquals(AnimeColors.Ink, light.onTertiary)
    }

    @Test
    fun typographyForAppLanguage_picksTheToonScaleForAnime() {
        val previous = AppDesignStyleState.style
        try {
            AppDesignStyleState.style = AppDesignStyle.ANIME
            val resolved = Typography.forAppLanguage(AppLanguage.EN)
            val expected = animeTypography()
            assertEquals(expected.headlineMedium.fontSize, resolved.headlineMedium.fontSize)
            assertEquals(expected.bodyMedium.fontWeight, resolved.bodyMedium.fontWeight)
            // And it must not leak into the other designs.
            AppDesignStyleState.style = AppDesignStyle.LANGOSPHERE
            val base = Typography.forAppLanguage(AppLanguage.EN)
            assertTrue(base.headlineMedium.fontSize != expected.headlineMedium.fontSize)
        } finally {
            AppDesignStyleState.style = previous
        }
    }

    @Test
    fun persianUiZeroesTheLatinOnlyLabelTracking() {
        val previous = AppDesignStyleState.style
        try {
            AppDesignStyleState.style = AppDesignStyle.ANIME
            // labelLarge carries 0.5sp of tracking for Latin, but Persian
            // letters have to stay connected, so FA must zero it.
            val fa = Typography.forAppLanguage(AppLanguage.FA)
            assertEquals(0f, fa.labelLarge.letterSpacing.value, 0.001f)
        } finally {
            AppDesignStyleState.style = previous
        }
    }
}
