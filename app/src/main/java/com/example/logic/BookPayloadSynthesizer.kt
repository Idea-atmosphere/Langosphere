package com.example.logic

import com.example.model.DocumentSlice
import com.example.model.SliceKind

/**
 * Assembles the text the user pastes into an AI chat: the sentence-level book
 * prompt from settings, the `LOCATION:` header for this slice, and the
 * numbered sentences themselves.
 *
 * Keeping this in one place matters because the three parts have to agree. The
 * prompt promises "sentence n of my list is entry n of your answer" and that
 * ids continue across chunks; the header states where the slice sits in the
 * book; the numbering is what the drift guard later checks the reply against.
 * If any one of them is generated ad hoc at a call site, the guard starts
 * reporting drift that is not there.
 */
object BookPayloadSynthesizer {

    /** A ready-to-send payload plus what the app needs to remember about it. */
    data class Payload(
        val text: String,
        val locationHeader: String,
        val firstId: Int,
        val lastId: Int,
        val sentenceCount: Int,
        /** Suggested filename for "Download payload". */
        val fileName: String,
    ) {
        val isEmpty: Boolean get() = sentenceCount == 0
    }

    /**
     * @param slice the extracted sentences, from [DocumentChunker].
     * @param prompt the resolved prompt from [BookPromptTemplates.buildPrompt].
     * @param startId the id the first sentence must carry, normally
     *   `checkpoint.nextId`, so chunk 2 continues chunk 1.
     * @param chunkLabel optional "chunk 2" suffix for the location header.
     */
    fun synthesize(
        slice: DocumentSlice,
        prompt: String,
        startId: Int = 1,
        chunkLabel: String = "",
        bookName: String = "",
    ): Payload {
        if (slice.sentences.isEmpty()) {
            return Payload("", "", startId, startId, 0, defaultFileName(bookName, startId, startId))
        }

        val firstId = startId.coerceAtLeast(1)
        val lastId = firstId + slice.sentences.size - 1
        val header = buildHeader(slice, chunkLabel)

        val body = buildString {
            // The prompt's own trailing placeholder is replaced by the real
            // text, so the model never sees the instruction twice.
            appendLine(prompt.replace("PASTE BOOK SENTENCES HERE", "").trimEnd())
            appendLine()
            appendLine(header)
            appendLine("IDS: start numbering at $firstId and end at $lastId (${slice.sentences.size} sentences).")
            appendLine(
                "FIRST SENTENCE ORDINAL ON THIS ${unitWord(slice.kind).uppercase()}: " +
                    "${slice.sentences.first().sentenceInUnit}",
            )
            appendLine()
            // LINE-BY-LINE: each sentence on its own line with ID — crucial for sync
            // User request: "خط به خط موقع کپی جملاتو جدا کن"
            slice.sentences.forEachIndexed { offset, sentence ->
                appendLine("${firstId + offset}. ${sentence.text.trim()}")
            }
        }.trimEnd()

        return Payload(
            text = body,
            locationHeader = header,
            firstId = firstId,
            lastId = lastId,
            sentenceCount = slice.sentences.size,
            fileName = defaultFileName(bookName, firstId, lastId),
        )
    }

    /**
     * The `LOCATION:` line. A slice that covers part of a page still names the
     * page it came from, and a chunk label is appended when a chapter is walked
     * in several passes, which produces the `chapter 3, chunk 2` form.
     */
    fun buildHeader(slice: DocumentSlice, chunkLabel: String = ""): String {
        val base = slice.locationHeader.ifBlank {
            DocumentChunker.locationHeader(
                kind = slice.kind,
                firstUnit = slice.firstUnitIndex,
                lastUnit = slice.lastUnitIndex,
                firstSentence = slice.sentences.firstOrNull()?.sentenceInUnit ?: 1,
            )
        }
        val label = chunkLabel.trim()
        return if (label.isEmpty()) base else "$base, $label"
    }

    private fun unitWord(kind: SliceKind): String = when (kind) {
        SliceKind.PDF_PAGES -> "page"
        SliceKind.EPUB_SPINE, SliceKind.EPUB_PARAGRAPHS -> "chapter"
    }

    private fun defaultFileName(bookName: String, firstId: Int, lastId: Int): String {
        val stem = bookName.ifBlank { "book" }
            .substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
            .trim('_')
            .take(48)
            .ifEmpty { "book" }
        return "${stem}_prompt_${firstId}-${lastId}.txt"
    }
}
