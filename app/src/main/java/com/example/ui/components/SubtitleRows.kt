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
import com.example.logic.autoTextDirection
import com.example.ui.theme.AppStrings
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
    glass: Boolean,
    onSeek: () -> Unit,
    header: @Composable RowScope.() -> Unit,
    body: @Composable () -> Unit
) {
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
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
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
    glass: Boolean,
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
    onSentenceClick: () -> Unit,
    onTranslate: () -> Unit,
    onStopTranslation: () -> Unit
) {
    SubtitleRowShell(
        isActive = isActive,
        glass = glass,
        onSeek = onSeek,
        header = {
            StatusPill(
                text = timeLabel,
                tone = if (isActive) PillTone.Accent else PillTone.Neutral
            )
            if (isActive) {
                StatusPill(text = strings.playingLabel, tone = PillTone.Positive)
            }
        },
        body = {
            ClickableWordText(
                text = englishText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = enColor,
                    shadow = textShadow,
                    fontFamily = enFont,
                    textAlign = TextAlign.Left
                ),
                highlightColor = enColor,
                onWordClick = onWordClick,
                onTextClick = onSentenceClick
            )
            translationText?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = faColor,
                        shadow = textShadow,
                        fontFamily = faFont,
                        textDirection = translation.autoTextDirection()
                    ),
                    // Auto per-content alignment: Persian Right, English Left
                    // regardless of app LayoutDirection (which is Rtl when FA).
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
    glass: Boolean,
    enColor: Color,
    faColor: Color,
    textShadow: Shadow,
    enFont: FontFamily,
    faFont: FontFamily,
    strings: AppStrings,
    onSeek: () -> Unit,
    onWordClick: (String) -> Unit,
    onSentenceClick: () -> Unit
) {
    SubtitleRowShell(
        isActive = isActive,
        glass = glass,
        onSeek = onSeek,
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
                if (isActive) StatusPill(text = strings.playingLabel, tone = PillTone.Positive)
                difficulty?.takeIf { it.isNotBlank() }?.let { StatusPill(text = it, tone = PillTone.Warning) }
                level?.takeIf { it.isNotBlank() }?.let { StatusPill(text = it, tone = PillTone.Accent) }
            }
        },
        body = {
            if (englishText.isNotBlank()) {
                ClickableWordText(
                    text = englishText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = enColor,
                        shadow = textShadow,
                        fontFamily = enFont,
                        textAlign = TextAlign.Left
                    ),
                    highlightColor = enColor,
                    onWordClick = onWordClick,
                    onTextClick = onSentenceClick
                )
            }
            translationText?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = faColor,
                        shadow = textShadow,
                        fontFamily = faFont,
                        textDirection = translation.autoTextDirection()
                    ),
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
        }
    )
}
