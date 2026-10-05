package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.theme.AppStrings

/**
 * The caller-owned actions behind the Online tab's action drawer: what the
 * six tool buttons actually do. [VideoPlayerScreen] only carries them
 * through; the Online tab owns the behaviour.
 */
data class OnlineDrawerActions(
    /** ⭐ on the handle bar: whether the open clip is already in the saved list. */
    val isBookmarked: Boolean = false,
    /** ⭐ on the handle bar: add the open clip to (or remove it from) the saved list. */
    val onToggleBookmark: (() -> Unit)? = null,
    /** Button 3: copy ONLY the raw active subtitle (.srt/.vtt), no prompt, no wrapper. */
    val onCopyRawSubtitle: () -> Unit,
    /** Button 4: import the primary (source-language) subtitle file. */
    val onImportMainSubtitle: () -> Unit,
    /** Button 5: import the secondary/target-language subtitle file. */
    val onImportTranslationSubtitle: () -> Unit,
    /** Button 6: import the JSON learning file (the Video tab's chooser). */
    val onImportJsonSubtitle: () -> Unit,
    /** The loaded primary subtitle's file name, shown on the tile (null = empty slot). */
    val mainAttachedName: String? = null,
    /** The loaded secondary subtitle's file name, shown on the tile (null = empty slot). */
    val translationAttachedName: String? = null,
)

/**
 * The Online tab's action drawer (کشوی ابزار) — the collapsible toolbar that
 * replaced the old «متن تعاملی» transcript pane directly beneath the video.
 *
 * Collapsed it is a slim handle bar — with the ⭐ save toggle sitting right
 * on it, so favouriting a clip never needs an extra unfold; opened it
 * reveals the six playback tools in a 3 × 2 grid:
 *
 * 1. quality — an expandable dropdown of the stream's renditions (moved out
 *    of the player settings sheet),
 * 2. «کارهای دیگر پخش‌کننده» — the extra player actions that used to close
 *    the settings sheet, now in their own sheet opened from here (the
 *    video-player settings themselves stay in the floating 3-dot cluster),
 * 3. copy raw subtitles — the active subtitle file's content ONLY, no
 *    pre-configured prompt and no wrapper,
 * 4. import the primary subtitle file,
 * 5. import the secondary/target subtitle file,
 * 6. import the JSON learning file.
 *
 * Buttons 4–6 reuse the Video tab's import pattern: the caller launches the
 * same document picker and parses the file with the same shared parser.
 */
@Composable
fun OnlineActionDrawer(
    qualities: List<String>,
    selectedQuality: Int,
    onSelectQuality: ((Int) -> Unit)?,
    onOpenExtraActions: () -> Unit,
    actions: OnlineDrawerActions,
    strings: AppStrings,
    /** True while a JSON learning file is attached to the open clip. */
    jsonAttached: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(260),
        label = "drawerChevron"
    )

    // The whole drawer mirrors for Persian — the same reading
    // direction as the surfaces that open it. English keeps LTR.
    CompositionLocalProvider(
        LocalLayoutDirection provides if (strings.isEn) LayoutDirection.Ltr else LayoutDirection.Rtl
    ) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        // ── The always-visible handle bar ──
        Surface(
            shape = RoundedCornerShape(OnlineStudioTokens.radiusSmall),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OnlineStudioTokens.radiusSmall))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = strings.onlineDrawerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                actions.onToggleBookmark?.let { toggle ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (actions.isBookmarked) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                            .clickable { toggle() }
                            .padding(6.dp)
                            .testTag("btnOnlineDrawerBookmark"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (actions.isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = strings.onlineBookmarkCd,
                            tint = if (actions.isBookmarked) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = strings.onlineDrawerToggleCd,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
        }

        // ── The six tools, revealed with a vertical accordion animation ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(180))
        ) {
            Surface(
                shape = RoundedCornerShape(OnlineStudioTokens.radiusSmall),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QualityDrawerButton(
                            qualities = qualities,
                            selectedQuality = selectedQuality,
                            onSelectQuality = onSelectQuality,
                            strings = strings,
                            qualityMenuOpen = qualityMenuOpen,
                            onQualityMenuOpenChange = { qualityMenuOpen = it },
                            modifier = Modifier.weight(1f)
                        )
                        DrawerActionButton(
                            icon = Icons.Filled.Apps,
                            label = strings.playerExtraActions,
                            onClick = onOpenExtraActions,
                            modifier = Modifier.weight(1f)
                        )
                        DrawerActionButton(
                            icon = Icons.Filled.ContentCopy,
                            label = strings.onlineCopyRawBtn,
                            onClick = actions.onCopyRawSubtitle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DrawerActionButton(
                            icon = Icons.Filled.Subtitles,
                            label = strings.onlineImportMainBtn,
                            onClick = actions.onImportMainSubtitle,
                            active = actions.mainAttachedName != null,
                            subLabel = actions.mainAttachedName,
                            modifier = Modifier.weight(1f)
                        )
                        DrawerActionButton(
                            icon = Icons.Filled.Translate,
                            label = strings.onlineImportTranslationBtn,
                            onClick = actions.onImportTranslationSubtitle,
                            active = actions.translationAttachedName != null,
                            subLabel = actions.translationAttachedName,
                            modifier = Modifier.weight(1f)
                        )
                        DrawerActionButton(
                            icon = Icons.Filled.FileDownload,
                            label = strings.onlineTbImportJson,
                            onClick = actions.onImportJsonSubtitle,
                            active = jsonAttached,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    }  // RTL provider
}

/**
 * Button 1: the quality selector as an expandable dropdown/popover. Shows
 * the active rendition underneath the label; disabled (with a hint) while
 * the fallback web player is running, where there are no renditions to pick.
 */
@Composable
private fun QualityDrawerButton(
    qualities: List<String>,
    selectedQuality: Int,
    onSelectQuality: ((Int) -> Unit)?,
    strings: AppStrings,
    qualityMenuOpen: Boolean,
    onQualityMenuOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = qualities.isNotEmpty() && onSelectQuality != null
    Box(modifier = modifier) {
        DrawerActionButton(
            icon = Icons.Filled.HighQuality,
            label = strings.onlineQualityBtn,
            subLabel = qualities.getOrNull(selectedQuality) ?: strings.onlineQualityNone,
            enabled = enabled,
            onClick = { if (enabled) onQualityMenuOpenChange(true) },
            // NOT matchParentSize: the dropdown takes no layout space, so a
            // matchParentSize button would size this Box to zero height and
            // the tile would render as an empty slot. Sizing the button by
            // its own content keeps the tile visible; the menu anchors to it.
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = qualityMenuOpen,
            onDismissRequest = { onQualityMenuOpenChange(false) }
        ) {
            qualities.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (index == selectedQuality) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSelectQuality?.invoke(index)
                        onQualityMenuOpenChange(false)
                    }
                )
            }
        }
    }
}

/** One tool tile of the drawer: icon on top, label (and optional hint) below. */
@Composable
private fun DrawerActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    subLabel: String? = null
) {
    val contentColor = if (!enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(OnlineStudioTokens.radiusSmall))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
