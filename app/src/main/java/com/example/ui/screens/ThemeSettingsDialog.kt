package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.GradientButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.SoftIconButton
import com.example.ui.theme.AppDesignStyle
import com.example.ui.theme.AppDesignStyleState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DesignStyleStrings
import com.example.ui.theme.LocalDesignStyle
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isMaterial3Design
import com.example.ui.theme.isNeobrutalismDesign

/**
 * Settings ▸ Theme section.
 *
 * Two independent choices live here:
 *  1. The light / dark / system mode picker.
 *  2. The app design picker — the whole visual system, either the app's own
 *     Langosphere skin (glass, gradients, liquid tab bar, very round
 *     corners), the Material Design 3 baseline (tonal surfaces, real
 *     Material components, the M3 top TabRow, the official shape and type
 *     scales), the Material You / M3 Expressive experience (dynamic color,
 *     spring motion, expressive shapes, emphasized type, and adaptive M3
 *     navigation — bottom NavigationBar on phones, NavigationRail on wider
 *     windows), or the Neobrutalism skin (cream/ink palette, square corners,
 *     thick ink borders, hard offset shadows, loud color blocks and flat ink
 *     icons — even this dialog and every option row re-skins). Switching it
 *     re-skins every screen, not just the colors.
 *
 * The design choice is stored in `app_prefs.design_style` and applied by
 * recreating the activity, so every screen is rebuilt with the new design
 * system immediately.
 */
@Composable
fun ThemeSettingsDialog(
    strings: AppStrings,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val designStrings = remember(strings.isEn) { DesignStyleStrings(strings.isEn) }
    val currentDesign = LocalDesignStyle.current

    val applyDesign: (AppDesignStyle) -> Unit = { style ->
        if (style != currentDesign) {
            AppDesignStyleState.set(context, style)
            // Recreating the activity rebuilds every screen (and every
            // remembered value) with the new shape/type/component system.
            (context as? Activity)?.recreate()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
            tonalElevation = if (isNeobrutalismDesign()) 0.dp else 6.dp,
            border = if (isNeobrutalismDesign()) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        title = strings.themeSectionTitle,
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

                // ── App design (the four complete design languages) ──
                Text(
                    text = designStrings.sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
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
                Text(
                    text = designStrings.applyNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(22.dp))

                GradientButton(
                    text = strings.close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
) {
    val scheme = MaterialTheme.colorScheme
    val material3 = isMaterial3Design()
    val neo = isNeobrutalismDesign()

    if (neo) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (selected) NeoBrutalismAccent else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (selected) scheme.surfaceContainerLowest else NeoBrutalismAccent)
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

    if (neo) {
        Column(
            modifier = modifier
                .background(if (selected) NeoBrutalismAccent else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (selected) scheme.surfaceContainerLowest else NeoBrutalismAccent)
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
