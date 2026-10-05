package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.EpubParser
import com.example.logic.FuzzyTextAligner
import com.example.logic.LessonDisplay
import com.example.logic.ReaderComfortState
import com.example.logic.ReaderHighlightState
import com.example.logic.SentenceMetrics
import com.example.logic.SentenceSegmenter
import com.example.logic.StudyModeState
import com.example.logic.autoTextDirection
import com.example.logic.isPersianText
import com.example.model.BookSentence
import com.example.model.JsonWord
import com.example.model.LeitnerCard
import com.example.ui.components.BookText
import com.example.ui.components.DifficultyDot
import com.example.ui.components.PeekTranslation
import com.example.ui.components.ReadingTimeLabel
import com.example.ui.components.SentenceCardAction
import com.example.ui.components.SentenceLessonNote
import com.example.ui.components.SentenceStudyLabels
import com.example.ui.components.SentenceTermText
import com.example.ui.components.SpeakerButton
import com.example.ui.components.anime.inkBorder
import com.example.ui.components.anime.inkShadow
import com.example.ui.components.fadingEdges
import com.example.ui.components.focusDim
import com.example.ui.components.neoHardShadow
import com.example.ui.components.sentenceKey
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.AppFontScope
import com.example.ui.theme.AppFontState
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign

/**
 * The merged reading surface: the book's own text stays on the page and the
 * imported translation is placed directly under the line it belongs to.
 *
 * This is the page-scoped rewrite of the older merged view. The bug it fixes:
 * turning to the next page sometimes printed sentences from *other* pages of
 * the imported JSON. Three things caused that, and all three are gone here:
 *
 *  1. The page filter used `location.contains("p. 1")`, which also matches
 *     `p. 10` ... `p. 19`. Page numbers are parsed into integers now and
 *     compared with `==`.
 *  2. A sentence from page N-1 or N+1 was allowed to win a line (a +/-1
 *     tolerance). A sentence belongs to exactly one page, so the window is
 *     gone.
 *  3. When the visible page had no imported sentence, the old code fell back
 *     to `bookSentences.take(30)` and to an "other sentences" list built from
 *     the WHOLE document. Both printed foreign text. Nothing outside the
 *     visible page is ever rendered here; unmatched entries of *this* page are
 *     listed at the bottom, and everything else is only counted.
 *
 * Matching order per line: text similarity inside the page, then - only when
 * the page has exactly as many imported sentences as rendered lines - a
 * positional rescue by sentence ordinal. The imported `location`
 * (`p. 12 s. 3` / `Ch. 4 s. 12`) written by BookLessonAligner is what makes
 * both steps reliable.
 */
@Composable
internal fun MergedReaderPaperV3(
    modifier: Modifier,
    bodyText: String,
    epubBlocks: List<EpubParser.Block>?,
    bookState: BookReaderState,
    pdfImageMode: Boolean,
    pdfPageIndex: Int,
    paper: Color,
    ink: Color,
    highlights: Map<String, Int>,
    learningWords: Set<String>,
    knownWords: Set<String>,
    labels: ReaderLabels,
    location: String,
    onWordTap: (String) -> Unit,
    onSurfaceTap: () -> Unit,
    onRemoveHighlight: (String) -> Unit,
    drawFrame: Boolean,
    bookSentences: List<BookSentence>,
    onLessonClick: (BookSentence) -> Unit,
    onWordClick: (String) -> Unit,
    onAddWordToLeitner: ((BookSentence, JsonWord) -> Unit)? = null,
    onAddSentenceToLeitner: ((BookSentence) -> Unit)? = null,
    /** Normalized text of the sentences already in the Leitner box. */
    savedSentenceTexts: Set<String> = emptySet(),
) {
    // Term cards and sentence note boxes are bilingual; the reader's own
    // language is what decides which side of that pair they use.
    val studyLabels = remember(labels) { SentenceStudyLabels(labels.isFa) }
    val neo = isNeobrutalismDesign()
    val anime = isAnimeDesign()
    val shape = when {
        neo -> RoundedCornerShape(0.dp)
        anime -> RoundedCornerShape(20.dp)
        else -> RoundedCornerShape(24.dp)
    }
    val frame = if (!drawFrame) {
        Modifier.background(paper)
    } else if (anime) {
        Modifier
            .inkShadow(offset = 4.dp, shape = shape)
            .clip(shape)
            .background(paper)
            .inkBorder(3.dp, shape)
    } else if (neo) {
        Modifier
            .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 5.dp)
            .background(paper)
            .border(2.dp, MaterialTheme.colorScheme.outline)
    } else {
        Modifier
            .clip(shape)
            .background(paper)
            .border(1.dp, ink.copy(alpha = 0.10f), shape)
    }

    val baseStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = mergedReaderFontFor(bodyText),
    )
    val textStyle = readerComfortTextStyle(
        base = baseStyle,
        appFamily = mergedReaderFontFor(bodyText),
        inkColor = ink,
    )
    val accent = if (anime) AnimeColors.Sky else MaterialTheme.colorScheme.primary
    val margin = ReaderComfortState.horizontalMargin.dp
    val sentenceGap = (ReaderComfortState.paragraphSpacing.dp * 1.2f).coerceAtLeast(12.dp)
    // This is a shared reader preference, so switching it immediately updates
    // both the merged PDF/EPUB view and the translation-only fallback list.
    val bubbleMode = ReaderComfortState.bubbleMode

    val isEpubDoc = bookState.kind == BookSourceKind.EPUB
    val currentPageNum = pdfPageIndex + 1
    val currentChapterNum = bookState.chapterIndex + 1

    // Everything below works on THIS page only.
    val pageEntries = remember(bookSentences, currentPageNum, currentChapterNum, isEpubDoc) {
        entriesForPage(bookSentences, currentPageNum, currentChapterNum, isEpubDoc)
    }
    val offPageCount = bookSentences.size - pageEntries.size

    BoxWithConstraints(modifier = modifier.then(frame)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }

        if (pdfImageMode) {
            LaunchedEffect(pdfPageIndex, widthPx) { bookState.requestPage(pdfPageIndex, widthPx) }
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val bitmap = bookState.pageBitmap
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .pointerInput(pdfPageIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale <= 1.02f) Offset.Zero else offset + pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = labels.pageImage,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = labels.renderingPage,
                                style = MaterialTheme.typography.bodySmall,
                                color = ink.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = ink.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = margin, vertical = 12.dp),
                )
                Column(
                    modifier = Modifier.padding(horizontal = margin, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(sentenceGap),
                ) {
                    Text(
                        text = pageHeader(pageEntries.size, offPageCount, isEpubDoc, currentPageNum, currentChapterNum),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.7f),
                    )
                    pageReadingTime(pageEntries, studyLabels.isFa, ink)
                    // Only this page's sentences. No take(30) fallback: an empty
                    // page shows a note, never another page's text.
                    if (pageEntries.isEmpty()) {
                        Text(
                            text = "برای این صفحه ترجمه‌ای وارد نشده است. محدودهٔ همین صفحه را کپی کنید و جواب هوش مصنوعی را وارد کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ink.copy(alpha = 0.6f),
                        )
                    }
                    pageEntries.forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (bubbleMode) 4.dp else 0.dp)
                                .sentenceBubble(ink, bubbleMode),
                        ) {
                            MergedSentenceBlock(
                                sentence = entry.sentence,
                                ink = ink,
                                showOriginal = true,
                                onLessonClick = onLessonClick,
                                bubbleMode = bubbleMode,
                                studyLabels = studyLabels,
                                onAddWordToLeitner = onAddWordToLeitner,
                                onAddSentenceToLeitner = onAddSentenceToLeitner,
                                savedSentenceTexts = savedSentenceTexts,
                            )
                        }
                    }
                }
            }
            mergedComfortOverlays()
            return@BoxWithConstraints
        }

        // Text mode: the book's own lines, each with its translation underneath.
        val items = remember(bodyText, epubBlocks, pageEntries) {
            when {
                epubBlocks != null -> epubBlocks.flatMap { block ->
                    when (block) {
                        is EpubParser.Block.Text -> {
                            val sentences = SentenceSegmenter.sentences(block.text)
                            if (sentences.isEmpty()) {
                                listOf(MergedItem.Para(block.text))
                            } else {
                                sentences.map { MergedItem.Line(it) }
                            }
                        }
                        is EpubParser.Block.Image -> listOf(MergedItem.Picture(block.entry))
                    }
                }
                bodyText.isNotBlank() -> {
                    val sentences = SentenceSegmenter.sentences(bodyText)
                    if (sentences.isEmpty()) {
                        listOf(MergedItem.Para(bodyText))
                    } else {
                        sentences.map { MergedItem.Line(it) }
                    }
                }
                // Scanned page with no extractable text: the imported sentences of
                // THIS page carry the reading surface.
                pageEntries.isNotEmpty() -> pageEntries.map { MergedItem.Line(it.sentence.english) }
                else -> listOf(
                    MergedItem.Para(
                        "این صفحه متن قابل استخراج ندارد (احتمالاً اسکن است). از دکمهٔ تصویر صفحه استفاده کنید یا همین صفحه را برای ترجمه کپی کنید."
                    )
                )
            }
        }

        val placement = remember(items, pageEntries) {
            val lines = items.map { if (it is MergedItem.Line) it.text else "" }
            matchLinesToEntries(lines, pageEntries)
        }
        val matches = placement.matches
        val matchedIds = remember(matches) { matches.filterNotNull().map { it.id }.toSet() }
        val unmatchedOnThisPage = remember(pageEntries, matchedIds) {
            pageEntries.map { it.sentence }.filter { it.id !in matchedIds }
        }

        // A PDF page with no extractable text and no imported sentences is
        // blank in the original file: a centered notice, never raw content.
        // (EPUB chapters always carry text, so they keep the paragraph path.)
        if (bodyText.isBlank() && pageEntries.isEmpty() && !isEpubDoc) {
            BlankPdfPageNotice(ink = ink)
            mergedComfortOverlays()
            return@BoxWithConstraints
        }

        val context = LocalContext.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadingEdges(),
            contentPadding = PaddingValues(horizontal = margin, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(sentenceGap),
        ) {
            item {
                Text(
                    text = pageHeader(pageEntries.size, offPageCount, isEpubDoc, currentPageNum, currentChapterNum),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.55f),
                )
                pageReadingTime(pageEntries, studyLabels.isFa, ink)
            }

            itemsIndexed(items = items, key = { index, item ->
                when (item) {
                    is MergedItem.Line -> "l_${index}_${item.text.hashCode()}"
                    is MergedItem.Para -> "p_${index}_${item.text.hashCode()}"
                    is MergedItem.Picture -> "i_${index}_${item.entry}"
                }
            }) { index, item ->
                when (item) {
                    is MergedItem.Line -> {
                        val matched = matches.getOrNull(index)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sentenceBubble(ink, bubbleMode && matched != null),
                        ) {
                        BookText(
                            text = item.text,
                            style = textStyle,
                            modifier = Modifier.fillMaxWidth(),
                            highlights = highlights,
                            learningWords = learningWords,
                            knownWords = knownWords,
                            accentColor = accent,
                            location = location,
                            onWordClick = onWordTap,
                            onSurfaceClick = onSurfaceTap,
                            onHighlight = { phrase, color ->
                                ReaderHighlightState.addHighlight(context, phrase, color, location)
                            },
                            onNote = { },
                            onRemoveHighlight = onRemoveHighlight,
                        )
                        if (matched == null) {
                            placement.linesInsideMerged[index]?.let { holderId ->
                                Text(
                                    text = studyLabels.translationInsideEntry(holderId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, top = 2.dp),
                                )
                            }
                        }
                        matched?.let { matchedSentence ->
                            val mergedEntry = matchedSentence.id in placement.mergedIds
                            if (bubbleMode) {
                                HorizontalDivider(
                                    color = ink.copy(alpha = 0.18f),
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(if (bubbleMode) 0.dp else 6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = if (bubbleMode) 0.dp else 6.dp,
                                        top = if (bubbleMode) 0.dp else 2.dp,
                                        bottom = if (bubbleMode) 0.dp else 4.dp,
                                    ),
                            ) {
                                MergedSentenceBlock(
                                    sentence = matchedSentence,
                                    ink = ink,
                                    showOriginal = false,
                                    onLessonClick = onLessonClick,
                                    mergedEntry = mergedEntry,
                                    bubbleMode = false,
                                    studyLabels = studyLabels,
                                    onAddWordToLeitner = onAddWordToLeitner,
                                    onAddSentenceToLeitner = onAddSentenceToLeitner,
                                    savedSentenceTexts = savedSentenceTexts,
                                )
                            }
                        }
                    }
                    }
                    is MergedItem.Para -> BookText(
                        text = item.text,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth(),
                        highlights = highlights,
                        learningWords = learningWords,
                        knownWords = knownWords,
                        accentColor = accent,
                        location = location,
                        onWordClick = onWordTap,
                        onSurfaceClick = onSurfaceTap,
                        onHighlight = { phrase, color ->
                            ReaderHighlightState.addHighlight(context, phrase, color, location)
                        },
                        onNote = { },
                        onRemoveHighlight = onRemoveHighlight,
                    )
                    is MergedItem.Picture -> {
                        val bitmap = bookState.epubImage(item.entry)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = labels.bookImage,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            // Only sentences of THIS page that failed to land on a line. Entries
            // of other pages are never rendered here - that was the next-page bug.
            if (unmatchedOnThisPage.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))
                        val unmatchedIds = unmatchedOnThisPage.map { it.id }
                        val idTail = if (unmatchedIds.size > 8) "…" else ""
                        val idText = unmatchedIds.take(8).joinToString("، ") + idTail
                        Text(
                            text = "جملات همین صفحه که زیر خط خودشان جا نگرفتند " +
                                "(${unmatchedOnThisPage.size}: $idText)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        unmatchedOnThisPage.forEach { sentence ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = if (bubbleMode) 4.dp else 0.dp)
                                    .sentenceBubble(ink, bubbleMode),
                            ) {
                                MergedSentenceBlock(
                                    sentence = sentence,
                                    ink = ink,
                                    showOriginal = true,
                                    onLessonClick = onLessonClick,
                                    bubbleMode = bubbleMode,
                                    studyLabels = studyLabels,
                                    onAddWordToLeitner = onAddWordToLeitner,
                                    onAddSentenceToLeitner = onAddSentenceToLeitner,
                                    savedSentenceTexts = savedSentenceTexts,
                                )
                            }
                        }
                    }
                }
            }
        }

        mergedComfortOverlays()
    }
}

/**
 * Centered notice for a PDF page that is blank in the original file: no
 * extractable text and no imported sentences. The Compose equivalent of the
 * `empty-page-placeholder` block - a soft blank-page glyph, one centered line - shared by
 * the merged view (with JSON) and the plain surface (without JSON). Pagination
 * is untouched: the navigator above keeps working through blank pages.
 */
@Composable
internal fun BlankPdfPageNotice(ink: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 64.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "▭",
            fontSize = 32.sp,
            color = ink.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "این صفحه در PDF خالی است.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = ink.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A single sentence card. Keeping the visual treatment in one modifier makes
 * the off state genuinely flow-like: no border, background or extra horizontal
 * padding is left around the original/translation pair.
 */
private fun Modifier.sentenceBubble(ink: Color, enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        val shape = RoundedCornerShape(16.dp)
        this
            .background(ink.copy(alpha = 0.045f), shape)
            .border(1.dp, ink.copy(alpha = 0.16f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    }

/**
 * One imported sentence: number, locator, translation, pronunciation, lesson.
 *
 * The original line goes through [SentenceTermText], so the words the JSON
 * carries are tappable terms with a floating meaning card, and the lesson sits
 * in a collapsible note box under the translation. Both are self-contained:
 * neither one is part of the page's line matching, so opening them cannot move
 * a translation to another line or change which page is on screen.
 */
@Composable
private fun MergedSentenceBlock(
    sentence: BookSentence,
    ink: Color,
    showOriginal: Boolean,
    onLessonClick: (BookSentence) -> Unit,
    /** True when the entry had to be placed by containment, i.e. it merges lines. */
    mergedEntry: Boolean = false,
    /** Adds the visual separator when this block shares a bubble with the source. */
    bubbleMode: Boolean = false,
    studyLabels: SentenceStudyLabels = SentenceStudyLabels(fa = true),
    onAddWordToLeitner: ((BookSentence, JsonWord) -> Unit)? = null,
    onAddSentenceToLeitner: ((BookSentence) -> Unit)? = null,
    savedSentenceTexts: Set<String> = emptySet(),
) {
    val focusKey = sentenceKey(sentence)
    var inlineLessonOpen by remember(sentence.id) { mutableStateOf(false) }
    val inlineLesson = StudyModeState.lessonDisplay == LessonDisplay.INLINE && sentence.hasLesson

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusDim(focusKey)
            .pointerInput(StudyModeState.focusMode, focusKey) {
                if (StudyModeState.focusMode) {
                    detectTapGestures(onTap = { StudyModeState.focus(focusKey) })
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DifficultyDot(
                difficulty = sentence.difficulty,
                level = sentence.level,
                fa = studyLabels.isFa,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = listOfNotNull(
                    "#${sentence.id}",
                    sentence.location.takeIf { it.isNotBlank() },
                    // The model was told one line = one entry; when it answered
                    // with several lines in one, saying so is the difference
                    // between "the app is broken" and "this entry covers three
                    // lines". The prompt now spells that case out.
                    studyLabels.mergedLines.takeIf {
                        mergedEntry || SentenceMetrics.isMergedEntry(sentence.english)
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f),
            )
            SpeakerButton(
                text = sentence.english,
                tint = ink.copy(alpha = 0.7f),
                contentDescription = studyLabels.speak,
                size = 16,
            )
        }
        if (showOriginal && sentence.english.isNotBlank()) {
            SentenceTermText(
                text = sentence.english,
                words = sentence.words,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                labels = studyLabels,
                modifier = Modifier.padding(top = 2.dp),
                onAddToLeitner = onAddWordToLeitner?.let { add -> { word: JsonWord -> add(sentence, word) } },
            )
        }
        sentence.translation?.takeIf { it.isNotBlank() }?.let { translation ->
            // In a unified bubble the dashed-like separation is represented by
            // a theme-aware divider; outside bubble mode the translation simply
            // follows the original in the normal reading flow.
            if (bubbleMode && showOriginal) {
                HorizontalDivider(
                    color = ink.copy(alpha = 0.18f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            PeekTranslation(
                text = translation,
                hintLabel = studyLabels.peekHint,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
                ),
                color = ink.copy(alpha = 0.90f),
                textAlign = if (translation.isPersianText()) TextAlign.Right else TextAlign.Left,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        sentence.pronunciation?.takeIf { it.isNotBlank() }?.let { pronunciation ->
            Text(
                text = pronunciation,
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.70f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // The sentence's own lesson, right under the sentence it belongs to.
        // It only shows while the reader wants lessons inline; in popup style
        // the button below opens the sheet instead.
        if (inlineLesson) {
            SentenceLessonNote(
                lesson = sentence.lesson,
                labels = studyLabels,
                modifier = Modifier.padding(top = 6.dp),
                expanded = inlineLessonOpen,
                onExpandedChange = { inlineLessonOpen = it },
            )
        }
        if (onAddSentenceToLeitner != null) {
            SentenceCardAction(
                label = studyLabels.sentenceToLeitner,
                savedLabel = studyLabels.sentenceSaved,
                alreadyAdded = LeitnerCard.normalizeFront(sentence.english) in savedSentenceTexts,
                onClick = { onAddSentenceToLeitner(sentence) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // In inline style this button unfolds the lesson in place; otherwise it
        // opens the sheet, which is what it has always done.
        TextButton(
            onClick = {
                if (inlineLesson) inlineLessonOpen = !inlineLessonOpen else onLessonClick(sentence)
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "درس جمله",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.mergedComfortOverlays() {
    if (ReaderComfortState.warmth > 0.001f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFFF9A3C).copy(alpha = ReaderComfortState.warmth))
        )
    }
    if (ReaderComfortState.dimming > 0.001f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = ReaderComfortState.dimming))
        )
    }
}

/** "زمان تخمینی مطالعه: N دقیقه" for the sentences of one page. */
@Composable
private fun pageReadingTime(entries: List<MergedEntry>, fa: Boolean, ink: Color) {
    if (entries.isEmpty()) return
    ReadingTimeLabel(
        lines = entries.map { it.sentence.english },
        fa = fa,
        color = ink.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 2.dp),
    )
}

private fun pageHeader(
    onPage: Int,
    elsewhere: Int,
    isEpub: Boolean,
    page: Int,
    chapter: Int,
): String {
    val where = if (isEpub) "فصل $chapter" else "صفحهٔ $page"
    val rest = if (elsewhere > 0) " • $elsewhere جمله در صفحات دیگر" else ""
    return "$where • $onPage جملهٔ ترجمه‌شده$rest"
}

/** A paragraph, one sentence line, or a picture from the book. */
private sealed class MergedItem {
    data class Para(val text: String) : MergedItem()
    data class Line(val text: String) : MergedItem()
    data class Picture(val entry: String) : MergedItem()
}

internal data class MergedEntry(
    val sentence: BookSentence,
    val norm: String,
    val page: Int?,
    val chapter: Int?,
    val ordinal: Int?,
)

private data class MergedAnchor(val page: Int?, val chapter: Int?, val ordinal: Int?)

private val PAGE_RE = Regex("(?:p|page)\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val CHAPTER_RE = Regex("(?:ch|chap|chapter)\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val ORDINAL_RE = Regex("s\\.?\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
private const val SIMILARITY_THRESHOLD = 0.60
private const val LENGTH_RATIO_LIMIT = 2.5

/** Shortest line containment may claim, so a stray phrase cannot match. */
private const val CONTAINMENT_MIN_CHARS = 12

/** How much longer than the line the entry must be to count as a merge. */
private const val CONTAINMENT_RATIO = 1.8

/** Share of a line's words that must appear inside an entry, in order. */
private const val CONTAINMENT_COVERAGE = 0.85

/**
 * The entries that belong to the page (or chapter) being read.
 *
 * Scoping is by the label inside `location`, which the import restored from the
 * copied payload, so a chunk is free to skip pages: a book whose front matter
 * is copied from pages 1, 3 and 5 renders page 1 with its own entries, page 3
 * with its own, and page 5 with its own - nothing is required to be contiguous
 * and no page borrows another page's lines.
 *
 * A model that answers a PDF chunk with `Ch. 4 s. 12` labels (or an EPUB chunk
 * with `p. 12 s. 3`) used to make every entry invisible: the strict filter
 * matched nothing, so the page showed "no translation imported" while the whole
 * batch sat in the document. Chapter 4 and page 4 are the same slot of the same
 * document and the numbers are the user's own, so when the strict pass finds
 * nothing the other label kind is accepted for THIS page only - never another
 * page.
 */
internal fun entriesForPage(
    bookSentences: List<BookSentence>,
    currentPage: Int,
    currentChapter: Int,
    isEpubDoc: Boolean,
): List<MergedEntry> {
    val entries = bookSentences.map { sentence ->
        val anchor = anchorOf(sentence.location)
        MergedEntry(
            sentence = sentence,
            norm = FuzzyTextAligner.normalize(sentence.english),
            page = anchor.page,
            chapter = anchor.chapter,
            ordinal = anchor.ordinal,
        )
    }
    val anchored = entries.any { it.page != null || it.chapter != null }
    val strict = when {
        // Legacy imports without any locator: there is nothing to scope by,
        // so the whole set is offered rather than hidden.
        !anchored -> entries
        isEpubDoc -> entries.filter { it.chapter == currentChapter }
        else -> entries.filter { it.page == currentPage }
    }
    val scoped = strict.ifEmpty {
        if (isEpubDoc) {
            entries.filter { it.page == currentChapter }
        } else {
            entries.filter { it.chapter == currentPage }
        }
    }
    return scoped.sortedWith(compareBy({ it.ordinal ?: Int.MAX_VALUE }, { it.sentence.id }))
}

/**
 * Reads `p. 12 s. 3` / `Ch. 4 s. 12` into numbers. Integers, not substrings:
 * page 1 and page 12 are different pages.
 */
private fun anchorOf(location: String): MergedAnchor {
    if (location.isBlank()) return MergedAnchor(null, null, null)
    val chapter = CHAPTER_RE.find(location)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val page = if (chapter != null) null else PAGE_RE.find(location)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val ordinal = ORDINAL_RE.find(location.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
    return MergedAnchor(page, chapter, ordinal)
}

/**
 * Places the page's imported sentences on the page's rendered lines.
 *
 * Pass 1: best text similarity, with a length-ratio guard and the sentence
 * ordinal as tie-breaker, each entry used at most once.
 * Pass 2: the ordinal the entry carries (`s. 7` = the seventh sentence of this
 * page). Precise, so it runs before the blind pairing below, and it is what
 * keeps two hard lines from pushing the other translations of the page into
 * the unmatched bucket.
 * Pass 3: containment. A model that ignores the one-line rule answers with a
 * signature line, a title block and a publisher's blurb inside ONE entry, so
 * nothing it holds can be similar to a whole line and the entry used to be
 * orphaned even though its opening words are a line of this page. The entry is
 * attached to the earliest line it contains (the block is then readable in
 * full under that line) instead of being listed as unplaceable.
 * Pass 4: when the page has exactly as many entries as lines, whatever is left
 * is paired in order - this rescues pages where extraction changed the
 * wording (ligatures, hyphenation, OCR) enough to fail similarity.
 *
 * Internal rather than private so the placement rules are covered by plain JVM
 * tests (`BookLinePlacementTest`) instead of only by looking at a device.
 */
internal fun matchLinesToEntries(
    lines: List<String>,
    entries: List<MergedEntry>,
): LinePlacement {
    val result = arrayOfNulls<BookSentence>(lines.size)
    if (entries.isEmpty()) return LinePlacement(result.toList())
    val used = mutableSetOf<Int>()
    val norms = lines.map { if (it.isBlank()) "" else FuzzyTextAligner.normalize(it) }

    for (index in lines.indices) {
        val norm = norms[index]
        if (norm.length < 3) continue
        var best: MergedEntry? = null
        var bestScore = SIMILARITY_THRESHOLD
        var bestDistance = Int.MAX_VALUE
        for (entry in entries) {
            if (entry.sentence.id in used) continue
            if (entry.norm.length < 3) continue
            val ratio = if (norm.length > entry.norm.length) {
                norm.length.toDouble() / entry.norm.length
            } else {
                entry.norm.length.toDouble() / norm.length
            }
            if (ratio > LENGTH_RATIO_LIMIT) continue
            val similarity = FuzzyTextAligner.similarity(norm, entry.norm)
            if (similarity < bestScore) continue
            val distance = kotlin.math.abs((entry.ordinal ?: (index + 1)) - (index + 1))
            if (similarity > bestScore || distance < bestDistance) {
                bestScore = similarity
                bestDistance = distance
                best = entry
            }
        }
        if (best != null) {
            result[index] = best.sentence
            used += best.sentence.id
        }
    }

    /*
     * Pass 2: the sentence ordinal.
     *
     * `s. 7` means the seventh sentence of this page, which is exactly what the
     * reader segmented out of it, so the entry belongs on the seventh rendered
     * line even when extraction changed its wording enough to miss
     * similarity. This runs before the blind pairing below because it is
     * precise, and it is what keeps two hard lines from pushing every other
     * translation of the page into the unmatched bucket.
     */
    val lineIndexes = lines.indices.filter { lines[it].isNotBlank() }
    for (entry in entries) {
        if (entry.sentence.id in used) continue
        val ordinal = entry.ordinal ?: continue
        val index = lineIndexes.getOrNull(ordinal - 1) ?: continue
        if (result[index] != null) continue
        result[index] = entry.sentence
        used += entry.sentence.id
    }

    /*
     * Pass 3: the earliest line a much longer entry contains.
     *
     * This is the merged-entry rescue. The line is looked for inside the entry
     * word by word, in order, because the model rewrites what it copies - the
     * sample that came back turned "-- Walt Jung, former IC apps engineer"
     * into "Walt Jung, Former IC apps engineer", which no exact search finds.
     * The entry must be far longer than the line, so this only ever fires on
     * an entry that swallowed whole lines; a normal one is placed above.
     */
    for (index in lines.indices) {
        if (result[index] != null) continue
        val norm = norms[index]
        if (norm.length < CONTAINMENT_MIN_CHARS) continue
        val lineWords = norm.split(' ').filter { it.isNotEmpty() }
        if (lineWords.isEmpty()) continue
        var best: MergedEntry? = null
        var bestCoverage = 0.0
        for (entry in entries) {
            if (entry.sentence.id in used) continue
            if (entry.norm.length < norm.length * CONTAINMENT_RATIO) continue
            val coverage = containmentCoverage(
                entry.norm.split(' ').filter { it.isNotEmpty() },
                lineWords,
            ) ?: continue
            // The most covered line wins; on a tie the shortest entry does,
            // because the one that holds the least extra text is the one this
            // line is most likely to be the opening of.
            val current = best
            val better = current == null || coverage > bestCoverage ||
                (coverage == bestCoverage && entry.norm.length < current.norm.length)
            if (better) {
                bestCoverage = coverage
                best = entry
            }
        }
        if (best != null) {
            result[index] = best.sentence
            used += best.sentence.id
        }
    }

    val lineCount = lines.count { it.isNotBlank() }
    if (lineCount == entries.size) {
        var cursor = 0
        for (index in lines.indices) {
            if (lines[index].isBlank()) continue
            val entry = entries.getOrNull(cursor)
            cursor++
            if (result[index] != null) continue
            if (entry == null || entry.sentence.id in used) continue
            result[index] = entry.sentence
            used += entry.sentence.id
        }
    }
    /*
     * Which lines carry an entry that is really several lines long.
     *
     * The prompt asks for one line per entry, so an entry that is far longer
     * than the line it sits under is the model merging a title block, a
     * signature or a blurb with the sentence next to it. Nothing can undo that
     * inside the reader - one `english` has one joined `translation`, and the
     * pieces cannot be cut apart again - but the entry can say so, and that is
     * the difference between "the app lost my line" and "this entry covers
     * three lines". The pass above and the ordinal pass feed the same rule:
     * what matters is the pair (line, entry) that ended up together, not which
     * pass paired them.
     */
    val mergedIds = mutableSetOf<Int>()
    for (index in lines.indices) {
        val sentence = result[index] ?: continue
        val lineLength = norms[index].length
        if (lineLength < CONTAINMENT_MIN_CHARS) continue
        val entry = entries.firstOrNull { it.sentence.id == sentence.id } ?: continue
        if (entry.norm.length >= lineLength * CONTAINMENT_RATIO) mergedIds += sentence.id
    }

    /*
     * The lines that entry swallowed.
     *
     * The blurb of the reported sample is one of them: entry 27 was answered
     * with the signature, the title block and the blurb, so the blurb's text
     * belongs to that entry and the entry sits under the signature. Without a
     * pointer the blurb's line would simply show nothing, which is what "this
     * part is not displayed" means. The line says where its translation is.
     */
    val linesInsideMerged = mutableMapOf<Int, Int>()
    if (mergedIds.isNotEmpty()) {
        for (index in lines.indices) {
            if (result[index] != null) continue
            val norm = norms[index]
            if (norm.length < CONTAINMENT_MIN_CHARS) continue
            val lineWords = norm.split(' ').filter { it.isNotEmpty() }
            if (lineWords.isEmpty()) continue
            val holder = entries.firstOrNull { entry ->
                entry.sentence.id in mergedIds &&
                    entry.norm.length >= norm.length * CONTAINMENT_RATIO &&
                    containmentCoverage(entry.norm.split(' ').filter { it.isNotEmpty() }, lineWords) != null
            }
            if (holder != null) linesInsideMerged[index] = holder.sentence.id
        }
    }
    return LinePlacement(result.toList(), mergedIds, linesInsideMerged)
}

/**
 * Share of a line's words that appear inside an entry in the same order, or
 * `null` when that share is too small to mean anything.
 *
 * The measure is the longest common subsequence of the two word lists, so a
 * word the line repeats ("... the IC Op Amp Cookbook" after an earlier "the")
 * still counts where a left-to-right walk would have given up. Words in
 * between are free, which is the point: the entry holds this line plus the
 * lines the model merged into it.
 */
private fun containmentCoverage(entryWords: List<String>, lineWords: List<String>): Double? {
    if (entryWords.isEmpty() || lineWords.isEmpty()) return null
    val matched = longestCommonSubsequence(entryWords, lineWords)
    val coverage = matched.toDouble() / lineWords.size
    return if (coverage < CONTAINMENT_COVERAGE) null else coverage
}

/** Length of the longest subsequence of words common to both lists, in order. */
private fun longestCommonSubsequence(left: List<String>, right: List<String>): Int {
    var previous = IntArray(right.size + 1)
    var current = IntArray(right.size + 1)
    for (i in left.indices) {
        for (j in right.indices) {
            current[j + 1] = if (left[i] == right[j]) {
                previous[j] + 1
            } else {
                maxOf(previous[j + 1], current[j])
            }
        }
        val swap = previous
        previous = current
        current = swap
        current.fill(0)
    }
    return previous[right.size]
}

/**
 * Where each line's translation went.
 *
 * @param matches one entry per rendered line, or `null` for a line no entry
 *   belongs to.
 * @param mergedIds the entries that hold several source lines. They are placed
 *   on the line they open with and labelled in the reader, because one entry
 *   has one joined translation and its pieces cannot be handed back to the
 *   lines they came from.
 * @param linesInsideMerged for a line that stayed empty because its own text
 *   is part of one of those entries: the index of the line to the id of the
 *   entry that swallowed it, so the reader can say where its translation went
 *   instead of leaving the line looking lost.
 */
internal data class LinePlacement(
    val matches: List<BookSentence?>,
    val mergedIds: Set<Int> = emptySet(),
    val linesInsideMerged: Map<Int, Int> = emptyMap(),
)

/** The reader scope's font, Persian or not, same rule as the plain page. */
private fun mergedReaderFontFor(text: String): FontFamily? =
    if (text.isPersianText()) {
        AppFontState.resolvedFamily(AppFontScope.READER, fa = true)
    } else {
        AppFontState.resolvedFamily(AppFontScope.READER, fa = false)
    }
