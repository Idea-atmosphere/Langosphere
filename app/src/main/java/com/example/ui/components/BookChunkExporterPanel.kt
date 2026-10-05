package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.logic.BookDocumentSource
import com.example.logic.BookJsonIngest
import com.example.logic.BookPayloadSynthesizer
import com.example.logic.BookPromptTemplates
import com.example.model.BookCheckpoint
import com.example.model.DocumentSlice
import com.example.model.DriftReport
import com.example.model.SliceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/** How the next slice is chosen. */
enum class ChunkRangeMode {
    NEXT_SENTENCES,
    NEXT_UNITS,
    UNIT_RANGE,
    PARAGRAPHS,
}

/**
 * The chunk workflow panel: produce a payload, hand it to a model, bring the
 * answer back.
 *
 * The panel deliberately owns only range-picker state. Ingest, merge and
 * persistence are callbacks, because those touch the session store and belong
 * to the caller's view model — and because keeping them out of here means the
 * range arithmetic can be reasoned about (and unit-tested through
 * [BookDocumentSource]) on its own.
 *
 * Continuation is automatic: [checkpoint] comes from the last ingest, so
 * "next" always means the sentence after the last one the model returned,
 * never a manually re-typed page number.
 */
@Composable
fun BookChunkExporterPanel(
    source: BookDocumentSource.Units?,
    checkpoint: BookCheckpoint,
    promptTemplate: String,
    masterSentenceCount: Int,
    drift: DriftReport?,
    onIngestPayload: (String) -> Unit,
    onExportMasterJson: () -> String,
    modifier: Modifier = Modifier,
    chunkSentences: Int = BookPromptTemplates.DEFAULT_CHUNK_SENTENCES,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(ChunkRangeMode.NEXT_SENTENCES) }
    var unitCountText by remember { mutableStateOf("5") }
    var fromUnitText by remember { mutableStateOf("") }
    var toUnitText by remember { mutableStateOf("") }
    var paragraphThresholdText by remember { mutableStateOf("6") }
    var payload by remember { mutableStateOf<BookPayloadSynthesizer.Payload?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = payload?.text
        if (uri == null || text.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray())
                    }
                }.isSuccess
            }
            toast(if (ok) "فایل ذخیره شد" else "ذخیره نشد")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = onExportMasterJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray())
                    }
                }.isSuccess
            }
            toast(if (ok) "خروجی کامل ذخیره شد" else "ذخیره نشد")
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().decodeToString()
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                toast("فایل خوانده نشد")
            } else {
                onIngestPayload(text)
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "استخراج و واردکردن جملات",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (source == null) {
                    "ابتدا یک PDF یا EPUB باز کنید."
                } else {
                    val unitWord = if (source.kind == SliceKind.PDF_PAGES) "صفحه" else "فصل"
                    "${source.unitCount} $unitWord • جملات ذخیره‌شده: $masterSentenceCount • " +
                        "ادامه از id ${checkpoint.nextId}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = mode == ChunkRangeMode.NEXT_SENTENCES,
                    onClick = { mode = ChunkRangeMode.NEXT_SENTENCES },
                    label = { Text("$chunkSentences جمله بعدی") },
                )
                FilterChip(
                    selected = mode == ChunkRangeMode.NEXT_UNITS,
                    onClick = { mode = ChunkRangeMode.NEXT_UNITS },
                    label = { Text("صفحات بعدی") },
                )
                FilterChip(
                    selected = mode == ChunkRangeMode.UNIT_RANGE,
                    onClick = { mode = ChunkRangeMode.UNIT_RANGE },
                    label = { Text("از X تا Y") },
                )
                if (source?.kind != SliceKind.PDF_PAGES) {
                    FilterChip(
                        selected = mode == ChunkRangeMode.PARAGRAPHS,
                        onClick = { mode = ChunkRangeMode.PARAGRAPHS },
                        label = { Text("پاراگراف") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (mode) {
                ChunkRangeMode.NEXT_SENTENCES -> Unit
                ChunkRangeMode.NEXT_UNITS -> OutlinedTextField(
                    value = unitCountText,
                    onValueChange = { unitCountText = it.filter { char -> char.isDigit() } },
                    label = { Text("تعداد") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ChunkRangeMode.UNIT_RANGE -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = fromUnitText,
                        onValueChange = { fromUnitText = it.filter { char -> char.isDigit() } },
                        label = { Text("از") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = toUnitText,
                        onValueChange = { toUnitText = it.filter { char -> char.isDigit() } },
                        label = { Text("تا") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                ChunkRangeMode.PARAGRAPHS -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = fromUnitText,
                        onValueChange = { fromUnitText = it.filter { char -> char.isDigit() } },
                        label = { Text("فصل") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = paragraphThresholdText,
                        onValueChange = {
                            paragraphThresholdText = it.filter { char -> char.isDigit() }
                        },
                        label = { Text("پاراگراف") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val units = source
                    if (units == null || units.isEmpty) {
                        toast("سندی باز نیست")
                        return@Button
                    }
                    val slice = buildSlice(
                        source = units,
                        mode = mode,
                        checkpoint = checkpoint,
                        chunkSentences = chunkSentences,
                        unitCount = unitCountText.toIntOrNull() ?: 5,
                        fromUnit = fromUnitText.toIntOrNull(),
                        toUnit = toUnitText.toIntOrNull(),
                        paragraphThreshold = paragraphThresholdText.toIntOrNull() ?: 6,
                    )
                    if (slice.isEmpty) {
                        toast("متنی برای این محدوده پیدا نشد")
                        payload = null
                        return@Button
                    }
                    payload = BookPayloadSynthesizer.synthesize(
                        slice = slice,
                        prompt = promptTemplate,
                        startId = checkpoint.nextId,
                        bookName = units.docName,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ساخت پیلود پرامپت")
            }

            val current = payload
            if (current != null && !current.isEmpty) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${current.locationHeader} • ids ${current.firstId}-${current.lastId} " +
                        "(${current.sentenceCount})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(current.text))
                            toast("پیلود کپی شد")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("کپی پیلود")
                    }
                    OutlinedButton(
                        onClick = { saveLauncher.launch(current.fileName) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("دانلود پیلود")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = clipboard.getText()?.text
                        if (text.isNullOrBlank()) {
                            toast("کلیپ‌بورد خالی است")
                        } else {
                            onIngestPayload(text)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("الصاق جواب JSON")
                }
                OutlinedButton(
                    onClick = {
                        openLauncher.launch(
                            arrayOf("application/json", "text/plain", "*/*"),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("باز کردن فایل")
                }
            }

            if (drift != null && !drift.isClean) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = driftMessage(drift),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (masterSentenceCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        exportLauncher.launch(
                            (source?.docName?.takeIf { it.isNotBlank() } ?: "book") + "_master.json",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("خروجی JSON کامل ($masterSentenceCount)")
                }
            }
        }
    }
}

/**
 * Turns the picker state into a slice. Kept as a plain function so the range
 * arithmetic is readable in one place and does not hide inside a lambda.
 */
private fun buildSlice(
    source: BookDocumentSource.Units,
    mode: ChunkRangeMode,
    checkpoint: BookCheckpoint,
    chunkSentences: Int,
    unitCount: Int,
    fromUnit: Int?,
    toUnit: Int?,
    paragraphThreshold: Int,
): DocumentSlice {
    val cursor = BookJsonIngest.nextCursor(checkpoint)
    return when (mode) {
        ChunkRangeMode.NEXT_SENTENCES -> BookDocumentSource.sliceNext(
            source = source,
            cursor = cursor,
            maxSentences = chunkSentences,
        )
        ChunkRangeMode.NEXT_UNITS -> BookDocumentSource.sliceNextUnits(
            source = source,
            cursor = cursor,
            unitCount = unitCount.coerceAtLeast(1),
        )
        ChunkRangeMode.UNIT_RANGE -> {
            val from = (fromUnit ?: (cursor.unitIndex + 1)).coerceAtLeast(1)
            val to = (toUnit ?: from).coerceAtLeast(from)
            BookDocumentSource.sliceUnitRange(source, from, to)
        }
        ChunkRangeMode.PARAGRAPHS -> BookDocumentSource.sliceParagraphs(
            source = source,
            chapterNumber = (fromUnit ?: (cursor.unitIndex + 1)).coerceAtLeast(1),
            fromParagraphNumber = 1,
            paragraphThreshold = paragraphThreshold.coerceAtLeast(1),
            maxSentences = chunkSentences,
        )
    }
}

/** A drift warning that says what happened and where, not just that it happened. */
private fun driftMessage(drift: DriftReport): String {
    val parts = ArrayList<String>()
    parts += "انتظار: ${drift.expectedCount} • دریافت: ${drift.receivedCount}"
    if (drift.missingIds.isNotEmpty()) {
        parts += "جاافتاده: ${drift.missingIds.take(8).joinToString(", ")}"
    }
    if (drift.duplicatedIds.isNotEmpty()) {
        parts += "تکراری: ${drift.duplicatedIds.take(8).joinToString(", ")}"
    }
    if (drift.unexpectedIds.isNotEmpty()) {
        parts += "اضافی: ${drift.unexpectedIds.take(8).joinToString(", ")}"
    }
    if (drift.mismatches.isNotEmpty()) {
        parts += "متن متفاوت در: ${drift.mismatches.take(5).joinToString(", ") { it.id.toString() }}"
    }
    drift.firstDriftId?.let { parts += "شروع انحراف از id $it" }
    return parts.joinToString(" — ")
}
