package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.logic.LessonDisplay
import com.example.model.LeitnerCard
import com.example.logic.ReaderComfortState
import com.example.logic.StudyModeState
import com.example.logic.autoTextDirection
import com.example.model.BookSentence
import com.example.model.JsonWord
import com.example.model.SentenceHighlight
import com.example.ui.theme.isAnimeDesign
import com.example.ui.components.anime.ToonChip
import com.example.ui.theme.AnimeColors

/**
 * Inline sentence display — eye-friendly like the PDF reader, not card-heavy.
 *
 * Each row shows the original sentence, its translation directly underneath,
 * and a «درس جمله» button that opens the lesson sheet.
 *
 * ## Why the lesson button is no longer conditional
 *
 * It used to render only when the entry already carried a lesson object, a
 * word list or a note. A translation-only import — the lightest and most
 * common prompt mode — therefore produced rows with no button at all, which is
 * exactly the "the sentence lesson does not work" symptom. The sheet always
 * has something to show (original, translation, location, level, TTS), so the
 * button is now always available and the sheet decides what to display.
 *
 * The sentence number is shown next to the location, because ids are the
 * contract between the copied chunk and the imported JSON: seeing `#42 - p. 12
 * s. 3` makes a mis-numbered answer obvious at a glance.
 */
@Composable
fun BookSentencePane(
    sentences: List<BookSentence>,
    expandedIds: Set<Int> = emptySet(), // backward compat
    onToggleLesson: (BookSentence) -> Unit = {},
    onAddWordToLeitner: (BookSentence, JsonWord) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    highlights: Map<Int, List<SentenceHighlight>> = emptyMap(),
    onHighlightSentence: ((BookSentence) -> Unit)? = null,
    onLessonClick: ((BookSentence) -> Unit)? = null,
    onWordClick: ((String) -> Unit)? = null,
    analyzeLabel: String = "درس جمله",
    leitnerLabel: String = "افزودن به جعبهٔ لایتنر",
    highlightLabel: String = "هایلایت",
    emptyLabel: String = "هنوز جمله‌ای وارد نشده است.",
    showSentenceNumbers: Boolean = true,
    studyLabels: SentenceStudyLabels = SentenceStudyLabels(fa = true),
    onAddSentenceToLeitner: ((BookSentence) -> Unit)? = null,
    /** Normalized text of the sentences already in the Leitner box. */
    savedSentenceTexts: Set<String> = emptySet(),
) {
    if (sentences.isEmpty()) {
        Column(modifier = modifier.padding(24.dp)) {
            Text(text = emptyLabel, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val lessonAction: (BookSentence) -> Unit = onLessonClick ?: onToggleLesson

    val paragraphSpacing = ReaderComfortState.paragraphSpacing.dp
    val bubbleMode = ReaderComfortState.bubbleMode

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = ReaderComfortState.horizontalMargin.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
    ) {
        item(key = "study-header") {
            // "زمان تخمینی مطالعه: N دقیقه" for exactly the sentences on
            // screen, computed from their own word counts at 130 wpm.
            ReadingTimeLabel(
                lines = sentences.map { it.english },
                fa = studyLabels.isFa,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(items = sentences, key = { it.id }) { sentence ->
            EyeFriendlySentenceRow(
                sentence = sentence,
                highlights = highlights[sentence.id].orEmpty(),
                onLessonClick = { lessonAction(sentence) },
                onWordClick = onWordClick,
                onHighlight = onHighlightSentence?.let { a -> { a(sentence) } },
                analyzeLabel = analyzeLabel,
                highlightLabel = highlightLabel,
                showSentenceNumber = showSentenceNumbers,
                studyLabels = studyLabels,
                onAddWordToLeitner = onAddWordToLeitner,
                onAddSentenceToLeitner = onAddSentenceToLeitner,
                sentenceSaved = LeitnerCard.normalizeFront(sentence.english) in savedSentenceTexts,
            )
            if (bubbleMode) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier.padding(top = paragraphSpacing)
                )
            }
        }
    }
}

/**
 * One eye-friendly sentence row: locator and difficulty, the original line
 * (with the JSON's terms tappable), the translation, and the row's study
 * controls.
 *
 * ## The shared study switches
 *
 * Three of the behaviours here come from [StudyModeState] rather than from a
 * parameter, because the film subtitle list offers the same three and they are
 * meant to be one setting:
 *
 *  - *challenge mode* hides the translation behind [PeekTranslation] until the
 *    learner presses it;
 *  - *focus mode* dims every row except the focused one, and the test of
 *    "which row is focused" is this row's own key;
 *  - *lesson display* decides what the «درس جمله» button does: unfold the
 *    lesson in place, or open it in the floating sheet.
 *
 * The key for the first two is `sentence.id`, which is stable for the life of
 * the document, so turning a page or re-importing JSON cannot leave the wrong
 * row dimmed.
 */
@Composable
private fun EyeFriendlySentenceRow(
    sentence: BookSentence,
    highlights: List<SentenceHighlight>,
    onLessonClick: () -> Unit,
    onWordClick: ((String) -> Unit)?,
    onHighlight: (() -> Unit)?,
    analyzeLabel: String,
    highlightLabel: String,
    showSentenceNumber: Boolean,
    studyLabels: SentenceStudyLabels,
    onAddWordToLeitner: (BookSentence, JsonWord) -> Unit,
    onAddSentenceToLeitner: ((BookSentence) -> Unit)? = null,
    sentenceSaved: Boolean = false,
) {
    // Focus mode: the row keys itself, and the whole row is the tap target.
    val focusKey = sentenceKey(sentence)
    var inlineLessonOpen by remember(sentence.id) { mutableStateOf(false) }

    // The inline accordion is only meaningful when there is something to show;
    // a translation-only entry keeps opening the full sheet.
    val inlineLesson = StudyModeState.lessonDisplay == LessonDisplay.INLINE && sentence.hasLesson
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val night = ReaderComfortState.isNight(appIsDark)
    val ink = Color(ReaderComfortState.textArgb(night))
    val haloShape = RoundedCornerShape(12.dp)
    val bubbleMode = ReaderComfortState.bubbleMode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusDim(focusKey)
            .pointerInput(StudyModeState.focusMode, focusKey) {
                if (StudyModeState.focusMode) {
                    detectTapGestures(onTap = { StudyModeState.focus(focusKey) })
                }
            }
            .then(
                if (bubbleMode) {
                    Modifier
                        .background(ink.copy(alpha = 0.045f), haloShape)
                        .border(width = 1.dp, color = ink.copy(alpha = 0.16f), shape = haloShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                } else {
                    // Traditional flow keeps the exact same interactive text,
                    // TTS and Leitner controls, only without the card chrome.
                    Modifier.padding(vertical = 4.dp)
                }
            ),
    ) {
        // Small locator label — "#42 - p. 12 s. 3" — with the difficulty dot
        // the JSON asked for: green easy, amber medium, red hard.
        val locator = when {
            showSentenceNumber -> sentence.displayLabel
            sentence.location.isNotBlank() -> sentence.location
            else -> ""
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DifficultyDot(
                difficulty = sentence.difficulty,
                level = sentence.level,
                fa = studyLabels.isFa,
                modifier = Modifier.padding(end = 6.dp),
            )
            if (locator.isNotBlank()) {
                Text(
                    text = locator,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            // Hear the line: tap speaks it, hold speaks it slowly.
            SpeakerButton(
                text = sentence.english,
                tint = MaterialTheme.colorScheme.tertiary,
                contentDescription = studyLabels.speak,
                size = 16,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Original — same style as the PDF reader's body text.
        //
        // When the imported JSON carries words for this sentence they are the
        // interactive layer: each one opens a floating card with its meaning
        // and IPA. Sentences without a word list keep the previous behaviour
        // exactly (dictionary tap, or plain text), so a translation-only
        // import looks and behaves as it always did.
        if (sentence.words.isNotEmpty()) {
            SentenceTermText(
                text = sentence.english,
                words = sentence.words,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f,
                    fontWeight = ReaderComfortState.resolveTextWeight(FontWeight.Normal),
                ),
                color = MaterialTheme.colorScheme.onSurface,
                labels = studyLabels,
                onWordClick = onWordClick,
                onTextClick = {},
                underlineWords = onWordClick != null,
                onAddToLeitner = { word -> onAddWordToLeitner(sentence, word) },
            )
        } else if (onWordClick != null) {
            ClickableWordText(
                text = sentence.english,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f
                ),
                highlightColor = MaterialTheme.colorScheme.onSurface,
                onWordClick = onWordClick,
                onTextClick = {}
            )
        } else {
            Text(
                text = highlightedText(sentence.english, highlights),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Simplified if exists — italic, muted, like a footnote
        sentence.simplified?.takeIf { it.isNotBlank() }?.let { simp ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = simp,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }

        // Translation — directly under the same line, and hidden while
        // challenge mode is on.
        sentence.translation?.takeIf { it.isNotBlank() }?.let { tr ->
            if (bubbleMode) {
                HorizontalDivider(
                    color = ink.copy(alpha = 0.18f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
            PeekTranslation(
                text = tr,
                hintLabel = studyLabels.peekHint,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f,
                    fontWeight = ReaderComfortState.resolveTextWeight(FontWeight.Normal),
                ),
                color = ink.copy(alpha = 0.90f),
                textAlign = if (tr.any { c -> c.code in 0x0600..0x06FF }) TextAlign.Right else TextAlign.Left,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Pronunciation — tiny, muted
        sentence.pronunciation?.takeIf { it.isNotBlank() }?.let { pron ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pron,
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.70f),
            )
        }

        // The sentence lesson, folded into a note box directly under the
        // sentence it belongs to. Expanding it grows this one row only: the
        // list is keyed by sentence id, so page turning and scrolling are
        // unaffected. The box is the lesson UI when the reader chose the
        // inline style; in popup style it stays out of the way and the button
        // below opens the sheet instead.
        if (inlineLesson) {
            SentenceLessonNote(
                lesson = sentence.lesson,
                labels = studyLabels,
                modifier = Modifier.padding(top = 6.dp),
                expanded = inlineLessonOpen,
                onExpandedChange = { inlineLessonOpen = it },
            )
        }

        // Action row — subtle, and the lesson button is always present.
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAnimeDesign()) {
                ToonChip(
                    text = analyzeLabel,
                    selected = sentence.hasStudyMaterial,
                    fill = AnimeColors.Lavender,
                    icon = Icons.Filled.School,
                    onClick = {
                        if (inlineLesson) inlineLessonOpen = !inlineLessonOpen else onLessonClick()
                    }
                )
            } else {
                TextButton(
                    onClick = {
                        if (inlineLesson) inlineLessonOpen = !inlineLessonOpen else onLessonClick()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 2.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = analyzeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (onHighlight != null) {
                TextButton(
                    onClick = onHighlight,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(text = highlightLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (sentence.words.isNotEmpty()) {
                Text(
                    text = "${sentence.words.size} واژه",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        if (onAddSentenceToLeitner != null) {
            Spacer(modifier = Modifier.height(2.dp))
            SentenceCardAction(
                label = studyLabels.sentenceToLeitner,
                savedLabel = studyLabels.sentenceSaved,
                alreadyAdded = sentenceSaved,
                onClick = { onAddSentenceToLeitner(sentence) },
            )
        }
    }
}

/**
 * The focus-mode key of one sentence.
 *
 * Stable for the life of the document, which is what focus mode needs: turning
 * a page or re-importing a translation cannot leave an unrelated row dimmed.
 * The "is it already in the Leitner box?" question is answered from the
 * sentence's text instead, because that is what the box itself keys on.
 */
internal fun sentenceKey(sentence: BookSentence): String = "sentence-${sentence.id}"

private fun highlightedText(text: String, highlights: List<SentenceHighlight>): AnnotatedString {
    if (highlights.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val ranges = highlights.mapNotNull { it.clampedTo(text.length) }.filter { it.isValid }.sortedBy { it.charStart }
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (h in ranges) {
            if (h.charStart < cursor) continue
            if (h.charStart > cursor) append(text.substring(cursor, h.charStart))
            pushStyle(SpanStyle(background = Color(h.colorArgb)))
            append(text.substring(h.charStart, h.charEnd))
            pop()
            cursor = h.charEnd
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
