package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Settings ▸ Theme ▸ Customize ▸ Layout & shapes.
 *
 * Everything in this file is a *layout* override the user can apply on top
 * of the active design language (DesignStyle.kt) without leaving it: where
 * the primary navigation sits, how round every corner in the app is, and in
 * which order the app's sections appear. A design still ships its own
 * defaults — each override has a "design default" value that changes
 * nothing — so these are adjustments to a skin, never a sixth skin.
 *
 * All three are backed by the same process-wide Compose-state-singleton
 * pattern as [AppPaletteState] and [AppDesignStyleState] (state mirrored
 * into `app_prefs`), so a change re-themes / re-lays-out every screen on the
 * next frame with no prop drilling and no activity recreate.
 */

// ── Where the primary navigation sits ──

/**
 * The user's tab-bar placement choice. [DESIGN] keeps whatever the active
 * design does by itself (top tab bar for Langosphere / Material 3 /
 * Neobrutalism, bottom bar for Material You and the toon skin); the other
 * two force the bar to one edge on every design.
 */
enum class AppTabBarPosition { DESIGN, TOP, BOTTOM }

/** True when the active design puts its primary navigation at the bottom. */
fun designPutsTabsAtBottom(style: AppDesignStyle): Boolean =
    style == AppDesignStyle.MATERIAL_YOU || style == AppDesignStyle.ANIME

// ── How round the corners are ──

/**
 * A roundness override applied to every corner radius in the app.
 *
 * The model is deliberately `radius * scale + bias` rather than a fixed
 * radius table: it keeps the *relation* between the shape tokens of the
 * active design (a dialog stays rounder than a chip) while still being
 * meaningful on a design whose own radii are all zero — the bias is what
 * lets the neobrutalist skin be rounded off a little without turning it
 * into a different design.
 */
enum class AppCornerStyle(val scale: Float, val bias: Dp) {
    /** Every corner square, whatever the design says. */
    SHARP(0f, 0.dp),

    /** Noticeably crisper than the design's own scale. */
    CRISP(0.45f, 0.dp),

    /** The design's own shape scale, untouched (the default). */
    DESIGN(1f, 0.dp),

    /** Rounder than the design's own scale. */
    ROUND(1.35f, 2.dp),

    /** As round as the app goes. */
    EXTRA_ROUND(1.8f, 6.dp);

    /** Applies this roundness to one radius of the active design. */
    fun applyTo(designRadius: Dp): Dp = when (this) {
        DESIGN -> designRadius
        else -> (designRadius * scale + bias).coerceAtLeast(0.dp)
    }
}

/**
 * Resolves a hardcoded corner radius through the user's roundness choice.
 *
 * Components that paint their own radius instead of reading
 * `MaterialTheme.shapes` (the glass cards, the liquid tab bar, the chunky
 * neo blocks) call this with the radius they used to hardcode, so the
 * setting reaches them too.
 */
@Composable
@ReadOnlyComposable
fun appCorner(designRadius: Dp): Dp = AppLayoutState.cornerStyle.applyTo(designRadius)

/** The five shape tokens of one design, as raw radii. */
data class AppCornerRadii(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp,
)

/**
 * The shape scale each design ships (the same numbers the static `Shapes`
 * values in Shapes.kt / DesignStyle.kt are built from), kept as radii so the
 * roundness override can be applied to them.
 */
fun designCornerRadii(style: AppDesignStyle, friendlyNeo: Boolean): AppCornerRadii = when (style) {
    AppDesignStyle.LANGOSPHERE -> AppCornerRadii(8.dp, 12.dp, 18.dp, 24.dp, 32.dp)
    AppDesignStyle.MATERIAL3 -> AppCornerRadii(4.dp, 8.dp, 12.dp, 16.dp, 28.dp)
    AppDesignStyle.MATERIAL_YOU -> AppCornerRadii(4.dp, 8.dp, 16.dp, 24.dp, 32.dp)
    AppDesignStyle.ANIME -> AppCornerRadii(10.dp, 14.dp, 20.dp, 28.dp, 36.dp)
    AppDesignStyle.NEOBRUTALISM ->
        if (friendlyNeo) AppCornerRadii(6.dp, 8.dp, 10.dp, 12.dp, 14.dp)
        else AppCornerRadii(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
}

/**
 * The Material shape scale handed to `MaterialTheme` for the active design,
 * with the user's roundness choice applied. With [AppCornerStyle.DESIGN]
 * this reproduces the design's own scale exactly.
 */
fun appShapesFor(
    style: AppDesignStyle,
    friendlyNeo: Boolean,
    corners: AppCornerStyle,
): Shapes {
    val radii = designCornerRadii(style, friendlyNeo)
    return Shapes(
        extraSmall = RoundedCornerShape(corners.applyTo(radii.extraSmall)),
        small = RoundedCornerShape(corners.applyTo(radii.small)),
        medium = RoundedCornerShape(corners.applyTo(radii.medium)),
        large = RoundedCornerShape(corners.applyTo(radii.large)),
        extraLarge = RoundedCornerShape(corners.applyTo(radii.extraLarge)),
    )
}

// ── The app's sections, in the user's own order ──

/**
 * One top-level section of the app. The pager in MainScreen used to key its
 * pages on fixed indices; it now keys them on these values, so the user can
 * reorder the tabs without the page content following the old positions.
 */
enum class AppTab(val prefValue: String) {
    READER("reader"),
    VIDEO("video"),
    ONLINE("online"),
    AGENT("agent"),
    LEITNER("leitner"),
}

/** The tab's label in the current in-app language. */
fun AppTab.titleIn(strings: AppStrings): String = when (this) {
    AppTab.READER -> strings.tabReader
    AppTab.VIDEO -> strings.tabVideo
    AppTab.ONLINE -> strings.tabOnline
    AppTab.AGENT -> strings.tabAgent
    AppTab.LEITNER -> strings.tabLeitner
}

/** The tab's glyph, shared by the tab bars and the reorder editor. */
fun AppTab.icon(): ImageVector = when (this) {
    AppTab.READER -> Icons.Outlined.MenuBook
    AppTab.VIDEO -> Icons.Filled.PlayArrow
    AppTab.ONLINE -> Icons.Filled.Public
    AppTab.AGENT -> Icons.Filled.Language
    AppTab.LEITNER -> Icons.Filled.Style
}

/**
 * The user's tab order (Settings ▸ Theme ▸ Customize ▸ Layout & shapes).
 *
 * Stored as a comma-separated list of [AppTab.prefValue] keys. Restoring is
 * defensive on purpose: unknown keys are dropped and missing ones are
 * appended in their declaration order, so a stored order always ends up as
 * a complete permutation even after the app gains or renames a section.
 */
object AppTabOrderState {
    private const val PREFS_NAME = "app_prefs"
    private const val PREF_KEY = "tab_order"

    val default: List<AppTab> = AppTab.entries.toList()

    var order: List<AppTab> by mutableStateOf(default)
        private set

    /** True while the user is on the app's own order. */
    val isDefault: Boolean
        get() = order == default

    /** Called before the first composition (MainActivity.onCreate). */
    fun restore(prefs: SharedPreferences) {
        order = parse(prefs.getString(PREF_KEY, null))
    }

    /** Moves one tab [delta] places (−1 = earlier, +1 = later). */
    fun move(context: Context, tab: AppTab, delta: Int) {
        val current = order.toMutableList()
        val from = current.indexOf(tab).takeIf { it >= 0 } ?: return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (to == from) return
        current.removeAt(from)
        current.add(to, tab)
        write(context, current)
    }

    /** Back to the app's own order (Reader, Video, Online, Assistant, Tools). */
    fun reset(context: Context) {
        order = default
        prefs(context).edit().remove(PREF_KEY).apply()
    }

    private fun write(context: Context, newOrder: List<AppTab>) {
        order = newOrder
        prefs(context).edit()
            .putString(PREF_KEY, newOrder.joinToString(",") { it.prefValue })
            .apply()
    }

    private fun parse(stored: String?): List<AppTab> {
        if (stored.isNullOrBlank()) return default
        val byKey = AppTab.entries.associateBy { it.prefValue }
        val parsed = stored.split(",")
            .mapNotNull { byKey[it.trim()] }
            .distinct()
        // Any section the stored order does not mention (a new tab added by
        // an app update) keeps its declared place at the end.
        return parsed + default.filterNot { it in parsed }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

// ── The layout overrides themselves ──

/**
 * Tab-bar placement + corner roundness. (The tab order is big enough to
 * deserve its own holder, [AppTabOrderState].)
 */
object AppLayoutState {
    private const val PREFS_NAME = "app_prefs"
    private const val TAB_POSITION_KEY = "tab_bar_position"
    private const val CORNER_STYLE_KEY = "corner_style"

    var tabBarPosition: AppTabBarPosition by mutableStateOf(AppTabBarPosition.DESIGN)
        private set

    var cornerStyle: AppCornerStyle by mutableStateOf(AppCornerStyle.DESIGN)
        private set

    /** True while every layout override is on its "design default" value. */
    val isDefault: Boolean
        get() = tabBarPosition == AppTabBarPosition.DESIGN &&
            cornerStyle == AppCornerStyle.DESIGN

    /** Called before the first composition (MainActivity.onCreate). */
    fun restore(prefs: SharedPreferences) {
        tabBarPosition = AppTabBarPosition.entries.getOrElse(
            prefs.getInt(TAB_POSITION_KEY, AppTabBarPosition.DESIGN.ordinal)
        ) { AppTabBarPosition.DESIGN }
        cornerStyle = AppCornerStyle.entries.getOrElse(
            prefs.getInt(CORNER_STYLE_KEY, AppCornerStyle.DESIGN.ordinal)
        ) { AppCornerStyle.DESIGN }
    }

    fun setTabBarPosition(context: Context, position: AppTabBarPosition) {
        tabBarPosition = position
        prefs(context).edit().putInt(TAB_POSITION_KEY, position.ordinal).apply()
    }

    fun setCornerStyle(context: Context, style: AppCornerStyle) {
        cornerStyle = style
        prefs(context).edit().putInt(CORNER_STYLE_KEY, style.ordinal).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Whether the primary navigation is rendered at the bottom of the screen
 * right now: the user's override when they set one, otherwise the active
 * design's own habit.
 */
@Composable
@ReadOnlyComposable
fun tabsAtBottom(): Boolean = when (AppLayoutState.tabBarPosition) {
    AppTabBarPosition.TOP -> false
    AppTabBarPosition.BOTTOM -> true
    AppTabBarPosition.DESIGN -> designPutsTabsAtBottom(LocalDesignStyle.current)
}
