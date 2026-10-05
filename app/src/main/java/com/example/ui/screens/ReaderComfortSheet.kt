package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.logic.LessonDisplay
import com.example.logic.ReaderComfortState
import com.example.logic.ReaderFontChoice
import com.example.logic.ReaderHighlightState
import com.example.logic.ReaderNoteState
import com.example.logic.ReaderPalettes
import com.example.logic.ReaderThemeMode
import com.example.logic.StudyModeState

/**
 * The reading-comfort panel of the Book tab: type size and spacing, the
 * reader's own day/night switch, paper and ink colors, and a reset.
 *
 * Highlight colors are no longer here — they now live in the text-selection
 * popup (Copy / Share / colors / Note), as requested. This sheet keeps only
 * the list of existing highlights/notes for review and management.
 *
 * Its labels are kept in this file rather than in the shared Strings.kt so the
 * panel stays self-contained; [ReaderComfortLabels] carries both languages.
 *
 * Layout fix: all FilterChip rows now scroll horizontally and every label uses
 * maxLines + ellipsis so Persian text never stacks letter-by-letter vertically.
 */
@Composable
fun ReaderComfortSheet(
    fa: Boolean,
    night: Boolean,
    isPdf: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val labels = remember(fa) { ReaderComfortLabels(fa) }
    var editingNightColors by remember(night) { mutableStateOf(night) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = labels.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = labels.close)
                    }
                }
                Text(
                    text = labels.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                // ── Quick row: size stepper + the reader's own day/night mode ──
                ComfortSection(labels.sectionQuick) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { ReaderComfortState.stepFontScale(-0.1f) }) {
                            Text("A-", fontSize = 16.sp, maxLines = 1)
                        }
                        Text(
                            text = "${(ReaderComfortState.fontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(56.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        TextButton(onClick = { ReaderComfortState.stepFontScale(0.1f) }) {
                            Text("A+", fontSize = 22.sp, maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { ReaderComfortState.resetAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(labels.resetAll, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        ReaderThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = ReaderComfortState.themeMode == mode,
                                onClick = { ReaderComfortState.setThemeMode(mode) },
                                label = { Text(labels.themeMode(mode), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (mode) {
                                            ReaderThemeMode.DAY -> Icons.Default.LightMode
                                            ReaderThemeMode.NIGHT -> Icons.Default.DarkMode
                                            ReaderThemeMode.FOLLOW_APP -> Icons.Default.SettingsBrightness
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = labels.themeModeHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ── Typography ──
                ComfortSection(labels.sectionText) {
                    ComfortSlider(
                        label = labels.fontSize,
                        value = ReaderComfortState.fontScale,
                        valueText = "${(ReaderComfortState.fontScale * 100).toInt()}%",
                        range = 0.7f..2.6f,
                        onValueChange = { ReaderComfortState.setFontScale(it) },
                    )
                    ComfortSlider(
                        label = labels.lineHeight,
                        value = ReaderComfortState.lineHeight,
                        valueText = String.format("%.2fx", ReaderComfortState.lineHeight),
                        range = 1.0f..2.8f,
                        onValueChange = { ReaderComfortState.setLineHeight(it) },
                    )
                    ComfortSlider(
                        label = labels.letterSpacing,
                        value = ReaderComfortState.letterSpacing,
                        valueText = String.format("%.1f", ReaderComfortState.letterSpacing),
                        range = -0.4f..2.0f,
                        onValueChange = { ReaderComfortState.setLetterSpacing(it) },
                    )
                    ComfortSlider(
                        label = labels.paragraphSpacing,
                        value = ReaderComfortState.paragraphSpacing,
                        valueText = "${ReaderComfortState.paragraphSpacing.toInt()}",
                        range = 0f..32f,
                        onValueChange = { ReaderComfortState.setParagraphSpacing(it) },
                    )
                    ComfortSlider(
                        label = labels.margin,
                        value = ReaderComfortState.horizontalMargin,
                        valueText = "${ReaderComfortState.horizontalMargin.toInt()}",
                        range = 0f..56f,
                        onValueChange = { ReaderComfortState.setHorizontalMargin(it) },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        ReaderFontChoice.values().forEach { choice ->
                            FilterChip(
                                selected = ReaderComfortState.fontChoice == choice,
                                onClick = { ReaderComfortState.setFontChoice(choice) },
                                label = { Text(labels.fontChoice(choice), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                    ComfortToggle(
                        label = labels.justify,
                        checked = ReaderComfortState.justify,
                        onCheckedChange = { ReaderComfortState.setJustify(it) },
                    )
                    ComfortToggle(
                        label = labels.boldText,
                        checked = ReaderComfortState.boldText,
                        onCheckedChange = { ReaderComfortState.setBoldText(it) },
                    )
                }

                // ── Study switches (shared with the film subtitles) ──
                ComfortSection(labels.sectionStudy) {
                    ComfortToggle(
                        label = labels.challengeMode,
                        checked = StudyModeState.challengeMode,
                        onCheckedChange = { StudyModeState.setChallengeMode(it) },
                        description = labels.challengeHint,
                    )
                    ComfortToggle(
                        label = labels.focusMode,
                        checked = StudyModeState.focusMode,
                        onCheckedChange = { StudyModeState.setFocusMode(it) },
                        description = labels.focusHint,
                    )
                    Text(
                        text = labels.lessonDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LessonDisplay.values().forEach { choice ->
                            FilterChip(
                                selected = StudyModeState.lessonDisplay == choice,
                                onClick = { StudyModeState.setLessonDisplay(choice) },
                                label = {
                                    Text(
                                        LessonDisplay.label(choice, fa),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                // ── Paper & ink ──
                ComfortSection(labels.sectionColors) {
                    Text(
                        text = labels.paletteHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderPalettes.ALL.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { palette ->
                                    PalettePreview(
                                        name = if (fa) palette.nameFa else palette.nameEn,
                                        background = Color(if (editingNightColors) palette.nightBackground else palette.dayBackground),
                                        ink = Color(if (editingNightColors) palette.nightText else palette.dayText),
                                        selected = ReaderComfortState.paletteKey == palette.key,
                                        modifier = Modifier.weight(1f),
                                        onClick = { ReaderComfortState.setPalette(palette.key) },
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = !editingNightColors,
                            onClick = { editingNightColors = false },
                            label = { Text(labels.editDay, maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = editingNightColors,
                            onClick = { editingNightColors = true },
                            label = { Text(labels.editNight, maxLines = 1, softWrap = false) },
                        )
                    }

                    ColorRow(
                        title = labels.backgroundColor,
                        current = Color(ReaderComfortState.backgroundArgb(editingNightColors)),
                        swatches = if (editingNightColors) NIGHT_BACKGROUNDS else DAY_BACKGROUNDS,
                        onPick = { ReaderComfortState.setCustomBackground(editingNightColors, it) },
                        onClear = { ReaderComfortState.setCustomBackground(editingNightColors, null) },
                        clearLabel = labels.usePaletteColor,
                    )
                    ColorRow(
                        title = labels.textColor,
                        current = Color(ReaderComfortState.textArgb(editingNightColors)),
                        swatches = if (editingNightColors) NIGHT_INKS else DAY_INKS,
                        onPick = { ReaderComfortState.setCustomText(editingNightColors, it) },
                        onClear = { ReaderComfortState.setCustomText(editingNightColors, null) },
                        clearLabel = labels.usePaletteColor,
                    )

                    ComfortSlider(
                        label = labels.warmth,
                        value = ReaderComfortState.warmth,
                        valueText = "${(ReaderComfortState.warmth * 100).toInt()}%",
                        range = 0f..0.35f,
                        onValueChange = { ReaderComfortState.setWarmth(it) },
                    )
                    ComfortSlider(
                        label = labels.dimming,
                        value = ReaderComfortState.dimming,
                        valueText = "${(ReaderComfortState.dimming * 100).toInt()}%",
                        range = 0f..0.6f,
                        onValueChange = { ReaderComfortState.setDimming(it) },
                    )
                    TextButton(onClick = { ReaderComfortState.resetColors() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(labels.resetColors, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }

                // ── Annotations (highlights & notes) — management only, colors are in selection popup ──
                ComfortSection(labels.sectionAnnotations) {
                    val highlights = ReaderHighlightState.items
                    val notes = ReaderNoteState.items
                    if (highlights.isEmpty() && notes.isEmpty()) {
                        Text(
                            text = labels.noHighlights,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    } else {
                        if (highlights.isNotEmpty()) {
                            Text(
                                text = labels.highlightCount(highlights.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            highlights.takeLast(40).asReversed().forEach { highlight ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(Color(highlight.colorArgb), RoundedCornerShape(4.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (highlight.location.isBlank()) highlight.text else "${highlight.text}  ·  ${highlight.location}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (highlight.note.isNotBlank()) {
                                            Text(
                                                text = highlight.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    IconButton(onClick = { ReaderHighlightState.remove(context, highlight) }) {
                                        Icon(Icons.Default.Delete, contentDescription = labels.removeHighlight, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(ReaderHighlightState.asPlainText())) }) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(labels.copyHighlights, maxLines = 1, softWrap = false)
                                }
                                TextButton(onClick = { ReaderHighlightState.clear(context) }) { Text(labels.clearHighlights, maxLines = 1, softWrap = false) }
                            }
                        }
                        if (notes.isNotEmpty()) {
                            Spacer(Modifier.size(4.dp))
                            Text(labels.notesTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            notes.takeLast(20).asReversed().forEach { note ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "${note.text} — ${note.note}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { ReaderNoteState.remove(context, note) }) {
                                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Session ──
                ComfortSection(labels.sectionReading) {
                    ComfortToggle(
                        label = labels.keepScreenOn,
                        checked = ReaderComfortState.keepScreenOn,
                        onCheckedChange = { ReaderComfortState.setKeepScreenOn(it) },
                    )
                    ComfortToggle(
                        label = labels.showProgress,
                        checked = ReaderComfortState.showChapterProgress,
                        onCheckedChange = { ReaderComfortState.setShowChapterProgress(it) },
                    )
                    if (isPdf) {
                        ComfortToggle(
                            label = labels.showPdfImages,
                            description = labels.showPdfImagesHint,
                            checked = ReaderComfortState.showPdfImages,
                            onCheckedChange = { ReaderComfortState.setShowPdfImages(it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds the reading text style from the comfort settings.
 *
 * @param base the app's own body style, used as the starting point.
 * @param appFamily the family chosen in Settings ▸ Theme ▸ Font, used when the
 *   reader is set to follow the app.
 */
fun readerComfortTextStyle(
    base: TextStyle,
    appFamily: FontFamily?,
    inkColor: Color,
): TextStyle {
    val baseSize = if (base.fontSize.value.isNaN() || base.fontSize.value <= 0f) 16f else base.fontSize.value
    val size = (baseSize * ReaderComfortState.fontScale).sp
    val family = when (ReaderComfortState.fontChoice) {
        ReaderFontChoice.APP -> appFamily ?: base.fontFamily
        ReaderFontChoice.SERIF -> FontFamily.Serif
        ReaderFontChoice.SANS -> FontFamily.SansSerif
        ReaderFontChoice.MONO -> FontFamily.Monospace
    }
    return base.copy(
        color = inkColor,
        fontSize = size,
        lineHeight = (ReaderComfortState.lineHeight).em,
        letterSpacing = ReaderComfortState.letterSpacing.sp,
        fontFamily = family,
        // The reading weight comes from the three-stop control (400/500/700) in
        // the reader's own toolbar. `boldText` is a view of the same setting,
        // so the comfort sheet's switch and the W button can never disagree.
        // A style without an explicit weight is the 400 baseline the three-stop
        // control starts from.
        fontWeight = ReaderComfortState.resolveTextWeight(base.fontWeight ?: FontWeight.Normal),
        textAlign = if (ReaderComfortState.justify) TextAlign.Justify else TextAlign.Start,
    )
}

@Composable
private fun ComfortSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    }
}

@Composable
private fun ComfortSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun ComfortToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PalettePreview(
    name: String,
    background: Color,
    ink: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = name, color = ink, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "Aa — آب", color = ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ColorRow(
    title: String,
    current: Color,
    swatches: List<Int>,
    onPick: (Int) -> Unit,
    onClear: () -> Unit,
    clearLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(current, RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            swatches.forEach { argb ->
                Swatch(
                    color = Color(argb),
                    selected = current.value == Color(argb).value,
                    onClick = { onPick(argb) },
                )
            }
        }
        TextButton(onClick = onClear) { Text(clearLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (selected) 30.dp else 26.dp)
            .background(color, RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
    )
}

// Hand-pick lists rather than a free RGB wheel
private val DAY_BACKGROUNDS = listOf(0xFFFFFFFF.toInt(), 0xFFFBF7F0.toInt(), 0xFFF6ECD9.toInt(), 0xFFEFEAE3.toInt(), 0xFFE9F1E7.toInt(), 0xFFE8EEF4.toInt())
private val NIGHT_BACKGROUNDS = listOf(0xFF000000.toInt(), 0xFF101315.toInt(), 0xFF1B1714.toInt(), 0xFF202326.toInt(), 0xFF121A17.toInt(), 0xFF101821.toInt())
private val DAY_INKS = listOf(0xFF111111.toInt(), 0xFF3A3A3A.toInt(), 0xFF4A4034.toInt(), 0xFF33403A.toInt(), 0xFF2E3A45.toInt(), 0xFF5A5A5A.toInt())
private val NIGHT_INKS = listOf(0xFFEDEDED.toInt(), 0xFFD9CDBA.toInt(), 0xFFC8CDD2.toInt(), 0xFFBFD2C6.toInt(), 0xFFBCCBD8.toInt(), 0xFF9AA0A6.toInt())

/** Bilingual labels for the comfort panel, kept beside the panel itself. */
class ReaderComfortLabels(private val fa: Boolean) {

    val title = if (fa) "راحتی خوندن" else "Reading comfort"
    val subtitle = if (fa) "تنظیمات اینجا فقط روی صفحهٔ کتاب اثر دارند و به تم برنامه کاری ندارند." else "These settings only affect the reading surface — the app's own theme stays as it is."
    val close = if (fa) "بستن" else "Close"

    val sectionQuick = if (fa) "دسترسی سریع" else "Quick"
    val sectionText = if (fa) "متن و فونت" else "Text & font"
    val sectionColors = if (fa) "رنگ کاغذ و قلم" else "Paper & ink"
    val sectionAnnotations = if (fa) "هایلایت‌ها و یادداشت‌ها" else "Highlights & notes"
    val sectionReading = if (fa) "جلسهٔ مطالعه" else "Reading session"
    val sectionStudy = if (fa) "حالت مطالعه (مشترک با زیرنویس فیلم)" else "Study mode (shared with film subtitles)"

    val challengeMode = if (fa) "حالت چالش: پنهان کردن ترجمه" else "Challenge mode: hide the translation"
    val challengeHint = if (fa) "ترجمهٔ هر جمله مات می‌شود و با یک لمس آشکار می‌شود؛ اول از حافظه پاسخ بدهید." else "Each translation is blurred and opens on a tap — answer from memory first."
    val focusMode = if (fa) "تمرکز روی یک جمله" else "Focus on one line"
    val focusHint = if (fa) "با روشن بودن این حالت، همهٔ جمله‌ها محو می‌شوند و جملهٔ لمس‌شده روشن می‌ماند. لمس دوباره آن را آزاد می‌کند." else "With this on, every other line fades and the one you tap stays bright. Tapping it again releases it."
    val lessonDisplay = if (fa) "نمایش درس جمله" else "Lesson display"

    val fontSize = if (fa) "اندازهٔ فونت" else "Font size"
    val lineHeight = if (fa) "فاصلهٔ خطوط" else "Line spacing"
    val letterSpacing = if (fa) "فاصلهٔ حروف" else "Letter spacing"
    val paragraphSpacing = if (fa) "فاصلهٔ پاراگراف‌ها" else "Paragraph spacing"
    val margin = if (fa) "حاشیهٔ کناری" else "Side margin"
    val justify = if (fa) "تراز دوطرفه" else "Justify text"
    val boldText = if (fa) "قلم ضخیم‌تر" else "Heavier weight"

    val paletteHint = if (fa) "پیش‌فرض سپیا است؛ کمترین نور آبی و براقی را دارد و برای مطالعهٔ طولانی مناسب‌تر از سفید خالص است." else "Sepia is the default: the least blue light and glare, and easier than pure white over long sessions."
    val editDay = if (fa) "حالت روز" else "Day side"
    val editNight = if (fa) "حالت شب" else "Night side"
    val backgroundColor = if (fa) "رنگ پس‌زمینه" else "Background color"
    val textColor = if (fa) "رنگ متن" else "Text color"
    val usePaletteColor = if (fa) "بازگشت به رنگ پالت" else "Use palette color"
    val warmth = if (fa) "فیلتر گرم (کاهش نور آبی)" else "Warm filter (less blue light)"
    val dimming = if (fa) "کم کردن نور صفحه" else "Extra dimming"
    val resetColors = if (fa) "بازنشانی رنگ‌ها" else "Reset colors"
    val resetAll = if (fa) "بازنشانی همه" else "Reset all"

    val noHighlights = if (fa) "هنوز چیزی هایلایت نشده است. متن را انتخاب کنید و رنگ را از منو بردارید." else "Nothing highlighted yet. Select text and pick a color from the menu."
    val removeHighlight = if (fa) "حذف" else "Remove"
    val copyHighlights = if (fa) "کپی همه" else "Copy all"
    val clearHighlights = if (fa) "پاک کردن همه" else "Clear all"
    val notesTitle = if (fa) "یادداشت‌ها" else "Notes"

    val keepScreenOn = if (fa) "روشن ماندن صفحه هنگام مطالعه" else "Keep screen on while reading"
    val showProgress = if (fa) "نمایش درصد پیشرفت" else "Show reading progress"
    val showPdfImages = if (fa) "نمایش تصویری صفحات PDF" else "PDF page images"
    val showPdfImagesHint = if (fa) "صفحه را دقیقاً مانند اصل کتاب با عکس، جدول و فرمول نشان می‌دهد." else "Shows the page as printed — pictures, tables and formulas included."

    fun themeMode(mode: ReaderThemeMode): String = when (mode) {
        ReaderThemeMode.FOLLOW_APP -> if (fa) "مطابق برنامه" else "Follow app"
        ReaderThemeMode.DAY -> if (fa) "روز" else "Day"
        ReaderThemeMode.NIGHT -> if (fa) "شب" else "Night"
    }
    val themeModeHint = if (fa) "مستقل از تم برنامه؛ مثلاً برنامه تیره بماند و کتاب روشن باشد." else "Independent of the app theme — keep the app dark and the book light, or the other way round."

    fun fontChoice(choice: ReaderFontChoice): String = when (choice) {
        ReaderFontChoice.APP -> if (fa) "فونت برنامه" else "App font"
        ReaderFontChoice.SERIF -> if (fa) "سریف (کتابی)" else "Serif"
        ReaderFontChoice.SANS -> if (fa) "بدون سریف" else "Sans"
        ReaderFontChoice.MONO -> if (fa) "تک‌عرض" else "Mono"
    }
    fun highlightCount(count: Int): String = if (fa) "$count مورد هایلایت‌شده در این کتاب" else "$count highlights in this document"
}
