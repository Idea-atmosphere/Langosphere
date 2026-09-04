package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Tracks
import com.example.logic.autoTextDirection
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppAccentColorState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.SubtitleColorState
import com.example.ui.theme.isNeobrutalismDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Player settings.
 *
 * Every option is grouped into its own glass card, and a card that has a
 * master switch (glassmorphism, subtitles, smart pause) carries that switch
 * in its own header instead of repeating the exact same title and
 * description twice in a row, which is what the previous version did.
 */
@Composable
fun PlayerSettingsSheet(
    prefs: PlayerPrefs,
    strings: AppStrings,
    audioTracks: List<Tracks.Group>,
    onSelectAudioTrack: (Tracks.Group, Int) -> Unit,
    subEnOffset: Double,
    subFaOffset: Double,
    onShiftSubEn: (Double) -> Unit,
    onShiftSubFa: (Double) -> Unit,
    offsetText: (Double) -> String,
    canSaveSrt: Boolean,
    onSaveSrt: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun importCustomFont(uri: Uri, isEnglish: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val destFile = File(
                    context.filesDir,
                    if (isEnglish) "custom_subtitle_font_en.ttf" else "custom_subtitle_font_fa.ttf"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    if (isEnglish) {
                        prefs.customFontPathEn = destFile.absolutePath
                        prefs.fontEn = "custom"
                    } else {
                        prefs.customFontPathFa = destFile.absolutePath
                        prefs.fontFa = "custom"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val fontLauncherEn = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importCustomFont(it, true) }
    }
    val fontLauncherFa = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importCustomFont(it, false) }
    }

    val customFamilyEn = rememberCustomFontFamily(prefs.customFontPathEn)
    val customFamilyFa = rememberCustomFontFamily(prefs.customFontPathFa)

    val neo = isNeobrutalismDesign()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(14.dp).fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (neo) 0.dp else 6.dp,
            border = if (neo) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Title bar ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                            .then(
                                if (neo) {
                                    Modifier.background(NeoBrutalismAccent)
                                } else {
                                    Modifier.background(brandBrush())
                                }
                            )
                            .then(
                                if (neo) Modifier.border(2.dp, MaterialTheme.colorScheme.outline) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (neo) Color.Black else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.playerSettingsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(if (neo) 4.dp else 3.dp)
                                .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                                .then(
                                    if (neo) {
                                        Modifier.background(NeoBrutalismAccent)
                                    } else {
                                        Modifier.background(brandBrush())
                                    }
                                )
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = strings.close,
                        onClick = onDismiss,
                        size = 36.dp
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                ) {
                    // ── Appearance ──
                    SettingsCard(
                        title = strings.design1Title,
                        subtitle = strings.design1Desc,
                        checked = prefs.glassmorphism,
                        onCheckedChange = { prefs.glassmorphism = it }
                    ) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = strings.appAccentColorTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.appAccentColorDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val accentOptions = remember {
                            listOf<Color?>(
                                null,
                                AccentIndigo,
                                AccentCyan,
                                AccentGreen,
                                AccentAmber,
                                AccentRed,
                                Color(0xFFE91E8C),
                                Color(0xFF7A4FD1),
                                Color(0xFFFF7043)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            accentOptions.forEach { swatch ->
                                ColorSwatch(
                                    color = swatch,
                                    selected = AppAccentColorState.color == swatch,
                                    emptyLabel = strings.defaultCd,
                                    onClick = { prefs.setAccentColor(swatch) }
                                )
                            }
                        }
                    }

                    // ── Subtitles ──
                    SettingsCard(
                        title = strings.showSubtitlesTitle,
                        subtitle = strings.showSubtitlesDesc,
                        accent = MaterialTheme.colorScheme.secondary,
                        checked = prefs.subtitlesEnabled,
                        onCheckedChange = { prefs.subtitlesEnabled = it }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SliderRow(
                            title = strings.subtitleFontSizeTitle,
                            valueLabel = "${(prefs.fontSizeFactor * 100).toInt()}%",
                            value = prefs.fontSizeFactor,
                            valueRange = 0.6f..2.0f,
                            onValueChange = { prefs.fontSizeFactor = it }
                        )
                        SliderRow(
                            title = strings.subtitlePositionTitle,
                            valueLabel = "${prefs.bottomPadding.toInt()}dp",
                            value = prefs.bottomPadding,
                            valueRange = 16f..250f,
                            onValueChange = { prefs.bottomPadding = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = strings.subtitleColorTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitleColorOptions = remember {
                            listOf(
                                Color.White,
                                AccentAmber,
                                AccentCyan,
                                AccentGreen,
                                AccentRed,
                                AccentIndigo,
                                Color(0xFFFFD54F),
                                Color(0xFF64B5F6)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SwatchGroupLabel(strings.subEnParenLabel)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            subtitleColorOptions.forEach { swatch ->
                                ColorSwatch(
                                    color = swatch,
                                    selected = SubtitleColorState.colorEn == swatch,
                                    emptyLabel = null,
                                    onClick = { prefs.setSubtitleColorEn(swatch) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        SwatchGroupLabel(strings.subFaParenLabel)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            subtitleColorOptions.forEach { swatch ->
                                ColorSwatch(
                                    color = swatch,
                                    selected = SubtitleColorState.colorFa == swatch,
                                    emptyLabel = null,
                                    onClick = { prefs.setSubtitleColorFa(swatch) }
                                )
                            }
                        }
                    }

                    // ── Fonts ──
                    SettingsCard(
                        title = strings.subtitleFontTitle,
                        subtitle = strings.subtitleFontDesc
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FontPicker(
                            label = strings.fontEnLabel,
                            selectedKey = prefs.fontEn,
                            customFamily = customFamilyEn,
                            hasCustom = prefs.customFontPathEn != null,
                            strings = strings,
                            onSelect = { prefs.fontEn = it },
                            onImport = { fontLauncherEn.launch("*/*") },
                            onRemoveCustom = {
                                prefs.customFontPathEn = null
                                if (prefs.fontEn == "custom") prefs.fontEn = "default"
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FontPicker(
                            label = strings.fontFaLabel,
                            selectedKey = prefs.fontFa,
                            customFamily = customFamilyFa,
                            hasCustom = prefs.customFontPathFa != null,
                            strings = strings,
                            onSelect = { prefs.fontFa = it },
                            onImport = { fontLauncherFa.launch("*/*") },
                            onRemoveCustom = {
                                prefs.customFontPathFa = null
                                if (prefs.fontFa == "custom") prefs.fontFa = "default"
                            }
                        )
                    }

                    // ── Smart pause ──
                    SettingsCard(
                        title = strings.smartPauseTitle,
                        subtitle = strings.smartPauseDesc,
                        accent = MaterialTheme.colorScheme.tertiary,
                        checked = prefs.smartPause,
                        onCheckedChange = { prefs.smartPause = it }
                    ) {
                        if (prefs.smartPause) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SliderRow(
                                title = strings.doubleTapSkipTitle,
                                valueLabel = strings.secondsLabel(prefs.skipSeconds),
                                value = prefs.skipSeconds.toFloat(),
                                valueRange = 2f..30f,
                                steps = 27,
                                onValueChange = { prefs.skipSeconds = it.toInt() }
                            )
                            ToggleRow(
                                title = strings.pauseDimTitle,
                                description = strings.pauseDimDesc,
                                checked = prefs.pauseDim,
                                onCheckedChange = { prefs.pauseDim = it }
                            )
                            ToggleRow(
                                title = strings.pauseHideUiTitle,
                                description = strings.pauseHideUiDesc,
                                checked = prefs.pauseHideUi,
                                onCheckedChange = { prefs.pauseHideUi = it }
                            )
                            ToggleRow(
                                title = strings.pauseRequireContinueTitle,
                                description = strings.pauseRequireContinueDesc,
                                checked = prefs.pauseRequireContinue,
                                onCheckedChange = { prefs.pauseRequireContinue = it }
                            )
                        }
                    }

                    // ── Audio tracks ──
                    SettingsCard(
                        title = strings.audioTrackTitle,
                        subtitle = strings.audioTrackDesc
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (audioTracks.isEmpty()) {
                            Text(
                                text = strings.audioTrackUnavailable,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            audioTracks.forEach { group ->
                                for (trackIndex in 0 until group.length) {
                                    val format = group.getTrackFormat(trackIndex)
                                    val label = format.label?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                                        ?: strings.audioTrackFallbackName(trackIndex + 1)
                                    val language = format.language?.takeIf { it.isNotBlank() }
                                    val selected = group.isTrackSelected(trackIndex)
                                    AudioTrackRow(
                                        label = label,
                                        language = language,
                                        selected = selected,
                                        onClick = { onSelectAudioTrack(group, trackIndex) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Sync ──
                    SettingsCard(
                        title = strings.syncTitle,
                        subtitle = strings.syncHint
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SubtitleShiftControls(
                            title = strings.langCodeEn,
                            offsetLabel = strings.syncCurrentOffset(strings.langCodeEn, offsetText(subEnOffset)),
                            currentOffset = subEnOffset,
                            exactLabel = strings.exactTimeLabel,
                            applyLabel = strings.applyBtn,
                            onShift = onShiftSubEn
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SubtitleShiftControls(
                            title = strings.langCodeFa,
                            offsetLabel = strings.syncCurrentOffset(strings.langCodeFa, offsetText(subFaOffset)),
                            currentOffset = subFaOffset,
                            exactLabel = strings.exactTimeLabel,
                            applyLabel = strings.applyBtn,
                            onShift = onShiftSubFa
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        GradientButton(
                            text = strings.saveSrtBtn,
                            onClick = onSaveSrt,
                            enabled = canSaveSrt,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!canSaveSrt) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.noFaSubtitleToSave,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    GradientButton(
                        text = strings.confirmReturnBtn,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * One settings group. When [checked] is provided the group's master switch
 * lives in the card header, so the title is never printed twice.
 */
@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    accent: Color? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val checkedValue = checked
    val changeHandler = onCheckedChange
    val trailing: (@Composable () -> Unit)? =
        if (checkedValue != null && changeHandler != null) {
            { Switch(checked = checkedValue, onCheckedChange = changeHandler) }
        } else null

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        tint = accent,
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        SectionHeader(title = title, subtitle = subtitle, trailing = trailing)
        content()
    }
}

@Composable
private fun SwatchGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun AudioTrackRow(
    label: String,
    language: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val neo = isNeobrutalismDesign()
    val shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp)
    val rowModifier = if (neo) {
        Modifier
            .background(if (selected) NeoBrutalismAccent else scheme.surfaceContainerLowest)
            .border(2.dp, scheme.outline)
    } else {
        Modifier
            .clip(shape)
            .background(
                if (selected) scheme.primary.copy(alpha = 0.12f)
                else scheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary.copy(alpha = 0.45f) else scheme.onSurface.copy(alpha = 0.06f),
                shape = shape
            )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(rowModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A square/ring that fills in when selected — same idea as a radio
        // button, but it matches the rest of the sheet.
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                .background(
                    if (selected) {
                        if (neo) Color.Black else scheme.primary
                    } else {
                        Color.Transparent
                    }
                )
                .border(
                    width = if (selected) 0.dp else 2.dp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    shape = if (neo) RoundedCornerShape(0.dp) else CircleShape
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                if (neo) Color.Black else scheme.primary
            } else {
                scheme.onSurface
            },
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        language?.let {
            StatusPill(text = it, tone = if (selected) PillTone.Accent else PillTone.Neutral)
        }
    }
}

/** Shared ±0.1 / ±0.5 shift control, reused for EN, FA and JSON subtitles. */
@Composable
fun SubtitleShiftControls(
    title: String,
    offsetLabel: String,
    modifier: Modifier = Modifier,
    currentOffset: Double = 0.0,
    exactLabel: String? = null,
    applyLabel: String? = null,
    onShift: (Double) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    var exactText by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = offsetLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Keep pill order LTR even when app is RTL (FA), otherwise -0.5/+0.5 flip.
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShiftPill(label = "-0.5", negative = true, modifier = Modifier.weight(1f)) { onShift(-0.5) }
                ShiftPill(label = "-0.1", negative = true, modifier = Modifier.weight(1f)) { onShift(-0.1) }
                ShiftPill(label = "+0.1", negative = false, modifier = Modifier.weight(1f)) { onShift(0.1) }
                ShiftPill(label = "+0.5", negative = false, modifier = Modifier.weight(1f)) { onShift(0.5) }
            }
        }
        if (exactLabel != null && applyLabel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                OutlinedTextField(
                    value = exactText,
                    onValueChange = { exactText = it },
                    label = { Text(exactLabel, style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Start,
                        textDirection = exactText.autoTextDirection()
                    )
                )
                GradientButton(
                    text = applyLabel,
                    onClick = {
                        val target = exactText.toDoubleOrNull()
                        if (target != null) onShift(target - currentOffset)
                    }
                )
                }
            }
        }
        if (footer != null) {
            Spacer(modifier = Modifier.height(10.dp))
            footer()
        }
    }
}

@Composable
private fun ShiftPill(
    label: String,
    negative: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    if (isNeobrutalismDesign()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(2.dp, MaterialTheme.colorScheme.outline)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 10.dp),
                textAlign = TextAlign.Center
            )
        }
        return
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .background(
                        if (isNeobrutalismDesign()) {
                            NeoBrutalismAccent
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        }
                    )
                    .then(
                        if (isNeobrutalismDesign()) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isNeobrutalismDesign()) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color?,
    selected: Boolean,
    emptyLabel: String?,
    onClick: () -> Unit
) {
    // The selected swatch grows slightly and gains a thick ring, so the
    // current choice is obvious even between two similar colors.
    val neo = isNeobrutalismDesign()
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "swatchScale"
    )
    val ringWidth = if (selected) 3.dp else 1.dp
    val swatchShape = if (neo) RoundedCornerShape(0.dp) else CircleShape
    val ringColor = if (selected) {
        if (neo) NeoBrutalismAccent else MaterialTheme.colorScheme.primary
    } else {
        if (neo) {
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(swatchShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(width = ringWidth, color = ringColor, shape = swatchShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = emptyLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

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
            selected -> NeoBrutalismAccent
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
