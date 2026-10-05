package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftIconButton
import com.example.ui.theme.AnimeMascotState
import com.example.ui.theme.AppCornerStyle
import com.example.ui.theme.AppDesignStyle
import com.example.ui.theme.AppLayoutState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.AppTabBarPosition
import com.example.ui.theme.AppTabOrderState
import com.example.ui.theme.DesignStyleStrings
import com.example.ui.theme.FriendlyNeobrutalismState
import com.example.ui.theme.LocalDesignStyle
import com.example.ui.theme.appCorner
import com.example.ui.theme.icon
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign
import com.example.ui.theme.tabsAtBottom
import com.example.ui.theme.titleIn

/**
 * Settings ▸ Theme ▸ Customize ▸ Layout & shapes.
 *
 * The adjustments a user can make *inside* the design they picked, instead
 * of having to switch design to get them:
 *
 *  - **Tab bar position** — the primary navigation at the top or the bottom
 *    edge on any design (each design still keeps its own bar; only the edge
 *    changes). "Design default" keeps the skin's own habit.
 *  - **Corner roundness** — five steps from fully square to extra round,
 *    applied to the active design's whole shape scale plus the components
 *    that paint their own radius, so buttons, cards, dialogs, sheets and the
 *    tab bar all follow.
 *  - **Tab order** — every section of the app moved wherever the user wants
 *    it; the pager keys its pages on the section, not on a fixed index.
 *  - **Extras for this design** — the knobs that only exist for the active
 *    skin (the friendly Neobrutalism variant, Sora on the toon skin).
 *
 * Every control applies immediately and the preview at the top is built
 * from the same live state and the same real components as the app, so the
 * corners and the tab edge in it are literally what the rest of the app is
 * doing.
 */
@Composable
fun LayoutSettingsDialog(
    strings: AppStrings,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val design = LocalDesignStyle.current

    ThemeSettingsShell(
        title = strings.layoutSectionTitle,
        strings = strings,
        onBack = onBack,
        onDismiss = onDismiss,
    ) {
        Text(
            text = strings.layoutSectionDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))
        GroupTitle(title = strings.layoutPreviewTitle, desc = strings.layoutPreviewDesc)
        Spacer(modifier = Modifier.height(10.dp))
        LayoutPreview(strings = strings)

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        // ── Where the tabs live ──
        GroupTitle(title = strings.layoutTabBarTitle, desc = strings.layoutTabBarDesc)
        Spacer(modifier = Modifier.height(10.dp))
        ChoiceRow(
            options = listOf(
                AppTabBarPosition.DESIGN to strings.layoutValueDesignDefault,
                AppTabBarPosition.TOP to strings.layoutTabBarTop,
                AppTabBarPosition.BOTTOM to strings.layoutTabBarBottom,
            ),
            selected = AppLayoutState.tabBarPosition,
            onSelect = { AppLayoutState.setTabBarPosition(context, it) },
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── How round everything is ──
        GroupTitle(title = strings.layoutCornersTitle, desc = strings.layoutCornersDesc)
        Spacer(modifier = Modifier.height(10.dp))
        ChoiceRow(
            options = listOf(
                AppCornerStyle.SHARP to strings.layoutCornerSharp,
                AppCornerStyle.CRISP to strings.layoutCornerCrisp,
                AppCornerStyle.DESIGN to strings.layoutValueDesignDefault,
                AppCornerStyle.ROUND to strings.layoutCornerRound,
                AppCornerStyle.EXTRA_ROUND to strings.layoutCornerExtra,
            ),
            selected = AppLayoutState.cornerStyle,
            onSelect = { AppLayoutState.setCornerStyle(context, it) },
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        // ── The order of the app's sections ──
        GroupTitle(title = strings.layoutTabOrderTitle, desc = strings.layoutTabOrderDesc)
        Spacer(modifier = Modifier.height(10.dp))
        TabOrderEditor(strings = strings)

        // ── Whatever only this design has ──
        val designStrings = DesignStyleStrings(strings)
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        GroupTitle(
            title = strings.layoutDesignExtrasTitle(designStrings.titleFor(design)),
            desc = null,
        )
        Spacer(modifier = Modifier.height(10.dp))
        when (design) {
            // Mirrors of the two design-scoped toggles that also sit next to
            // their design in Settings ▸ Theme ▸ App design — they belong in
            // both places: there while choosing a design, here while tuning
            // the one already on.
            AppDesignStyle.NEOBRUTALISM -> SwitchRow(
                title = designStrings.friendlyNeoTitle,
                desc = designStrings.friendlyNeoDesc,
                checked = FriendlyNeobrutalismState.enabled,
                onCheckedChange = { FriendlyNeobrutalismState.set(context, it) },
            )
            AppDesignStyle.ANIME -> SwitchRow(
                title = designStrings.animeMascotTitle,
                desc = designStrings.animeMascotDesc,
                checked = AnimeMascotState.enabled,
                onCheckedChange = { AnimeMascotState.set(context, it) },
            )
            else -> Text(
                text = strings.layoutDesignExtrasNone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Heading + optional one-line explanation of one group of controls. */
@Composable
private fun GroupTitle(title: String, desc: String?) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (desc != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A wrapping row of mutually exclusive options. Deliberately built from
 * plain Compose primitives plus [appCorner], so the chips themselves also
 * show the roundness the user is choosing.
 */
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val corner = appCorner(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { (value, label) ->
                    val isSelected = value == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(corner))
                            .background(if (isSelected) scheme.primary else scheme.surfaceVariant)
                            .border(
                                width = when {
                                    isNeobrutalismDesign() || isAnimeDesign() -> 2.dp
                                    isSelected -> 0.dp
                                    else -> 1.dp
                                },
                                color = if (isNeobrutalismDesign() || isAnimeDesign()) {
                                    scheme.outline
                                } else {
                                    scheme.outlineVariant
                                },
                                shape = RoundedCornerShape(corner),
                            )
                            .clickable { onSelect(value) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                // Keeps the last row's chips the same width as the rows above.
                repeat(3 - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Title + description + a trailing switch, as used by the design extras. */
@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The live preview.
 *
 * It is not a picture of the design: it is the design. The frame is drawn
 * with [appCorner] and real [GlassCard] / [Button] / [OutlinedButton]
 * components, and the mock tab strip hops from the top edge to the bottom
 * edge exactly as [tabsAtBottom] says — so every choice above is visible
 * here the moment it is made, in the active skin's own colors, borders and
 * shape scale.
 */
@Composable
private fun LayoutPreview(strings: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    val atBottom = tabsAtBottom()
    val chunky = isNeobrutalismDesign() || isAnimeDesign()
    val frameCorner = appCorner(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(frameCorner))
            .background(scheme.background)
            .border(
                width = if (chunky) 2.dp else 1.dp,
                color = if (chunky) scheme.outline else scheme.outlineVariant,
                shape = RoundedCornerShape(frameCorner),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!atBottom) PreviewTabStrip()

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.layoutPreviewCardTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.layoutPreviewCardBody,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) {
                    Text(strings.layoutPreviewPrimaryBtn)
                }
                OutlinedButton(onClick = {}) {
                    Text(strings.layoutPreviewSecondaryBtn)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // A small surface on MaterialTheme.shapes.small, so the preview
            // also shows what happens to the app's chips and pills.
            Surface(
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = strings.layoutPreviewChip,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (atBottom) PreviewTabStrip()
    }
}

/** The mock tab bar of the preview: the app's sections, in the user's order. */
@Composable
private fun PreviewTabStrip() {
    val scheme = MaterialTheme.colorScheme
    val chunky = isNeobrutalismDesign() || isAnimeDesign()
    val barCorner = appCorner(20.dp)
    val itemCorner = appCorner(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(barCorner))
            .background(scheme.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = if (chunky) 2.dp else 0.dp,
                color = if (chunky) scheme.outline else scheme.outlineVariant,
                shape = RoundedCornerShape(barCorner),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTabOrderState.order.forEachIndexed { index, tab ->
            // The first tab stands in for "the selected one", so the
            // indicator's corners are on show as well.
            val isSelected = index == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(itemCorner))
                    .background(if (isSelected) scheme.primary else scheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The tab-order editor: one row per section with the two arrows that move
 * it earlier or later, plus a reset back to the app's own order. The list
 * is the live [AppTabOrderState], so the preview above and the real tab bar
 * behind the dialog both follow every tap.
 */
@Composable
private fun TabOrderEditor(strings: AppStrings) {
    val context = LocalContext.current
    val order = AppTabOrderState.order
    val scheme = MaterialTheme.colorScheme
    val corner = appCorner(16.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEachIndexed { index, tab ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner))
                    .background(scheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = tab.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tab.titleIn(strings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SoftIconButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = strings.layoutMoveEarlier,
                    onClick = { AppTabOrderState.move(context, tab, -1) },
                    enabled = index > 0,
                    size = 34.dp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                SoftIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = strings.layoutMoveLater,
                    onClick = { AppTabOrderState.move(context, tab, 1) },
                    enabled = index < order.lastIndex,
                    size = 34.dp,
                )
            }
        }
        TextButton(
            onClick = { AppTabOrderState.reset(context) },
            enabled = !AppTabOrderState.isDefault,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.layoutResetOrder)
        }
    }
}
