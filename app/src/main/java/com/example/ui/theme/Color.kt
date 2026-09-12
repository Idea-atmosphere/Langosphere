package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ── Default app palette (Material 3 Expressive) ──
// A calm but modern palette built on an indigo / teal / violet triad:
//   • primary   — indigo, used for actions and the brand gradient's start
//   • secondary — teal, used for "content exists" states
//   • tertiary  — violet, the brand gradient's counterpart
// Light mode uses cool off-whites instead of stark white; dark mode uses a
// near-black blue-tinted charcoal that looks much deeper on OLED panels while
// keeping WCAG-friendly text contrast. Designed for long reading sessions, so
// nothing is fully saturated.
// This is the app's DEFAULT color layer. On Android 12+ the theme switches
// to the device's Material You dynamic colors (wallpaper-based) by default;
// these palettes remain the fallback for older devices (and whenever the
// dynamic color option is off), so both variants are kept in sync.
val LightPrimary = Color(0xFF3A5AD4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDDE3FF)
val LightOnPrimaryContainer = Color(0xFF101C4F)
val LightSecondary = Color(0xFF2F7D6B)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCFF0E6)
val LightOnSecondaryContainer = Color(0xFF0A342A)
val LightTertiary = Color(0xFF7A4FD1)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFEBE0FF)
val LightOnTertiaryContainer = Color(0xFF2A1360)
val LightBackground = Color(0xFFF4F6FC)
val LightOnBackground = Color(0xFF181B26)
val LightSurface = Color(0xFFFBFCFF)
val LightOnSurface = Color(0xFF181B26)
val LightSurfaceVariant = Color(0xFFE4E8F5)
val LightOnSurfaceVariant = Color(0xFF474F63)
val LightOutline = Color(0xFF8A93A8)
val LightError = Color(0xFFC0463A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE3DE)
val LightOnErrorContainer = Color(0xFF3E0F09)

val DarkPrimary = Color(0xFFAFC1FF)
val DarkOnPrimary = Color(0xFF13224F)
val DarkPrimaryContainer = Color(0xFF2B4181)
val DarkOnPrimaryContainer = Color(0xFFDDE3FF)
val DarkSecondary = Color(0xFF82D8C0)
val DarkOnSecondary = Color(0xFF00382C)
val DarkSecondaryContainer = Color(0xFF23594A)
val DarkOnSecondaryContainer = Color(0xFFC5F3E5)
val DarkTertiary = Color(0xFFCDB4FF)
val DarkOnTertiary = Color(0xFF321269)
val DarkTertiaryContainer = Color(0xFF4B3186)
val DarkOnTertiaryContainer = Color(0xFFEBE0FF)
val DarkBackground = Color(0xFF0B0E15)
val DarkOnBackground = Color(0xFFE6E9F5)
val DarkSurface = Color(0xFF12161F)
val DarkOnSurface = Color(0xFFE6E9F5)
val DarkSurfaceVariant = Color(0xFF1F2532)
val DarkOnSurfaceVariant = Color(0xFFBAC2D6)
val DarkOutline = Color(0xFF7C879F)
val DarkError = Color(0xFFF4B4AC)
val DarkOnError = Color(0xFF5A120C)
val DarkErrorContainer = Color(0xFF5E2A25)
val DarkOnErrorContainer = Color(0xFFFFDAD5)

// ── Shared Accent Colors — desaturated versions of common signal colors so long reading/viewing sessions feel less harsh on the eyes ──
val AccentGreen = Color(0xFF52A87A)
val AccentRed = Color(0xFFDD7A6C)
val AccentCyan = Color(0xFF43B4C2)
val AccentIndigo = Color(0xFF7B8AE8)
val AccentAmber = Color(0xFFE1A855)

// ── Subtitle text colors for LIGHT surfaces ──
// The video overlay always sits on a black backdrop, so it keeps using
// White/AccentAmber there. But the subtitle LIST below the player uses the
// theme background, where white text is unreadable in light mode. These are
// the default colors for subtitle text drawn on light surfaces (the user's
// custom SubtitleColorState choice always overrides them).
val SubtitleEnOnLight = Color(0xFF1C1E26)   // near-black slate — ~15:1 contrast on the paper-white background
val SubtitleFaOnLight = Color(0xFF8A5A00)   // deep amber-brown — readable Persian gold on light surfaces

// ── Anime / Toon design tokens ──
// The fixed palette of the ANIME design language (see DesignStyle.kt): a
// kawaii manga-panel look built from a cream "paper" canvas, thick
// dark-indigo ink outlines and saturated pastel color blocks. Unlike the
// app's other palettes this one is never wallpaper-driven — the ink + pastel
// contrast IS the identity of the skin, so Theme.kt skips dynamic color for
// it.
//
// Contrast note: every *saturated* accent below (Sakura/Sky/Sunny/Mint/
// Lavender) is light enough that [AnimeColors.Ink] (0xFF24203A) sits on it
// well above the WCAG AA 4.5:1 threshold in BOTH themes, which is why the
// toon components always draw ink-colored text/icons on those fills and
// never white text on a pastel.
//
// The `*Soft` washes are different: they are near-white by construction, so
// on the night canvas they would be a glaring light patch carrying the
// near-white `onSurface` text — 1.0:1 contrast, i.e. invisible. Each one
// therefore has a `*SoftDark` counterpart (a deep, desaturated version of
// the same hue) and components must resolve them through
// `ui/components/anime/ToonPrimitives.kt` → `toonSoft()` rather than
// referencing the light constant directly. Pair any fill with `toonOn()`,
// which picks ink or paper by measuring the fill's luminance.
//
// The three palette-driven ACCENT tokens (Sakura / Sky / Sunny, plus their
// Soft/SoftDark companions) are Compose state, not constants: the user's
// palette choice (Settings ▸ Theme ▸ App colors) repaints them through
// [applyPalette] while the structural colors (ink, canvases, text tones and
// the semantic Mint/Lavender accents) stay fixed. That is why the palette
// finally "works" on this skin — its components read these tokens directly,
// not the Material color scheme.
object AnimeColors {
    /** Outlines, body text and the hard offset shadows (light mode). */
    val Ink = Color(0xFF24203A)
    /** Outline/shadow color in dark mode — near-black so the ink edge and
     *  the hard offset shadow still separate a card from the night canvas. */
    val InkDark = Color(0xFF080610)

    /** Light canvas (cream manga paper). */
    val Paper = Color(0xFFFFF6EC)
    /** Light alternate surface (example blocks, card headers). */
    val Paper2 = Color(0xFFFFEEDD)
    /** Dark canvas. Deepened so raised surfaces read as genuinely lifted. */
    val Night = Color(0xFF141024)
    /** Dark card/sheet surface — one clear step above [Night]. */
    val Night2 = Color(0xFF221C3C)
    /** Dark raised surface: menus, pressed rows, the alternate block fill. */
    val Night3 = Color(0xFF2E2650)

    // ── Palette-driven accent tokens ──
    // Defaults are the skin's own pastel triad; a null role keeps (or
    // restores) the default hue. The Soft/SoftDark companions are always
    // derived from the current accent so the day/night contrast contract
    // holds for custom palettes too (soft = washed toward paper for the
    // light canvas, softDark = deepened toward Night for the dark canvas).
    /** Primary — CTAs, the selected tab blob's partner, mascot accent. */
    var Sakura by mutableStateOf(Color(0xFFFF6FA5))
        private set
    var SakuraSoft by mutableStateOf(Color(0xFFFFD4E4))
        private set
    var SakuraSoftDark by mutableStateOf(Color(0xFF4A2135))
        private set

    /** Secondary — player chrome, links, "EN" labels. */
    var Sky by mutableStateOf(Color(0xFF5DC8F5))
        private set
    var SkySoft by mutableStateOf(Color(0xFFD4F0FC))
        private set
    var SkySoftDark by mutableStateOf(Color(0xFF123A4E))
        private set

    /** Accent — highlights, stars/sparkles, tapped words. */
    var Sunny by mutableStateOf(Color(0xFFFFD447))
        private set
    var SunnySoft by mutableStateOf(Color(0xFFFFF1B8))
        private set
    var SunnySoftDark by mutableStateOf(Color(0xFF463415))
        private set

    /** Success — known words, "Got it". */
    val Mint = Color(0xFF7EE0B0)
    val MintSoft = Color(0xFFD6F7E7)
    val MintSoftDark = Color(0xFF123F2C)

    /** Tertiary — the assistant / AI surfaces. */
    val Lavender = Color(0xFFB48CFF)
    val LavenderSoft = Color(0xFFE8DCFF)
    val LavenderSoftDark = Color(0xFF322052)

    /** Secondary text on the light canvas. */
    val Muted = Color(0xFF6F6A86)
    /** Secondary text on the dark canvas (8.8:1 on [Night2]). */
    val MutedDark = Color(0xFFC3BBDC)

    /** Body text color on the dark canvas. */
    val OnNight = Color(0xFFF4F0FC)

    val Error = Color(0xFFE5484D)

    /** The five Leitner "jar" fills, in box order. */
    val BoxFills: List<Color>
        get() = listOf(Sky, Mint, Sunny, Lavender, Sakura)

    // ── Palette sync (called by MyApplicationTheme before the scheme is built) ──
    // Role mapping for the toon skin: palette primary → Sakura, secondary →
    // Sky, tertiary → Sunny. Mint (success) and Lavender (assistant) are
    // semantic tokens and keep their fixed hues.

    /** Repaints the three palette-driven accents (null = that design's own default hue). */
    fun applyPalette(primary: Color?, secondary: Color?, tertiary: Color?) {
        if (primary == null) {
            Sakura = COLOR_SAKURA; SakuraSoft = COLOR_SAKURA_SOFT; SakuraSoftDark = COLOR_SAKURA_SOFT_DARK
        } else {
            Sakura = primary; SakuraSoft = softOf(primary); SakuraSoftDark = softDarkOf(primary)
        }
        if (secondary == null) {
            Sky = COLOR_SKY; SkySoft = COLOR_SKY_SOFT; SkySoftDark = COLOR_SKY_SOFT_DARK
        } else {
            Sky = secondary; SkySoft = softOf(secondary); SkySoftDark = softDarkOf(secondary)
        }
        if (tertiary == null) {
            Sunny = COLOR_SUNNY; SunnySoft = COLOR_SUNNY_SOFT; SunnySoftDark = COLOR_SUNNY_SOFT_DARK
        } else {
            Sunny = tertiary; SunnySoft = softOf(tertiary); SunnySoftDark = softDarkOf(tertiary)
        }
    }

    /** Restores the skin's own default pastel triad. */
    fun resetPalette() = applyPalette(null, null, null)

    // ── The toon skin's own default triad (hand-tuned, restored by resetPalette) ──
    private val COLOR_SAKURA = Color(0xFFFF6FA5)
    private val COLOR_SAKURA_SOFT = Color(0xFFFFD4E4)
    private val COLOR_SAKURA_SOFT_DARK = Color(0xFF4A2135)
    private val COLOR_SKY = Color(0xFF5DC8F5)
    private val COLOR_SKY_SOFT = Color(0xFFD4F0FC)
    private val COLOR_SKY_SOFT_DARK = Color(0xFF123A4E)
    private val COLOR_SUNNY = Color(0xFFFFD447)
    private val COLOR_SUNNY_SOFT = Color(0xFFFFF1B8)
    private val COLOR_SUNNY_SOFT_DARK = Color(0xFF463415)

    /** A wash of [c] for the light (paper) canvas — readable with ink text. */
    private fun softOf(c: Color): Color = lerp(c, Color.White, 0.68f)

    /** The deep night-canvas counterpart of [c] — readable with paper text. */
    private fun softDarkOf(c: Color): Color = lerp(c, Night, 0.78f)
}
