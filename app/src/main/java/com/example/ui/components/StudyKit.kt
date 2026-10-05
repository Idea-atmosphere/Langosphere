package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.logic.DifficultyTone
import com.example.logic.SentenceMetrics
import com.example.logic.StudyModeState
import com.example.logic.TextDirectionUtils
import com.example.logic.TtsSpeaker
import com.example.logic.autoTextDirection
import com.example.ui.theme.AppStrings
import com.example.logic.LessonDisplay

/**
 * The small study controls that every reading surface shares.
 *
 * The book reader and the film subtitle list are different screens with
 * different layouts, but a learner expects the same four things from both:
 * hear the line, hide the translation until they have tried it, keep their eye
 * on one line, and see how hard a line is. Those live here as one kit, so the
 * two surfaces cannot drift apart and neither has to grow its own copy of the
 * same gesture code.
 */

// ──────────────────────────────── speech ────────────────────────────────

/**
 * The small speaker next to a line or a word.
 *
 * A short tap speaks at normal speed; a long press speaks slowly, which is
 * what a learner needs for a word they cannot parse. The platform engine is
 * shared and lazily built ([TtsSpeaker]), so a list of a thousand rows holds
 * exactly one engine.
 */
@Composable
fun SpeakerButton(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
    size: Int = 18,
) {
    if (text.isBlank()) return
    val context = LocalContext.current
    val description = contentDescription ?: text

    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            // `this.` is required: the parameter above has the same name as the
            // semantics property and would otherwise be resolved instead.
            .semantics { this.contentDescription = description }
            .pointerInput(text) {
                detectTapGestures(
                    onTap = { TtsSpeaker.speak(context, text, slow = false) },
                    onLongPress = { TtsSpeaker.speak(context, text, slow = true) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.VolumeUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size.dp),
        )
    }
}

// ──────────────────────────── blur / peek mode ────────────────────────────

/**
 * A translation that hides itself while challenge mode is on.
 *
 * The web reading surface does this with `filter: blur(5px)`; the equivalent
 * radius and interaction are reproduced here:
 *
 *  - real blur on Android 12 and newer ([Modifier.blur]);
 *  - on older devices, where the platform cannot blur a layer, the text is
 *    faded instead of shown, so the answer is still out of sight;
 *  - pressing reveals it, and a tap toggles it open until tapped again - a
 *    hold works on a phone and a click works on a keyboard/AT device.
 *
 * The reveal state is per row and lives only as long as the row is on screen,
 * so turning the page re-hides everything, which is the point of the mode.
 */
@Composable
fun PeekTranslation(
    text: String,
    modifier: Modifier = Modifier,
    blurred: Boolean = StudyModeState.challengeMode,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
    hintLabel: String = "برای دیدن ترجمه نگه دار",
    /**
     * Reveal state, when the caller owns it (the quiz re-arms the blur for
     * every new question). Left null the widget keeps it itself.
     */
    revealed: Boolean? = null,
    onRevealChange: ((Boolean) -> Unit)? = null,
) {
    if (text.isBlank()) return
    // A Persian/Arabic translation must be an actual RTL paragraph, not just
    // an RTL-looking string in a left-aligned box. This remains true during
    // blur/reveal transitions and on every reader surface using this widget.
    val isRtl = TextDirectionUtils.isRtl(text)
    val resolvedTextAlign = if (isRtl) TextAlign.Right else textAlign
    val resolvedStyle = style.copy(textDirection = text.autoTextDirection())
    if (!blurred) {
        Text(
            text = text,
            style = resolvedStyle,
            color = color,
            textAlign = resolvedTextAlign,
            modifier = modifier,
        )
        return
    }

    var ownRevealed by remember(text) { mutableStateOf(false) }
    val isRevealed = revealed ?: ownRevealed
    fun setRevealed(value: Boolean) {
        if (revealed == null) ownRevealed = value
        onRevealChange?.invoke(value)
    }
    var pressed by remember(text) { mutableStateOf(false) }
    val hidden = !isRevealed && !pressed
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Do not snap from a sharp answer to an opaque answer. The same animated
    // progress drives the Android blur and the legacy fallback veil.
    val concealProgress by animateFloatAsState(
        targetValue = if (hidden) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "translationConceal",
    )

    Box(
        modifier = modifier
            .semantics { contentDescription = hintLabel }
            .pointerInput(text) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { setRevealed(!isRevealed) },
                )
            },
        contentAlignment = if (isRtl) Alignment.TopEnd else Alignment.TopStart,
    ) {
        Text(
            text = text,
            style = resolvedStyle,
            color = color,
            textAlign = resolvedTextAlign,
            // The Box owns the caller's full-width modifier while the Text
            // must explicitly fill that width too; otherwise TextAlign.Right
            // has no visible room to align a blurred Persian paragraph.
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canBlur) {
                        Modifier
                            .blur((StudyModeState.BLUR_DP * concealProgress).dp)
                            .graphicsLayer { alpha = 1f - (0.18f * concealProgress) }
                    } else {
                        Modifier
                    }
                ),
        )
        if (!canBlur && concealProgress > 0f) {
            // No platform blur available: the same information hiding, done
            // with alpha. A frosted band would look like a rendering bug.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f * concealProgress)),
            )
        }
    }
}

/**
 * The dimming half of focus mode: [StudyModeState.alphaFor] answers 1f unless
 * the learner has focused a different line, so applying this unconditionally
 * costs nothing and keeps call sites free of conditions.
 */
@Composable
fun Modifier.focusDim(key: String?): Modifier =
    this.graphicsLayer { alpha = StudyModeState.alphaFor(key) }

// ─────────────────────────── difficulty and timing ───────────────────────────

/** The colour of one difficulty tone. Neutral grey when the tone is unknown. */
@Composable
fun difficultyColor(tone: DifficultyTone): Color = when (tone) {
    DifficultyTone.EASY -> Color(0xFF3FA34D)
    DifficultyTone.MEDIUM -> Color(0xFFE0A800)
    DifficultyTone.HARD -> Color(0xFFD1453B)
    DifficultyTone.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
}

/**
 * A small colour dot saying how hard the line is, with the tone as its
 * accessibility label. Draws nothing when the JSON carried neither a
 * difficulty nor a level - a meaningless grey dot on every row would be noise.
 */
@Composable
fun DifficultyDot(
    difficulty: String?,
    level: String?,
    modifier: Modifier = Modifier,
    fa: Boolean = true,
    diameter: Int = 8,
) {
    val tone = SentenceMetrics.tone(difficulty, level)
    if (tone == DifficultyTone.UNKNOWN) return
    val label = SentenceMetrics.toneLabel(tone, fa)
    Box(
        modifier = modifier
            .size(diameter.dp)
            .clip(CircleShape)
            .background(difficultyColor(tone))
            .semantics { contentDescription = label },
    )
}

/**
 * "زمان تخمینی مطالعه: ۳ دقیقه" for a section, computed from its own text at
 * the 130 words-per-minute study pace. Renders nothing for an empty section.
 */
@Composable
fun ReadingTimeLabel(
    text: String,
    modifier: Modifier = Modifier,
    fa: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val minutes = SentenceMetrics.readingMinutes(text)
    if (minutes <= 0) return
    Text(
        text = SentenceMetrics.readingTimeLabel(minutes, fa),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** The same label for a list of lines. */
@Composable
fun ReadingTimeLabel(
    lines: List<String>,
    modifier: Modifier = Modifier,
    fa: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val minutes = SentenceMetrics.readingMinutes(lines)
    if (minutes <= 0) return
    Text(
        text = SentenceMetrics.readingTimeLabel(minutes, fa),
        style = style,
        color = color,
        modifier = modifier,
    )
}

// ──────────────────────────── sentence card action ────────────────────────────

/**
 * The "[+ لایتنر جمله]" affordance, shared by the reader and the player so the
 * two phrase it the same way.
 *
 * [alreadyAdded] flips it to a "saved" state rather than hiding it: a learner
 * who cannot see the button assumes the feature is missing, while a disabled
 * one that says the sentence is already in the box explains itself.
 */
@Composable
fun SentenceCardAction(
    label: String,
    savedLabel: String,
    alreadyAdded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (alreadyAdded) savedLabel else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (alreadyAdded) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            } else {
                accent
            },
            modifier = Modifier
                .clip(CircleShape)
                .pointerInput(alreadyAdded) {
                    detectTapGestures(onTap = { if (!alreadyAdded) onClick() })
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}


/**
 * The three shared switches as one compact, scrollable strip.
 *
 * The reader's comfort sheet has room for labelled rows; the player's subtitle
 * list does not. This is the same three settings in the space of a chip row,
 * so neither surface has to own a private copy of them.
 */
@Composable
fun StudyModeStrip(
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .nestedScroll(rememberConfinedSwipeConnection())
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = StudyModeState.challengeMode,
            onClick = { StudyModeState.toggleChallengeMode() },
            label = { Text(strings.studyChallengeMode, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
        )
        FilterChip(
            selected = StudyModeState.focusMode,
            onClick = {
                StudyModeState.toggleFocusMode()
                if (!StudyModeState.focusMode) StudyModeState.clearFocus()
            },
            label = { Text(strings.studyFocusMode, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
        )
        LessonDisplay.values().forEach { choice ->
            FilterChip(
                selected = StudyModeState.lessonDisplay == choice,
                onClick = { StudyModeState.setLessonDisplay(choice) },
                label = { Text(LessonDisplay.label(choice, !strings.isEn), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
