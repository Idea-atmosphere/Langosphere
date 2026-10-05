package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.logic.HighlightSpanMerger
import com.example.model.SentenceHighlight

/**
 * Selectable sentence text with tappable highlights.
 *
 * ## The magnifier and the handles
 *
 * The distorted close-up came from drawing a lens by hand. There is no need
 * for one: wrapping text in a [SelectionContainer] gives the platform
 * selection behaviour — on API 28+ that is the framework `Magnifier`, driven
 * by the same text layout the glyphs came from, with handles pinned to the
 * baseline by the toolkit. It cannot drift or flicker because nothing is being
 * mirrored or re-rendered in a second surface. So the fix is deletion: remove
 * the custom lens drawing and use this composable, which adds no touch
 * handling of its own to selection.
 *
 * ## Tapping a highlight
 *
 * A tap is resolved to a character offset through [TextLayoutResult], then to a
 * span through [HighlightSpanMerger.findAt]. Because spans are kept
 * non-overlapping, exactly one span can match, so the popup never has to guess
 * which highlight was meant. The offset hit-test also means highlights react
 * correctly after a font-size change or rotation without any stored geometry.
 */
@Composable
fun BookSelectableText(
    sentenceId: Int,
    text: String,
    highlights: List<SentenceHighlight>,
    onRemoveHighlight: (SentenceHighlight) -> Unit,
    onAnalyzeSentence: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    removeLabel: String = "حذف هایلایت",
    analyzeLabel: String = "درس جمله / Analyze",
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var tapped by remember { mutableStateOf<SentenceHighlight?>(null) }
    var popupOffset by remember { mutableStateOf(IntOffset.Zero) }

    val annotated = remember(text, highlights) {
        annotateHighlights(text, highlights)
    }

    SelectionContainer(modifier = modifier) {
        Text(
            text = annotated,
            style = style,
            onTextLayout = { result -> layout = result },
            modifier = Modifier.pointerInput(highlights, text) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position)
                    val hit = HighlightSpanMerger.findAt(highlights, sentenceId, offset)
                    if (hit == null) {
                        tapped = null
                    } else {
                        tapped = hit
                        popupOffset = IntOffset(
                            x = position.x.toInt(),
                            y = position.y.toInt(),
                        )
                    }
                }
            },
        )
    }

    val selected = tapped
    if (selected != null) {
        HighlightActionPopup(
            offset = popupOffset,
            onDismiss = { tapped = null },
            onRemove = {
                tapped = null
                onRemoveHighlight(selected)
            },
            onAnalyze = {
                tapped = null
                onAnalyzeSentence(selected.sentenceId)
            },
            removeLabel = removeLabel,
            analyzeLabel = analyzeLabel,
        )
    }
}

/**
 * The contextual actions for a tapped highlight. A [Popup] rather than a
 * dialog: it is anchored to the touch point, dismisses on an outside tap, and
 * does not steal the reading position.
 */
@Composable
fun HighlightActionPopup(
    offset: IntOffset,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onAnalyze: () -> Unit,
    removeLabel: String = "حذف هایلایت",
    analyzeLabel: String = "درس جمله / Analyze",
) {
    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                TextButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = removeLabel,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = removeLabel, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onAnalyze) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = analyzeLabel,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = analyzeLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Paints highlight spans by character offset. Spans are clamped and assumed
 * non-overlapping (the invariant [HighlightSpanMerger] maintains), so a single
 * left-to-right pass is enough and no span can be painted twice.
 */
private fun annotateHighlights(
    text: String,
    highlights: List<SentenceHighlight>,
): AnnotatedString {
    if (text.isEmpty() || highlights.isEmpty()) return AnnotatedString(text)

    val spans = highlights
        .mapNotNull { span -> span.clampedTo(text.length) }
        .filter { span -> span.isValid }
        .sortedBy { span -> span.charStart }

    if (spans.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            if (span.charStart < cursor) continue
            if (span.charStart > cursor) append(text.substring(cursor, span.charStart))
            pushStyle(SpanStyle(background = Color(span.colorArgb)))
            append(text.substring(span.charStart, span.charEnd))
            pop()
            cursor = span.charEnd
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
