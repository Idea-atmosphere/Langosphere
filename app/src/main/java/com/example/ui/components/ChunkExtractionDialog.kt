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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.logic.BookDocumentSource
import com.example.logic.BookJsonIngest
import com.example.logic.BookPromptTemplates
import com.example.logic.BookSlicePayload
import com.example.model.BookCheckpoint
import com.example.model.DocumentSlice
import com.example.model.SliceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Copy the pages I choose, in a shape the AI cannot misread."
 *
 * The user picks a page (or chapter) range and gets that range on the
 * clipboard, ready to paste under the CEFR prompt from the AI teaching
 * settings.
 *
 * ## Why the copied text is no longer bare prose
 *
 * The prompts promise the model a numbered list plus a `LOCATION:` and an
 * `IDS:` line, but this dialog used to copy plain sentences with nothing
 * around them. The model then invented its own ids and its own `location`
 * strings, so the reader could not put a translation back under the line it
 * belonged to, and the per-sentence lesson button looked broken.
 *
 * Now [BookSlicePayload] renders the chunk with a short binding header and one
 * `[id | p. N s. M] sentence` line per sentence, and the exact slice is saved
 * against [docKey]. Import then re-anchors the answer to that saved slice, so
 * the reader matches on the document's own text instead of the model's
 * paraphrase of it.
 *
 * ## Why it still goes through [BookDocumentSource]
 *
 * Raw PDF text layers are not readable: words break as `under-\nstand` at line
 * ends and every page repeats its running header and footer. Slicing through
 * the document source rejoins those words and drops the repeated furniture.
 *
 * ## Auto-staged range
 *
 * The range opens on the continuation of [checkpoint] — the point the last
 * imported lesson JSON reached — so the common case is one tap.
 */
@Composable
fun ChunkExtractionDialog(
    source: BookDocumentSource.Units,
    checkpoint: BookCheckpoint,
    currentPageNumber: Int,
    onDismiss: () -> Unit,
    docKey: String = "",
    bookTitle: String = "",
    sourceLanguage: String = "",
    targetLanguage: String = "Persian",
    level: String = BookPromptTemplates.DEFAULT_LEVEL,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val unitWord = if (source.kind == SliceKind.PDF_PAGES) "صفحه" else "فصل"
    val unitCount = maxOf(1, source.unitCount)
    val stagedFrom = remember(checkpoint, source) {
        val next = BookJsonIngest.nextCursor(checkpoint).unitIndex + 1
        next.coerceIn(1, unitCount)
    }
    // Detected once from the document itself, so the header tells the model
    // which language it is reading even when the caller passed nothing.
    val detectedLanguage = remember(source) {
        BookSlicePayload.detectLanguage(
            source.units.asSequence().filter { it.isNotBlank() }.take(3).joinToString(" "),
        )
    }
    val effectiveSourceLanguage = sourceLanguage.ifBlank { detectedLanguage }

    var fromText by remember { mutableStateOf(stagedFrom.toString()) }
    var toText by remember {
        mutableStateOf((stagedFrom + 4).coerceAtMost(unitCount).toString())
    }
    var extracted by remember { mutableStateOf("") }
    var plain by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun from(): Int = (fromText.toIntOrNull() ?: stagedFrom).coerceIn(1, unitCount)
    fun to(): Int = (toText.toIntOrNull() ?: from()).coerceIn(from(), unitCount)

    /**
     * Extracts the current range and remembers it, so copy, save and import
     * all describe exactly the same sentences.
     */
    fun build(): String? {
        val slice = BookDocumentSource.sliceUnitRange(source, from(), to())
        if (slice.isEmpty) {
            message = "در این محدوده متنی پیدا نشد."
            extracted = ""
            plain = ""
            return null
        }
        val firstId = checkpoint.nextId
        val payload = BookSlicePayload.build(
            slice = slice,
            firstId = firstId,
            bookTitle = bookTitle.ifBlank { source.docName },
            sourceLanguage = effectiveSourceLanguage,
            targetLanguage = targetLanguage,
            level = level,
        )
        if (payload.isBlank()) {
            message = "در این محدوده متنی پیدا نشد."
            extracted = ""
            plain = ""
            return null
        }
        // Persisted before the user pastes anything, so the answer can be
        // re-anchored later even if the app is closed in between.
        BookSlicePayload.save(context, docKey, slice, firstId)
        extracted = payload
        plain = plainTextOf(slice)
        val lastId = firstId + slice.sentenceCount - 1
        message = "${slice.sentenceCount} جمله • شناسه $firstId تا $lastId • " +
            "${slice.locationHeader.removePrefix("LOCATION: ")} • زبان: ${effectiveSourceLanguage.ifBlank { "نامشخص" }}"
        return payload
    }

    // Re-extract whenever the range changes, so the preview and the copy button
    // never describe a stale range.
    fun invalidate() {
        extracted = ""
        plain = ""
        message = null
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = extracted
        if (uri == null || text.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray())
                    }
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (saved) "متن ذخیره شد" else "ذخیره نشد",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کپی متن $unitWord‌های انتخابی") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "این سند $unitCount $unitWord دارد. بازه را انتخاب کن تا متن تمیزشده با شمارهٔ جمله و نشانی صفحه کپی شود؛ " +
                        "همان را زیر پرامپت «تنظیمات › پرامپت‌ها» بگذار و خروجی JSON را برگردان.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = {
                            fromText = it.filter { char -> char.isDigit() }
                            invalidate()
                        },
                        label = { Text("از $unitWord") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = {
                            toText = it.filter { char -> char.isDigit() }
                            invalidate()
                        },
                        label = { Text("تا $unitWord") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {
                            toText = (from() + 4).coerceAtMost(unitCount).toString()
                            invalidate()
                        },
                        label = { Text("+5") },
                    )
                    AssistChip(
                        onClick = {
                            toText = (from() + 9).coerceAtMost(unitCount).toString()
                            invalidate()
                        },
                        label = { Text("+10") },
                    )
                    AssistChip(
                        onClick = {
                            val page = currentPageNumber.coerceIn(1, unitCount)
                            fromText = page.toString()
                            toText = page.toString()
                            invalidate()
                        },
                        label = { Text("$unitWord فعلی") },
                    )
                }

                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (extracted.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = extracted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (plain.isNotBlank()) {
                                clipboard.setText(AnnotatedString(plain))
                                Toast.makeText(context, "متن ساده کپی شد", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("کپی متن ساده (بدون شماره)")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val stem = source.docName.ifBlank { "book" }
                                .substringBeforeLast('.')
                                .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
                                .trim('_')
                                .ifBlank { "book" }
                            saveLauncher.launch("${stem}_${from()}-${to()}.txt")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("ذخیره در فایل متنی")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = extracted.ifBlank { build().orEmpty() }
                    if (text.isBlank()) return@TextButton
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(
                        context,
                        "متن $unitWord ${from()} تا ${to()} با شمارهٔ جمله کپی شد",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text("کپی برای هوش مصنوعی")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { build() }) { Text("پیش‌نمایش") }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        },
    )
}

/**
 * Plain fallback: one sentence per line, blank line between pages/chapters.
 * Kept for users who paste into a tool of their own and do not want the
 * bracketed ids.
 */
private fun plainTextOf(slice: DocumentSlice): String =
    slice.sentences
        .groupBy { it.unitIndex }
        .values
        .joinToString("\n\n") { unit ->
            unit.joinToString("\n") { sentence -> sentence.text.trim() }.trim()
        }
        .trim()
