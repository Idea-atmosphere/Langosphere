package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.logic.ReaderComfortState
import com.example.logic.ReaderHighlightState
import com.example.logic.ReaderNoteState
import com.example.ui.components.anime.ToonCard
import com.example.ui.components.anime.ToonChip
import com.example.ui.components.anime.inkBorder
import com.example.ui.components.anime.toonSoft
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign
import com.example.ui.theme.neoAccent
import kotlin.math.roundToInt

private const val WORD_TAG = "reader_word"
private val WORD_REGEX = Regex("[\\p{L}\\p{M}][\\p{L}\\p{M}'\\u2019\\-]*")

/** One occurrence of a highlighted phrase inside this paragraph. */
private data class HighlightRun(
    val start: Int,
    val end: Int,
    val phrase: String,
    val colorArgb: Int,
)

/**
 * A paragraph of the reading surface.
 *
 * Selection is deliberately NOT hand-written any more. The paragraph is a
 * read-only [BasicTextField], which means the text is selected by the same
 * engine that selects text everywhere else on the phone:
 *  - the drag handles are the platform's own and stay pinned to the baseline,
 *  - the close-up while dragging is the framework `Magnifier` (API 28+), which
 *    magnifies the real pixels instead of re-drawing the text in a box,
 *  - RTL, hyphenation and line wrapping behave like the rest of the system.
 *
 * A read-only field still reports its [TextFieldValue], so the reader knows
 * exactly what is selected and can offer its own actions:
 *  - copy / share / note / highlight in [AnchoredToolbar],
 *  - **remove** the highlight when highlighted text is selected again,
 *  - a small popup with remove / analyze when a highlight is tapped.
 *
 * The system text menu is replaced by [ReaderTextToolbar] so that only the
 * reader's own toolbar appears.
 *
 * ## The toolbar floats above everything
 *
 * Both overlays — the selection toolbar and the tapped-highlight card — are
 * window-level [Popup]s, not absolutely-positioned boxes inside the paragraph.
 * That placement is what used to bury them: a later sibling (the translation
 * container under the line) paints over an earlier one, so a toolbar opened
 * below a selection disappeared behind the translation, and the page frame's
 * clip cut it at the edges. A popup is outside the page layout entirely, so no
 * translation box, note box or clipped parent can cover it, on any theme.
 *
 * A popup does not follow its anchor, so the paragraph's window origin is
 * captured when an overlay opens and any later move (a scroll, a rotation, a
 * font change) hides the overlay instead of leaving it floating over the wrong
 * line. The selection itself survives: tapping it asks the platform for the
 * menu again and the toolbar re-anchors.
 */
@Composable
fun BookText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    highlights: Map<String, Int> = emptyMap(),
    highlightInkColor: Color = Color(0xFF1B1714),
    learningWords: Set<String> = emptySet(),
    knownWords: Set<String> = emptySet(),
    accentColor: Color = Color.Unspecified,
    tapToHighlight: Boolean = false,
    selectable: Boolean = false,
    selectionEnabled: Boolean = true,
    location: String = "",
    onWordClick: (String) -> Unit = {},
    onSurfaceClick: (() -> Unit)? = null,
    onHighlight: ((String, Int) -> Unit)? = null,
    onNote: ((String) -> Unit)? = null,
    onRemoveHighlight: ((String) -> Unit)? = null,
    onAnalyzeSentence: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val isAnime = isAnimeDesign()
    val isNeo = isNeobrutalismDesign()

    // Where every highlighted phrase actually sits in this paragraph. Used for
    // painting, for hit-testing taps and for the "remove" action.
    val highlightRuns = remember(text, highlights) {
        val runs = ArrayList<HighlightRun>()
        if (text.isNotEmpty()) {
            val lowerText = text.lowercase()
            highlights.forEach { (phrase, colorInt) ->
                val clean = phrase.trim()
                if (clean.isEmpty()) return@forEach
                val lowerPhrase = clean.lowercase()
                var idx = lowerText.indexOf(lowerPhrase)
                while (idx >= 0) {
                    runs += HighlightRun(idx, idx + lowerPhrase.length, clean, colorInt)
                    idx = lowerText.indexOf(lowerPhrase, idx + 1)
                }
            }
        }
        runs.sortedBy { it.start }
    }

    val annotated = remember(
        text,
        highlightRuns,
        learningWords,
        knownWords,
        accentColor,
        highlightInkColor,
        style.color,
    ) {
        buildAnnotatedString {
            append(text)
            highlightRuns.forEach { run ->
                addStyle(
                    SpanStyle(background = Color(run.colorArgb), color = highlightInkColor),
                    run.start,
                    run.end,
                )
            }
            WORD_REGEX.findAll(text).forEach { match ->
                val word = match.value
                val lower = word.lowercase()
                val start = match.range.first
                val end = match.range.last + 1
                addStringAnnotation(tag = WORD_TAG, annotation = word, start = start, end = end)
                val insideHighlight = highlightRuns.any { start >= it.start && end <= it.end }
                if (!insideHighlight) {
                    when {
                        lower in learningWords -> addStyle(
                            SpanStyle(
                                color = if (accentColor == Color.Unspecified) style.color else accentColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                            start,
                            end,
                        )
                        lower in knownWords -> addStyle(
                            SpanStyle(color = style.color.copy(alpha = 0.55f)),
                            start,
                            end,
                        )
                    }
                }
            }
        }
    }

    val resolvedStyle = remember(style, text) {
        style.copy(textDirection = if (isRtlText(text)) TextDirection.Rtl else TextDirection.Ltr)
    }

    val layoutResult = remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    fun wordAt(index: Int): String? {
        if (text.isEmpty()) return null
        val idx = index.coerceIn(0, text.length - 1)
        WORD_REGEX.findAll(text).forEach { match ->
            if (idx in match.range) return match.value
        }
        return null
    }

    fun cursorPos(layout: TextLayoutResult, offset: Int, atBottom: Boolean): Offset {
        val clamped = offset.coerceIn(0, text.length)
        val line = try {
            layout.getLineForOffset(clamped.coerceAtMost((text.length - 1).coerceAtLeast(0)))
        } catch (e: Exception) {
            0
        }
        val x = try {
            layout.getHorizontalPosition(clamped, true)
        } catch (e: Exception) {
            0f
        }
        val y = try {
            if (atBottom) layout.getLineBottom(line) else layout.getLineTop(line)
        } catch (e: Exception) {
            0f
        }
        return Offset(x, y)
    }

    // ── The non-selectable surface: a plain paragraph with word lookup only. ──
    if (!selectionEnabled) {
        Text(
            text = annotated,
            style = resolvedStyle,
            modifier = modifier.pointerInput(text, highlightRuns) {
                detectTapGestures { offset ->
                    val layout = layoutResult.value
                    val pos = layout?.getOffsetForPosition(offset)
                    val word = pos?.let { wordAt(it) }
                    if (word != null) onWordClick(word) else onSurfaceClick?.invoke()
                }
            },
            onTextLayout = { layoutResult.value = it },
            overflow = TextOverflow.Clip,
            softWrap = true,
        )
        return
    }

    var fieldValue by remember(text) { mutableStateOf(TextFieldValue(text)) }
    var showToolbar by remember(text) { mutableStateOf(false) }
    var tappedRun by remember(text) { mutableStateOf<HighlightRun?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var pendingNoteText by remember { mutableStateOf("") }

    val selection = fieldValue.selection
    val selStart = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val selEnd = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    val selectedText = if (selEnd > selStart) text.substring(selStart, selEnd) else ""
    val overlappingRuns = remember(highlightRuns, selStart, selEnd) {
        if (selEnd <= selStart) emptyList()
        else highlightRuns.filter { it.start < selEnd && it.end > selStart }
    }

    fun clearSelection() {
        fieldValue = TextFieldValue(text = text, selection = androidx.compose.ui.text.TextRange(selStart))
        showToolbar = false
    }

    fun removePhrase(phrase: String) {
        val callback = onRemoveHighlight
        if (callback != null) {
            callback(phrase)
        } else {
            ReaderHighlightState.items
                .firstOrNull { it.text.equals(phrase.trim(), ignoreCase = true) }
                ?.let { ReaderHighlightState.remove(context, it) }
        }
    }

    // The platform asks for a text menu once a selection gesture settles; that
    // request is used as the cue to show the reader's own toolbar instead.
    val toolbarVisible = remember { mutableStateOf(false) }
    val textToolbar = remember {
        ReaderTextToolbar(
            onShow = { toolbarVisible.value = true },
            onHide = { toolbarVisible.value = false },
        )
    }
    LaunchedEffect(toolbarVisible.value) {
        if (toolbarVisible.value) {
            tappedRun = null
            showToolbar = true
        } else {
            showToolbar = false
        }
    }

    val selectionColors = remember(isAnime, isNeo, accentColor) {
        val handle = when {
            isAnime -> AnimeColors.Sky
            accentColor != Color.Unspecified -> accentColor
            else -> Color(0xFF4C7DF0)
        }
        TextSelectionColors(handleColor = handle, backgroundColor = handle.copy(alpha = 0.28f))
    }

    val transformation = remember(annotated) {
        VisualTransformation { TransformedText(annotated, OffsetMapping.Identity) }
    }

    // Where this paragraph sits in the window, and where it sat when the
    // current overlay opened. A plain holder, not a subscribed state: it is
    // only ever read from the layout callback and the effect below, so
    // scrolling never recomposes the paragraph through it.
    val paragraphOrigin = remember { mutableStateOf(Offset.Zero) }
    var overlayAnchorOrigin by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(tappedRun, showToolbar, selectedText) {
        overlayAnchorOrigin =
            if (tappedRun != null || (showToolbar && selectedText.isNotBlank())) {
                paragraphOrigin.value
            } else {
                Offset.Unspecified
            }
    }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val now = coordinates.positionInWindow()
            val anchor = overlayAnchorOrigin
            if (anchor != Offset.Unspecified && (now - anchor).getDistance() > 2f) {
                // The paragraph moved under its overlay (a scroll): hide the
                // overlay rather than let it float over the wrong line. The
                // selection is kept - tapping it re-opens the toolbar.
                showToolbar = false
                tappedRun = null
                overlayAnchorOrigin = Offset.Unspecified
            }
            paragraphOrigin.value = now
        },
    ) {
        val boxMaxWidthPx = with(density) { maxWidth.toPx() }
        val toolbarWidthPx = with(density) { 300.dp.toPx() }
        val toolbarHeightPx = with(density) { 120.dp.toPx() }
        val toolbarGapPx = with(density) { 12.dp.toPx() }
        val popupWidthPx = with(density) { 210.dp.toPx() }

        CompositionLocalProvider(
            LocalTextToolbar provides textToolbar,
            LocalTextSelectionColors provides selectionColors,
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { updated ->
                    val wasCollapsed = fieldValue.selection.collapsed
                    // readOnly guarantees the text cannot change; only the
                    // selection is taken from the platform.
                    fieldValue = TextFieldValue(text = text, selection = updated.selection)
                    if (updated.selection.collapsed) {
                        if (wasCollapsed) {
                            // A real tap: a highlight opens its popup, a word is
                            // looked up, anything else is a surface tap.
                            val cursor = updated.selection.start.coerceIn(0, text.length)
                            val run = highlightRuns.firstOrNull { cursor >= it.start && cursor < it.end }
                            if (run != null) {
                                tappedRun = run
                                showToolbar = false
                            } else {
                                tappedRun = null
                                val word = wordAt(cursor)
                                if (word != null) onWordClick(word) else onSurfaceClick?.invoke()
                            }
                        } else {
                            // The selection was dismissed, not tapped.
                            showToolbar = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                textStyle = resolvedStyle,
                visualTransformation = transformation,
                onTextLayout = { layoutResult.value = it },
                cursorBrush = SolidColor(Color.Transparent),
            )
        }

        // ── Popup for a tapped highlight: remove it, or analyze the sentence ──
        // Window-level, so the translation container below the paragraph (a
        // later sibling, which paints over in-layout overlays) can never cover
        // it. Non-focusable: taps outside keep reaching the text, exactly as
        // with the old in-layout card.
        val run = tappedRun
        val layout = layoutResult.value
        if (run != null && layout != null) {
            val anchor = cursorPos(layout, run.start, atBottom = true)
            val popupX = (anchor.x - popupWidthPx / 2f)
                .coerceIn(0f, (boxMaxWidthPx - popupWidthPx).coerceAtLeast(0f))
            Popup(
                offset = IntOffset(popupX.roundToInt(), (anchor.y + toolbarGapPx).roundToInt()),
                onDismissRequest = { tappedRun = null },
                properties = PopupProperties(focusable = false),
            ) {
                Box(modifier = Modifier.width(210.dp)) {
                    HighlightActionCard(
                        onRemove = {
                            removePhrase(run.phrase)
                            tappedRun = null
                        },
                        onAnalyze = if (onAnalyzeSentence == null) null else {
                            {
                                onAnalyzeSentence(sentenceAround(text, run.start))
                                tappedRun = null
                            }
                        },
                        onClose = { tappedRun = null },
                    )
                }
            }
        }

        // ── The reading toolbar for the current selection ──
        // Window-level for the same reason as the highlight card above: the
        // toolbar usually opens below the selection, which is exactly where
        // the translation container sits, and an in-layout box loses that
        // paint-order fight every time.
        if (showToolbar && selectedText.isNotBlank() && layout != null) {
            val startTop = cursorPos(layout, selStart, atBottom = false)
            val endBottom = cursorPos(layout, selEnd, atBottom = true)
            val toolbarY = if (startTop.y - toolbarHeightPx - toolbarGapPx > 0f) {
                startTop.y - toolbarHeightPx - toolbarGapPx
            } else {
                endBottom.y + toolbarGapPx
            }
            val startX = cursorPos(layout, selStart, atBottom = true).x
            val endX = cursorPos(layout, selEnd, atBottom = true).x
            val centerX = if (selEnd - selStart > 80) boxMaxWidthPx / 2f else (startX + endX) / 2f
            val toolbarX = (centerX - toolbarWidthPx / 2f)
                .coerceIn(0f, (boxMaxWidthPx - toolbarWidthPx).coerceAtLeast(0f))
            Popup(
                offset = IntOffset(toolbarX.roundToInt(), toolbarY.roundToInt()),
                onDismissRequest = { showToolbar = false },
                properties = PopupProperties(focusable = false),
            ) {
                Box(modifier = Modifier.width(300.dp)) {
                    AnchoredToolbar(
                        selectedText = selectedText,
                        highlightedPhrases = overlappingRuns.map { it.phrase }.distinct(),
                        onCopy = {
                            clipboard.setText(AnnotatedString(selectedText))
                            clearSelection()
                        },
                        onShare = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, selectedText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                            clearSelection()
                        },
                        onHighlightColor = { colorInt ->
                            if (onHighlight != null) {
                                onHighlight(selectedText, colorInt)
                            } else {
                                try {
                                    ReaderHighlightState.addHighlight(context, selectedText, colorInt, location)
                                } catch (e: Exception) {
                                    // A missing document key simply means nothing to store.
                                }
                            }
                            clearSelection()
                        },
                        onRemoveHighlights = {
                            overlappingRuns.map { it.phrase }.distinct().forEach { removePhrase(it) }
                            clearSelection()
                        },
                        onNote = {
                            pendingNoteText = selectedText
                            showNoteDialog = true
                            showToolbar = false
                        },
                        onClose = { clearSelection() },
                    )
                }
            }
        }

        if (showNoteDialog) {
            NoteEditDialog(
                selectedText = pendingNoteText,
                onDismiss = {
                    showNoteDialog = false
                    clearSelection()
                },
                onSave = { note ->
                    if (onNote != null) onNote(pendingNoteText)
                    try {
                        ReaderHighlightState.addNote(context, pendingNoteText, note, location)
                        ReaderNoteState.add(context, pendingNoteText, note, location)
                    } catch (e: Exception) {
                        // Notes are best-effort; a failed write must not break reading.
                    }
                    showNoteDialog = false
                    clearSelection()
                },
            )
        }
    }
}

/**
 * Replaces the system text menu. The platform still drives WHEN a menu should
 * appear (after a long press, after a handle is released), but the reader draws
 * its own toolbar, so copy/share/note/highlight live in one place.
 */
private class ReaderTextToolbar(
    private val onShow: () -> Unit,
    private val onHide: () -> Unit,
) : TextToolbar {

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        status = TextToolbarStatus.Shown
        onShow()
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        onHide()
    }
}

/** The sentence containing [index], used when analyzing a tapped highlight. */
private fun sentenceAround(text: String, index: Int): String {
    if (text.isEmpty()) return ""
    val terminators = ".!?…؟"
    val pivot = index.coerceIn(0, text.length - 1)
    var start = pivot
    while (start > 0 && text[start - 1] !in terminators) start--
    var end = pivot
    while (end < text.length && text[end] !in terminators) end++
    if (end < text.length) end++
    return text.substring(start, end).trim()
}

/** Remove / analyze actions for a highlight that was tapped. */
@Composable
private fun HighlightActionCard(
    onRemove: () -> Unit,
    onAnalyze: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val isNeo = isNeobrutalismDesign()
    val shape = if (isNeo) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRemove)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حذف هایلایت",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (onAnalyze != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onAnalyze)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "درس جمله / Analyze",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "بستن",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchoredToolbar(
    selectedText: String,
    highlightedPhrases: List<String>,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNote: () -> Unit,
    onHighlightColor: (Int) -> Unit,
    onRemoveHighlights: () -> Unit,
    onClose: () -> Unit,
) {
    val isAnime = isAnimeDesign()
    val isNeo = isNeobrutalismDesign()
    val shape = if (isNeo) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp)
    val container: @Composable (@Composable () -> Unit) -> Unit = { content ->
        when {
            isAnime -> ToonCard(
                fill = toonSoft(AnimeColors.SkySoft),
                contentPadding = PaddingValues(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { content() }
            isNeo -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(2.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(10.dp)
            ) { content() }
            else -> Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(10.dp)) { content() }
            }
        }
    }
    container {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (selectedText.length > 64) selectedText.take(64) + "…" else selectedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isAnime) {
                    ToonChip(
                        text = "کپی",
                        selected = false,
                        fill = AnimeColors.Mint,
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    )
                    ToonChip(
                        text = "اشتراک",
                        selected = false,
                        fill = AnimeColors.Lavender,
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    )
                    ToonChip(
                        text = "یادداشت",
                        selected = false,
                        fill = AnimeColors.Sunny,
                        onClick = onNote,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FilledTonalButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = if (isNeo) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("کپی", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                    FilledTonalButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = if (isNeo) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("اشتراک", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                    FilledTonalButton(
                        onClick = onNote,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = if (isNeo) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.NoteAlt, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("یادداشت", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "بستن",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selecting text that is already highlighted turns the palette row
            // into a remove action, which is how a highlight is taken back.
            if (highlightedPhrases.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(if (isNeo) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                        .clickable(onClick = onRemoveHighlights)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (highlightedPhrases.size > 1) {
                            "حذف ${highlightedPhrases.size} هایلایت"
                        } else {
                            "حذف هایلایت"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "هایلایت",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                ReaderComfortState.HIGHLIGHT_COLORS.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onHighlightColor(argb) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditDialog(selectedText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "یادداشت برای:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (selectedText.length > 100) selectedText.take(100) + "…" else selectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = {
                        Text("یادداشت خود را بنویسید…", style = MaterialTheme.typography.bodySmall)
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("لغو") }
                    Button(onClick = { onSave(note) }, enabled = note.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text("ذخیره")
                    }
                }
            }
        }
    }
}

fun isRtlText(text: String): Boolean {
    var rtl = 0
    var ltr = 0
    for (char in text) {
        when (char.code) {
            in 0x0590..0x08FF, in 0xFB1D..0xFDFF, in 0xFE70..0xFEFF -> rtl++
            in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F -> ltr++
        }
        if (rtl + ltr > 400) break
    }
    return rtl > ltr
}

fun readerWordsOf(text: String): List<String> = WORD_REGEX.findAll(text).map { it.value }.toList()
