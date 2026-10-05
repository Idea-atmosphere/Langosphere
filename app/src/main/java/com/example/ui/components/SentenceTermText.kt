/*
 * The interactive half of the book reader's imported JSON: the target words of
 * a sentence become tappable terms, and the sentence's own lesson sits in a
 * collapsible note box underneath it.
 *
 * ## Why the meaning is a popup and not a re-layout
 *
 * The reader is a page of the book with the translation injected under the
 * line it belongs to, and the line-to-entry matching is positional. Anything
 * that changes the height of a line while the reader is looking at it can move
 * the page under the finger, and anything that reorders items can put a
 * translation under the wrong line. Both the meaning card and the note box are
 * therefore additive: the card is a [Popup] (it is not part of the page
 * layout at all) and the note box is an [AnimatedVisibility] inside the row it
 * belongs to, so expanding it grows that one row and nothing else. Item keys
 * and the page-scoped sentence list are untouched, which is what keeps page
 * turning stable.
 *
 * ## 1-to-1 with the prompt contract
 *
 * Every `words` entry the prompt asks for carries `translation`,
 * `pronunciation` (IPA), `partOfSpeech`, `meaningInContext` and `examples`.
 * The term card shows exactly those, so what the model was told to produce is
 * what the reader displays.
 */
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.example.logic.autoTextDirection
import com.example.model.JsonLesson
import com.example.model.JsonWord
import java.util.regex.Pattern


/** Tag used for the term annotations inside the built [AnnotatedString]. */
private const val TERM_TAG = "lango_term"

/** Tag used for plain-word annotations, which is what a dictionary tap reads. */
private const val WORD_TAG = "lango_word"

/** Latin word shape used for tappable words, same as the reader's other surfaces. */
private val WORD_REGEX = "\\b[a-zA-Z][a-zA-Z0-9'-]*\\b".toRegex()

/** Bilingual labels for the term card and the sentence note box. */
class SentenceStudyLabels(private val fa: Boolean) {

    /** True for the Persian UI, so callers can match the surrounding text. */
    val isFa: Boolean get() = fa

    val noteTitle = if (fa) "درس جمله" else "Sentence lesson"
    val noteBadge = if (fa) "درس" else "NOTE"
    val explanation = if (fa) "توضیح" else "Explanation"
    val grammar = if (fa) "گرامر" else "Grammar"
    val structure = if (fa) "ساختار جمله" else "Structure"
    val expand = if (fa) "باز کردن درس جمله" else "Open the sentence lesson"
    val collapse = if (fa) "جمع کردن درس جمله" else "Close the sentence lesson"
    val inContext = if (fa) "در این جمله" else "In this sentence"
    val addToLeitner = if (fa) "افزودن به جعبهٔ لایتنر" else "Add to the Leitner box"

    // ── the controls the reader and the player share (see StudyKit) ──
    val speak = if (fa) "خواندن با صدای بلند (نگه داشتن: آهسته)" else "Read aloud (hold: slow)"
    val sentenceToLeitner = if (fa) "+ لایتنر جمله" else "+ Sentence to Leitner"
    val sentenceSaved = if (fa) "در لایتنر" else "In Leitner"
    val peekHint = if (fa) "برای دیدن ترجمه نگه دار" else "Hold to reveal the translation"
    val readingTime = if (fa) "زمان تخمینی مطالعه" else "Estimated reading time"
    val focusHint = if (fa) "برای تمرکز روی این جمله بزن" else "Tap to focus this line"
    val challengeOff = if (fa) "نمایش همهٔ ترجمه‌ها" else "Show all translations"
    val challengeOn = if (fa) "حالت چالش روشن است" else "Challenge mode is on"

    /** Shown on an entry the model built out of more than one line. */
    val mergedLines = if (fa) "چند خط در یک ورودی" else "Several lines in one entry"

    /**
     * Shown under a line whose own text is part of such an entry: the
     * translation exists, it is inside the entry named here.
     */
    fun translationInsideEntry(id: Int): String = if (fa) {
        "ترجمهٔ این خط داخل ورودی #$id است (آن ورودی چند خط را یکجا دارد)"
    } else {
        "The translation of this line is inside entry #$id (that entry holds several lines)"
    }
}

/**
 * Draws [text] with the sentence's target words highlighted; tapping one opens
 * a floating card with its meaning and IPA.
 *
 * Words that are NOT in [words] keep the old behaviour: with an
 * [onWordClick] handler they are tappable (dictionary lookup), otherwise the
 * whole line stays inert and [onTextClick] fires. Nothing about the layout
 * changes while the card is open.
 *
 * @param words the JSON vocabulary of this sentence; an empty list renders a
 *   plain line with no term affordance.
 * @param onWordClick fired for a tapped word that is not a target term, or
 *   `null` to leave non-term words inert.
 * @param onTextClick fired when the tap misses every word.
 * @param underlineWords underlines every word, the affordance the sentence
 *   list has always used for its dictionary taps. The merged page passes
 *   `false` so its own line keeps the plain look it had.
 * @param onAddToLeitner adds the word of the open card to the Leitner box;
 *   `null` hides that button.
 */
@Composable
fun SentenceTermText(
    text: String,
    words: List<JsonWord>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    termColor: Color = MaterialTheme.colorScheme.tertiary,
    labels: SentenceStudyLabels = SentenceStudyLabels(fa = true),
    onWordClick: ((String) -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
    underlineWords: Boolean = false,
    onAddToLeitner: ((JsonWord) -> Unit)? = null,
) {
    if (text.isBlank()) return

    val terms = remember(words) { usableTerms(words) }
    val annotated = remember(text, terms, onWordClick != null, termColor, color, underlineWords) {
        annotateSentence(
            text = text,
            hits = termHits(text, terms),
            termColor = termColor,
            wordColor = color,
            linkWords = onWordClick != null,
            underlineWords = underlineWords,
        )
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lineOrigin by remember { mutableStateOf(Offset.Zero) }
    var openWord by remember { mutableStateOf<JsonWord?>(null) }
    var anchor by remember { mutableStateOf(Offset.Zero) }

    val byWord = remember(terms) { terms.associateBy { it.word.trim().lowercase() } }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = annotated,
            style = style.copy(
                color = color,
                textDirection = text.autoTextDirection(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { lineOrigin = it.positionInWindow() }
                .pointerInput(annotated, terms) {
                    fun resolve(position: Offset): Boolean {
                        val result = layout ?: return false
                        val offset = result.getOffsetForPosition(position)
                        val term = annotated
                            .getStringAnnotations(TERM_TAG, offset, offset)
                            .firstOrNull()?.item
                        if (term != null) {
                            val word = byWord[term.trim().lowercase()]
                            if (word != null) {
                                anchor = lineOrigin + position
                                openWord = word
                                return true
                            }
                        }
                        val plain = annotated
                            .getStringAnnotations(WORD_TAG, offset, offset)
                            .firstOrNull()?.item
                        return if (plain != null) {
                            onWordClick?.invoke(plain)
                            true
                        } else {
                            false
                        }
                    }
                    detectTapGestures(
                        onTap = { position -> if (!resolve(position)) onTextClick?.invoke() },
                        onLongPress = { position -> resolve(position) },
                    )
                },
            onTextLayout = { layout = it },
        )

        openWord?.let { word ->
            Popup(
                popupPositionProvider = remember(anchor) { TermPopupPositionProvider(anchor) },
                onDismissRequest = { openWord = null },
            ) {
                TermCard(
                    word = word,
                    labels = labels,
                    accent = termColor,
                    onAddToLeitner = if (onAddToLeitner != null) { { onAddToLeitner(word) } } else null,
                    onDismiss = { openWord = null },
                )
            }
        }
    }
}

/**
 * The sentence lesson as a collapsible note: a badge, a one-line header and,
 * once opened, the explanation, the grammar note and the sentence skeleton.
 *
 * Renders nothing when the entry carries no lesson at all, so a
 * translation-only import looks exactly as it did before.
 */
@Composable
fun SentenceLessonNote(
    lesson: JsonLesson?,
    modifier: Modifier = Modifier,
    labels: SentenceStudyLabels = SentenceStudyLabels(fa = true),
    accent: Color = MaterialTheme.colorScheme.tertiary,
    initiallyExpanded: Boolean = false,
    /**
     * Open/closed state. Leave it `null` to let the note remember its own
     * state; pass a value when the row's "درس جمله" button has to open and
     * close it (the inline lesson setting).
     */
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val explanation = lesson?.explanation?.takeIf { it.isNotBlank() }
    val grammar = listOfNotNull(
        lesson?.grammar?.takeIf { it.isNotBlank() },
        lesson?.grammarTranslation?.takeIf { it.isNotBlank() },
    ).distinct().joinToString(" — ").takeIf { it.isNotBlank() }
    val structure = lesson?.structure?.takeIf { it.isNotBlank() }
    if (explanation == null && grammar == null && structure == null) return

    var internalExpanded by remember(lesson) { mutableStateOf(initiallyExpanded) }
    val isOpen = expanded ?: internalExpanded
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, accent.copy(alpha = 0.22f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = !isOpen
                    internalExpanded = next
                    onExpandedChange?.invoke(next)
                }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = labels.noteBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = labels.noteTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isOpen) labels.collapse else labels.expand,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = isOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                explanation?.let { NoteSection(labels.explanation, it, accent) }
                grammar?.let { NoteSection(labels.grammar, it, accent) }
                structure?.let { NoteSection(labels.structure, it, accent) }
            }
        }
    }
}

@Composable
private fun NoteSection(title: String, body: String, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = body.autoTextDirection()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** The floating meaning window of one term: meaning, IPA, use and examples. */
@Composable
private fun TermCard(
    word: JsonWord,
    labels: SentenceStudyLabels,
    accent: Color,
    onAddToLeitner: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 300.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word.word.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                word.pronunciation?.takeIf { it.isNotBlank() }?.let { ipa ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ipa,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
            }
            word.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = translation.autoTextDirection()),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            word.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
                Text(
                    text = pos,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            word.meaningInContext?.takeIf { it.isNotBlank() }?.let { context ->
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                Text(
                    text = "${labels.inContext}: $context",
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = context.autoTextDirection()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            word.examples.take(2).forEach { example ->
                Text(
                    text = "• $example",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        textDirection = example.autoTextDirection(),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SpeakerButton(
                    text = word.word,
                    tint = accent,
                    contentDescription = word.word,
                    size = 16,
                )
                if (onAddToLeitner != null) {
                    TextButton(
                        onClick = {
                            onAddToLeitner()
                            onDismiss()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 2.dp,
                        ),
                    ) {
                        Text(
                            text = labels.addToLeitner,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                    }
                }
            }
        }
    }
}

// ──────── string building ────────

/** The vocabulary of a sentence, blanks dropped and the longest terms first. */
private fun usableTerms(words: List<JsonWord>): List<JsonWord> =
    words.filter { it.word.trim().isNotEmpty() }
        .sortedByDescending { it.word.trim().length }

/**
 * Short inflectional endings a term may carry in the sentence itself.
 *
 * Deliberately conservative: `s`, `es`, `ed`, `d`, `ing`, `'s`. A longer list
 * starts rewriting unrelated words (`hard` -> `hardly`), which is worse than
 * missing a form.
 */
private const val INFLECTION = "(?:s|es|ed|d|ing|'s|\u2019s)?"

/**
 * Matches one term, tolerating the two differences that actually occur between
 * the JSON and the page:
 *
 *  - the JSON stores the dictionary form while the sentence carries the
 *    inflected one - the prompt's own worked example has `"word": "brush off"`
 *    for a sentence that reads `brushed off`, so an exact match would leave the
 *    term invisible on the very case it was written for;
 *  - extraction re-wraps lines, so a multi-word term can meet any run of
 *    whitespace between its words.
 *
 * The boundaries are Unicode-aware: a term may contain regex metacharacters
 * (it is quoted before it becomes a pattern) and it may never match inside a
 * longer word, so `rain` does not match `training`.
 */
private fun termPattern(raw: String): Regex {
    val tokens = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val body = tokens.joinToString("\\s+") { tokenPattern(it) }
    return Regex("(?<![\\p{L}\\p{N}])" + body + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
}

/**
 * One word of a term: the word itself, its last letter optionally doubled
 * (English doubles a final consonant before -ing / -ed: `run` -> `running`,
 * `stop` -> `stopped`), and then an optional [INFLECTION].
 */
private fun tokenPattern(token: String): String {
    val last = token.lastOrNull()
    val doubled = if (last != null && last.isLetter()) "(?:" + Pattern.quote(last.toString()) + ")?" else ""
    return Pattern.quote(token) + doubled + INFLECTION
}

/**
 * All non-overlapping occurrences of the sentence's terms.
 *
 * Longest-first ordering means a phrase such as `brush off` wins over the bare
 * `brush` inside it, and the overlap filter drops the shorter one instead of
 * producing two nested annotations.
 */
private fun termHits(text: String, terms: List<JsonWord>): List<TermHit> {
    if (text.isEmpty() || terms.isEmpty()) return emptyList()
    val found = mutableListOf<TermHit>()
    for (term in terms) {
        val raw = term.word.trim()
        if (raw.isEmpty()) continue
        for (match in termPattern(raw).findAll(text)) {
            found += TermHit(match.range.first, match.range.last + 1, term)
        }
    }
    val accepted = mutableListOf<TermHit>()
    for (hit in found.sortedWith(compareByDescending<TermHit> { it.end - it.start }.thenBy { it.start })) {
        if (accepted.none { hit.start < it.end && hit.start < hit.end }) accepted += hit
    }
    return accepted.sortedBy { it.start }
}

/**
 * Builds the drawn line: term ranges are annotated and coloured, and every
 * Latin word gets a word annotation so a dictionary tap still resolves.
 *
 * Character offsets of the built string are identical to [text] because only
 * substrings of it are appended - no normalisation happens here, which is what
 * keeps both annotation kinds aligned.
 */
private fun annotateSentence(
    text: String,
    hits: List<TermHit>,
    termColor: Color,
    wordColor: Color,
    linkWords: Boolean,
    underlineWords: Boolean,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (hit in hits) {
        if (hit.start > cursor) append(text.substring(cursor, hit.start))
        val start = length
        append(text.substring(hit.start, hit.end))
        val end = length
        addStyle(
            style = SpanStyle(color = termColor, textDecoration = TextDecoration.Underline),
            start = start,
            end = end,
        )
        addStringAnnotation(tag = TERM_TAG, annotation = hit.term.word.trim(), start = start, end = end)
        cursor = hit.end
    }
    if (cursor < text.length) append(text.substring(cursor))

    if (linkWords) {
        for (match in WORD_REGEX.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            addStringAnnotation(tag = WORD_TAG, annotation = match.value, start = start, end = end)
            // Term ranges already carry their own colour and rule; this pass
            // only decorates the words around them.
            if (hits.none { start >= it.start && end <= it.end }) {
                addStyle(
                    style = SpanStyle(
                        color = wordColor,
                        textDecoration = if (underlineWords) TextDecoration.Underline else TextDecoration.None,
                    ),
                    start = start,
                    end = end,
                )
            }
        }
    }
}

private data class TermHit(val start: Int, val end: Int, val term: JsonWord)

/**
 * Places the meaning card under the tapped word, flipped above it when the
 * bottom of the window is too close, and always fully on screen.
 */
private class TermPopupPositionProvider(private val anchor: Offset) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 24
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchor.x.toInt() - popupContentSize.width / 2).coerceIn(margin, maxX)
        val below = anchor.y.toInt() + margin
        val y = if (below + popupContentSize.height + margin <= windowSize.height) {
            below
        } else {
            (anchor.y.toInt() - popupContentSize.height - margin).coerceAtLeast(margin)
        }
        return IntOffset(x, y)
    }
}
