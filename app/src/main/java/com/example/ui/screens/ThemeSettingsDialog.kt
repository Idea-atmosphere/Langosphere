package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.GradientButton
import com.example.ui.components.NeoBlock
import com.example.ui.components.SectionHeader
import com.example.ui.components.SoftIconButton
import com.example.ui.components.fontFamilyFor
import com.example.ui.components.rememberCustomFontFamily
import com.example.ui.components.rememberPlayerPrefs
import com.example.ui.theme.AppDesignStyle
import com.example.ui.theme.AppDesignStyleState
import com.example.ui.theme.AppFontScope
import com.example.ui.theme.AppFontState
import com.example.ui.theme.AppPaletteState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DesignStyleStrings
import com.example.ui.theme.FontChoice
import com.example.ui.theme.LocalDesignStyle
import com.example.ui.theme.neoAccent
import com.example.ui.theme.PaletteRole
import com.example.ui.theme.ThemePalette
import com.example.ui.theme.ThemePalettes
import com.example.ui.theme.formatHexColor
import com.example.ui.theme.parseHexColorOrNull
import com.example.ui.components.anime.ToonButton
import com.example.ui.components.anime.ToonCard
import com.example.ui.components.anime.ToonChip
import com.example.ui.components.anime.ToonSwitch
import com.example.ui.components.anime.inkBorder
import com.example.ui.components.anime.toonOn
import com.example.ui.components.anime.toonSoft
import com.example.ui.components.anime.inkShadow
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.AnimeMascotState
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isMaterial3Design
import com.example.ui.theme.isNeobrutalismDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One screen of the Settings ▸ Theme area. */
enum class ThemeSettingsSection {
    /** The hub: theme mode + the buttons that open the three sections. */
    HUB,

    /** The five design languages (used to be inline in [HUB]). */
    DESIGN,

    /** Preset palettes + the custom palette editor. */
    COLORS,

    /** The per-scope font pickers. */
    FONT,
}

/**
 * The tiny back stack of the Settings ▸ Theme area: which sections are open
 * and, crucially, in which order they were opened — so [back] always returns
 * to the exact place the button was pressed instead of dumping the user out
 * of settings.
 *
 * The stack lives in saveable state, so it also survives the `recreate()`
 * that applying a new design triggers: after the whole app is rebuilt in the
 * new skin, the user is still standing in the design picker.
 */
@Stable
class ThemeSettingsNav internal constructor(private val pathState: MutableState<String>) {

    private val stack: List<ThemeSettingsSection>
        get() = pathState.value
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }
            .mapNotNull { name -> runCatching { ThemeSettingsSection.valueOf(name) }.getOrNull() }

    /** The section on screen, or null when the whole area is closed. */
    val current: ThemeSettingsSection?
        get() = stack.lastOrNull()

    /** True while any theme dialog is open. */
    val isOpen: Boolean
        get() = current != null

    /** Push a section on top of the current one (the caller stays below).
     *  Re-opening the section already on screen is a no-op, so the stack can
     *  never grow a duplicate step to walk back through. */
    fun open(section: ThemeSettingsSection) {
        val current = stack
        if (current.lastOrNull() == section) return
        pathState.value = (current + section).joinToString(SEPARATOR) { it.name }
    }

    /** Pop back to whatever opened the current section (closes the area if
     *  nothing did). */
    fun back() {
        pathState.value = stack.dropLast(1).joinToString(SEPARATOR) { it.name }
    }

    /** Close the theme area entirely, whatever depth it is at. */
    fun closeAll() {
        pathState.value = ""
    }

    private companion object {
        const val SEPARATOR = ">"
    }
}

/** Remembers a [ThemeSettingsNav] whose stack survives configuration
 *  changes and the design-switch activity recreate. */
@Composable
fun rememberThemeSettingsNav(): ThemeSettingsNav {
    val path = rememberSaveable { mutableStateOf("") }
    return remember(path) { ThemeSettingsNav(path) }
}

/**
 * Renders whichever Settings ▸ Theme dialog [nav] currently points at, and
 * wires every button to the shared back stack: section buttons push, Back
 * pops to the hub, Close clears the whole stack. Callers just open
 * [ThemeSettingsSection.HUB] from their settings menu.
 */
@Composable
fun ThemeSettingsHost(
    nav: ThemeSettingsNav,
    strings: AppStrings,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    when (nav.current) {
        null -> Unit

        ThemeSettingsSection.HUB -> ThemeSettingsDialog(
            strings = strings,
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenDesign = { nav.open(ThemeSettingsSection.DESIGN) },
            onOpenColors = { nav.open(ThemeSettingsSection.COLORS) },
            onOpenFonts = { nav.open(ThemeSettingsSection.FONT) },
            onDismiss = { nav.closeAll() },
        )

        ThemeSettingsSection.DESIGN -> AppDesignSettingsDialog(
            strings = strings,
            onBack = { nav.back() },
            onDismiss = { nav.closeAll() },
        )

        ThemeSettingsSection.COLORS -> AppColorsSettingsDialog(
            strings = strings,
            onBack = { nav.back() },
            onDismiss = { nav.closeAll() },
        )

        ThemeSettingsSection.FONT -> FontSettingsDialog(
            strings = strings,
            onBack = { nav.back() },
            onDismiss = { nav.closeAll() },
        )
    }
}

/**
 * The Settings ▸ Theme area. The settings menu has a single 🎨 Theme entry
 * that opens ThemeSettingsDialog; that dialog is a small HUB — every other
 * section lives in its own dialog reached from a button inside it, and each
 * of those dialogs carries a Back control that returns to this hub (the
 * place the button was pressed), so moving between sections is one tap in
 * either direction:
 *
 *  - [ThemeSettingsDialog] (this composable): the light / dark / system mode
 *    picker, plus the three section buttons below. Nothing else lives here,
 *    which keeps the dialog short enough to read at a glance.
 *  - [AppDesignSettingsDialog]: the app design picker — the whole visual
 *    system, either the app's own Langosphere skin (glass, gradients, liquid
 *    tab bar, very round corners), the Material Design 3 baseline (tonal
 *    surfaces, real Material components, the M3 top TabRow, the official
 *    shape and type scales), the Material You / M3 Expressive experience
 *    (dynamic color, spring motion, expressive shapes, emphasized type, and
 *    adaptive M3 navigation — bottom NavigationBar on phones, NavigationRail
 *    on wider windows), the Neobrutalism skin (cream/ink palette, square
 *    corners, thick ink borders, hard offset shadows, loud color blocks and
 *    flat ink icons — even these dialogs and every option row re-skin) or
 *    the anime/toon skin (with its Sora mascot toggle). Switching it
 *    re-skins every screen, not just the colors.
 *  - [AppColorsSettingsDialog]: the preset palettes shipped per design
 *    (each design gets palettes that suit it — ink-safe pastels for the
 *    toon skin, loud ink-friendly blocks for neobrutalism, day and night
 *    alike) plus the custom palette editor where every role can be picked
 *    from swatches or typed in as a hex code.
 *  - [FontSettingsDialog]: the per-scope font pickers — the whole app, the
 *    subtitles, the reading files (PDF/text) and the Leitner flashcards —
 *    each with an English/general AND a Persian picker, plus importable
 *    .ttf/.otf fonts. (This picker used to live in the player settings; it
 *    moved here because a font is an app-wide concern.)
 *
 * All four share the [ThemeSettingsShell] chrome, which draws the Back
 * control whenever a dialog was opened from the hub. The design choice is
 * stored in `app_prefs.design_style` and applied by recreating the activity,
 * so every screen is rebuilt with the new design system immediately; the
 * caller keeps the open-section stack in saveable state, so after that
 * recreation the user lands back in the design dialog they were using.
 */
@Composable
fun ThemeSettingsDialog(
    strings: AppStrings,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onOpenDesign: () -> Unit,
    onOpenColors: () -> Unit,
    onOpenFonts: () -> Unit,
    onDismiss: () -> Unit
) {
    val designStrings = remember(strings) { DesignStyleStrings(strings) }
    val currentDesign = LocalDesignStyle.current

    ThemeSettingsShell(title = strings.themeSectionTitle, strings = strings, onDismiss = onDismiss) {
        Text(
            text = strings.themeModeTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = strings.themeModeDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        val modes = listOf(
            Triple(AppThemeMode.LIGHT, Icons.Outlined.LightMode, strings.themeLightMenu),
            Triple(AppThemeMode.DARK, Icons.Outlined.DarkMode, strings.themeDarkMenu),
            Triple(AppThemeMode.SYSTEM, Icons.Outlined.SettingsBrightness, strings.themeSystemMenu)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            modes.forEach { (mode, icon, label) ->
                ThemeModeCard(
                    label = label,
                    icon = icon,
                    selected = currentThemeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(18.dp))

        // ── The theme area's sections, each behind its own button ──
        // The app-design picker used to be printed inline here, which made
        // this dialog a long scroll; it now sits behind the first button,
        // exactly like App colors and Font. Every one of them returns here
        // with its Back control.
        Text(
            text = strings.themeSectionsTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = strings.themeSectionsDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        SectionLinkRow(
            title = strings.themeDesignTitle,
            // The button says which design is on, so the user does not have
            // to open the section just to check.
            subtitle = strings.themeActiveValue(designStrings.titleFor(currentDesign)),
            icon = Icons.Outlined.Brush,
            onClick = onOpenDesign,
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Same for the palette: preset name, "custom" when the user mixed
        // their own roles, or the design's own default.
        val isDefaultPalette = AppPaletteState.primary == null &&
            AppPaletteState.secondary == null &&
            AppPaletteState.tertiary == null
        val paletteName = when {
            isDefaultPalette -> strings.defaultCd
            else -> AppPaletteState.selectedPreset(currentDesign)
                ?.let { strings.paletteName(it) }
                ?: strings.customPaletteTitle
        }
        SectionLinkRow(
            title = strings.themeColorsTitle,
            subtitle = strings.themeActiveValue(paletteName),
            icon = Icons.Outlined.Palette,
            onClick = onOpenColors,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SectionLinkRow(
            title = strings.themeFontTitle,
            icon = Icons.Outlined.TextFields,
            onClick = onOpenFonts,
        )
    }
}

/**
 * Settings ▸ Theme ▸ App design — the five complete design languages, in
 * their own dialog behind the first button of the Theme hub (it used to be
 * printed inline in the Theme dialog, which made that dialog a long scroll).
 *
 * Picking a design recreates the activity so every screen is rebuilt with
 * the new design system; because the caller stores the open-section stack in
 * saveable state, this dialog reopens right after that rebuild — the user
 * sees the result in place and can keep trying designs, then press Back to
 * return to the Theme hub.
 */
@Composable
fun AppDesignSettingsDialog(
    strings: AppStrings,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val designStrings = remember(strings) { DesignStyleStrings(strings) }
    val currentDesign = LocalDesignStyle.current

    val applyDesign: (AppDesignStyle) -> Unit = { style ->
        if (style != currentDesign) {
            AppDesignStyleState.set(context, style)
            // Recreating the activity rebuilds every screen (and every
            // remembered value) with the new shape/type/component system.
            (context as? Activity)?.recreate()
        }
    }

    ThemeSettingsShell(
        title = strings.themeDesignTitle,
        strings = strings,
        onBack = onBack,
        onDismiss = onDismiss,
    ) {
        Text(
            text = designStrings.sectionDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        DesignStyleRow(
            title = designStrings.langosphereTitle,
            icon = Icons.Outlined.AutoAwesome,
            selected = currentDesign == AppDesignStyle.LANGOSPHERE,
            selectedLabel = designStrings.selectedLabel,
            onClick = { applyDesign(AppDesignStyle.LANGOSPHERE) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        DesignStyleRow(
            title = designStrings.material3Title,
            icon = Icons.Outlined.Widgets,
            selected = currentDesign == AppDesignStyle.MATERIAL3,
            selectedLabel = designStrings.selectedLabel,
            onClick = { applyDesign(AppDesignStyle.MATERIAL3) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        DesignStyleRow(
            title = designStrings.materialYouTitle,
            icon = Icons.Outlined.Palette,
            selected = currentDesign == AppDesignStyle.MATERIAL_YOU,
            selectedLabel = designStrings.selectedLabel,
            onClick = { applyDesign(AppDesignStyle.MATERIAL_YOU) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        DesignStyleRow(
            title = designStrings.neobrutalismTitle,
            icon = Icons.Filled.Bolt,
            selected = currentDesign == AppDesignStyle.NEOBRUTALISM,
            selectedLabel = designStrings.selectedLabel,
            onClick = { applyDesign(AppDesignStyle.NEOBRUTALISM) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        DesignStyleRow(
            title = designStrings.animeTitle,
            icon = Icons.Filled.Face,
            selected = currentDesign == AppDesignStyle.ANIME,
            selectedLabel = designStrings.selectedLabel,
            onClick = { applyDesign(AppDesignStyle.ANIME) },
            // The toon option previews itself: a cream chip with the
            // three signature dots behind an ink border.
            swatch = { AnimeSwatch() },
        )

        // The mascot toggle only makes sense while the toon skin is
        // the active design, so it appears with it.
        if (currentDesign == AppDesignStyle.ANIME) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = designStrings.animeMascotTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = designStrings.animeMascotDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                ToonSwitch(
                    checked = AnimeMascotState.enabled,
                    onCheckedChange = { AnimeMascotState.set(context, it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = designStrings.applyNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A row inside the Theme dialog that opens one of the theme area's
 * sub-sections (App design / App colors / Font). Re-skins with the active
 * design like every other row here, carries a chevron on the trailing edge
 * (mirrored in RTL) and can show the section's current value as a
 * [subtitle], so the hub answers "which one is on?" without a tap.
 */
@Composable
private fun SectionLinkRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // "›" points the wrong way in a right-to-left layout (Persian).
    val chevron = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›"

    if (isAnimeDesign()) {
        val fill = toonSoft(AnimeColors.SkySoft)
        ToonCard(
            modifier = modifier.fillMaxWidth(),
            fill = fill,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AnimeColors.Sky)
                        .inkBorder(2.dp, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = AnimeColors.Ink)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = toonOn(fill),
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = toonOn(fill).copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ToonChip(text = chevron, selected = true, fill = AnimeColors.Sunny)
            }
        }
        return
    }

    if (isNeobrutalismDesign()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(neoAccent())
                    .border(2.dp, scheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = chevron,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = scheme.onSurface,
            )
        }
        return
    }

    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, scheme.outline.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = scheme.primary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = chevron,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = scheme.primary,
        )
    }
}

/**
 * Settings ▸ Theme ▸ App colors — its own dialog, opened from a button
 * inside the Theme dialog. The preset palettes of the active design plus
 * the per-role custom editor with swatches and hex input.
 */
@Composable
fun AppColorsSettingsDialog(
    strings: AppStrings,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    ThemeSettingsShell(
        title = strings.themeColorsTitle,
        strings = strings,
        onBack = onBack,
        onDismiss = onDismiss,
    ) {
        Text(
            text = strings.themeColorsDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        AppColorsSection(strings = strings)
    }
}

/**
 * Settings ▸ Theme ▸ Font — its own dialog, opened from a button inside the
 * Theme dialog. The four scopes (whole app / subtitles / reading files /
 * Leitner cards), each with an English and a Persian picker, plus built-in
 * families and importable custom fonts.
 */
@Composable
fun FontSettingsDialog(
    strings: AppStrings,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    ThemeSettingsShell(
        title = strings.themeFontTitle,
        strings = strings,
        onBack = onBack,
        onDismiss = onDismiss,
    ) {
        Text(
            text = strings.themeFontDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        FontSection(strings = strings)
    }
}

/**
 * The shared chrome of the Settings ▸ Theme dialogs (theme hub / design /
 * colors / font): the re-skinning Surface, the scrollable body, the header
 * row and the bottom actions.
 *
 * Sections opened from the hub pass [onBack]. When they do, the shell adds
 * the whole return path back to where the button was pressed: a back arrow
 * before the title, a "Back to Theme" button next to Close, and the system
 * back gesture (Dialog's dismiss request) goes one level up instead of
 * throwing the user out of settings entirely. Close always closes the whole
 * theme area.
 */
@Composable
private fun ThemeSettingsShell(
    title: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        // Back gesture / tap-outside: step back to the hub when this dialog
        // came from it, otherwise close.
        onDismissRequest = onBack ?: onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            color = if (isNeobrutalismDesign()) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = MaterialTheme.shapes.extraLarge,
            // Neither the neobrutalist nor the toon skin uses tonal
            // elevation: both carry their depth in an ink border (3dp for
            // the toon card) instead.
            tonalElevation = if (isNeobrutalismDesign() || isAnimeDesign()) 0.dp else 6.dp,
            border = when {
                isNeobrutalismDesign() -> BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                isAnimeDesign() -> BorderStroke(3.dp, MaterialTheme.colorScheme.outline)
                else -> null
            },
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        // AutoMirrored: the arrow flips itself in the RTL
                        // (Persian) layout.
                        SoftIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            onClick = onBack,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    SectionHeader(
                        title = title,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SoftIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = strings.close,
                        onClick = onDismiss,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                content()

                Spacer(modifier = Modifier.height(22.dp))

                if (onBack != null) {
                    // Two bottom actions: back to the Theme hub (the place
                    // this section was opened from) and close everything.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackActionButton(
                            text = strings.backToThemeBtn,
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        GradientButton(
                            text = strings.close,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    GradientButton(
                        text = strings.close,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The secondary "back to the previous section" button of
 * [ThemeSettingsShell]. Quieter than the [GradientButton] next to it (so
 * Close still reads as the primary action) and re-skinned per design like
 * everything else in this area.
 */
@Composable
private fun BackActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val icon = Icons.AutoMirrored.Filled.ArrowBack

    if (isAnimeDesign()) {
        ToonButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            icon = icon,
            fill = AnimeColors.Sky,
        )
        return
    }

    if (isNeobrutalismDesign()) {
        NeoBlock(
            modifier = modifier,
            container = scheme.surfaceContainerLowest,
            borderColor = scheme.outline,
            borderWidth = 2.dp,
            shadowOffset = 4.dp,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = scheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        return
    }

    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, scheme.outline.copy(alpha = 0.30f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = scheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * One selectable design language (Langosphere / Material Design 3 /
 * Material You / Neubrutalism). When the app is itself in the neobrutalist
 * skin the rows become square ink-outlined cards whose active choice is a
 * flat yellow block with a square check — the settings UI re-skins too.
 */
@Composable
private fun DesignStyleRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    selectedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional preview of the design being offered, shown instead of the
     *  generic icon tile (the toon option uses it to show its palette). */
    swatch: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val material3 = isMaterial3Design()
    val neo = isNeobrutalismDesign()

    if (isAnimeDesign()) {
        // Under the toon skin the picker re-skins too: each option becomes a
        // ToonCard whose active state is a Sunny block with a ✦ marker.
        val rowFill = if (selected) toonSoft(AnimeColors.SunnySoft) else scheme.surface
        val onRow = toonOn(rowFill)
        ToonCard(
            modifier = modifier.fillMaxWidth(),
            fill = rowFill,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (swatch != null) {
                    swatch()
                } else {
                    val disc = toonSoft(AnimeColors.SkySoft)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(disc)
                            .inkBorder(2.dp, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, Modifier.size(22.dp), tint = toonOn(disc))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onRow,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    ToonChip(text = "✦ $selectedLabel", selected = true, fill = AnimeColors.Sakura)
                }
            }
        }
        return
    }

    if (neo) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (selected) neoAccent() else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (selected) scheme.surfaceContainerLowest else neoAccent())
                    .border(2.dp, scheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) scheme.onSurface else Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.Black else scheme.onSurface,
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Square radio: a black square appears when the row is active.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(2.dp, scheme.outline)
                    .background(if (selected) Color.Black else Color.Transparent)
            )
        }
        return
    }

    val shape = MaterialTheme.shapes.large
    val container by animateColorAsState(
        targetValue = when {
            selected && material3 -> scheme.secondaryContainer
            selected -> scheme.primary.copy(alpha = 0.12f)
            material3 -> scheme.surfaceContainerLow
            else -> scheme.surfaceVariant.copy(alpha = 0.35f)
        },
        label = "design-row-bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant,
        label = "design-row-border",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(if (material3) MaterialTheme.shapes.small else CircleShape)
                .background(
                    if (selected) scheme.primary.copy(alpha = 0.18f)
                    else scheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                if (selected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun ThemeModeCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val material3 = isMaterial3Design()
    val neo = isNeobrutalismDesign()

    if (isAnimeDesign()) {
        val cardFill = if (selected) toonSoft(AnimeColors.SkySoft) else scheme.surface
        val onCard = toonOn(cardFill)
        ToonCard(
            modifier = modifier,
            fill = cardFill,
            onClick = onClick,
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val disc = if (selected) AnimeColors.Sunny else scheme.surface
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(disc)
                        .inkBorder(2.dp, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = toonOn(disc))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        return
    }

    if (neo) {
        Column(
            modifier = modifier
                .background(if (selected) neoAccent() else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (selected) scheme.surfaceContainerLowest else neoAccent())
                    .border(2.dp, scheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) scheme.onSurface else Color.Black,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.Black else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        return
    }

    val shape = if (material3) MaterialTheme.shapes.large else RoundedCornerShape(20.dp)
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> scheme.primary
            material3 -> scheme.outlineVariant
            else -> scheme.onSurface.copy(alpha = 0.08f)
        },
        label = "theme-card-border",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            selected && material3 -> scheme.secondaryContainer
            selected -> scheme.primary.copy(alpha = 0.14f)
            material3 -> scheme.surfaceContainerLow
            else -> scheme.surfaceVariant.copy(alpha = 0.35f)
        },
        label = "theme-card-bg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected || material3) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "theme-card-scale",
    )
    val contentColor = if (selected) scheme.primary else scheme.onSurfaceVariant

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(if (material3) MaterialTheme.shapes.small else CircleShape)
                .background(
                    if (selected) scheme.primary.copy(alpha = 0.18f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * The "Anime (Toon)" preview swatch: the cream paper canvas with the three
 * signature accents (Sakura, Sky, Sunny) stamped on it, wrapped in the ink
 * border and hard shadow that define the whole skin. It shows what the
 * option does before the user commits to it.
 */
@Composable
private fun AnimeSwatch() {
    // Deliberately hardcoded to the *light* palette: this is a preview of
    // what the Anime skin looks like, so it must not restyle itself with the
    // current theme. The ink is passed explicitly for the same reason.
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .size(width = 54.dp, height = 42.dp)
            .inkShadow(offset = 3.dp, shape = shape, color = AnimeColors.Ink)
            .clip(shape)
            .background(AnimeColors.Paper)
            .inkBorder(2.dp, shape, AnimeColors.Ink),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(AnimeColors.Sakura, AnimeColors.Sky, AnimeColors.Sunny).forEach { dot ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .inkBorder(1.5.dp, CircleShape, AnimeColors.Ink)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// App colors: per-design preset palettes + the custom palette editor
// ═════════════════════════════════════════════════════════════Palettes══

/** One selectable entry of the palette grid. */
private data class PaletteOption(
    val label: String,
    /** The three role colors, or null for "the design's own default". */
    val colors: List<Color>?,
    val preset: ThemePalette?,
    val selected: Boolean,
)

/**
 * The preset palette grid of the active design (plus the "default" card
 * that clears the override) followed by the per-role custom editor.
 */
@Composable
private fun AppColorsSection(strings: AppStrings) {
    val context = LocalContext.current
    val design = LocalDesignStyle.current
    val presets = remember(design) { ThemePalettes.forDesign(design) }
    val isDefaultPalette = AppPaletteState.primary == null &&
        AppPaletteState.secondary == null &&
        AppPaletteState.tertiary == null
    val selectedPreset = AppPaletteState.selectedPreset(design)

    // ── Preset palettes ──
    Text(
        text = strings.palettePresetsTitle,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(10.dp))

    val options = listOf(
        PaletteOption(
            label = strings.defaultCd,
            colors = null,
            preset = null,
            selected = isDefaultPalette
        )
    ) + presets.map { preset ->
        PaletteOption(
            label = strings.paletteName(preset),
            colors = listOf(preset.primary, preset.secondary, preset.tertiary),
            preset = preset,
            selected = selectedPreset?.key == preset.key
        )
    }
    options.chunked(2).forEach { rowOptions ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowOptions.forEach { option ->
                PalettePresetCard(
                    label = option.label,
                    colors = option.colors,
                    selected = option.selected,
                    onClick = { AppPaletteState.set(context, option.preset) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Keep the two columns aligned when the last row has one card.
            if (rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(10.dp))

    // ── Custom palette (swatches + hex per role) ──
    CustomPaletteSection(strings = strings)
}

/**
 * One palette card: three color dots plus the palette's name. Re-skins per
 * design like every other row in this dialog (toon card / ink square /
 * rounded Material row).
 */
@Composable
private fun PalettePresetCard(
    label: String,
    colors: List<Color>?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    if (isAnimeDesign()) {
        val fill = if (selected) toonSoft(AnimeColors.SunnySoft) else scheme.surface
        ToonCard(
            modifier = modifier,
            fill = fill,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaletteDots(colors = colors)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = toonOn(fill),
                    maxLines = 1,
                )
            }
        }
        return
    }

    if (isNeobrutalismDesign()) {
        Row(
            modifier = modifier
                .background(if (selected) neoAccent() else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaletteDots(colors = colors)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.Black else scheme.onSurface,
                maxLines = 1,
            )
        }
        return
    }

    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) scheme.primary.copy(alpha = 0.12f)
                else scheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.25f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaletteDots(colors = colors)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The three role dots of a palette; hollow rings for the "default" card. */
@Composable
private fun PaletteDots(colors: List<Color>?) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (colors == null) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, scheme.outline.copy(alpha = 0.5f), CircleShape)
                )
            }
        } else {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, scheme.outline.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}

/**
 * The custom palette editor: one row per role (primary / secondary /
 * tertiary). Each role can be picked from a quick-swatch strip, typed in as
 * a hex code, or reset to the design's own color.
 */
@Composable
private fun CustomPaletteSection(strings: AppStrings) {
    val context = LocalContext.current

    Text(
        text = strings.customPaletteTitle,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = strings.customPaletteDesc,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))

    PaletteRole.entries.forEachIndexed { index, role ->
        val roleLabel = when (role) {
            PaletteRole.PRIMARY -> strings.paletteRolePrimary
            PaletteRole.SECONDARY -> strings.paletteRoleSecondary
            PaletteRole.TERTIARY -> strings.paletteRoleTertiary
        }
        RoleColorEditor(
            roleLabel = roleLabel,
            current = AppPaletteState.roleColor(role),
            onApply = { color -> AppPaletteState.setRole(context, role, color) },
            strings = strings,
        )
        if (index != PaletteRole.entries.size - 1) {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** One role of the custom palette: swatches strip + hex input + reset. */
@Composable
private fun RoleColorEditor(
    roleLabel: String,
    current: Color?,
    onApply: (Color?) -> Unit,
    strings: AppStrings,
) {
    val scheme = MaterialTheme.colorScheme
    // The field mirrors the current color and clears itself on reset; typing
    // only re-syncs when the color actually changes (e.g. via a swatch).
    var hexText by remember(current) { mutableStateOf(current?.let { formatHexColor(it) } ?: "") }
    var hexError by remember { mutableStateOf(false) }
    val quickColors = remember {
        listOf(
            Color(0xFF3A5AD4),
            Color(0xFF00838F),
            Color(0xFF2E7D32),
            Color(0xFFE65100),
            Color(0xFFC2185B),
            Color(0xFF7A4FD1),
            Color(0xFF212121),
            Color(0xFFF5F5F5),
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The current color (hollow marker while the role is on default).
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(current ?: scheme.surfaceVariant)
                    .border(1.5.dp, scheme.outline.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.resetBtn,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onApply(null)
                        hexError = false
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            quickColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (current == color) 3.dp else 1.dp,
                            color = if (current == color) scheme.primary
                            else scheme.outline.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onApply(color) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Hex always reads LTR (#AABBCC), whatever the app language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = hexText,
                    onValueChange = {
                        hexText = it
                        hexError = false
                    },
                    label = { Text(strings.hexInputLabel, style = MaterialTheme.typography.labelSmall) },
                    isError = hexError,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start),
                )
                GradientButton(
                    text = strings.applyBtn,
                    onClick = {
                        val parsed = parseHexColorOrNull(hexText)
                        if (parsed == null) {
                            hexError = true
                        } else {
                            hexError = false
                            onApply(parsed)
                        }
                    }
                )
            }
        }
        if (hexError) {
            Text(
                text = strings.hexInvalidError,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Fonts: one choice per scope (app / subtitles / reader / Leitner cards)
// ═══════════════════════════════════════════════════════════════════════

/** Which scope's font the user is currently editing in the font section. */
private enum class FontSettingsScope { APP, SUBTITLES, READER, LEITNER }

/**
 * The font section: four scope chips and, below them, the font picker(s) of
 * the selected scope. EVERY scope offers TWO pickers — English/general and
 * Persian — so the user can give Persian its own face everywhere (Persian
 * UI copy, Persian reader pages, Persian Leitner content), not just in the
 * subtitles. "Subtitles" keeps its original EN/FA pickers; the other scopes
 * each get a matched EN/FA pair.
 *
 * Inheritance: a scope left on "default" inherits the whole-app font of the
 * matching language (see AppFontState.resolvedFamily), so changing the app
 * font re-skins the reader, the subtitles and the cards too — unless the
 * user pinned a separate font for that scope.
 */
@Composable
private fun FontSection(strings: AppStrings) {
    val context = LocalContext.current
    val playerPrefs = rememberPlayerPrefs()
    var activeScope by remember { mutableStateOf(FontSettingsScope.APP) }

    // Import plumbing: the activity-result callback cannot carry a
    // parameter, so each (scope, language) destination gets its own
    // launcher; the picked file is copied into the app's private storage
    // and that scope+language is pointed at it.
    val coroutineScope = rememberCoroutineScope()

    fun importFont(uri: Uri, fileName: String, applyPath: (String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val destFile = File(context.filesDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) { applyPath(destFile.absolutePath) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // rememberLauncherForActivityResult must be called directly in the
    // composable body (one launcher per scope+language destination).
    val appEnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, AppFontScope.APP.fileName) { path ->
                AppFontState.set(context, AppFontScope.APP, fa = false, FontChoice("custom", path))
            }
        }
    }
    val appFaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, AppFontScope.APP.fileNameFa) { path ->
                AppFontState.set(context, AppFontScope.APP, fa = true, FontChoice("custom", path))
            }
        }
    }
    val readerEnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, AppFontScope.READER.fileName) { path ->
                AppFontState.set(context, AppFontScope.READER, fa = false, FontChoice("custom", path))
            }
        }
    }
    val readerFaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, AppFontScope.READER.fileNameFa) { path ->
                AppFontState.set(context, AppFontScope.READER, fa = true, FontChoice("custom", path))
            }
        }
    }
    val leitnerEnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, AppFontScope.LEITNER.fileName) { path ->
                AppFontState.set(context, AppFontScope.LEITNER, fa = false, FontChoice("custom", path))
            }
        }
    }
    val leitnerFaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, AppFontScope.LEITNER.fileNameFa) { path ->
                AppFontState.set(context, AppFontScope.LEITNER, fa = true, FontChoice("custom", path))
            }
        }
    }
    val subEnFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, "custom_subtitle_font_en.ttf") { path ->
                playerPrefs.customFontPathEn = path
                playerPrefs.fontEn = "custom"
            }
        }
    }
    val subFaFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFont(it, "custom_subtitle_font_fa.ttf") { path ->
                playerPrefs.customFontPathFa = path
                playerPrefs.fontFa = "custom"
            }
        }
    }

    val scopes = listOf(
        FontSettingsScope.APP to strings.fontScopeApp,
        FontSettingsScope.SUBTITLES to strings.fontScopeSubtitles,
        FontSettingsScope.READER to strings.fontScopeReader,
        FontSettingsScope.LEITNER to strings.fontScopeLeitner,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        scopes.forEach { (scope, label) ->
            FontScopeChip(
                label = label,
                selected = activeScope == scope,
                onClick = { activeScope = scope },
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Selecting a built-in family keeps an already-imported custom font on
    // file (so the user can hop back to it); "remove custom" clears it.
    fun pick(scope: AppFontScope, fa: Boolean, key: String) {
        val current = AppFontState.choice(scope, fa)
        AppFontState.set(context, scope, fa, FontChoice(key, if (key == "custom") current.customPath else null))
    }

    when (activeScope) {
        FontSettingsScope.APP -> ScopeFontPickers(
            strings = strings,
            choiceEn = AppFontState.app,
            choiceFa = AppFontState.appFa,
            onSelectEn = { pick(AppFontScope.APP, false, it) },
            onSelectFa = { pick(AppFontScope.APP, true, it) },
            onImportEn = { appEnLauncher.launch("*/*") },
            onImportFa = { appFaLauncher.launch("*/*") },
            onRemoveEn = { AppFontState.set(context, AppFontScope.APP, false, FontChoice.DEFAULT) },
            onRemoveFa = { AppFontState.set(context, AppFontScope.APP, true, FontChoice.DEFAULT) },
        )

        FontSettingsScope.SUBTITLES -> {
            Text(
                text = strings.subtitleFontDesc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            FontPicker(
                label = strings.fontEnLabel,
                selectedKey = playerPrefs.fontEn,
                customFamily = rememberCustomFontFamily(playerPrefs.customFontPathEn),
                hasCustom = playerPrefs.customFontPathEn != null,
                strings = strings,
                onSelect = { playerPrefs.fontEn = it },
                onImport = { subEnFontLauncher.launch("*/*") },
                onRemoveCustom = {
                    playerPrefs.customFontPathEn = null
                    if (playerPrefs.fontEn == "custom") playerPrefs.fontEn = "default"
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            FontPicker(
                label = strings.fontFaLabel,
                selectedKey = playerPrefs.fontFa,
                customFamily = rememberCustomFontFamily(playerPrefs.customFontPathFa),
                hasCustom = playerPrefs.customFontPathFa != null,
                strings = strings,
                onSelect = { playerPrefs.fontFa = it },
                onImport = { subFaFontLauncher.launch("*/*") },
                onRemoveCustom = {
                    playerPrefs.customFontPathFa = null
                    if (playerPrefs.fontFa == "custom") playerPrefs.fontFa = "default"
                },
            )
        }

        FontSettingsScope.READER -> ScopeFontPickers(
            strings = strings,
            choiceEn = AppFontState.reader,
            choiceFa = AppFontState.readerFa,
            onSelectEn = { pick(AppFontScope.READER, false, it) },
            onSelectFa = { pick(AppFontScope.READER, true, it) },
            onImportEn = { readerEnLauncher.launch("*/*") },
            onImportFa = { readerFaLauncher.launch("*/*") },
            onRemoveEn = { AppFontState.set(context, AppFontScope.READER, false, FontChoice.DEFAULT) },
            onRemoveFa = { AppFontState.set(context, AppFontScope.READER, true, FontChoice.DEFAULT) },
        )

        FontSettingsScope.LEITNER -> ScopeFontPickers(
            strings = strings,
            choiceEn = AppFontState.leitner,
            choiceFa = AppFontState.leitnerFa,
            onSelectEn = { pick(AppFontScope.LEITNER, false, it) },
            onSelectFa = { pick(AppFontScope.LEITNER, true, it) },
            onImportEn = { leitnerEnLauncher.launch("*/*") },
            onImportFa = { leitnerFaLauncher.launch("*/*") },
            onRemoveEn = { AppFontState.set(context, AppFontScope.LEITNER, false, FontChoice.DEFAULT) },
            onRemoveFa = { AppFontState.set(context, AppFontScope.LEITNER, true, FontChoice.DEFAULT) },
        )
    }
}

/**
 * The EN + FA picker pair shared by the whole-app / reader / Leitner
 * scopes: two full [FontPicker]s under their language labels.
 */
@Composable
private fun ScopeFontPickers(
    strings: AppStrings,
    choiceEn: FontChoice,
    choiceFa: FontChoice,
    onSelectEn: (String) -> Unit,
    onSelectFa: (String) -> Unit,
    onImportEn: () -> Unit,
    onImportFa: () -> Unit,
    onRemoveEn: () -> Unit,
    onRemoveFa: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FontPicker(
            label = strings.fontEnLabel,
            selectedKey = choiceEn.key,
            customFamily = choiceEn.family,
            hasCustom = choiceEn.customPath != null,
            strings = strings,
            onSelect = onSelectEn,
            onImport = onImportEn,
            onRemoveCustom = onRemoveEn,
        )
        Spacer(modifier = Modifier.height(16.dp))
        FontPicker(
            label = strings.fontFaLabel,
            selectedKey = choiceFa.key,
            customFamily = choiceFa.family,
            hasCustom = choiceFa.customPath != null,
            strings = strings,
            onSelect = onSelectFa,
            onImport = onImportFa,
            onRemoveCustom = onRemoveFa,
        )
    }
}

/** One scope chip of the font section. */
@Composable
private fun FontScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (isNeobrutalismDesign()) {
        Box(
            modifier = Modifier
                .background(if (selected) neoAccent() else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.Black else scheme.onSurface,
                maxLines = 1,
            )
        }
        return
    }
    Surface(
        color = if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) scheme.primary else scheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            maxLines = 1,
        )
    }
}

/**
 * The font picker (moved here from the player settings when the font
 * options moved to Settings ▸ Theme): a horizontally scrolling strip of
 * built-in families whose labels preview their own font, plus the custom
 * font import/remove actions.
 */
@Composable
private fun FontPicker(
    label: String,
    selectedKey: String,
    customFamily: FontFamily?,
    hasCustom: Boolean,
    strings: AppStrings,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onRemoveCustom: () -> Unit
) {
    val fontOptions = remember(strings) {
        listOf(
            "default" to strings.fontDefault,
            "serif" to "Serif",
            "sansserif" to "Sans",
            "monospace" to "Mono",
            "cursive" to "Cursive"
        )
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            fontOptions.forEach { (key, optionLabel) ->
                FontActionChip(
                    label = optionLabel,
                    fontFamily = fontFamilyFor(key),
                    selected = selectedKey == key,
                    onClick = { onSelect(key) }
                )
            }
            if (hasCustom) {
                FontActionChip(
                    label = strings.fontCustomLabel,
                    fontFamily = customFamily ?: FontFamily.Default,
                    selected = selectedKey == "custom",
                    onClick = { onSelect("custom") }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FontActionChip(
                label = strings.importCustomFontBtn,
                onClick = onImport
            )
            if (hasCustom) {
                FontActionChip(
                    label = strings.removeCustomFontBtn,
                    destructive = true,
                    onClick = onRemoveCustom
                )
            }
        }
    }
}

/**
 * A selectable/action chip of the font picker. Neobrutalism: selected chips
 * become the loud yellow block with ink-black text; idle chips are raised
 * squares with the ink border; destructive stays a flat error block.
 */
@Composable
private fun FontActionChip(
    label: String,
    fontFamily: FontFamily? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Option chips preview their font (labelMedium); action buttons are the
    // smaller labelSmall, matching the original layout in all designs.
    val textStyle = if (fontFamily != null) {
        MaterialTheme.typography.labelMedium.copy(fontFamily = fontFamily)
    } else {
        MaterialTheme.typography.labelSmall
    }
    if (isNeobrutalismDesign()) {
        val container = when {
            selected -> neoAccent()
            destructive -> scheme.error
            else -> scheme.surfaceContainerLowest
        }
        val content = when {
            selected -> Color.Black
            destructive -> scheme.onError
            else -> scheme.onSurface
        }
        Box(
            modifier = Modifier
                .background(container)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1
            )
        }
        return
    }
    val container = when {
        selected -> scheme.primary.copy(alpha = 0.18f)
        destructive -> scheme.error.copy(alpha = 0.12f)
        else -> scheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val content = when {
        selected -> scheme.primary
        destructive -> scheme.error
        else -> scheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}
