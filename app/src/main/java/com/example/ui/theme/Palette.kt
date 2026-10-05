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
 * (primary / secondary / tertiary) TWICE: one triad for the day (light)
 * canvas and one for the night (dark) canvas. Splitting the two is what
 * keeps a palette readable in both modes — a deep, saturated day color
 * carries white glyphs on a paper canvas but turns into a muddy, unreadable
 * patch on a near-black one, so every night triad is the lighter, softer
 * counterpart of its day triad (the Material 3 "tone 40 by day / tone 80 by
 * night" rule), while the ink-based skins (neobrutalism, toon) keep loud
 * light blocks in both modes because their glyphs are always ink-black.
 *
 * Each design language ships its own set of presets tuned to that skin — the
 * toon presets stay ink-safe pastels (applied to the skin's own AnimeColors
 * tokens, day and night), the neobrutalist ones keep loud ink-friendly block
 * colors in the tertiary role (the skin's signature blocks paint from it via
 * neoAccent()), the Material ones stay close to the M3 tonal system — so
 * whatever the active design is, the offered palettes "fit" it by default and
 * stay legible in both light and dark mode. The display name is a string
 * resource resolved through AppStrings, so it follows the in-app language
 * like every other label.
 */
data class ThemePalette(
    val key: String,
    @StringRes val nameRes: Int,
    // ── Day (light canvas) triad ──
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    // ── Night (dark canvas) triad ──
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkTertiary: Color,
) {
    /** The color this preset gives [role] on the requested canvas. */
    fun colorFor(role: PaletteRole, isDark: Boolean): Color = when (role) {
        PaletteRole.PRIMARY -> if (isDark) darkPrimary else primary
        PaletteRole.SECONDARY -> if (isDark) darkSecondary else secondary
        PaletteRole.TERTIARY -> if (isDark) darkTertiary else tertiary
    }

    /** The three role colors of one canvas, in role order (for previews). */
    fun triad(isDark: Boolean): List<Color> =
        PaletteRole.entries.map { colorFor(it, isDark) }
}

/** The three overridable roles of the app palette. */
enum class PaletteRole(val prefKey: String, val darkPrefKey: String) {
    PRIMARY("palette_primary", "palette_primary_dark"),
    SECONDARY("palette_secondary", "palette_secondary_dark"),
    TERTIARY("palette_tertiary", "palette_tertiary_dark");

    /** The pref key holding this role's color for the requested canvas. */
    fun prefKeyFor(isDark: Boolean): String = if (isDark) darkPrefKey else prefKey
}

/**
 * The default palette presets, per design language.
 *
 * Contrast contract of every triad below:
 *  - Day colors are dark/saturated enough (luminance well under the 0.45
 *    threshold Theme.kt's `contrastingOnColor` uses) that white glyphs sit
 *    on them, and they read as a real accent on the light canvas.
 *  - Night colors are the light, slightly desaturated counterparts, so they
 *    glow on the dark canvas and carry near-black glyphs.
 *  - The two ink skins are the exception BY DESIGN: neobrutalism and the
 *    toon skin always draw ink-black glyphs on their blocks/accents, so
 *    their night triads stay light too — just a touch softer so they do not
 *    glare on the charcoal/night canvas.
 */
object ThemePalettes {

    private val langosphere = listOf(
        ThemePalette(
            key = "ls_indigo", nameRes = R.string.palette_ls_indigo,
            primary = Color(0xFF3A5AD4), secondary = Color(0xFF2F7D6B), tertiary = Color(0xFF7A4FD1),
            darkPrimary = Color(0xFFAFC1FF), darkSecondary = Color(0xFF82D8C0), darkTertiary = Color(0xFFCDB4FF)
        ),
        ThemePalette(
            key = "ls_ocean", nameRes = R.string.palette_ls_ocean,
            primary = Color(0xFF1565C0), secondary = Color(0xFF00838F), tertiary = Color(0xFF5E35B1),
            darkPrimary = Color(0xFFA8C8FF), darkSecondary = Color(0xFF6ED8E6), darkTertiary = Color(0xFFCFBCFF)
        ),
        ThemePalette(
            key = "ls_forest", nameRes = R.string.palette_ls_forest,
            primary = Color(0xFF2E7D32), secondary = Color(0xFF556B2F), tertiary = Color(0xFF00695C),
            darkPrimary = Color(0xFF9FD79B), darkSecondary = Color(0xFFC3D293), darkTertiary = Color(0xFF6FDACB)
        ),
        ThemePalette(
            key = "ls_sunset", nameRes = R.string.palette_ls_sunset,
            primary = Color(0xFFD84315), secondary = Color(0xFFC2185B), tertiary = Color(0xFFEF6C00),
            darkPrimary = Color(0xFFFFB59B), darkSecondary = Color(0xFFFFB1C8), darkTertiary = Color(0xFFFFCC80)
        ),
        ThemePalette(
            key = "ls_rose", nameRes = R.string.palette_ls_rose,
            primary = Color(0xFFC2185B), secondary = Color(0xFFAD1457), tertiary = Color(0xFF6A1B9A),
            darkPrimary = Color(0xFFFFB1C8), darkSecondary = Color(0xFFFFA8C4), darkTertiary = Color(0xFFE2B8FF)
        ),
    )

    private val material3 = listOf(
        // The M3 tonal system: day roles sit around tone 40, night roles
        // around tone 80 of the same hue — exactly how the baseline scheme
        // in Color.kt flips between light and dark.
        ThemePalette(
            key = "m3_baseline", nameRes = R.string.palette_m3_baseline,
            primary = Color(0xFF6750A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260),
            darkPrimary = Color(0xFFCFBCFF), darkSecondary = Color(0xFFCCC2DC), darkTertiary = Color(0xFFEFB8C8)
        ),
        ThemePalette(
            key = "m3_blue", nameRes = R.string.palette_m3_blue,
            primary = Color(0xFF2E5FA3), secondary = Color(0xFF4E6A8E), tertiary = Color(0xFF6E5AA8),
            darkPrimary = Color(0xFFAAC7FF), darkSecondary = Color(0xFFB5C9EF), darkTertiary = Color(0xFFCBBEFF)
        ),
        ThemePalette(
            key = "m3_green", nameRes = R.string.palette_m3_green,
            primary = Color(0xFF38693C), secondary = Color(0xFF52634F), tertiary = Color(0xFF3A646C),
            darkPrimary = Color(0xFF9CD49E), darkSecondary = Color(0xFFB8CCB2), darkTertiary = Color(0xFFA3CED8)
        ),
        ThemePalette(
            key = "m3_amber", nameRes = R.string.palette_m3_amber,
            primary = Color(0xFF8B5E00), secondary = Color(0xFF6C5D3F), tertiary = Color(0xFF7B5892),
            darkPrimary = Color(0xFFF5C26B), darkSecondary = Color(0xFFDAC5A0), darkTertiary = Color(0xFFE7B7FF)
        ),
        ThemePalette(
            key = "m3_teal", nameRes = R.string.palette_m3_teal,
            primary = Color(0xFF006A6A), secondary = Color(0xFF4D6356), tertiary = Color(0xFF526079),
            darkPrimary = Color(0xFF5FDCDB), darkSecondary = Color(0xFFB3CCBB), darkTertiary = Color(0xFFBAC8E8)
        ),
    )

    private val materialYou = listOf(
        ThemePalette(
            key = "my_verdant", nameRes = R.string.palette_my_verdant,
            primary = Color(0xFF3F6B4E), secondary = Color(0xFF52634E), tertiary = Color(0xFF3A646C),
            darkPrimary = Color(0xFFA5D3B3), darkSecondary = Color(0xFFB8CCB2), darkTertiary = Color(0xFFA3CED8)
        ),
        ThemePalette(
            key = "my_spring", nameRes = R.string.palette_my_spring,
            primary = Color(0xFF2E7D32), secondary = Color(0xFF558B2F), tertiary = Color(0xFF00796B),
            darkPrimary = Color(0xFFA3DBA1), darkSecondary = Color(0xFFC0DE9B), darkTertiary = Color(0xFF6FDFCB)
        ),
        ThemePalette(
            key = "my_sky", nameRes = R.string.palette_my_sky,
            primary = Color(0xFF1565C0), secondary = Color(0xFF00838F), tertiary = Color(0xFF5C6BC0),
            darkPrimary = Color(0xFFA8C8FF), darkSecondary = Color(0xFF6ED8E6), darkTertiary = Color(0xFFC0C8FF)
        ),
        ThemePalette(
            key = "my_moss", nameRes = R.string.palette_my_moss,
            primary = Color(0xFF556B2F), secondary = Color(0xFF33691E), tertiary = Color(0xFF00695C),
            darkPrimary = Color(0xFFC6D695), darkSecondary = Color(0xFFBEDF9E), darkTertiary = Color(0xFF7FE0CD)
        ),
        ThemePalette(
            key = "my_blossom", nameRes = R.string.palette_my_blossom,
            primary = Color(0xFFC2185B), secondary = Color(0xFFAD1457), tertiary = Color(0xFF6A1B9A),
            darkPrimary = Color(0xFFFFB2C9), darkSecondary = Color(0xFFFFACC5), darkTertiary = Color(0xFFE3BAFF)
        ),
    )

    private val neobrutalism = listOf(
        // The skin's signature BLOCK color is the palette's tertiary role —
        // it is what neoAccent() resolves to in every chunky component — so
        // each preset varies it. All block colors are light/saturated enough
        // for the ink-black glyphs on them AND read as a proper "loud block"
        // on both the cream (day) and charcoal (night) canvases. The night
        // triads keep that loudness (ink glyphs need a light block) but are
        // lifted/softened a step so they do not vibrate on the dark canvas.
        ThemePalette(
            key = "neo_classic", nameRes = R.string.palette_neo_classic,
            primary = Color(0xFF432DD7), secondary = Color(0xFFFF6B6B), tertiary = Color(0xFFFDC800),
            darkPrimary = Color(0xFF9B8CFF), darkSecondary = Color(0xFFFF9B9B), darkTertiary = Color(0xFFFFD84D)
        ),
        ThemePalette(
            key = "neo_punk", nameRes = R.string.palette_neo_punk,
            primary = Color(0xFF7C4DFF), secondary = Color(0xFF40C4FF), tertiary = Color(0xFFFF4081),
            darkPrimary = Color(0xFFB49BFF), darkSecondary = Color(0xFF7FD8FF), darkTertiary = Color(0xFFFF79A8)
        ),
        ThemePalette(
            key = "neo_red", nameRes = R.string.palette_neo_red,
            primary = Color(0xFF432DD7), secondary = Color(0xFF00897B), tertiary = Color(0xFFFF5252),
            darkPrimary = Color(0xFF9B8CFF), darkSecondary = Color(0xFF4DD0C2), darkTertiary = Color(0xFFFF8A80)
        ),
        ThemePalette(
            key = "neo_green", nameRes = R.string.palette_neo_green,
            primary = Color(0xFF00695C), secondary = Color(0xFF432DD7), tertiary = Color(0xFF00E676),
            darkPrimary = Color(0xFF4DD8C4), darkSecondary = Color(0xFF9B8CFF), darkTertiary = Color(0xFF69F0AE)
        ),
        ThemePalette(
            key = "neo_blue", nameRes = R.string.palette_neo_blue,
            primary = Color(0xFF283593), secondary = Color(0xFFFF6B6B), tertiary = Color(0xFF40C4FF),
            darkPrimary = Color(0xFF8C97E8), darkSecondary = Color(0xFFFF9B9B), darkTertiary = Color(0xFF80D8FF)
        ),
    )

    private val anime = listOf(
        // Role mapping on the toon skin: primary → Sakura (CTAs/mascot),
        // secondary → Sky (chrome/labels), tertiary → Sunny (highlights).
        // Every triad is built from the skin's own saturated-pastel family:
        // these hues are painted behind ink glyphs on BOTH the day (paper)
        // and the night canvas, so the night triads are the *brighter*,
        // airier version of each pastel — they stay ink-safe while reading
        // as a soft manga glow instead of a neon patch on the dark canvas.
        ThemePalette(
            key = "toon_sakura", nameRes = R.string.palette_toon_sakura,
            primary = Color(0xFFFF6FA5), secondary = Color(0xFF5DC8F5), tertiary = Color(0xFFFFD447),
            darkPrimary = Color(0xFFFF9CC1), darkSecondary = Color(0xFF8FDBF8), darkTertiary = Color(0xFFFFE07A)
        ),
        ThemePalette(
            key = "toon_sky", nameRes = R.string.palette_toon_sky,
            primary = Color(0xFF5DC8F5), secondary = Color(0xFFFF6FA5), tertiary = Color(0xFF7EE0B0),
            darkPrimary = Color(0xFF8FDBF8), darkSecondary = Color(0xFFFF9CC1), darkTertiary = Color(0xFFA5EBC8)
        ),
        ThemePalette(
            key = "toon_mint", nameRes = R.string.palette_toon_mint,
            primary = Color(0xFF7EE0B0), secondary = Color(0xFF5DC8F5), tertiary = Color(0xFFB48CFF),
            darkPrimary = Color(0xFFA5EBC8), darkSecondary = Color(0xFF8FDBF8), darkTertiary = Color(0xFFCBAEFF)
        ),
        ThemePalette(
            key = "toon_lavender", nameRes = R.string.palette_toon_lavender,
            primary = Color(0xFFB48CFF), secondary = Color(0xFFFF6FA5), tertiary = Color(0xFF5DC8F5),
            darkPrimary = Color(0xFFCBAEFF), darkSecondary = Color(0xFFFF9CC1), darkTertiary = Color(0xFF8FDBF8)
        ),
        ThemePalette(
            key = "toon_sunny", nameRes = R.string.palette_toon_sunny,
            primary = Color(0xFFFFD447), secondary = Color(0xFFFF6FA5), tertiary = Color(0xFF7EE0B0),
            darkPrimary = Color(0xFFFFE07A), darkSecondary = Color(0xFFFF9CC1), darkTertiary = Color(0xFFA5EBC8)
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
 * role overrides PER CANVAS (day and night are stored separately), applied
 * on top of whatever base color scheme the active design/dynamic-color layer
 * produced. Backed by the same process-wide Compose-state-singleton pattern
 * as the subtitle colors, so MyApplicationTheme picks changes up immediately
 * and every screen re-themes without prop drilling. Choosing "default"
 * clears all six values; choosing a preset writes all six; the custom editor
 * can also set one role of one canvas at a time (via swatches or a typed hex
 * code), which is what lets a light-mode accent stay deep while its
 * dark-mode counterpart stays light enough to keep text readable.
 */
object AppPaletteState {
    private const val PREFS_NAME = "app_prefs"
    private const val LEGACY_ACCENT_PREF_KEY = "app_accent_color"
    /** Marks that the one-time day/night split migration already ran. */
    private const val DARK_SPLIT_MIGRATED_KEY = "palette_dark_split_migrated"

    // ── Day (light canvas) roles ──
    var dayPrimary: Color? by mutableStateOf(null)
    var daySecondary: Color? by mutableStateOf(null)
    var dayTertiary: Color? by mutableStateOf(null)

    // ── Night (dark canvas) roles ──
    var nightPrimary: Color? by mutableStateOf(null)
    var nightSecondary: Color? by mutableStateOf(null)
    var nightTertiary: Color? by mutableStateOf(null)

    // Legacy aliases kept so call sites written before the day/night split
    // keep compiling; they always address the DAY canvas.
    var primary: Color?
        get() = dayPrimary
        set(value) { dayPrimary = value }
    var secondary: Color?
        get() = daySecondary
        set(value) { daySecondary = value }
    var tertiary: Color?
        get() = dayTertiary
        set(value) { dayTertiary = value }

    /** True while every role of both canvases is on the design's own color. */
    val isDefault: Boolean
        get() = dayPrimary == null && daySecondary == null && dayTertiary == null &&
            nightPrimary == null && nightSecondary == null && nightTertiary == null

    /** Called before the first composition (MainActivity.onCreate). */
    fun restore(prefs: SharedPreferences) {
        if (prefs.contains(PaletteRole.PRIMARY.prefKey)) {
            dayPrimary = prefs.getInt(PaletteRole.PRIMARY.prefKey, 0)
                .takeIf { it != 0 }
                ?.let { Color(it) }
        } else {
            // Migration: the old single "app accent color" picker (from the
            // player settings) becomes this palette's primary role so an
            // existing user's color survives the move to Settings ▸ Theme —
            // once. The legacy pref is consumed here so a later "default"
            // reset is not undone by the next launch.
            val legacyAccent = prefs.getInt(LEGACY_ACCENT_PREF_KEY, 0)
            dayPrimary = if (legacyAccent != 0) Color(legacyAccent) else null
            if (legacyAccent != 0) prefs.edit().remove(LEGACY_ACCENT_PREF_KEY).apply()
        }
        daySecondary = prefs.getInt(PaletteRole.SECONDARY.prefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }
        dayTertiary = prefs.getInt(PaletteRole.TERTIARY.prefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }

        nightPrimary = prefs.getInt(PaletteRole.PRIMARY.darkPrefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }
        nightSecondary = prefs.getInt(PaletteRole.SECONDARY.darkPrefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }
        nightTertiary = prefs.getInt(PaletteRole.TERTIARY.darkPrefKey, 0)
            .takeIf { it != 0 }
            ?.let { Color(it) }

        // One-time migration for installs from before the day/night split:
        // back then a role held ONE color used by both canvases, so the
        // stored value is copied into the night canvas as well (and
        // persisted) — the app looks exactly the same after the update, and
        // the user can then retune the night triad on its own. The flag
        // keeps a later deliberate reset of a night role from being undone
        // on the next launch.
        if (!prefs.getBoolean(DARK_SPLIT_MIGRATED_KEY, false)) {
            val editor = prefs.edit()
            PaletteRole.entries.forEach { role ->
                if (roleColor(role, isDark = true) == null) {
                    val dayColor = roleColor(role, isDark = false)
                    if (dayColor != null) {
                        setRoleState(role, isDark = true, color = dayColor)
                        editor.putInt(role.darkPrefKey, dayColor.toArgb())
                    }
                }
            }
            editor.putBoolean(DARK_SPLIT_MIGRATED_KEY, true).apply()
        }
    }

    /** Applies a preset to BOTH canvases (or null = the design's own palette). */
    fun set(context: Context, palette: ThemePalette?) {
        val editor = prefs(context).edit()
        PaletteRole.entries.forEach { role ->
            listOf(false, true).forEach { isDark ->
                val color = palette?.colorFor(role, isDark)
                setRoleState(role, isDark, color)
                if (color == null) editor.remove(role.prefKeyFor(isDark))
                else editor.putInt(role.prefKeyFor(isDark), color.toArgb())
            }
        }
        editor.putBoolean(DARK_SPLIT_MIGRATED_KEY, true)
        editor.apply()
    }

    /**
     * Overrides (or resets, with null) one single role of one single canvas —
     * `isDark = false` edits the day palette, `true` the night one.
     */
    fun setRole(context: Context, role: PaletteRole, isDark: Boolean, color: Color?) {
        setRoleState(role, isDark, color)
        val editor = prefs(context).edit()
        if (color == null) editor.remove(role.prefKeyFor(isDark))
        else editor.putInt(role.prefKeyFor(isDark), color.toArgb())
        editor.putBoolean(DARK_SPLIT_MIGRATED_KEY, true)
        editor.apply()
    }

    /** Legacy entry point: edits the day canvas. */
    fun setRole(context: Context, role: PaletteRole, color: Color?) =
        setRole(context, role, isDark = false, color = color)

    /** The stored override for [role] on the requested canvas, if any. */
    fun roleColor(role: PaletteRole, isDark: Boolean): Color? = when (role) {
        PaletteRole.PRIMARY -> if (isDark) nightPrimary else dayPrimary
        PaletteRole.SECONDARY -> if (isDark) nightSecondary else daySecondary
        PaletteRole.TERTIARY -> if (isDark) nightTertiary else dayTertiary
    }

    /** Legacy entry point: reads the day canvas. */
    fun roleColor(role: PaletteRole): Color? = roleColor(role, isDark = false)

    private fun setRoleState(role: PaletteRole, isDark: Boolean, color: Color?) {
        when (role) {
            PaletteRole.PRIMARY -> if (isDark) nightPrimary = color else dayPrimary = color
            PaletteRole.SECONDARY -> if (isDark) nightSecondary = color else daySecondary = color
            PaletteRole.TERTIARY -> if (isDark) nightTertiary = color else dayTertiary = color
        }
    }

    /**
     * The preset currently matching the stored roles (null when the user is
     * on the design's default palette or on a hand-mixed custom palette).
     * Both canvases have to match, so retuning only the night triad of a
     * preset correctly shows up as a custom palette.
     */
    fun selectedPreset(style: AppDesignStyle): ThemePalette? =
        ThemePalettes.forDesign(style).firstOrNull { preset ->
            PaletteRole.entries.all { role ->
                roleColor(role, isDark = false) == preset.colorFor(role, isDark = false) &&
                    roleColor(role, isDark = true) == preset.colorFor(role, isDark = true)
            }
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
