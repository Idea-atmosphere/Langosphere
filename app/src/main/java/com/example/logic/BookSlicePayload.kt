package com.example.logic

import android.content.Context
import com.example.model.DocumentSlice
import com.example.model.SliceKind
import com.example.model.SourceSentence

/**
 * Builds the text the user copies into the AI, and remembers exactly what was
 * sent so the JSON that comes back can be re-anchored to the document.
 *
 * Why this exists
 * ---------------
 * The book prompts promise the model an anchored, numbered list. The copy
 * button used to send none of that - it sent bare sentences separated by
 * newlines. The model therefore invented its own ids and its own `location`
 * strings, and the reader, which places a returned entry by page + text, could
 * not place anything: the translations existed but fell into the "other
 * sentences" bucket instead of appearing under their line, and the
 * per-sentence lesson button looked dead.
 *
 * What the payload carries now
 * ----------------------------
 * Only the anchors, nothing else. The teaching rules live in the prompt in
 * Settings -> AI teaching & learning, where they belong; repeating them in
 * every copied chunk wasted tokens, drowned the text, and let two copies of
 * the contract drift apart. One header line plus one marker per sentence:
 *
 * ```
 * @LANGO v3 | ids 21-35 | pages 12-13 | src=English | dst=Persian | B1
 * [21|p12s1] The house on the hill had been empty for years.
 * [22|p12s2] No one remembered who had built it.
 * ```
 *
 * `[id|pXsY]` is deliberately the shortest thing that still pins a sentence to
 * a page: `21` is the id to echo back, `p12s1` is page 12, sentence 1 of that
 * page (`c` instead of `p` for EPUB chapters). Even a model that ignores the
 * header usually copies a bracketed prefix correctly, and if it does not, the
 * id-based re-anchoring in [BookLessonAligner] repairs it, because the exact
 * slice is persisted here per document.
 */
object BookSlicePayload {

    const val VERSION = 3

    private const val PREFS = "book_slice_payload_v2"
    private const val FIELD_SEP = "\u0001"
    private const val ROW_SEP = "\u0002"

    /** The slice that was handed to the AI, kept until its JSON comes back. */
    data class SentSlice(
        val docKey: String,
        val kind: SliceKind,
        val firstId: Int,
        val sentences: List<SourceSentence>,
        val savedAt: Long = System.currentTimeMillis(),
    ) {
        val count: Int get() = sentences.size
        val lastId: Int get() = if (sentences.isEmpty()) firstId else firstId + sentences.size - 1
        fun idOf(index: Int): Int = firstId + index
        fun byId(): Map<Int, SourceSentence> =
            sentences.mapIndexed { index, sentence -> idOf(index) to sentence }.toMap()
    }

    // ---------------------------------------------------------------- payload

    /**
     * Renders the copy payload: one header line, then one anchored line per
     * sentence. No rules and no JSON schema - those come from the prompt the
     * user pasted once at the top of the chat.
     */
    fun build(
        slice: DocumentSlice,
        firstId: Int,
        bookTitle: String,
        sourceLanguage: String,
        targetLanguage: String,
        level: String,
    ): String {
        if (slice.isEmpty) return ""
        val lastId = firstId + slice.sentenceCount - 1
        val builder = StringBuilder()

        builder.append("@LANGO v").append(VERSION)
        builder.append(" | ids ").append(firstId).append('-').append(lastId)
        builder.append(" | ").append(rangeLabel(slice))
        builder.append(" | src=").append(sourceLanguage.ifBlank { "auto" })
        builder.append(" | dst=").append(targetLanguage.ifBlank { "Persian" })
        builder.append(" | ").append(level.ifBlank { BookPromptTemplates.DEFAULT_LEVEL })
        if (bookTitle.isNotBlank()) builder.append(" | ").append(bookTitle.trim().take(60))
        if (slice.isLastSlice) builder.append(" | last")
        builder.appendLine()

        slice.sentences.forEachIndexed { offset, sentence ->
            val id = firstId + offset
            builder.append('[').append(id).append('|')
                .append(marker(slice.kind, sentence))
                .append("] ")
                .appendLine(sentence.text.trim())
        }

        builder.append("@END ").append(firstId).append('-').append(lastId)
        return builder.toString().trimEnd()
    }

    /** `p12s1` for a PDF page, `c4s12` for an EPUB chapter or section. */
    fun marker(kind: SliceKind, sentence: SourceSentence): String {
        val prefix = if (kind == SliceKind.PDF_PAGES) "p" else "c"
        return "$prefix${sentence.unitIndex + 1}s${sentence.sentenceInUnit}"
    }

    /** Human range for the header line, e.g. `pages 12-13` or `chapter 4`. */
    private fun rangeLabel(slice: DocumentSlice): String {
        val first = slice.firstUnitIndex + 1
        val last = slice.lastUnitIndex + 1
        val noun = if (slice.kind == SliceKind.PDF_PAGES) "page" else "chapter"
        return if (first == last) "$noun $first" else "${noun}s $first-$last"
    }

    /**
     * Parses a `p12s3` / `c4s12` marker back into a page (or chapter) number
     * and a sentence ordinal. Used when a reply echoes the marker but garbles
     * the human-readable `location`.
     */
    fun parseMarker(raw: String): Pair<Int, Int>? {
        val match = MARKER.find(raw.trim().lowercase()) ?: return null
        val unit = match.groupValues[2].toIntOrNull() ?: return null
        val ordinal = match.groupValues[3].toIntOrNull() ?: return null
        return unit to ordinal
    }

    private val MARKER = Regex("\\b([pc])\\.?\\s?(\\d{1,5})\\s?s\\.?\\s?(\\d{1,4})\\b")

    // ------------------------------------------------------------ persistence

    /** Stores the slice that was just copied, so import can re-anchor it. */
    fun save(context: Context, docKey: String, slice: DocumentSlice, firstId: Int) {
        if (docKey.isBlank() || slice.isEmpty) return
        val encoded = buildString {
            append(slice.kind.name).append(FIELD_SEP)
            append(firstId).append(FIELD_SEP)
            append(System.currentTimeMillis())
            slice.sentences.forEach { sentence ->
                append(ROW_SEP)
                append(sentence.index).append(FIELD_SEP)
                append(sentence.unitIndex).append(FIELD_SEP)
                append(sentence.sentenceInUnit).append(FIELD_SEP)
                append(sentence.charStart).append(FIELD_SEP)
                append(sentence.charEnd).append(FIELD_SEP)
                append(sentence.location.replace(FIELD_SEP, " ").replace(ROW_SEP, " ")).append(FIELD_SEP)
                append(sentence.text.replace(FIELD_SEP, " ").replace(ROW_SEP, " "))
            }
        }
        prefs(context).edit().putString(docKey, encoded).apply()
    }

    /** Restores the last copied slice for a document, or null if there is none. */
    fun load(context: Context, docKey: String): SentSlice? {
        if (docKey.isBlank()) return null
        val raw = prefs(context).getString(docKey, null) ?: return null
        val rows = raw.split(ROW_SEP)
        if (rows.size < 2) return null
        val head = rows.first().split(FIELD_SEP)
        val kind = runCatching { SliceKind.valueOf(head.getOrElse(0) { "" }) }.getOrNull() ?: return null
        val firstId = head.getOrNull(1)?.toIntOrNull() ?: return null
        val savedAt = head.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
        val sentences = rows.drop(1).mapNotNull { row ->
            val parts = row.split(FIELD_SEP)
            if (parts.size < 7) return@mapNotNull null
            SourceSentence(
                index = parts[0].toIntOrNull() ?: return@mapNotNull null,
                unitIndex = parts[1].toIntOrNull() ?: 0,
                sentenceInUnit = parts[2].toIntOrNull() ?: 1,
                charStart = parts[3].toIntOrNull() ?: 0,
                charEnd = parts[4].toIntOrNull() ?: 0,
                location = parts[5],
                text = parts.drop(6).joinToString(FIELD_SEP),
            )
        }
        if (sentences.isEmpty()) return null
        return SentSlice(docKey, kind, firstId, sentences, savedAt)
    }

    fun clear(context: Context, docKey: String) {
        if (docKey.isBlank()) return
        prefs(context).edit().remove(docKey).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------ language detection

    private val LATIN_HINTS: List<Pair<String, Set<String>>> = listOf(
        "English" to setOf("the", "and", "that", "with", "which", "was", "were", "this", "there", "would", "about"),
        "French" to setOf("les", "des", "une", "que", "pour", "dans", "avec", "est", "pas", "elle"),
        "Spanish" to setOf("los", "las", "una", "que", "para", "con", "por", "pero", "como", "est\u00e1"),
        "German" to setOf("und", "der", "die", "das", "nicht", "ist", "ein", "eine", "mit", "sich"),
        "Italian" to setOf("che", "non", "per", "una", "con", "come", "pi\u00f9", "sono", "nella", "gli"),
        "Portuguese" to setOf("que", "n\u00e3o", "uma", "para", "com", "como", "mais", "dos", "est\u00e1", "pelo"),
        "Turkish" to setOf("bir", "ve", "bu", "i\u00e7in", "ile", "olarak", "daha", "gibi", "kadar", "ama"),
    )

    /**
     * Best-effort source-language detection from a text sample.
     *
     * Script ranges settle the non-Latin cases outright; for Latin scripts a
     * small stop-word vote is enough to label the book. The result is only a
     * label for the prompt header and the session record, so an occasional
     * wrong guess degrades gracefully - and the user can still override it.
     */
    fun detectLanguage(sample: String): String {
        val text = sample.take(4000)
        if (text.isBlank()) return ""

        var arabicLike = 0
        var persianOnly = 0
        var cyrillic = 0
        var greek = 0
        var hebrew = 0
        var han = 0
        var kana = 0
        var hangul = 0
        var devanagari = 0
        var thai = 0
        var latin = 0

        for (ch in text) {
            when (ch.code) {
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF -> {
                    arabicLike++
                    if (ch in "\u067E\u0686\u0698\u06AF\u06CC\u06A9") persianOnly++
                }
                in 0x0400..0x04FF -> cyrillic++
                in 0x0370..0x03FF -> greek++
                in 0x0590..0x05FF -> hebrew++
                in 0x4E00..0x9FFF -> han++
                in 0x3040..0x30FF -> kana++
                in 0xAC00..0xD7AF -> hangul++
                in 0x0900..0x097F -> devanagari++
                in 0x0E00..0x0E7F -> thai++
                in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F -> latin++
            }
        }

        val strongest = listOf(
            "arabic" to arabicLike,
            "cyrillic" to cyrillic,
            "greek" to greek,
            "hebrew" to hebrew,
            "han" to han,
            "kana" to kana,
            "hangul" to hangul,
            "devanagari" to devanagari,
            "thai" to thai,
            "latin" to latin,
        ).maxByOrNull { it.second } ?: return ""
        if (strongest.second == 0) return ""

        return when (strongest.first) {
            "arabic" -> if (persianOnly * 20 > arabicLike) "Persian" else "Arabic"
            "cyrillic" -> "Russian"
            "greek" -> "Greek"
            "hebrew" -> "Hebrew"
            "kana" -> "Japanese"
            "han" -> if (kana > 0) "Japanese" else "Chinese"
            "hangul" -> "Korean"
            "devanagari" -> "Hindi"
            "thai" -> "Thai"
            else -> detectLatinLanguage(text)
        }
    }

    private fun detectLatinLanguage(text: String): String {
        val words = text.lowercase()
            .split(Regex("[^\\p{L}']+"))
            .filter { it.length > 1 }
        if (words.isEmpty()) return "English"
        val scores = LATIN_HINTS.map { (language, hints) ->
            language to words.count { it in hints }
        }
        val best = scores.maxByOrNull { it.second }
        return if (best == null || best.second == 0) "English" else best.first
    }
}
