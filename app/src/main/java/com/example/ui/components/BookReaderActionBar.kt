package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.logic.BookBatchCopier
import com.example.logic.BookDocumentSource
import com.example.logic.BookJsonIngest
import com.example.logic.BookLessonAligner
import com.example.logic.BookPromptTemplates
import com.example.logic.BookSessionStore
import com.example.logic.BookSlicePayload
import com.example.logic.LessonDisplay
import com.example.logic.ReaderComfortState
import com.example.logic.StudyModeState
import com.example.model.BookCheckpoint
import com.example.model.BookSentence
import com.example.model.JsonWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The book reader's visible controls: open a file, copy a page range for the
 * AI, import the lesson JSON that comes back, and drop it again.
 *
 * ## What changed, and why the feature used to fail
 *
 * Importing used to trust the model's answer as-is. In practice the model
 * renumbers, re-punctuates and reformats `location`, and the reader — which
 * places a translation under a line by matching page + text — could not find
 * the line any more. The translations were imported, but they surfaced in the
 * "other sentences" bucket rather than under the sentence, and the per-sentence
 * lesson button looked dead.
 *
 * The import path now:
 * 1. recalls the exact slice that [ChunkExtractionDialog] copied (per document,
 *    surviving restarts) and feeds it to the ingest step as the expected text,
 *    so drift is detected against real sentences instead of a bare count;
 * 2. re-anchors every returned entry through [BookLessonAligner], restoring the
 *    document's own sentence text and a canonical `p. N s. M` locator, which
 *    turns the reader's fuzzy match into an exact one;
 * 3. reports coverage in plain Persian, so a partial answer is visible
 *    immediately rather than silently half-applied.
 *
 * The source language is detected from the document itself and stored with the
 * session, instead of assuming English.
 *
 * ## The visible row keeps the two book/AI actions together
 *
 * Importing is the primary action, so it keeps its own icon. Range extraction
 * sits immediately beside it for quick access; less-frequent actions (open
 * another file, smart 5-page copy, AI prompt, display settings, study switches,
 * chapters, page images and delete) remain in the kebab (⋮) overflow menu.
 */
@Composable
fun BookReaderActionBar(
    docUri: Uri?,
    docName: String,
    docKey: String,
    isEpub: Boolean,
    currentPageNumber: Int,
    onOpenFileClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    sourceLanguage: String = "",
    targetLanguage: String = "Persian",
    cefrLevel: String = BookPromptTemplates.DEFAULT_LEVEL,
    onAddWordToLeitner: ((BookSentence, JsonWord) -> Unit)? = null, // kept for compat, not used here anymore
    onSentencesUpdated: (() -> Unit)? = null,
    fa: Boolean = true,
    /** Opens the display/comfort sheet; when null the kebab hides that row. */
    onComfortClick: (() -> Unit)? = null,
    /** Opens the chapter list; when null the kebab hides that row. */
    onChaptersClick: (() -> Unit)? = null,
    /** Flips page image/text; when null the kebab hides that row. */
    onTogglePageImages: (() -> Unit)? = null,
    pageImageMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val store = remember { BookSessionStore(context) }

    var source by remember(docUri, isEpub) { mutableStateOf<BookDocumentSource.Units?>(null) }
    var sentences by remember(docKey) { mutableStateOf<List<BookSentence>>(emptyList()) }
    var checkpoint by remember(docKey) { mutableStateOf(BookCheckpoint.EMPTY) }
    var detectedLanguage by remember(docKey) { mutableStateOf("") }

    var showImport by remember { mutableStateOf(false) }
    var showExtract by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var importFailed by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }

    // The document is re-extracted page by page only when it actually changes.
    LaunchedEffect(docUri, isEpub, docName) {
        val uri = docUri
        if (uri == null) {
            source = null
            return@LaunchedEffect
        }
        isPreparing = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                if (isEpub) {
                    BookDocumentSource.loadEpub(context, uri, docName)
                } else {
                    BookDocumentSource.loadPdf(context, uri, docName)
                }
            }.getOrNull()
        }
        source = loaded
        // Detect the book's own language from a few real pages. Only a label,
        // but it drives the prompt header and the stored session, so "the app
        // detects the source language" stops being a guess of "English".
        detectedLanguage = withContext(Dispatchers.Default) {
            loaded?.let {
                BookSlicePayload.detectLanguage(
                    it.units.asSequence().filter { unit -> unit.isNotBlank() }.take(3).joinToString(" "),
                )
            }.orEmpty()
        }
        isPreparing = false
    }

    // Restore checkpoint + sentences for export / next-id logic
    LaunchedEffect(docKey) {
        if (docKey.isBlank()) return@LaunchedEffect
        val restored = withContext(Dispatchers.IO) {
            runCatching { store.loadSentences(docKey) to store.loadCheckpoint(docKey) }.getOrNull()
        }
        if (restored != null) {
            sentences = restored.first
            checkpoint = restored.second
        }
    }

    val effectiveSourceLanguage = sourceLanguage.ifBlank { detectedLanguage }
    val kebabLabels = remember(fa) { ReaderKebabLabels(fa) }

    fun openExtract() {
        if (source == null) {
            Toast.makeText(
                context,
                if (isPreparing) "متن سند در حال آماده‌سازی است…" else "اول یک فایل PDF یا EPUB باز کنید.",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            showExtract = true
        }
    }

    // ── "کپی ۵ صفحهٔ بعد برای هوش مصنوعی" ──
    // One tap for the whole reading loop: it continues after the pages
    // already handed over (or already imported), builds the same
    // `@LANGO v3` payload the range picker builds, copies it and
    // remembers the last page, so the next tap carries on where this one
    // stopped without the learner tracking page numbers by hand.
    fun smartCopyNext() {
        val units = source
        if (units == null) {
            Toast.makeText(
                context,
                if (isPreparing) "متن سند در حال آماده‌سازی است…" else "اول یک فایل PDF یا EPUB باز کنید.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (docKey.isBlank()) {
            Toast.makeText(context, "کلید سند مشخص نیست؛ ابتدا فایل را باز کنید.", Toast.LENGTH_SHORT).show()
            return
        }
        val startPage = BookBatchCopier.nextStartPage(
            copiedPage = BookBatchCopier.lastPage(context, docKey),
            // The furthest page the imported answer actually reached;
            // the copy checkpoint only breaks the tie when the JSON
            // carried no readable locator.
            checkpointPage = sentences
                .mapNotNull { sentence -> BookBatchCopier.unitOfLocation(sentence.location) }
                .maxOrNull(),
        )
        val batch = BookBatchCopier.buildNext(
            context = context,
            docKey = docKey,
            source = units,
            startPage = startPage,
            firstId = checkpoint.nextId,
            bookTitle = docName,
            sourceLanguage = effectiveSourceLanguage,
            targetLanguage = targetLanguage,
            level = cefrLevel,
        )
        if (batch == null) {
            Toast.makeText(context, "صفحه‌ای برای کپی نمانده است.", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setText(AnnotatedString(batch.text))
        val tail = if (batch.isLast) " (آخرین بخش کتاب)" else ""
        Toast.makeText(
            context,
            "صفحات ${batch.firstPage} تا ${batch.lastPage} کپی شد — ${batch.sentenceCount} جمله، id ${batch.firstId} تا ${batch.lastId}$tail",
            Toast.LENGTH_LONG,
        ).show()
    }

    // The book prompt the learner pastes once per AI chat, with this
    // document's languages, level and title already filled in — the same text
    // the AI teaching dialog builds for the book section.
    fun copyAiPrompt() {
        val prompt = BookPromptTemplates.buildPrompt(
            level = cefrLevel,
            mode = BookPromptTemplates.BookPromptMode.READING_LEARNING,
            targetLanguage = targetLanguage,
            chunkSize = BookPromptTemplates.DEFAULT_CHUNK_SENTENCES,
            sourceLanguage = effectiveSourceLanguage,
            bookTitle = docName,
        )
        clipboard.setText(AnnotatedString(prompt))
        Toast.makeText(context, kebabLabels.promptCopied, Toast.LENGTH_SHORT).show()
    }

    // Every secondary action in one overflow menu; import keeps its own icon
    // because it is the primary action of the AI loop. Toggles read the same
    // shared switches the reading surfaces read, so the check mark is always
    // the current choice.
    val kebabItems = buildList {
        add(KebabItem(text = kebabLabels.openFile, onClick = onOpenFileClick))
        add(KebabItem(text = kebabLabels.smartCopy, onClick = ::smartCopyNext))
        add(KebabItem(text = kebabLabels.copyPrompt, onClick = ::copyAiPrompt))
        onComfortClick?.let { comfort ->
            add(KebabItem(text = kebabLabels.displaySettings, onClick = comfort))
        }
        add(
            KebabItem(
                text = kebabLabels.bubbleView,
                leadingIcon = Icons.Filled.ChatBubble,
                status = kebabLabels.bubbleStatus(ReaderComfortState.bubbleMode),
                checked = ReaderComfortState.bubbleMode,
                onClick = { ReaderComfortState.toggleBubbleMode() },
            )
        )
        add(
            KebabItem(
                text = kebabLabels.challenge,
                checked = StudyModeState.challengeMode,
                onClick = { StudyModeState.toggleChallengeMode() },
            )
        )
        add(
            KebabItem(
                text = kebabLabels.lessonDisplay(LessonDisplay.label(StudyModeState.lessonDisplay, fa)),
                onClick = {
                    StudyModeState.setLessonDisplay(
                        if (StudyModeState.lessonDisplay == LessonDisplay.INLINE) {
                            LessonDisplay.POPUP
                        } else {
                            LessonDisplay.INLINE
                        }
                    )
                },
            )
        )
        add(
            KebabItem(
                text = kebabLabels.weightRow(ReaderComfortState.textWeightIdx),
                onClick = { ReaderComfortState.cycleTextWeight() },
            )
        )
        onTogglePageImages?.let { toggle ->
            add(
                KebabItem(
                    text = if (pageImageMode) kebabLabels.showText else kebabLabels.showPageImage,
                    checked = pageImageMode,
                    onClick = toggle,
                )
            )
        }
        onChaptersClick?.let { chapters ->
            add(KebabItem(text = kebabLabels.chapters, onClick = chapters))
        }
        // Delete JSON — only offered when we have sentences for this doc.
        if (sentences.isNotEmpty()) {
            add(
                KebabItem(
                    text = kebabLabels.deleteJson,
                    destructive = true,
                    onClick = { showDeleteConfirm = true },
                )
            )
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = { showImport = true }) {
            Icon(
                imageVector = Icons.Filled.DataObject,
                contentDescription = "ورود فایل ترجمه / درس (JSON)",
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = ::openExtract) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = kebabLabels.extractRange,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        ReaderKebabMenu(
            items = kebabItems,
            tint = tint,
            contentDescription = kebabLabels.menuCd,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف JSON؟", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "تمام ${sentences.size} جملهٔ وارد شده برای این سند حذف می‌شود. برای بازگشت باید دوباره فایل را وارد کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (docKey.isNotBlank()) {
                                    runCatching { store.deleteDocument(docKey) }
                                }
                            }
                            if (docKey.isNotBlank()) BookSlicePayload.clear(context, docKey)
                            sentences = emptyList()
                            checkpoint = BookCheckpoint.EMPTY
                            onSentencesUpdated?.invoke()
                            Toast.makeText(context, "JSON حذف شد", Toast.LENGTH_SHORT).show()
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

    if (showImport) {
        BookJsonImportDialog(
            onDismiss = {
                showImport = false
                importStatus = null
                importFailed = false
            },
            statusMessage = importStatus,
            isError = importFailed,
            onImport = { raw ->
                // Route the ingest pipeline's per-item failures to logcat:
                // "Failed item ID: 27 at subtitles[27]: ..." with the cause.
                // The pipeline itself is pure Kotlin (JVM-safe), so the
                // Android handler is installed here, on the Android side.
                com.example.logic.BookImportLog.handler = { tag, message, cause ->
                    android.util.Log.e(tag, message, cause)
                }
                // What was actually copied for this document, if it is still known.
                val sentSlice = if (docKey.isNotBlank()) BookSlicePayload.load(context, docKey) else null
                val expected = sentSlice?.sentences.orEmpty()
                val expectedFirstId = sentSlice?.firstId ?: checkpoint.nextId

                val result = BookJsonIngest.ingest(
                    raw,
                    expected = expected,
                    expectedFirstId = expectedFirstId,
                )
                if (!result.isSuccess) {
                    importFailed = true
                    val repairInfo = if (result.repairs.isNotEmpty()) {
                        "\nتلاش‌های ترمیم: ${result.repairs.take(3).joinToString(" | ")}"
                    } else ""
                    importStatus = when (result.errorKey) {
                        "ingest_empty" -> "متنی برای ورود پیدا نشد."
                        "ingest_no_entries" -> "جمله‌ای در این JSON نبود.$repairInfo\nنکته: مطمئن شوید خروجی AI کامل کپی شده (ناقص نباشد)."
                        else -> "JSON خوانده نشد — حتی پس از ترمیم خودکار.$repairInfo\nنکته: احتمالاً گیومه‌های داخل ترجمه escape نشده یا خروجی ناقص است. بچ را کوچک‌تر کنید (10-15 جمله)."
                    }
                } else {
                    // Restore the document's own text and locators before merging,
                    // so the reader can match each entry to its printed line.
                    val aligned = BookLessonAligner.align(result.sentences, sentSlice)
                    // mergeDetailed, not merge: a duplicate id carrying a
                    // different sentence is kept under a fresh id rather than
                    // silently replacing the stored one (see BookJsonIngest).
                    val detailed = BookJsonIngest.mergeDetailed(sentences, aligned.sentences)
                    val merged = detailed.sentences
                    sentences = merged
                    checkpoint = BookJsonIngest.checkpointOf(merged)
                    importFailed = false

                    // The exact import accounting, with ids, so a "sentence N
                    // is missing" report can be answered from logcat alone:
                    // what the JSON carried, what was parsed, which positions
                    // were empty, and which duplicate ids moved where.
                    android.util.Log.w(
                        IMPORT_LOG_TAG,
                        "book import doc=$docKey received=${result.receivedEntries} " +
                            "parsed=${result.sentences.size} " +
                            "droppedPositions=${result.droppedPositions} " +
                            "itemErrors=${result.itemErrors} " +
                            "remapped=${detailed.remappedIds} total=${merged.size}",
                    )

                    /*
                     * The pages the batch landed on, named one by one.
                     *
                     * "p. 1 s. 1 تا p. 5 s. 1" was read as a promise of pages 1
                     * to 5, while a chunk of front matter often covers 1, 3 and
                     * 5 only - which looks like two pages went missing. The
                     * label alone (page or chapter) is what the reader groups
                     * by, so listing those is both shorter and true.
                     */
                    val spots = aligned.sentences.mapNotNull { sentence ->
                        sentence.location
                            .takeIf { it.isNotBlank() }
                            ?.replace(Regex("\\s+s\\.\\s*\\d+$"), "")
                    }.distinct()
                    val spotTail = if (spots.size > 6) "…" else ""
                    val where = when {
                        spots.isEmpty() -> ""
                        spots.size == 1 -> " — ${spots.first()}"
                        else -> " — ${spots.take(6).joinToString("، ")}$spotTail"
                    }
                    // A repair is not always a truncation. Escaping a quote or
                    // dropping a trailing comma changes nothing about the
                    // content, and calling that "the JSON was incomplete" made
                    // a healthy batch read as a damaged one. Truncation is the
                    // case that really was cut off, so only that says so.
                    val repaired = when {
                        result.repairs.isEmpty() -> ""
                        result.truncated -> " • خروجی ناقص بود و ترمیم شد"
                        else -> " • JSON ترمیم شد (بدون تغییر در محتوا)"
                    }
                    // Zero-drop accounting in the status line itself: how many
                    // objects the JSON carried, which positions were empty, and
                    // which duplicate ids were re-numbered instead of lost.
                    val countInfo = if (result.receivedEntries != aligned.sentences.size) {
                        " (از ${result.receivedEntries} ورودی)"
                    } else {
                        ""
                    }
                    val dropInfo = if (result.droppedPositions.isNotEmpty()) {
                        val shown = result.droppedPositions.take(6).joinToString("، ")
                        val tail = if (result.droppedPositions.size > 6) "…" else ""
                        " • ${result.droppedPositions.size} ورودی خالی رد شد (موقعیت: $shown$tail)"
                    } else {
                        ""
                    }
                    val remapInfo = if (detailed.remappedIds.isNotEmpty()) {
                        val shown = detailed.remappedIds.take(4)
                            .joinToString("، ") { "${it.requestedId} به ${it.assignedId}" }
                        val tail = if (detailed.remappedIds.size > 4) "…" else ""
                        " • ${detailed.remappedIds.size} id تکراری شماره‌گذاری مجدد شد ($shown$tail)"
                    } else {
                        ""
                    }
                    val errorInfo = if (result.itemErrors.isNotEmpty()) {
                        val shown = result.itemErrors.take(4).joinToString("، ") {
                            it.idSnapshot?.toString() ?: "موقعیت ${it.position}"
                        }
                        val tail = if (result.itemErrors.size > 4) "…" else ""
                        " • ${result.itemErrors.size} ورودی خطا داد و رد شد (id: $shown$tail)"
                    } else {
                        ""
                    }
                    importStatus = "${aligned.sentences.size} جمله وارد شد$countInfo$where، در مجموع ${merged.size} جمله$repaired$dropInfo$remapInfo$errorInfo\n" +
                        BookLessonAligner.summaryFa(aligned.report)

                    // A fully covered chunk is done with; keeping it would only
                    // risk re-anchoring the next, unrelated import against it.
                    if (docKey.isNotBlank() && aligned.report.isClean) {
                        BookSlicePayload.clear(context, docKey)
                    }

                    if (docKey.isNotBlank()) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    store.upsertDocument(
                                        docKey = docKey,
                                        docName = docName,
                                        sourceLanguage = effectiveSourceLanguage.ifBlank {
                                            result.metadata.language.orEmpty()
                                        },
                                        targetLanguage = targetLanguage,
                                        level = cefrLevel,
                                    )
                                    store.saveSentences(docKey, merged)
                                }
                            }
                            // Notify parent (ReaderScreen) to reload inline list
                            onSentencesUpdated?.invoke()
                        }
                    } else {
                        onSentencesUpdated?.invoke()
                    }
                }
            },
        )
    }

    val loadedSource = source
    if (showExtract && loadedSource != null) {
        ChunkExtractionDialog(
            source = loadedSource,
            checkpoint = checkpoint,
            currentPageNumber = currentPageNumber,
            onDismiss = { showExtract = false },
            docKey = docKey,
            bookTitle = docName,
            sourceLanguage = effectiveSourceLanguage,
            targetLanguage = targetLanguage,
            level = cefrLevel,
        )
    }
}

/** Logcat tag for the book import accounting (received / parsed / dropped / remapped). */
private const val IMPORT_LOG_TAG = "BookImport"

/** Shares the currently imported sentences as plain text, for anything outside the app. */
fun shareBookSentences(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(intent, null))
}
