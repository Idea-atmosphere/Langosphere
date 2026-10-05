package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.EpubParser
import com.example.logic.ReaderComfortState
import com.example.logic.SentenceCardFactory
import com.example.logic.ReaderHighlightState
import com.example.logic.ReaderThemeMode
import com.example.logic.isPersianText
import com.example.model.JsonWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.components.BookReaderActionBar
import com.example.ui.components.BookText
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.SentenceStudyLabels
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.brandBrush
import com.example.ui.components.fadingEdges
import com.example.ui.components.neoHardShadow
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppFontScope
import com.example.ui.theme.AppFontState
import com.example.ui.theme.AppStrings
import com.example.ui.theme.neoAccent
import com.example.ui.components.anime.SoraMascot
import com.example.ui.components.anime.SoraSpeechBubble
import com.example.ui.components.anime.ToonChip
import com.example.ui.components.anime.ToonIconButton
import com.example.ui.components.anime.inkBorder
import com.example.ui.components.anime.inkShadow
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isNeobrutalismDesign
import com.example.ui.theme.showSoraMascot

/**
 * The Book tab: a dictionary panel, a document picker and the reading surface.
 *
 * The reading surface is the part that a person stares at for hours, so it is
 * treated as its own little reader rather than as "a Text in the app's theme":
 *  - paper and ink colors, type size, line/letter/paragraph spacing, margins
 *    and justification come from [ReaderComfortState], with warm sepia defaults
 *    chosen for eye comfort,
 *  - it has its OWN day/night switch, so the app can stay dark while the page
 *    stays light (or the other way round),
 *  - tapping a word looks it up; selecting text opens the reading toolbar,
 *    where a highlight can be added or removed ([ReaderHighlightState] keeps
 *    the marks per document),
 *  - EPUB files are read chapter by chapter, illustrations included,
 *  - PDFs can be shown as rendered page images, which is the only way to see
 *    their pictures, tables, formulas and scanned pages.
 *
 * Document switching and the AI pipeline are NOT hidden behind the file name
 * any more: [BookReaderActionBar] sits next to the title with JSON import and
 * page-range extraction as direct actions; less-frequent controls (open,
 * smart copy, prompt copy, display settings and study switches) stay in its
 * kebab (⋮) menu. The reading tools below it are just as spare: font size
 * down/up and day/night.
 */
@Composable
fun ReaderScreen(viewModel: AppViewModel) {
    val text by viewModel.readerText.collectAsState()
    val rFileName by viewModel.readerFileName.collectAsState()
    val readerDocumentUri by viewModel.readerDocumentUri.collectAsState()
    val readerDocumentType by viewModel.readerDocumentType.collectAsState()
    val isDictLoaded by viewModel.isDictionaryLoaded.collectAsState()
    val isPdf by viewModel.readerIsPdf.collectAsState()
    val pageCount by viewModel.readerPageCount.collectAsState()
    val currentPage by viewModel.readerCurrentPage.collectAsState()
    val isFullscreen by viewModel.isReaderFullscreen.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val context = LocalContext.current
    val strings = remember(appLanguage, context) { AppStrings(appLanguage, context) }
    // The reader's own labels live beside the reader (see ReaderComfortSheet.kt);
    // the language is taken from the strings we already have.
    val fa = remember(strings) { strings.close.any { it.code in 0x0600..0x06FF } }
    val labels = remember(fa) { ReaderLabels(fa) }
    // Term cards and sentence note boxes carry their own bilingual labels; the
    // reader's language is the only thing they need to know.
    val studyLabels = remember(fa) { SentenceStudyLabels(fa) }
    // Sentences already in the Leitner box, so "[+ لایتنر جمله]" shows its
    // saved state the moment any surface adds the line.
    val savedSentenceTexts by viewModel.savedSentenceTexts.collectAsState()

    LaunchedEffect(Unit) { ReaderComfortState.load(context) }

    val bookState = rememberBookReaderState()

    // BookReaderState owns only in-memory parser/render state. Reopen the
    // persisted SAF document when this screen (or the app process) is recreated
    // so its actions use the same source that produced the cached reader text.
    LaunchedEffect(readerDocumentUri, readerDocumentType, rFileName) {
        val name = rFileName
        when (readerDocumentType) {
            ReaderDocumentType.PDF -> readerDocumentUri?.let { bookState.openPdf(it, name) }
            ReaderDocumentType.EPUB -> readerDocumentUri?.let { bookState.openEpub(it, name) }
            ReaderDocumentType.TEXT -> bookState.openPlainText(name)
            ReaderDocumentType.NONE -> Unit
        }
    }

    var showComfortSheet by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }

    // ── Book JSON sentences: now shown inline where the book text was ──
    // Previously these lived only inside BookReaderActionBar's popup dialog.
    // User request: show original + translation directly in the main reading
    // area, with a "درس جمله" button that opens a bottom-sheet like video subs.
    val bookSessionStore = remember { com.example.logic.BookSessionStore(context) }
    var bookSentences by remember { mutableStateOf<List<com.example.model.BookSentence>>(emptyList()) }
    var bookCheckpoint by remember { mutableStateOf<com.example.model.BookCheckpoint>(com.example.model.BookCheckpoint.EMPTY) }
    var selectedLessonSentence by remember { mutableStateOf<com.example.model.BookSentence?>(null) }
    var bookSentencesVersion by remember { mutableStateOf(0) } // bump to force reload after import

    // Load sentences whenever docKey changes or after an import
    // Uses fallback to legacy key (without URI hash) for migration of old data
    LaunchedEffect(bookState.docKey, bookState.legacyDocKey, rFileName, bookSentencesVersion) {
        val key = if (bookState.docKey.isNotEmpty()) bookState.docKey else rFileName
        val legacyKey = bookState.legacyDocKey
        if (key.isBlank()) {
            bookSentences = emptyList()
            bookCheckpoint = com.example.model.BookCheckpoint.EMPTY
            return@LaunchedEffect
        }
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                bookSessionStore.loadSentencesWithFallback(key, legacyKey) to bookSessionStore.loadCheckpointWithFallback(key, legacyKey)
            }.getOrNull()
        }
        if (loaded != null) {
            bookSentences = loaded.first
            bookCheckpoint = loaded.second
        } else {
            bookSentences = emptyList()
            bookCheckpoint = com.example.model.BookCheckpoint.EMPTY
        }
    }

    // The toon reader tints each word by how well it is known. A card that
    // has climbed to the last Leitner box counts as "known", anything still in
    // circulation is "learning". Derived, not stored, so it follows the box.
    val leitnerCards by viewModel.leitnerCards.collectAsState()
    val knownWords = remember(leitnerCards) {
        leitnerCards.filter { it.boxLevel >= LEITNER_KNOWN_LEVEL }
            .mapTo(mutableSetOf()) { it.word.lowercase() }
    }
    val learningWords = remember(leitnerCards) {
        leitnerCards.filter { it.boxLevel < LEITNER_KNOWN_LEVEL }
            .mapTo(mutableSetOf()) { it.word.lowercase() }
    }

    val isImporting by viewModel.isImportingDict.collectAsState()
    val importCount by viewModel.importCount.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importedFiles by viewModel.importedDictFiles.collectAsState()

    // The imported-dictionary list used to be permanently expanded and pushed
    // the actual reading area off screen once a few files were imported.
    var showDictFiles by remember { mutableStateOf(false) }

    // ── What is open, and how it should be drawn ──
    val isEpub = bookState.kind == BookSourceKind.EPUB
    val chapter = bookState.chapter()
    val bodyText = if (isEpub) chapter?.text.orEmpty() else text
    val docTitle = if (isEpub) bookState.docName else rFileName
    val pdfImageMode = bookState.kind == BookSourceKind.PDF &&
        ReaderComfortState.showPdfImages &&
        bookState.pageImageMode
    val hasContent = bodyText.isNotEmpty() || pdfImageMode

    // The reader's day/night side, decided independently of the app theme.
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val night = ReaderComfortState.isNight(appIsDark)
    val paperColor = Color(ReaderComfortState.backgroundArgb(night))
    val inkColor = Color(ReaderComfortState.textArgb(night))

    // Highlights are stored per document, so they survive closing the file.
    val docKey = if (bookState.docKey.isNotEmpty()) bookState.docKey else rFileName
    LaunchedEffect(docKey) { ReaderHighlightState.open(context, docKey) }
    val highlightMap = ReaderHighlightState.colorMap()

    // Removing a highlight is the counterpart of adding one: selecting marked
    // text again (or tapping it) must be able to take the mark back.
    val onRemoveHighlight: (String) -> Unit = { phrase ->
        ReaderHighlightState.items
            .firstOrNull { it.text.equals(phrase.trim(), ignoreCase = true) }
            ?.let { ReaderHighlightState.remove(context, it) }
    }

    // Long reading sessions should not be interrupted by the screen turning off.
    val view = LocalView.current
    DisposableEffect(ReaderComfortState.keepScreenOn, hasContent) {
        view.keepScreenOn = ReaderComfortState.keepScreenOn && hasContent
        onDispose { view.keepScreenOn = false }
    }

    val location = when {
        isEpub -> labels.chapterShort(bookState.chapterIndex + 1)
        isPdf && pageCount > 0 -> labels.pageShort(currentPage + 1)
        else -> ""
    }
    // Simple tap = dictionary lookup. Highlighting is now done via text selection menu.
    val onWordTap: (String) -> Unit = { word ->
        viewModel.lookupWord(word)
    }

    val textFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            val name = displayNameOf(context, it)
            val documentType = when {
                EpubParser.isEpub(name, mimeType) -> ReaderDocumentType.EPUB
                mimeType?.contains("pdf", ignoreCase = true) == true ||
                    name.endsWith(".pdf", ignoreCase = true) -> ReaderDocumentType.PDF
                else -> ReaderDocumentType.TEXT
            }

            // Keep the original source URI and its read grant. The extracted
            // page text is cached for display, but chunk extraction must be able
            // to reopen the PDF after the activity or process is restarted.
            viewModel.rememberReaderDocument(it, name, documentType)
            if (documentType != ReaderDocumentType.EPUB) {
                viewModel.loadTextFile(it, mimeType)
            }
        }
    }

    val dictFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.loadDictionary(it)
        }
    }

    // Importing Progress Dialog
    if (isImporting) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = strings.importingDbTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.importingWordsCount(importCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Import Error Dialog
    if (importError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportError() },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
            },
            title = {
                Text(
                    text = strings.errorTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            text = {
                Text(
                    text = importError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { viewModel.clearImportError() }) {
                    Text(strings.ok)
                }
            }
        )
    }

    // EPUB parsing / page rendering problems
    bookState.errorKey?.let { key ->
        AlertDialog(
            onDismissRequest = { bookState.clearError() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed) },
            title = { Text(text = strings.errorTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (key == "epub_failed") labels.epubFailed else labels.pageRenderFailed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { bookState.clearError() }) { Text(strings.ok) }
            }
        )
    }

    if (showComfortSheet) {
        ReaderComfortSheet(
            fa = fa,
            night = night,
            isPdf = bookState.kind == BookSourceKind.PDF || isPdf,
            onDismiss = { showComfortSheet = false },
        )
    }

    if (showChapterList && isEpub) {
        ChapterListDialog(
            titles = bookState.book?.chapters?.mapIndexed { index, item ->
                item.title.ifBlank { labels.chapterShort(index + 1) }
            }.orEmpty(),
            selectedIndex = bookState.chapterIndex,
            title = labels.chapters,
            closeLabel = strings.close,
            onSelect = {
                bookState.goToChapter(it)
                showChapterList = false
            },
            onDismiss = { showChapterList = false },
        )
    }

    // Fullscreen reading mode — shown over everything else when active, with
    // the same comfort settings, navigator and highlighter as the normal layout.
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { viewModel.setReaderFullscreen(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = paperColor) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = docTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = inkColor,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        // The same document/AI controls are available in
                        // fullscreen, where the file name is not tappable at all.
                        BookReaderActionBar(
                            docUri = bookState.docUri,
                            docName = docTitle,
                            docKey = docKey,
                            isEpub = isEpub,
                            currentPageNumber = currentPage + 1,
                            onOpenFileClick = { textFileLauncher.launch(arrayOf("*/*")) },
                            tint = inkColor,
                            onSentencesUpdated = { bookSentencesVersion++ },
                            fa = fa,
                            onComfortClick = { showComfortSheet = true },
                            onChaptersClick = if (isEpub) {
                                { showChapterList = true }
                            } else {
                                null
                            },
                            onTogglePageImages = if (bookState.kind == BookSourceKind.PDF) {
                                { bookState.setPageImageMode(!bookState.pageImageMode) }
                            } else {
                                null
                            },
                            pageImageMode = bookState.pageImageMode,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ReaderToolbar(labels = labels, night = night)
                        Spacer(modifier = Modifier.width(6.dp))
                        SoftIconButton(
                            icon = Icons.Default.FullscreenExit,
                            contentDescription = strings.exitFullscreenCd,
                            onClick = { viewModel.setReaderFullscreen(false) },
                            tint = inkColor,
                            size = 36.dp,
                        )
                    }

                    if (isEpub && bookState.chapterCount > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ChapterBar(
                            labels = labels,
                            index = bookState.chapterIndex,
                            count = bookState.chapterCount,
                            title = chapter?.title.orEmpty(),
                            ink = inkColor,
                            onPrevious = { bookState.previousChapter() },
                            onNext = { bookState.nextChapter() },
                            onOpenList = { showChapterList = true },
                        )
                    }

                    if (!isEpub && isPdf && pageCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        PdfPageNavigator(
                            currentPage = currentPage,
                            pageCount = pageCount,
                            onGoToPage = { viewModel.goToReaderPage(it) },
                            onPrevious = { viewModel.previousReaderPage() },
                            onNext = { viewModel.nextReaderPage() },
                            strings = strings
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val hasContentFs = bodyText.isNotEmpty() || pdfImageMode
                    // Same condition as the normal branch below: a PDF/EPUB page
                    // with imported JSON always renders merged line-by-line, even
                    // when the page itself has no extractable text. The old
                    // `hasContentFs &&` gate dumped ALL imported sentences as one
                    // raw list on every blank page in fullscreen.
                    if (bookSentences.isNotEmpty() && (isEpub || isPdf || hasContentFs)) {
                        // MERGED in fullscreen too — PDF stays
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${bookSentences.size} جمله • PDF + ترجمه ادغام شده",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = inkColor.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                )
                                val delScopeFs = rememberCoroutineScope()
                                TextButton(
                                    onClick = {
                                        delScopeFs.launch(Dispatchers.IO) {
                                            val key = if (bookState.docKey.isNotEmpty()) bookState.docKey else rFileName
                                            if (key.isNotBlank()) runCatching { bookSessionStore.deleteDocument(key) }
                                            withContext(Dispatchers.Main) {
                                                bookSentences = emptyList()
                                                bookCheckpoint = com.example.model.BookCheckpoint.EMPTY
                                                bookSentencesVersion++
                                            }
                                        }
                                    }
                                ) {
                                    Text("حذف JSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f))
                                }
                            }
                            MergedReaderPaperV3(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                bodyText = bodyText,
                                epubBlocks = if (isEpub) chapter?.blocks else null,
                                bookState = bookState,
                                pdfImageMode = pdfImageMode,
                                pdfPageIndex = currentPage,
                                paper = paperColor,
                                ink = inkColor,
                                highlights = highlightMap,
                                learningWords = learningWords,
                                knownWords = knownWords,
                                labels = labels,
                                location = location,
                                onWordTap = onWordTap,
                                onSurfaceTap = { viewModel.setReaderFullscreen(false) },
                                onRemoveHighlight = onRemoveHighlight,
                                drawFrame = false,
                                bookSentences = bookSentences,
                                onLessonClick = { sentence -> selectedLessonSentence = sentence },
                                onWordClick = { word -> viewModel.lookupWord(word) },
                                onAddWordToLeitner = { _, word -> viewModel.addWordToLeitner(word.word.orEmpty(), wordDefinitionFor(word)) },
                                onAddSentenceToLeitner = { viewModel.addSentenceToLeitner(SentenceCardFactory.fromBook(it)) },
                                savedSentenceTexts = savedSentenceTexts,
                            )
                        }
                    } else if (bookSentences.isNotEmpty()) {
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${bookSentences.size} جمله • ادامه از id ${bookCheckpoint.nextId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = inkColor.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                )
                                val delScopeFs = rememberCoroutineScope()
                                TextButton(
                                    onClick = {
                                        delScopeFs.launch(Dispatchers.IO) {
                                            val key = if (bookState.docKey.isNotEmpty()) bookState.docKey else rFileName
                                            if (key.isNotBlank()) runCatching { bookSessionStore.deleteDocument(key) }
                                            withContext(Dispatchers.Main) {
                                                bookSentences = emptyList()
                                                bookCheckpoint = com.example.model.BookCheckpoint.EMPTY
                                                bookSentencesVersion++
                                            }
                                        }
                                    }
                                ) {
                                    Text("حذف JSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f))
                                }
                            }
                            com.example.ui.components.BookSentencePane(
                                sentences = bookSentences,
                                onLessonClick = { sentence -> selectedLessonSentence = sentence },
                                onWordClick = { word -> viewModel.lookupWord(word) },
                                onAddWordToLeitner = { _, word -> viewModel.addWordToLeitner(word.word.orEmpty(), wordDefinitionFor(word)) },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                analyzeLabel = "درس جمله",
                                highlightLabel = "هایلایت",
                                studyLabels = studyLabels,
                                onAddSentenceToLeitner = { viewModel.addSentenceToLeitner(SentenceCardFactory.fromBook(it)) },
                                savedSentenceTexts = savedSentenceTexts,
                            )
                        }
                    } else if (!isEpub && isPdf && pageCount > 0 && bodyText.isBlank() && !pdfImageMode) {
                        // Blank PDF page without JSON in fullscreen: the same
                        // notice, not an empty paper. (Image mode still renders
                        // the page bitmap below, so it is excluded.)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            BlankPdfPageNotice(ink = inkColor)
                        }
                    } else {
                        ReaderPaper(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            bodyText = bodyText,
                            epubBlocks = if (isEpub) chapter?.blocks else null,
                            bookState = bookState,
                            pdfImageMode = pdfImageMode,
                            pdfPageIndex = currentPage,
                            paper = paperColor,
                            ink = inkColor,
                            highlights = highlightMap,
                            learningWords = learningWords,
                            knownWords = knownWords,
                            labels = labels,
                            location = location,
                            onWordTap = onWordTap,
                            onSurfaceTap = { viewModel.setReaderFullscreen(false) },
                            onRemoveHighlight = onRemoveHighlight,
                            drawFrame = false,
                        )
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // ── Dictionary panel: state, actions and (collapsed) imported files ──
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = if (isDictLoaded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDictLoaded) strings.dictLoadedActive else strings.dictEmpty,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.dictHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                StatusPill(
                    text = strings.importedFilesCount(importedFiles.size),
                    icon = if (isDictLoaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                    tone = if (isDictLoaded) PillTone.Positive else PillTone.Negative,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientButton(
                    text = strings.addDictionary,
                    onClick = { dictFileLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1.1f),
                )

                if (isDictLoaded) {
                    OutlinedTonalAction(
                        text = strings.clearAll,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.clearAllDictionaries() },
                        modifier = Modifier.weight(0.9f),
                    )
                }
            }

            // Imported dictionary files — collapsed by default.
            if (importedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                val chevronRotation by animateFloatAsState(
                    targetValue = if (showDictFiles) 180f else 0f,
                    animationSpec = tween(durationMillis = 240),
                    label = "dict-files-chevron",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDictFiles = !showDictFiles }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.importedFilesCount(importedFiles.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
                AnimatedVisibility(visible = showDictFiles) {
                    Column {
                        importedFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = when (file.type) {
                                        "mdx" -> Icons.Default.CheckCircle
                                        "mdd" -> Icons.Default.Warning
                                        "db" -> Icons.Default.Storage
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when (file.type) {
                                        "mdx" -> MaterialTheme.colorScheme.primary
                                        "mdd" -> MaterialTheme.colorScheme.tertiary
                                        "db" -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                SoftIconButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = strings.deleteCd,
                                    onClick = { viewModel.removeDictionary(file.name, file.type) },
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 30.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Document bar ──
        if (docTitle.isEmpty()) {
            // Nothing loaded yet: make the import target the hero of the screen
            // instead of a plain outlined button.
            val neo = isNeobrutalismDesign()
            val heroShape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (neo) {
                            Modifier
                                .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 4.dp)
                                .background(neoAccent())
                                .border(2.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            Modifier
                                .clip(heroShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                        )
                                    )
                                )
                                .border(1.dp, brandBrush(alpha = 0.35f), heroShape)
                        }
                    )
                    .clickable { textFileLauncher.launch(arrayOf("*/*")) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = if (neo) Color.Black else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.selectTextPdf,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (neo) Color.Black else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // The picker accepts EPUB now, which is worth saying out loud.
                    Text(
                        text = labels.supportedFormats,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (neo) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // A lesson JSON can be imported before any document is open, so the
            // controls are offered here too rather than only once a file exists.
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookReaderActionBar(
                    docUri = bookState.docUri,
                    docName = docTitle,
                    docKey = docKey,
                    isEpub = isEpub,
                    currentPageNumber = currentPage + 1,
                    onOpenFileClick = { textFileLauncher.launch(arrayOf("*/*")) },
                    onSentencesUpdated = { bookSentencesVersion++ },
                    fa = fa,
                    onComfortClick = { showComfortSheet = true },
                )
            }
        } else {
            val neo = isNeobrutalismDesign()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                        .background(
                            if (neo) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                        .then(
                            if (neo) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline) else Modifier
                        )
                        .clickable { textFileLauncher.launch(arrayOf("*/*")) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = strings.selectTextPdf,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = docTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                // JSON import and page-range copy stay beside the document title;
                // less-frequent actions remain in the kebab (⋮) menu.
                Spacer(modifier = Modifier.width(4.dp))
                BookReaderActionBar(
                    docUri = bookState.docUri,
                    docName = docTitle,
                    docKey = docKey,
                    isEpub = isEpub,
                    currentPageNumber = currentPage + 1,
                    onOpenFileClick = { textFileLauncher.launch(arrayOf("*/*")) },
                    onSentencesUpdated = { bookSentencesVersion++ },
                    fa = fa,
                    onComfortClick = { showComfortSheet = true },
                    onChaptersClick = if (isEpub) {
                        { showChapterList = true }
                    } else {
                        null
                    },
                    onTogglePageImages = if (bookState.kind == BookSourceKind.PDF) {
                        { bookState.setPageImageMode(!bookState.pageImageMode) }
                    } else {
                        null
                    },
                    pageImageMode = bookState.pageImageMode,
                )

                if (hasContent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SoftIconButton(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = strings.fullscreenCd,
                        onClick = { viewModel.toggleReaderFullscreen() },
                        size = 36.dp,
                    )
                }
            }

            // ── Reading tools: font size and day/night only, the rest is in the kebab ──
            if (hasContent) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderToolbar(labels = labels, night = night)
                    Spacer(modifier = Modifier.weight(1f))
                    if (ReaderComfortState.showChapterProgress) {
                        Text(
                            text = when {
                                isEpub -> labels.chapterProgress(bookState.chapterIndex + 1, bookState.chapterCount)
                                isPdf && pageCount > 0 -> labels.pageProgress(currentPage + 1, pageCount)
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (isEpub && bookState.chapterCount > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            ChapterBar(
                labels = labels,
                index = bookState.chapterIndex,
                count = bookState.chapterCount,
                title = chapter?.title.orEmpty(),
                ink = MaterialTheme.colorScheme.onSurface,
                onPrevious = { bookState.previousChapter() },
                onNext = { bookState.nextChapter() },
                onOpenList = { showChapterList = true },
            )
        }

        // PDF page navigator: previous/next buttons plus a manual page number field.
        if (!isEpub && isPdf && pageCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            PdfPageNavigator(
                currentPage = currentPage,
                pageCount = pageCount,
                onGoToPage = { viewModel.goToReaderPage(it) },
                onPrevious = { viewModel.previousReaderPage() },
                onNext = { viewModel.nextReaderPage() },
                strings = strings
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (bookState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = labels.openingBook,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (bookSentences.isNotEmpty() && (isEpub || isPdf || hasContent)) {
            // FIX for "صفحه بعد خراب می‌شه و json ورودی رو نشون می‌ده"
            // Previously: hasContent && bookSentences -> Merged, else if bookSentences -> BookSentencePane (all JSON)
            // Problem: scanned PDF pages have empty text, hasContent=false, so next page showed ALL JSON as list
            // New: for PDF/EPUB with JSON, ALWAYS use MergedReaderPaper line-by-line, even if bodyText empty.
            // MergedReaderPaper will filter by page number via location, so next page shows only its sentences, not all.
            // This also implements user's request: "خط به خط موقع کپی جملات از pdf جدا کن" -> display is also line-by-line.
            // ── MERGED MODE: PDF/EPUB text stays, translations auto under same line ──
            // Fixes "فقط متونی نمایش داده می‌شه که تو جیسون هست و بعضی متون تو pdf حذف می‌شه"
            // Previously we replaced ReaderPaper entirely with BookSentencePane.
            // Now we keep the original book text and inject translations underneath
            // matching paragraphs via FuzzyTextAligner, so nothing is deleted.
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Header with count + actions (copy / export / delete) — same place as JSON import
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${bookSentences.size} جمله • ادامه از id ${bookCheckpoint.nextId} • PDF + ترجمه ادغام شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val ctx = LocalContext.current
                        val delScope = rememberCoroutineScope()
                        var showDeleteConfirm by remember { mutableStateOf(false) }

                        androidx.compose.material3.TextButton(
                            onClick = {
                                val textToCopy = bookSentences.joinToString("\n\n") { s ->
                                    listOfNotNull(
                                        s.location.takeIf { it.isNotBlank() },
                                        s.english.takeIf { it.isNotBlank() },
                                        s.translation?.takeIf { it.isNotBlank() },
                                    ).joinToString("\n")
                                }
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                                android.widget.Toast.makeText(ctx, "متن دوزبانه کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("کپی متن", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val payload = com.example.logic.BookJsonIngest.exportMasterJson(
                                    sentences = bookSentences,
                                    metadata = com.example.logic.BookJsonIngest.IngestMetadata(
                                        bookTitle = docTitle.ifBlank { "book" },
                                        language = "English",
                                        targetLanguage = "Persian",
                                        level = "B1",
                                    )
                                )
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(payload.take(10000)))
                                android.widget.Toast.makeText(ctx, "JSON در کلیپ‌بورد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("خروجی", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { showDeleteConfirm = true },
                        ) {
                            Text("حذف JSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("حذف فایل JSON وارد شده؟", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text(
                                        text = "تمام ${bookSentences.size} جملهٔ وارد شده برای «${docTitle.ifBlank { docKey.ifBlank { "این سند" } }}» حذف می‌شود. متن PDF باقی می‌ماند.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDeleteConfirm = false
                                            delScope.launch(Dispatchers.IO) {
                                                val key = if (bookState.docKey.isNotEmpty()) bookState.docKey else rFileName
                                                if (key.isNotBlank()) {
                                                    runCatching { bookSessionStore.deleteDocument(key) }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    bookSentences = emptyList()
                                                    bookCheckpoint = com.example.model.BookCheckpoint.EMPTY
                                                    bookSentencesVersion++
                                                    android.widget.Toast.makeText(ctx, "JSON حذف شد - PDF باقی ماند", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text("حذف", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) { Text("لغو") }
                                }
                            )
                        }
                    }
                }

                // Merged view: original PDF/EPUB stays, translation auto under same paragraph
                MergedReaderPaperV3(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    bodyText = bodyText,
                    epubBlocks = if (isEpub) chapter?.blocks else null,
                    bookState = bookState,
                    pdfImageMode = pdfImageMode,
                    pdfPageIndex = currentPage,
                    paper = paperColor,
                    ink = inkColor,
                    highlights = highlightMap,
                    learningWords = learningWords,
                    knownWords = knownWords,
                    labels = labels,
                    location = location,
                    onWordTap = onWordTap,
                    onSurfaceTap = { viewModel.toggleReaderFullscreen() },
                    onRemoveHighlight = onRemoveHighlight,
                    drawFrame = true,
                    bookSentences = bookSentences,
                    onLessonClick = { sentence -> selectedLessonSentence = sentence },
                    onWordClick = { word -> viewModel.lookupWord(word) },
                    onAddWordToLeitner = { _, word -> viewModel.addWordToLeitner(word.word.orEmpty(), wordDefinitionFor(word)) },
                    onAddSentenceToLeitner = { viewModel.addSentenceToLeitner(SentenceCardFactory.fromBook(it)) },
                    savedSentenceTexts = savedSentenceTexts,
                )
            }
        } else if (bookSentences.isNotEmpty()) {
            // No PDF/EPUB content, only JSON (e.g., imported without opening a file)
            // Show inline eye-friendly list as before — nothing is deleted because there is no PDF
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${bookSentences.size} جمله • ادامه از id ${bookCheckpoint.nextId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val ctx = LocalContext.current
                        val delScope = rememberCoroutineScope()
                        var showDeleteConfirm by remember { mutableStateOf(false) }

                        androidx.compose.material3.TextButton(
                            onClick = {
                                val textToCopy = bookSentences.joinToString("\n\n") { s ->
                                    listOfNotNull(
                                        s.location.takeIf { it.isNotBlank() },
                                        s.english.takeIf { it.isNotBlank() },
                                        s.translation?.takeIf { it.isNotBlank() },
                                    ).joinToString("\n")
                                }
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                                android.widget.Toast.makeText(ctx, "متن دوزبانه کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("کپی متن", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { showDeleteConfirm = true },
                        ) {
                            Text("حذف JSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("حذف فایل JSON وارد شده؟", fontWeight = FontWeight.Bold) },
                                text = { Text("تمام ${bookSentences.size} جمله حذف می‌شود.", style = MaterialTheme.typography.bodySmall) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDeleteConfirm = false
                                            delScope.launch(Dispatchers.IO) {
                                                val key = if (bookState.docKey.isNotEmpty()) bookState.docKey else rFileName
                                                if (key.isNotBlank()) runCatching { bookSessionStore.deleteDocument(key) }
                                                withContext(Dispatchers.Main) {
                                                    bookSentences = emptyList()
                                                    bookCheckpoint = com.example.model.BookCheckpoint.EMPTY
                                                    bookSentencesVersion++
                                                }
                                            }
                                        }
                                    ) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("لغو") } }
                            )
                        }
                    }
                }
                com.example.ui.components.BookSentencePane(
                    sentences = bookSentences,
                    onLessonClick = { sentence -> selectedLessonSentence = sentence },
                    onWordClick = { word -> viewModel.lookupWord(word) },
                    onAddWordToLeitner = { _, word -> viewModel.addWordToLeitner(word.word.orEmpty(), wordDefinitionFor(word)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    analyzeLabel = "درس جمله",
                    highlightLabel = "هایلایت",
                    studyLabels = studyLabels,
                onAddSentenceToLeitner = { viewModel.addSentenceToLeitner(SentenceCardFactory.fromBook(it)) },
                savedSentenceTexts = savedSentenceTexts,
                )
            }
        } else if (!hasContent) {
            // ── Empty reading surface ──
            // A PDF page with no extractable text is blank in the original
            // file, not a missing document: the blank notice, not the
            // open-file hint. (Pagination above keeps working through it.)
            if (!isEpub && isPdf && pageCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    BlankPdfPageNotice(ink = inkColor)
                }
            } else {
                val anime = isAnimeDesign()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (anime) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (showSoraMascot()) {
                                SoraMascot(size = 180.dp)
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            SoraSpeechBubble(text = strings.soraOpenPdfHint)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.emptyReaderHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.MenuBook,
                            title = strings.emptyReaderHint,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        } else {
            ReaderPaper(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                bodyText = bodyText,
                epubBlocks = if (isEpub) chapter?.blocks else null,
                bookState = bookState,
                pdfImageMode = pdfImageMode,
                pdfPageIndex = currentPage,
                paper = paperColor,
                ink = inkColor,
                highlights = highlightMap,
                learningWords = learningWords,
                knownWords = knownWords,
                labels = labels,
                location = location,
                onWordTap = onWordTap,
                onSurfaceTap = { viewModel.toggleReaderFullscreen() },
                onRemoveHighlight = onRemoveHighlight,
                drawFrame = true,
            )
        }
    }

    // Lesson bottom-sheet — same model as video's JsonSubtitleRow lesson button
    // Shown at root level so it appears over both normal and fullscreen modes
    selectedLessonSentence?.let { sentence ->
        com.example.ui.components.BookSentenceLessonSheet(
            sentence = sentence,
            onDismiss = { selectedLessonSentence = null },
            onAddWordToLeitner = { word ->
                viewModel.addWordToLeitner(word.word.orEmpty(), wordDefinitionFor(word))
            },
            knownWords = knownWords,
        )
    }
}

/**
 * The page itself: comfort colors, comfort typography, paragraphs, EPUB
 * illustrations or a rendered PDF page, plus the warm/dim overlays that make
 * long night sessions bearable.
 */
@Composable
private fun ReaderPaper(
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
    location: String = "",
    onWordTap: (String) -> Unit,
    onSurfaceTap: () -> Unit,
    onRemoveHighlight: (String) -> Unit = {},
    drawFrame: Boolean,
) {
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
        fontFamily = readerFontFor(bodyText),
    )
    val textStyle = readerComfortTextStyle(
        base = baseStyle,
        appFamily = readerFontFor(bodyText),
        inkColor = ink,
    )
    val accent = if (anime) AnimeColors.Sky else MaterialTheme.colorScheme.primary
    val margin = ReaderComfortState.horizontalMargin.dp
    val paragraphGap = ReaderComfortState.paragraphSpacing.dp

    BoxWithConstraints(modifier = modifier.then(frame)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }

        if (pdfImageMode) {
            // Rendered page: the only way to see a PDF's pictures, tables and
            // scanned pages. Pinch to zoom, drag to pan.
            LaunchedEffect(pdfPageIndex, widthPx) {
                bookState.requestPage(pdfPageIndex, widthPx)
            }
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val bitmap = bookState.pageBitmap
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        } else {
            val items = remember(bodyText, epubBlocks) {
                if (epubBlocks != null) {
                    epubBlocks.map { block ->
                        when (block) {
                            is EpubParser.Block.Text -> ReaderItem.Para(block.text)
                            is EpubParser.Block.Image -> ReaderItem.Picture(block.entry)
                        }
                    }
                } else {
                    splitParagraphs(bodyText).map { ReaderItem.Para(it) }
                }
            }

            val context = LocalContext.current
            LazyColumn(
                modifier = Modifier.fillMaxSize().fadingEdges(),
                contentPadding = PaddingValues(horizontal = margin, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(paragraphGap),
            ) {
                items(items = items) { item ->
                    when (item) {
                        is ReaderItem.Para -> BookText(
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
                            onHighlight = { phrase, color -> ReaderHighlightState.addHighlight(context, phrase, color, location) },
                            onNote = { /* note handled inside BookText via ReaderHighlightState */ },
                            onRemoveHighlight = onRemoveHighlight,
                        )
                        is ReaderItem.SentenceLine -> BookText(
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
                            onHighlight = { phrase, color -> ReaderHighlightState.addHighlight(context, phrase, color, location) },
                            onNote = { },
                            onRemoveHighlight = onRemoveHighlight,
                        )
                        is ReaderItem.Picture -> {
                            // EPUB illustrations, decoded on demand. The picture is
                            // scaled to the column width and keeps its aspect ratio.
                            val bitmap = bookState.epubImage(item.entry)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = labels.bookImage,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Comfort overlays, drawn over the page and ignored by touch: a warm
        // wash to cut blue light, and software dimming for reading in the dark
        // below the system's own minimum brightness.
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
}

/** One thing on the page: a paragraph of text, or a picture from the book. */
private sealed class ReaderItem {
    data class Para(val text: String) : ReaderItem()
    data class SentenceLine(val text: String) : ReaderItem()
    data class Picture(val entry: String) : ReaderItem()
}

/**
 * Turns extracted text into paragraphs.
 *
 * PDF extraction hard-wraps every visual line, which on a phone produces
 * ragged, unreadable text. Joining wrapped lines back into paragraphs (and
 * breaking overly long ones) is what makes the comfort settings
 * (line/paragraph spacing, justification) mean anything.
 */
/**
 * Single-contract paragraph splitter: uses SentenceSegmenter for cleaning and
 * then preserves paragraph breaks (double newline). This replaces the previous
 * ad-hoc hyphen handling that duplicated SentenceSegmenter.clean.
 * Mission requirement: one contract for segmentation, not three different definitions.
 */
private fun splitParagraphs(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    // Use the canonical cleaner (handles running lines, hyphen, control chars)
    val cleaned = com.example.logic.SentenceSegmenter.clean(text)
    if (cleaned.isBlank()) return emptyList()
    // Split by paragraph (blank line), then ensure no paragraph exceeds 1800 chars
    // by breaking at sentence boundaries, not arbitrary char count.
    val rawParas = cleaned.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
    val result = mutableListOf<String>()
    for (para in rawParas) {
        if (para.length <= 1800) {
            result += para
        } else {
            // Break long paragraph at sentence boundaries (single contract)
            val sentences = com.example.logic.SentenceSegmenter.sentences(para)
            var buf = StringBuilder()
            for (s in sentences) {
                if (buf.length + s.length + 1 > 1800 && buf.isNotEmpty()) {
                    result += buf.toString()
                    buf = StringBuilder(s)
                } else {
                    if (buf.isNotEmpty()) buf.append(' ')
                    buf.append(s)
                }
            }
            if (buf.isNotEmpty()) result += buf.toString()
        }
    }
    return if (result.isEmpty()) listOf(text) else result
}

/** The document's display name, used for the title and the highlight key. */
private fun displayNameOf(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(index)
                if (!name.isNullOrBlank()) return name
            }
        }
    } catch (e: Exception) {
        // Some providers refuse the query; the path fallback below still works.
    }
    return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
}

/**
 * The reading tools that stay permanently visible: font size down/up and the
 * page's own day/night switch.
 *
 * Everything else that used to sit here (display settings, text weight, page
 * images, chapters) moved into the kebab (⋮) menu in the document bar, which
 * is why this row takes no callbacks any more: the two pills write straight
 * to [ReaderComfortState], the same store the comfort sheet writes to.
 */
@Composable
private fun ReaderToolbar(
    labels: ReaderLabels,
    night: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ReaderFontStepButton(
            text = "A−",
            contentDescription = labels.fontSmaller,
            enabled = ReaderComfortState.fontScale > FONT_SCALE_MIN + 0.001f,
            onClick = { ReaderComfortState.setFontScale(ReaderComfortState.fontScale - FONT_SCALE_STEP) },
        )
        Spacer(modifier = Modifier.width(6.dp))
        ReaderFontStepButton(
            text = "A+",
            contentDescription = labels.fontLarger,
            enabled = ReaderComfortState.fontScale < FONT_SCALE_MAX - 0.001f,
            onClick = { ReaderComfortState.setFontScale(ReaderComfortState.fontScale + FONT_SCALE_STEP) },
        )
        Spacer(modifier = Modifier.width(6.dp))
        // One tap flips the page between its day and night side without
        // touching the app's theme.
        SoftIconButton(
            icon = if (night) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (night) labels.switchToDay else labels.switchToNight,
            onClick = {
                ReaderComfortState.setThemeMode(
                    if (night) ReaderThemeMode.DAY else ReaderThemeMode.NIGHT
                )
            },
            size = 36.dp,
        )
    }
}

/** One step of [ReaderComfortState.setFontScale], mirrored from its 0.7..2.6 clamp. */
private const val FONT_SCALE_MIN = 0.7f
private const val FONT_SCALE_MAX = 2.6f
private const val FONT_SCALE_STEP = 0.1f

/**
 * One font-size pill of the reading toolbar.
 *
 * A plain text pill rather than a [SoftIconButton] because the label (A− / A+)
 * is clearer than any glyph at this size. Colours come from the theme, so the
 * control survives every design language, and the disabled ends of the range
 * fade instead of lying about doing something.
 */
@Composable
private fun ReaderFontStepButton(
    text: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 38.dp)
            .clip(CircleShape)
            .background(scheme.secondaryContainer.copy(alpha = if (isAnimeDesign()) 0.9f else 0.55f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.35f),
            maxLines = 1,
        )
    }
}

/** Chapter stepper for EPUB files, the equivalent of the PDF page navigator. */
@Composable
private fun ChapterBar(
    labels: ReaderLabels,
    index: Int,
    count: Int,
    title: String,
    ink: Color,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenList: () -> Unit,
) {
    val neo = isNeobrutalismDesign()
    val shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (neo) {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline)
                    } else {
                        Modifier
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageStepButton(
                text = labels.previousChapter,
                enabled = index > 0,
                onClick = onPrevious,
                forward = false,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenList)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title.ifBlank { labels.chapterShort(index + 1) },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                )
                Text(
                    text = labels.chapterProgress(index + 1, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.7f),
                )
            }
            PageStepButton(
                text = labels.nextChapter,
                enabled = index < count - 1,
                onClick = onNext,
                forward = true,
            )
        }
    }
}

@Composable
private fun ChapterListDialog(
    titles: List<String>,
    selectedIndex: Int,
    title: String,
    closeLabel: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                titles.forEachIndexed { index, chapterTitle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            text = chapterTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == selectedIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) { Text(closeLabel) }
        }
    )
}

/**
 * The font for a reading surface: the READER scope's Persian choice when the
 * page itself is Persian, its English/general choice otherwise — each
 * inheriting the whole-app font (Settings ▸ Theme ▸ Font) while left on
 * "default" (see [AppFontState.resolvedFamily]).
 */
private fun readerFontFor(text: String): FontFamily? =
    if (text.isPersianText()) {
        AppFontState.resolvedFamily(AppFontScope.READER, fa = true)
    } else {
        AppFontState.resolvedFamily(AppFontScope.READER, fa = false)
    }

/** Text action in a single tone, used next to the primary GradientButton. */
@Composable
private fun OutlinedTonalAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isNeobrutalismDesign()) {
        // Neobrutalism: a flat white/raised square with an ink border; the
        // tone survives only in the text so destructive stays legible.
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(2.dp, MaterialTheme.colorScheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.32f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Prev/next buttons plus a manual page-number field for navigating an
 * imported PDF's pages (1-based in the UI, 0-based internally in the
 * ViewModel). Used both in the normal reader layout and the fullscreen
 * reading dialog.
 */
@Composable
private fun PdfPageNavigator(
    currentPage: Int,
    pageCount: Int,
    onGoToPage: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    var pageInput by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }
    val neo = isNeobrutalismDesign()
    val anime = isAnimeDesign()
    val shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp)

    // Keep page controls LTR so Prev stays left / Next stays right even
    // when the app composition is RTL (FA). Otherwise SpaceBetween flips.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (anime) {
                        // The toon navigator is a flat white strip with an ink
                        // edge; the arrows themselves carry the shadow.
                        Modifier
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surface)
                            .inkBorder(2.dp, shape)
                    } else if (neo) {
                        // Shadow first, then the flat card and its ink border;
                        // the clip below only applies to the soft designs.
                        Modifier
                            .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 3.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline)
                    } else {
                        Modifier
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                shape
                            )
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PageStepButton(
                text = strings.prevPage,
                enabled = currentPage > 0,
                onClick = onPrevious,
                forward = false,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input -> if (input.length <= 6 && input.all { it.isDigit() }) pageInput = input },
                    modifier = Modifier.width(72.dp),
                    singleLine = true,
                    shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val target = pageInput.toIntOrNull()
                        if (target != null) onGoToPage((target - 1).coerceIn(0, pageCount - 1))
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (anime) {
                    // The page counter is a sunny sticker tilted 6°, like a
                    // page number stamped onto a manga panel.
                    ToonChip(
                        text = strings.pageOfCount(pageCount),
                        modifier = Modifier.graphicsLayer { rotationZ = -6f },
                        selected = true,
                        fill = AnimeColors.Sunny,
                    )
                } else {
                Text(
                    text = strings.pageOfCount(pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
            }

            PageStepButton(
                text = strings.nextPage,
                enabled = currentPage < pageCount - 1,
                onClick = onNext,
                forward = true,
            )
        }
    }
}

@Composable
private fun PageStepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    forward: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.4f)
    if (isAnimeDesign()) {
        // Toon page turners: two 48dp round ink buttons, sky for forward and
        // lavender for back. The label survives as the content description so
        // the target stays announced even without visible text.
        ToonIconButton(
            icon = if (forward) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft,
            contentDescription = text,
            onClick = onClick,
            fill = if (forward) AnimeColors.Sky else AnimeColors.Lavender,
            enabled = enabled,
        )
        return
    }
    if (isNeobrutalismDesign()) {
        Box(
            modifier = Modifier
                .background(
                    if (enabled) {
                        scheme.surfaceContainerLowest
                    } else {
                        scheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                )
                .border(1.5.dp, scheme.outline)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
        return
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (enabled) 0.14f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * The one-line Leitner definition of a JSON word: translation, meaning in
 * context, extra explanation and IPA, in that order.
 *
 * Shared by the sentence lesson sheet and the term card, so a word saved from
 * either surface lands in the box in the same shape.
 */
private fun wordDefinitionFor(word: JsonWord): String = listOfNotNull(
    word.translation?.takeIf { it.isNotBlank() },
    word.meaningInContext?.takeIf { it.isNotBlank() },
    word.extraExplanation?.takeIf { it.isNotBlank() },
    word.pronunciation?.takeIf { it.isNotBlank() }?.let { "[$it]" },
).joinToString(" — ").ifBlank { word.word.orEmpty() }

/** Bilingual labels for the reading tools added to this screen. */
class ReaderLabels(private val fa: Boolean) {

    /** True when this reader is running in Persian; the study card and the
     *  sentence note box build their own labels from it. */
    val isFa: Boolean get() = fa

    val fontSmaller = if (fa) "کوچک‌تر کردن متن" else "Make the text smaller"
    val fontLarger = if (fa) "بزرگ‌تر کردن متن" else "Make the text larger"
    val switchToDay = if (fa) "حالت روز" else "Day mode"
    val switchToNight = if (fa) "حالت شب" else "Night mode"
    val highlighter = if (fa) "قلم هایلایت" else "Highlighter"
    val chapters = if (fa) "فهرست فصل‌ها" else "Chapters"
    val previousChapter = if (fa) "فصل قبل" else "Previous"
    val nextChapter = if (fa) "فصل بعد" else "Next"
    val openingBook = if (fa) "در حال باز کردن کتاب…" else "Opening the book…"
    val renderingPage = if (fa) "در حال ساخت تصویر صفحه…" else "Rendering the page…"
    val pageImage = if (fa) "تصویر صفحهٔ PDF" else "PDF page image"
    val bookImage = if (fa) "تصویر کتاب" else "Book illustration"
    val openAnotherFile = if (fa) "باز کردن فایل دیگر" else "Open another file"
    val importLessonJson = if (fa) "ورود فایل ترجمه / درس (JSON)" else "Import lesson JSON"
    val extractChunk = if (fa) "انتخاب بازهٔ صفحه و کپی متن" else "Extract a page range"
    val removeHighlight = if (fa) "حذف هایلایت" else "Remove highlight"
    val supportedFormats = if (fa) {
        "PDF ، EPUB ، TXT — عکس‌های صفحات PDF هم قابل نمایش است"
    } else {
        "PDF, EPUB, TXT — PDF page images included"
    }
    val epubFailed = if (fa) {
        "این فایل EPUB خوانده نشد. ممکن است فایل خراب باشد یا DRM داشته باشد."
    } else {
        "This EPUB could not be opened. The file may be damaged or DRM-protected."
    }
    val pageRenderFailed = if (fa) {
        "تصویر این صفحه ساخته نشد؛ می‌توانی همان متن را بخوانی."
    } else {
        "That page could not be rendered; the extracted text is still available."
    }

    fun chapterShort(number: Int): String = if (fa) "فصل $number" else "Ch. $number"

    fun pageShort(number: Int): String = if (fa) "صفحهٔ $number" else "p. $number"

    fun chapterProgress(index: Int, count: Int): String =
        if (fa) "فصل $index از $count" else "Chapter $index of $count"

    fun pageProgress(index: Int, count: Int): String =
        if (fa) "صفحهٔ $index از $count" else "Page $index of $count"
}

/**
 * A card that has reached the last Leitner box is treated as learned by the
 * reader's word tinting. Mirrors LeitnerBoxManager.MAX_BOX_LEVEL.
 */
private const val LEITNER_KNOWN_LEVEL = 5
