package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.logic.StudyModeState
import com.example.logic.autoTextDirection
import com.example.ui.theme.AppStrings
import com.example.ui.theme.LanguageWeightState
import com.example.ui.theme.appCorner

/**
 * Building blocks of the Online tab's redesigned hub and learning player.
 *
 * The visual language follows the app's paper cards, expressed with the
 * Material color roles so every palette and Day/Night mode adapts on its
 * own (nothing is hard-coded except the always-dark duration badge):
 *
 * | design token     | Compose role                          |
 * |------------------|---------------------------------------|
 * | `--surface`      | `colorScheme.surface`                 |
 * | `--bg-soft`      | `colorScheme.surfaceVariant`          |
 * | `--border`       | `colorScheme.outlineVariant`          |
 * | `--accent`       | `colorScheme.primary`                 |
 * | `--accent-soft`  | `primary` at 22 % alpha               |
 * | `--text`         | `colorScheme.onSurface`               |
 * | `--text-soft`    | `colorScheme.onSurfaceVariant`        |
 * | `--radius`       | [OnlineStudioTokens.radius] (18 dp)   |
 * | `--radius-s`     | [OnlineStudioTokens.radiusSmall] (12) |
 * | `--dur`          | [OnlineStudioTokens.DURATION_MS]      |
 *
 * Radii go through [appCorner], so the user's corner-roundness choice
 * (Settings ▸ Theme ▸ Layout & shapes) applies here too.
 */
object OnlineStudioTokens {
    const val DURATION_MS = 260
    const val ACCENT_SOFT_ALPHA = 0.22f
    val radius: Dp @Composable get() = appCorner(18.dp)
    val radiusSmall: Dp @Composable get() = appCorner(12.dp)

    /** `color-mix(in srgb, var(--surface) 80%, var(--bg-soft))` — a resting card. */
    @Composable
    fun restingCard(): Color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, 0.2f)
}

/**
 * One subtitle cue shared by the Online interactive transcript and the
 * local Video tab (`.cue-card`): time, «پخش این بخش», «+ لایتنر», the
 * English line with tappable words (always LTR), and the translation
 * (direction of its own script; blurred in challenge mode). A «نکته گرامری»
 * badge opens a JSON lesson when one exists.
 *
 * The cue being spoken lights up (`.cue-card.is-active`): accent border,
 * full surface and a 3 dp accent-soft halo, all animated.
 */
@Composable
fun CueCard(
    timeLabel: String?,
    englishText: String,
    translationText: String?,
    isActive: Boolean,
    enColor: Color,
    faColor: Color,
    enFont: FontFamily,
    faFont: FontFamily,
    strings: AppStrings,
    onSeek: () -> Unit,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Replay starts this cue and is intentionally separate from its end-pause. */
    onReplay: (() -> Unit)? = null,
    onToggleLoop: (() -> Unit)? = null,
    loopActive: Boolean = false,
    onTogglePauseAtEnd: (() -> Unit)? = null,
    pauseAtEndActive: Boolean = false,
    onAddToLeitner: (() -> Unit)? = null,
    leitnerSaved: Boolean = false,
    lessonLabel: String? = null,
    onLesson: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    isTranslating: Boolean = false,
    translateEnabled: Boolean = true,
    onStopTranslation: () -> Unit = {},
    studyLabels: SentenceStudyLabels? = null,
    focusKey: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val radius = OnlineStudioTokens.radiusSmall
    val shape = RoundedCornerShape(radius)
    val haloShape = RoundedCornerShape(radius + 3.dp)
    val progress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(OnlineStudioTokens.DURATION_MS),
        label = "cueActive"
    )
    val resting = OnlineStudioTokens.restingCard()
    // Cue cards always keep their full bubble surface. Do not touch the fixed
    // outer or inner padding below: a learner's transcript rhythm must not jump.
    val background = lerp(resting, scheme.surface, progress)
    val borderColor = lerp(scheme.outlineVariant, scheme.primary, progress)
    val halo = scheme.primary.copy(alpha = OnlineStudioTokens.ACCENT_SOFT_ALPHA * progress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 3.dp)
            // Shared focus mode: the other lines dim, a tap re-focuses.
            .focusDim(focusKey)
            .pointerInput(StudyModeState.focusMode, focusKey) {
                if (StudyModeState.focusMode && focusKey != null) {
                    detectTapGestures(onTap = { StudyModeState.focus(focusKey) })
                }
            }
            // box-shadow: 0 0 0 3px var(--accent-soft)
            .border(3.dp, halo, haloShape)
            .padding(3.dp)
            .clip(shape)
            .background(background)
            .border(if (isActive) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onSeek)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (timeLabel != null) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (studyLabels != null) {
                        SpeakerButton(
                            text = englishText,
                            tint = scheme.onSurfaceVariant,
                            contentDescription = studyLabels.speak,
                            size = 16,
                        )
                    }
                    if (onTranslate != null) {
                        if (isTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            CueIconButton(
                                icon = Icons.Default.Close,
                                contentDescription = strings.stopCd,
                                tint = scheme.error,
                                onClick = onStopTranslation
                            )
                        } else {
                            CueIconButton(
                                icon = Icons.Default.Translate,
                                contentDescription = strings.onlineCueTranslateCd,
                                tint = scheme.tertiary,
                                enabled = translateEnabled,
                                onClick = onTranslate
                            )
                        }
                    }
                    // The three per-cue transport actions stay circular and
                    // exactly 30dp, including their hit target. They are shared
                    // by local, online and JSON cue lists through this card.
                    if (onReplay != null) {
                        CueIconButton(
                            icon = Icons.Default.Replay,
                            contentDescription = strings.cueReplayCd,
                            tint = scheme.primary,
                            onClick = onReplay
                        )
                    }
                    if (onToggleLoop != null) {
                        CueIconButton(
                            icon = Icons.Default.Repeat,
                            contentDescription = strings.cueLoopCd,
                            tint = if (loopActive) scheme.primary else scheme.onSurfaceVariant,
                            active = loopActive,
                            onClick = onToggleLoop
                        )
                    }
                    if (onTogglePauseAtEnd != null) {
                        CueIconButton(
                            icon = Icons.Default.Pause,
                            contentDescription = strings.cuePauseAtEndCd,
                            tint = if (pauseAtEndActive) scheme.primary else scheme.onSurfaceVariant,
                            active = pauseAtEndActive,
                            onClick = onTogglePauseAtEnd
                        )
                    }
                    if (onAddToLeitner != null) {
                        CueTextButton(
                            label = if (leitnerSaved) strings.onlineCueLeitnerSaved else strings.onlineCueLeitner,
                            contentDescription = strings.onlineCueLeitnerCd,
                            icon = if (leitnerSaved) Icons.Default.Check else null,
                            enabled = !leitnerSaved,
                            onClick = onAddToLeitner
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val enWeight = LanguageWeightState.sourceWeight.weight
            val faWeight = LanguageWeightState.targetWeight.weight
            if (englishText.isNotBlank()) {
                // The English line reads left-to-right even in the Persian UI.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ClickableWordText(
                        text = englishText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = enColor,
                            fontFamily = enFont,
                            fontWeight = enWeight,
                            textAlign = TextAlign.Left,
                            lineHeight = 1.6.em
                        ),
                        highlightColor = enColor,
                        onWordClick = onWordClick,
                        onTextClick = onSeek
                    )
                }
            }
            translationText?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(Modifier.height(4.dp))
                PeekTranslation(
                    text = translation,
                    hintLabel = studyLabels?.peekHint ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = faColor,
                        fontFamily = faFont,
                        fontWeight = faWeight,
                        textDirection = translation.autoTextDirection(),
                        lineHeight = 1.8.em
                    ),
                    color = faColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (lessonLabel != null && onLesson != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onLesson,
                    shape = RoundedCornerShape(50),
                    color = scheme.tertiary.copy(alpha = 0.14f),
                    contentColor = scheme.tertiary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            lessonLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CueIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (active) 0.24f else if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun CueTextButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (enabled) scheme.secondary else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    }
}
