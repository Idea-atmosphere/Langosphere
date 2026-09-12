package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.example.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * One named palette preset offered in Settings ▸ Theme ▸ App colors.
 *
 * A preset covers the three color roles the app's palette overrides
 * (primary / secondary / tertiary). Each design language ships its own set
 * of presets tuned to that skin — the toon presets stay ink-safe pastels
 * (applied to the skin's own AnimeColors tokens, day and night), the
 * neobrutalist ones keep loud ink-friendly block colors in the tertiary
 * role (the skin's signature blocks paint from it via neoAccent()), the
 * Material ones stay close to the M3 tonal system — so whatever the active
 * design is, the offered palettes "fit" it by default and stay legible in
 * both light and dark mode. The display name is a string resource resolved
 * through AppStrings, so it follows the in-app language like every other label.
 */
data class ThemePalette(
    val key: String,
    @StringRes val nameRes: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

/** The three overridable roles of the app palette. */
enum class PaletteRole(val prefKey: String) {
    PRIMARY("palette_primary"),
    SECONDARY("palette_secondary"),
    TERTIARY("palette_tertiary")
}

/**
 * The default palette presets, per design language.
 */
object ThemePalettes {

    private val langosphere = listOf(
        ThemePalette(
            key = "ls_indigo", nameRes = R.string.palette_ls_indigo,
            primary = Color(0xFF3A5AD4), secondary = Color(0xFF2F7D6B), tertiary = Color(0xFF7A4FD1)
        ),
        ThemePalette(
            key = "ls_ocean", nameRes = R.string.palette_ls_ocean,
            primary = Color(0xFF1565C0), secondary = Color(0xFF00838F), tertiary = Color(0xFF5E35B1)
        ),
        ThemePalette(
            key = "ls_forest", nameRes = R.string.palette_ls_forest,
            primary = Color(0xFF2E7D32), secondary = Color(0xFF556B2F), tertiary = Color(0xFF00695C)
        ),
        ThemePalette(
            key = "ls_sunset", nameRes = R.string.palette_ls_sunset,
            primary = Color(0xFFD84315), secondary = Color(0xFFC2185B), tertiary = Color(0xFFEF6C00)
        ),
        ThemePalette(
            key = "ls_rose", nameRes = R.string.palette_ls_rose,
            primary = Color(0xFFC2185B), secondary = Color(0xFFAD1457), tertiary = Color(0xFF6A1B9A)
        ),
    )

    private val material3 = listOf(
        ThemePalette(
            key = "m3_baseline", nameRes = R.string.palette_m3_baseline,
            primary = Color(0xFF6750A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260)
        ),
        ThemePalette(
            key = "m3_blue", nameRes = R.string.palette_m3_blue,
            primary = Color(0xFF2E5FA3), secondary = Color(0xFF4E6A8E), tertiary = Color(0xFF6E5AA8)
        ),
        ThemePalette(
            key = "m3_green", nameRes = R.string.palette_m3_green,
            primary = Color(0xFF38693C), secondary = Color(0xFF52634F), tertiary = Color(0xFF3A646C)
        ),
        ThemePalette(
            key = "m3_amber", nameRes = R.string.palette_m3_amber,
            primary = Color(0xFF8B5E00), secondary = Color(0xFF6C5D3F), tertiary = Color(0xFF7B5892)
        ),
        ThemePalette(
            key = "m3_teal", nameRes = R.string.palette_m3_teal,
            primary = Color(0xFF006A6A), secondary = Color(0xFF4D6356), tertiary = Color(0xFF526079)
        ),
    )

    private val materialYou = listOf(
        ThemePalette(
            key = "my_verdant", nameRes = R.string.palette_my_verdant,
            primary = Color(0xFF3F6B4E), secondary = Color(0xFF52634E), tertiary = Color(0xFF3A646C)
        ),
        ThemePalette(
            key = "my_spring", nameRes = R.string.palette_my_spring,
            primary = Color(0xFF2E7D32), secondary = Color(0xFF558B2F), tertiary = Color(0xFF00796B)
        ),
        ThemePalette(
            key = "my_sky", nameRes = R.string.palette_my_sky,
            primary = Color(0xFF1565C0), secondary = Color(0xFF00838F), tertiary = Color(0xFF5C6BC0)
        ),
        ThemePalette(
            key = "my_moss", nameRes = R.string.palette_my_moss,
            primary = Color(0xFF556B2F), secondary = Color(0xFF33691E), tertiary = Color(0xFF00695C)
        ),
        ThemePalette(
            key = "my_blossom", nameRes = R.string.palette_my_blossom,
            primary = Color(0xFFC2185B), secondary = Color(0xFFAD1457), tertiary = Color(0xFF6A1B9A)
        ),
    )

    private val neobrutalism = listOf(
        // The skin's signature BLOCK color is the palette's tertiary role —
        // it is what neoAccent() resolves to in every chunky component — so
        // each preset varies it. All block colors are light/saturated enough
        // for the ink-black glyphs on them AND read as a proper "loud block"
        // on both the cream (day) and charcoal (night) canvases. Primary and
        // secondary support them with the skin's indigo/pink DNA.
        ThemePalette(
            key = "neo_classic", nameRes = R.string.palette_neo_classic,
            primary = Color(0xFF432DD7), secondary = Color(0xFFFF6B6B), tertiary = Color(0xFFFDC800)
        ),
        ThemePalette(
            key = "neo_punk", nameRes = R.string.palette_neo_punk,
            primary = Color(0xFF7C4DFF), secondary = Color(0xFF40C4FF), tertiary = Color(0xFFFF4081)
        ),
        ThemePalette(
            key = "neo_red", nameRes = R.string.palette_neo_red,
            primary = Color(0xFF432DD7), secondary = Color(0xFF00897B), tertiary = Color(0xFFFF5252)
        ),
        ThemePalette(
            key = "neo_green", nameRes = R.string.palette_neo_green,
            primary = Color(0xFF00695C), secondary = Color(0xFF432DD7), tertiary = Color(0xFF00E676)
        ),
        ThemePalette(
            key = "neo_blue", nameRes = R.string.palette_neo_blue,
            primary = Color(0xFF283593), secondary = Color(0xFFFF6B6B), tertiary = Color(0xFF40C4FF)
        ),
    )

    private val anime = listOf(
        // Role mapping on the toon skin: primary → Sakura (CTAs/mascot),
        // secondary → Sky (chrome/labels), tertiary → Sunny (highlights).
        // Every triad is built from the skin's own saturated-pastel family:
        // these hues are painted behind ink glyphs on BOTH the day (paper)
        // and the night canvas, so any preset keeps the ink-on-pastel
        // contrast contract in light and dark mode alike.
        ThemePalette(
            key = "toon_sakura", nameRes = R.string.palette_toon_sakura,
            primary = Color(0xFFFF6FA5), secondary = Color(0xFF5DC8F5), tertiary = Color(0xFFFFD447)
        ),
        ThemePalette(
            key = "toon_sky", nameRes = R.string.palette_toon_sky,
            primary = Color(0xFF5DC8F5), secondary = Color(0xFFFF6FA5), tertiary = Color(0xFF7EE0B0)
        ),
        ThemePalette(
            key = "toon_mint", nameRes = R.string.palette_toon_mint,
            primary = Color(0xFF7EE0B0), secondary = Color(0xFF5DC8F5), tertiary = Color(0xFFB48CFF)
        ),
        ThemePalette(
            key = "toon_lavender", nameRes = R.string.palette_toon_lavender,
            primary = Color(0xFFB48CFF), secondary = Color(0xFFFF6FA5), tertiary = Color(0xFF5DC8F5)
        ),
        ThemePalette(
            key = "toon_sunny", nameRes = R.string.palette_toon_sunny,
            primary = Color(0xFFFFD447), secondary = Color(0xFFFF6FA5), tertiary = Color(0xFF7EE0B0)
        ),
    )

    /** The presets offered for the given design language. */
    fun forDesign(style: AppDesignStyle): List<ThemePalette> = when (style) {
        AppDesignStyle.LANGOSPHERE -> langosphere
        AppDesignStyle.MATERIAL3 -> material3
        AppDesignStyle.MATERIAL_YOU -> materialYou
        AppDesignStyle.NEOBRUTALISM -> neobrutalism
        AppDesignStyle.ANIME -> anime
    }
}

/**
 * The user's palette choice (Settings ▸ Theme ▸ App colors): three optional
 * role overrides applied on top of whatever base color scheme the active
 * design/dynamic-color layer produced. Backed by the same process-wide
 * Compose-state-singleton pattern as the subtitle colors, so MyApplicationTheme
 * picks changes up immediately and every screen re-themes without prop
 * drilling. Choosing "default" clears all three roles; choosing a preset
 * writes all three; the custom editor can also set each role one by one
 * (via swatches or a typed hex code).
 */
object AppPaletteState {
    private const val PREFS_NAME = "app_prefs"
    private const val LEGACY_ACCENT_PREF_KEY = "app_accent_color"

    var primary: Color? by mutableStateOf(null)
    var secondary: Color? by mutableStateOf(null)
    var tertiary: Color? by mutableStateOf(null)

    /** Called before the first composition (MainActivity.onCreate). */
    fun restore(prefs: SharedPreferences) {
        if (prefs.contains(PaletteRole.PRIMARY.prefKey)) {
            primary = prefs.getInt(PaletteRole.PRIMARY.prefKey, 0)
                .takeIf { it != 0 }
                ?.let { Color(it) }
        } else {
            // Migration: the old single "app accent color" picker (from the
            // player settings) becomes this palette's primary role so an
            // existing user's color survives the move to Settings ▸ Theme —
            // once. The legacy pref is consumed here so a later "default"
            // reset is not undone by the next launch.
            val legacyAccent = prefs.getInt(LEGACY_ACCENT_PREF_KEY, 0)
            primary = if (legacyAccent != 0) Color(legacyAccent) else null
            if (legacyAccent != 0) prefs.edit().remove(LEGACY_ACCENT_PREF_KEY).apply()
        }
        secondary = prefs.getInt(PaletteRole.SECONDARY.prefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }
        tertiary = prefs.getInt(PaletteRole.TERTIARY.prefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }
    }

    /** Applies a preset (or null = the design's own default palette). */
    fun set(context: Context, palette: ThemePalette?) {
        val editor = prefs(context).edit()
        if (palette == null) {
            primary = null
            secondary = null
            tertiary = null
            PaletteRole.entries.forEach { role -> editor.remove(role.prefKey) }
        } else {
            primary = palette.primary
            secondary = palette.secondary
            tertiary = palette.tertiary
            editor.putInt(PaletteRole.PRIMARY.prefKey, palette.primary.toArgb())
            editor.putInt(PaletteRole.SECONDARY.prefKey, palette.secondary.toArgb())
            editor.putInt(PaletteRole.TERTIARY.prefKey, palette.tertiary.toArgb())
        }
        editor.apply()
    }

    /** Overrides (or resets, with null) one single role of the palette. */
    fun setRole(context: Context, role: PaletteRole, color: Color?) {
        when (role) {
            PaletteRole.PRIMARY -> primary = color
            PaletteRole.SECONDARY -> secondary = color
            PaletteRole.TERTIARY -> tertiary = color
        }
        val editor = prefs(context).edit()
        if (color == null) editor.remove(role.prefKey)
        else editor.putInt(role.prefKey, color.toArgb())
        editor.apply()
    }

    fun roleColor(role: PaletteRole): Color? = when (role) {
        PaletteRole.PRIMARY -> primary
        PaletteRole.SECONDARY -> secondary
        PaletteRole.TERTIARY -> tertiary
    }

    /**
     * The preset currently matching the stored roles (null when the user is
     * on the design's default palette or on a hand-mixed custom palette).
     */
    fun selectedPreset(style: AppDesignStyle): ThemePalette? =
        ThemePalettes.forDesign(style).firstOrNull { preset ->
            preset.primary == primary &&
                preset.secondary == secondary &&
                preset.tertiary == tertiary
        }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Parses a user-typed hex color ("#3A5AD4", "3A5AD4" or "FF3A5AD4") into a
 * [Color], or null when the text is not a valid hex color. Used by the
 * custom palette editor in Settings ▸ Theme ▸ App colors.
 */
fun parseHexColorOrNull(input: String): Color? {
    val cleaned = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    val argbHex = when (cleaned.length) {
        6 -> "FF$cleaned"
        8 -> cleaned
        else -> return null
    }
    if (cleaned.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val argb = argbHex.toLongOrNull(16) ?: return null
    if (argb < 0L || argb > 0xFFFFFFFFL) return null
    return Color(argb)
}

/** Formats a color back as "#RRGGBB" for display inside the hex field. */
fun formatHexColor(color: Color): String =
    "#" + Integer.toHexString(color.toArgb() and 0xFFFFFF).uppercase().padStart(6, '0')
