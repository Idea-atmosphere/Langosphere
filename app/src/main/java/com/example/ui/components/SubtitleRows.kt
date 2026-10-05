package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.logic.StudyModeState
import com.example.logic.autoTextDirection
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.LanguageWeightState
import com.example.ui.components.anime.BubbleTail
import com.example.ui.components.anime.ToonBubble
import com.example.ui.components.anime.ToonChip
import com.example.ui.components.anime.toonOn
import com.example.ui.components.anime.toonPopSpring
import com.example.ui.components.anime.toonSoft
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.AppStrings
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign

/**
 * Subtitle list entries.
 *
 * Both the imported EN/FA lines and the JSON learning-package lines share
 * the same shell: a glass card that gains a gradient accent bar, a tinted
 * background and a raised timestamp pill while it is the line being
 * spoken, so the active line is obvious even at a glance.
 */
@Composable
private fun SubtitleRowShell(
    isActive: Boolean,
    onSeek: () -> Unit,
    header: @Composable RowScope.() -> Unit,
    body: @Composable () -> Unit,
    /** Stable key of this line; enables the shared focus mode. */
    focusKey: String? = null,
) {
    // Focus mode is shared with the book reader, so a film line dims and
    // re-focuses on tap exactly like a book sentence does.
    val studyModifier = Modifier
        .focusDim(focusKey)
        .pointerInput(StudyModeState.focusMode, focusKey) {
            if (StudyModeState.focusMode && focusKey != null) {
                detectTapGestures(onTap = { StudyModeState.focus(focusKey) })
            }
        }
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "rowActive"
    )
    val barColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        animationSpec = tween(durationMillis = 260),
        label = "rowBar"
    )

    if (isAnimeDesign()) {
        // Toon subtitles are manga speech bubbles: the line being spoken
        // pops in at full size and opacity on the toon spring, while the
        // lines around it hang back at 60% alpha and 0.96 scale so the
        // active one is unmistakable while scrolling.
        val pop by animateFloatAsState(
            targetValue = if (isActive) 1f else 0.96f,
            animationSpec = toonPopSpring(),
            label = "toon-subtitle-pop",
        )
        ToonBubble(
            modifier = studyModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                    alpha = if (isActive) 1f else 0.60f
                }
                .clickable(onClick = onSeek),
            tail = BubbleTail.Left,
            fill = if (isActive) toonSoft(AnimeColors.SunnySoft) else null,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToonChip(text = "EN", selected = true, fill = AnimeColors.Sky)
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = header,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            body()
        }
        return
    }

    GlassCard(
        modifier = studyModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        tint = if (isActive) MaterialTheme.colorScheme.primary else null,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(start = 10.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        onClick = onSeek
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent rail: grows into a full bar for the active line.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((26 + 34 * activeProgress).dp)
                    .clip(
                        if (isNeobrutalismDesign()) {
                            RoundedCornerShape(0.dp)
                        } else {
                            RoundedCornerShape(3.dp)
                        }
                    )
                    .background(barColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    content = header
                )
                Spacer(modifier = Modifier.height(8.dp))
                body()
            }
        }
    }
}

/** Small pill action used inside subtitle rows. */
@Composable
private fun RowAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    if (isAnimeDesign()) {
        // Row actions become toon chips: the tone color fills the chip and
        // the label stays ink, which is the skin's contrast contract.
        val fill = when (color) {
            scheme.primary -> AnimeColors.Sakura
            scheme.secondary -> AnimeColors.Sky
            else -> AnimeColors.Lavender
        }
        ToonChip(
            text = label,
            modifier = modifier,
            selected = enabled,
            fill = fill,
            icon = icon,
            onClick = if (enabled) onClick else null,
        )
        return
    }
    if (isNeobrutalismDesign()) {
        // Flat square action block: the tone color filled with its
        // on-color glyph/label (yellow/pink → ink; indigo → white), dimmed
        // and de-bordered when disabled.
        val onColor = when (color) {
            scheme.primary -> scheme.onPrimary
            scheme.secondary -> scheme.onSecondary
            else -> scheme.onTertiary
        }
        val dimColor = scheme.onSurfaceVariant.copy(alpha = 0.5f)
        Box(
            modifier = modifier
                .background(
                    if (enabled) color
                    else scheme.surfaceVariant
                )
                .border(
                    width = if (enabled) 2.dp else 0.dp,
                    color = scheme.outline
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (enabled) onColor else dimColor
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) onColor else dimColor,
                    maxLines = 1
                )
            }
        }
        return
    }
    Surface(
        color = color.copy(alpha = if (enabled) 0.14f else 0.06f),
        contentColor = color.copy(alpha = if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/** One imported EN(/FA) subtitle line. */
@Composable
fun SubtitleLineRow(
    timeLabel: String,
    englishText: String,
    translationText: String?,
    isActive: Boolean,
    enColor: Color,
    faColor: Color,
    textShadow: Shadow,
    enFont: FontFamily,
    faFont: FontFamily,
    strings: AppStrings,
    isTranslating: Boolean,
    translateEnabled: Boolean,
    onSeek: () -> Unit,
    onPlayWithAutoStop: () -> Unit,
    onWordClick: (String) -> Unit,
    onTranslate: () -> Unit,
    onStopTranslation: () -> Unit,
    /** Wording of the shared controls; null hides them (old call sites). */
    studyLabels: SentenceStudyLabels? = null,
) {
    SubtitleRowShell(
        isActive = isActive,
        onSeek = onSeek,
        header = {
            StatusPill(
                text = timeLabel,
                tone = if (isActive) PillTone.Accent else PillTone.Neutral
            )
            if (isActive) {
                StatusPill(text = strings.playingLabel, tone = PillTone.Positive)
            }
            // Hear the sentence: the same small speaker as the book reader.
            if (studyLabels != null) {
                SpeakerButton(
                    text = englishText,
                    tint = enColor,
                    contentDescription = studyLabels.speak,
                    size = 16,
                )
            }
        },
        body = {
            val enWeight = LanguageWeightState.sourceWeight.weight
            val faWeight = LanguageWeightState.targetWeight.weight
            ClickableWordText(
                text = englishText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = enColor,
                    shadow = textShadow,
                    fontFamily = enFont,
                    fontWeight = enWeight,
                    textAlign = TextAlign.Left
                ),
                highlightColor = enColor,
                onWordClick = onWordClick,
                onTextClick = onSeek
            )
            translationText?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(modifier = Modifier.height(8.dp))
                val faBubbleFill = toonSoft(AnimeColors.LavenderSoft)
                val faLine: @Composable () -> Unit = {
                    // Challenge mode hides this line too, exactly as in the
                    // book reader and the JSON list.
                    PeekTranslation(
                        text = translation,
                        hintLabel = studyLabels?.peekHint ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isAnimeDesign()) toonOn(faBubbleFill) else faColor,
                            shadow = if (isAnimeDesign()) Shadow.None else textShadow,
                            fontFamily = faFont,
                            fontWeight = faWeight,
                        ),
                        color = if (isAnimeDesign()) toonOn(faBubbleFill) else faColor,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isAnimeDesign()) {
                    // The Persian line gets its own lavender bubble with the
                    // tail on the opposite side, so the two languages read as
                    // two speakers in a manga panel.
                    ToonBubble(
                        modifier = Modifier.fillMaxWidth(),
                        tail = BubbleTail.Right,
                        fill = faBubbleFill,
                        contentPadding = PaddingValues(12.dp),
                    ) { faLine() }
                } else {
                    faLine()
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RowAction(
                    label = strings.playFromStartBtn,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PlayArrow,
                    onClick = onSeek
                )
                RowAction(
                    label = strings.playAutoStopBtn,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1.2f),
                    onClick = onPlayWithAutoStop
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (isTranslating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val neo = isNeobrutalismDesign()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (neo) {
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                } else {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                                }
                            )
                            .then(
                                if (neo) {
                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = if (neo) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.translatingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (neo) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = strings.stopCd,
                        onClick = onStopTranslation,
                        tint = MaterialTheme.colorScheme.error,
                        size = 34.dp
                    )
                }
            } else {
                RowAction(
                    label = strings.aiTranslateBtn,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = translateEnabled,
                    onClick = onTranslate
                )
            }
        }
    )
}

/** One line of a JSON subtitle-learning package. */
@Composable
fun JsonSubtitleRow(
    timeLabel: String?,
    idLabel: String?,
    level: String?,
    difficulty: String?,
    englishText: String,
    translationText: String?,
    isActive: Boolean,
    enColor: Color,
    faColor: Color,
    textShadow: Shadow,
    enFont: FontFamily,
    faFont: FontFamily,
    strings: AppStrings,
    onSeek: () -> Unit,
    onWordClick: (String) -> Unit,
    onSentenceClick: () -> Unit,
    /**
     * The shared study controls, identical to the book reader's: hear the
     * line, hide the translation, keep one line focused and turn the line
     * into a Leitner card. All optional, so a caller that does not offer them
     * (or an older screen) keeps the row exactly as it was.
     */
    studyLabels: SentenceStudyLabels? = null,
    /** Stable key of this line, used by challenge and focus mode. */
    focusKey: String? = null,
    sentenceSaved: Boolean = false,
    onAddSentenceToLeitner: (() -> Unit)? = null,
) {
    val labels = studyLabels
    SubtitleRowShell(
        isActive = isActive,
        onSeek = onSeek,
        focusKey = focusKey,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (timeLabel != null) {
                    StatusPill(
                        text = timeLabel,
                        tone = if (isActive) PillTone.Accent else PillTone.Neutral
                    )
                }
                if (idLabel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = idLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                // The same coloured dot as the book reader's sentence rows:
                // green easy, amber medium, red hard, nothing when unknown.
                DifficultyDot(difficulty = difficulty, level = level)
                if (isActive) StatusPill(text = strings.playingLabel, tone = PillTone.Positive)
                difficulty?.takeIf { it.isNotBlank() }?.let { StatusPill(text = it, tone = PillTone.Warning) }
                level?.takeIf { it.isNotBlank() }?.let { StatusPill(text = it, tone = PillTone.Accent) }
                // Hear the line, exactly like the book reader's speaker.
                if (labels != null) {
                    SpeakerButton(
                        text = englishText,
                        tint = enColor,
                        contentDescription = labels.speak,
                        size = 16,
                    )
                }
            }
        },
        body = {
            val enWeight = LanguageWeightState.sourceWeight.weight
            val faWeight = LanguageWeightState.targetWeight.weight
            if (englishText.isNotBlank()) {
                ClickableWordText(
                    text = englishText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = enColor,
                        shadow = textShadow,
                        fontFamily = enFont,
                        fontWeight = enWeight,
                        textAlign = TextAlign.Left
                    ),
                    highlightColor = enColor,
                    onWordClick = onWordClick,
                    onTextClick = onSeek
                )
            }
            translationText?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(modifier = Modifier.height(6.dp))
                // Challenge mode hides this line too: it is one setting for the
                // whole app, not a reader-only one.
                PeekTranslation(
                    text = translation,
                    hintLabel = labels?.peekHint ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = faColor,
                        shadow = textShadow,
                        fontFamily = faFont,
                        fontWeight = faWeight,
                    ),
                    color = faColor,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RowAction(
                    label = strings.playFromStartBtn,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PlayArrow,
                    onClick = onSeek
                )
                RowAction(
                    label = strings.lessonSheetTitle,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1.2f),
                    icon = Icons.Default.School,
                    onClick = onSentenceClick
                )
            }
            if (onAddSentenceToLeitner != null && labels != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SentenceCardAction(
                    label = labels.sentenceToLeitner,
                    savedLabel = labels.sentenceSaved,
                    alreadyAdded = sentenceSaved,
                    onClick = onAddSentenceToLeitner,
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    )
}
