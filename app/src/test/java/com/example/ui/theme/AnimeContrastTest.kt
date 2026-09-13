package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contrast guarantees for the ANIME palette.
 *
 * The toon skin paints text directly on palette fills rather than on the
 * Material `on*` roles, so a fill added without a matching content color is
 * an accessibility bug that renders as "light box, invisible label". That is
 * exactly what happened on the night canvas when the light `*Soft` washes
 * were used as-is: contrast fell to ~1.0:1.
 *
 * These tests pin the fix: every fill the skin can paint must clear WCAG AA
 * (4.5:1) against the color `toonOn()` would choose for it, and the night
 * surfaces must clear AA for both body and secondary text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimeContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Mirrors ToonPrimitives.toonOn(): whichever candidate contrasts more. */
    private fun onFill(fill: Color): Color =
        if (contrast(AnimeColors.Ink, fill) >= contrast(AnimeColors.OnNight, fill)) {
            AnimeColors.Ink
        } else {
            AnimeColors.OnNight
        }

    private fun assertReadable(name: String, fill: Color) {
        val ratio = contrast(onFill(fill), fill)
        assertTrue(
            "$name fails WCAG AA for body text: %.2f:1 (need 4.5:1)".format(ratio),
            ratio >= 4.5f,
        )
    }

    @Test
    fun everyDarkWashIsReadable() {
        // The night counterparts of the soft tints — the ones that were
        // near-white (and therefore unreadable) before.
        assertReadable("SakuraSoftDark", AnimeColors.SakuraSoftDark)
        assertReadable("SkySoftDark", AnimeColors.SkySoftDark)
        assertReadable("SunnySoftDark", AnimeColors.SunnySoftDark)
        assertReadable("MintSoftDark", AnimeColors.MintSoftDark)
        assertReadable("LavenderSoftDark", AnimeColors.LavenderSoftDark)
    }

    @Test
    fun everyLightWashIsReadable() {
        assertReadable("SakuraSoft", AnimeColors.SakuraSoft)
        assertReadable("SkySoft", AnimeColors.SkySoft)
        assertReadable("SunnySoft", AnimeColors.SunnySoft)
        assertReadable("MintSoft", AnimeColors.MintSoft)
        assertReadable("LavenderSoft", AnimeColors.LavenderSoft)
        assertReadable("Paper", AnimeColors.Paper)
        assertReadable("Paper2", AnimeColors.Paper2)
    }

    @Test
    fun everySaturatedAccentIsReadable() {
        // These are used identically in both themes (ink glyphs on a bright
        // fill), so they have to hold without a night variant.
        assertReadable("Sakura", AnimeColors.Sakura)
        assertReadable("Sky", AnimeColors.Sky)
        assertReadable("Sunny", AnimeColors.Sunny)
        assertReadable("Mint", AnimeColors.Mint)
        assertReadable("Lavender", AnimeColors.Lavender)
        AnimeColors.BoxFills.forEachIndexed { i, fill -> assertReadable("BoxFills[$i]", fill) }
    }

    @Test
    fun nightSurfacesCarryReadableBodyAndSecondaryText() {
        listOf(
            "Night" to AnimeColors.Night,
            "Night2" to AnimeColors.Night2,
            "Night3" to AnimeColors.Night3,
        ).forEach { (name, bg) ->
            assertTrue(
                "$name body text is too low: %.2f:1".format(contrast(AnimeColors.OnNight, bg)),
                contrast(AnimeColors.OnNight, bg) >= 4.5f,
            )
            assertTrue(
                "$name secondary text is too low: %.2f:1".format(contrast(AnimeColors.MutedDark, bg)),
                contrast(AnimeColors.MutedDark, bg) >= 4.5f,
            )
        }
    }

    @Test
    fun paperSurfacesCarryReadableBodyAndSecondaryText() {
        listOf("Paper" to AnimeColors.Paper, "Paper2" to AnimeColors.Paper2).forEach { (name, bg) ->
            assertTrue(
                "$name body text is too low: %.2f:1".format(contrast(AnimeColors.Ink, bg)),
                contrast(AnimeColors.Ink, bg) >= 4.5f,
            )
            assertTrue(
                "$name secondary text is too low: %.2f:1".format(contrast(AnimeColors.Muted, bg)),
                contrast(AnimeColors.Muted, bg) >= 4.5f,
            )
        }
    }

    @Test
    fun theNightRampIsActuallyASeparationOfSurfaces() {
        // Cards must read as lifted off the canvas. The steps are subtle by
        // design (the ink border does the heavy lifting), but they must exist
        // and must go in the right direction.
        assertTrue(
            "Night2 must be lighter than the Night canvas",
            AnimeColors.Night2.luminance() > AnimeColors.Night.luminance(),
        )
        assertTrue(
            "Night3 must be lighter than Night2",
            AnimeColors.Night3.luminance() > AnimeColors.Night2.luminance(),
        )
    }

    @Test
    fun inkStaysDarkerThanEveryNightSurfaceSoBordersRead() {
        // Depth in this skin is the ink border plus the hard offset shadow;
        // if InkDark were lighter than the canvas, cards would lose their edge.
        listOf(AnimeColors.Night, AnimeColors.Night2, AnimeColors.Night3).forEach { surface ->
            assertTrue(
                "InkDark must stay darker than every night surface",
                AnimeColors.InkDark.luminance() < surface.luminance(),
            )
        }
    }

    @Test
    fun darkSchemeUsesDeepContainersNotTheLightWashes() {
        // The original bug in one assertion: a near-white container paired
        // with near-white content text.
        val dark = animeDarkColors()
        listOf(
            dark.primaryContainer to dark.onPrimaryContainer,
            dark.secondaryContainer to dark.onSecondaryContainer,
            dark.tertiaryContainer to dark.onTertiaryContainer,
            dark.surface to dark.onSurface,
            dark.surfaceVariant to dark.onSurfaceVariant,
            dark.background to dark.onBackground,
        ).forEach { (container, onContainer) ->
            assertTrue(
                "A dark-scheme container/content pair falls below AA: %.2f:1"
                    .format(contrast(container, onContainer)),
                contrast(container, onContainer) >= 4.5f,
            )
        }
    }

    @Test
    fun lightSchemeNeverPutsWhiteTextOnAPastel() {
        val light = animeLightColors()
        listOf(
            light.primary to light.onPrimary,
            light.secondary to light.onSecondary,
            light.tertiary to light.onTertiary,
        ).forEach { (fill, onFill) ->
            assertTrue(
                "Content on a pastel must be ink, never white",
                onFill == AnimeColors.Ink,
            )
            assertTrue(
                "A light-scheme accent pair falls below AA: %.2f:1".format(contrast(fill, onFill)),
                contrast(fill, onFill) >= 4.5f,
            )
        }
    }
}
