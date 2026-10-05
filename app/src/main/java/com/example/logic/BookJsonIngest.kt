package com.example.logic

import com.example.model.BookCheckpoint
import com.example.model.BookSentence
import com.example.model.DriftReport
import com.example.model.JsonLesson
import com.example.model.JsonWord
import com.example.model.SentenceMismatch
import com.example.model.SourceSentence
import kotlin.math.max

/**
 * Reads an AI reply for the BOOK pipeline: repair, parse, validate against
 * what was actually sent, work out where the document stopped, and merge the
 * batch into the running session.
 *
 * This is separate from the video subtitle parser on purpose. The two share the
 * `subtitles[]` envelope, but a book batch has to answer questions a subtitle
 * file never does — "did the model quietly merge sentences 7 and 8?", "which
 * page do we continue from?", "is this chunk already in the session?" — and
 * the video path is stable and must not be destabilised to answer them.
 *
 * Pure Kotlin (via [JsonRepairEngine]), so every branch below is unit-tested
 * without an Android runtime.
 */
object BookJsonIngest {

    /** Metadata of an ingested batch, as the model reported it. */
    data class IngestMetadata(
        val source: String = "book",
        val bookTitle: String = "",
        val language: String = "",
        val targetLanguage: String = "",
        val level: String = "",
        val description: String = "",
    )

    /** Everything one ingest produced. */
    data class IngestResult(
        val sentences: List<BookSentence>,
        val metadata: IngestMetadata,
        val checkpoint: BookCheckpoint,
        val drift: DriftReport,
        val repairs: List<String>,
        val truncated: Boolean,
        val errorKey: String? = null,
        /**
         * Raw objects found in `subtitles[]` before anything was read.
         *
         * Together with [sentences] and [droppedPositions] this is what makes
         * a silent loss impossible to miss: `receivedEntries - sentences.size`
         * must equal `droppedPositions.size`, and the import status line names
         * both numbers, so \"41 in, 41 kept\" reads exactly like that.
         */
        val receivedEntries: Int = sentences.size,
        /**
         * 1-based positions in `subtitles[]` that carried nothing importable
         * (no original, no translation, no note, no lesson, no words) and were
         * therefore not turned into sentences. Anything with any signal at all
         * is kept — see [BookJsonIngest.readSentence].
         */
        val droppedPositions: List<Int> = emptyList(),
        /**
         * Entries that threw while being read. Reading one bad entry must
         * never abort — or silently skip inside — the other forty; each
         * failure is named here, in the repairs list and on the platform log.
         */
        val itemErrors: List<ItemError> = emptyList(),
    ) {
        val isSuccess: Boolean get() = errorKey == null && sentences.isNotEmpty()
        val wasRepaired: Boolean get() = repairs.isNotEmpty()

        /**
         * True when every raw object became a sentence: nothing dropped as
         * empty and nothing failed to read. The parity tripwire in
         * [BookJsonIngest.ingest] additionally verifies that kept + dropped +
         * failed accounts for every raw object, so a miscount can only ever
         * be loud, never silent.
         */
        val isLossless: Boolean
            get() = errorKey == null && droppedPositions.isEmpty() && itemErrors.isEmpty()

        companion object {
            fun failure(errorKey: String, repairs: List<String> = emptyList()) = IngestResult(
                sentences = emptyList(),
                metadata = IngestMetadata(),
                checkpoint = BookCheckpoint.EMPTY,
                drift = DriftReport.CLEAN,
                repairs = repairs,
                truncated = false,
                errorKey = errorKey,
            )
        }
    }

    /**
     * One entry of `subtitles[]` that could not be read.
     *
     * @param position 1-based position in `subtitles[]`.
     * @param idSnapshot the id the entry claimed, when one could be read.
     * @param message the failure, in one line.
     */
    data class ItemError(
        val position: Int,
        val idSnapshot: Int?,
        val message: String,
    )

    /** Similarity below which a returned sentence counts as "not what we sent". */
    private const val MISMATCH_THRESHOLD = 0.55

    /**
     * Ingests a raw reply.
     *
     * @param raw whatever the user pasted or dropped in — fences, prose and
     *   truncation included.
     * @param expected the sentences that were sent, when known. Supplying them
     *   is what turns the ingest into a drift check instead of blind trust.
     * @param expectedFirstId the id the first sent sentence was given, so a
     *   model that restarted numbering at 1 can still be matched up.
     */
    fun ingest(
        raw: String,
        expected: List<SourceSentence> = emptyList(),
        expectedFirstId: Int = 1,
    ): IngestResult {
        if (raw.isBlank()) return IngestResult.failure("ingest_empty")

        val (parsed, repair) = JsonRepairEngine.repairAndParse(raw)
        var root: JsonValue? = null
        var entries: List<JsonValue.Obj> = emptyList()
        var allRepairs = repair.repairs.toMutableList()
        var wasTruncated = repair.truncated

        if (parsed != null) {
            root = when (parsed) {
                is JsonValue.Arr -> JsonValue.Obj(mapOf("subtitles" to parsed))
                else -> parsed
            }
            entries = (root["subtitles"] ?: root["sentences"] ?: root["entries"])
                ?.asList()
                ?.filterIsInstance<JsonValue.Obj>()
                .orEmpty()
        }

        // Fallback: the whole-document parse failed (or found no entries), but
        // the text may still hold individually fine objects - one broken entry
        // must not cost the other forty. Object-by-object salvage runs first
        // because it keeps full entries (id, location, lesson, words); the
        // regex pass below it is the last resort and only recovers
        // english+translation. Either way the result is flagged as partial.
        if (parsed == null || entries.isEmpty()) {
            val salvaged = tryObjectSalvage(raw, expectedFirstId)
            val recovered: List<BookSentence>
            if (salvaged.isNotEmpty()) {
                allRepairs += "بازیابی اضطراری شی‌ءبه‌شی‌ء — ${salvaged.size} جمله از متن خراب استخراج شد. چون کل سند یکجا خوانده نشد، در صورت امکان خروجی JSON سالم از AI بگیرید."
                recovered = salvaged
            } else {
                val regexResult = tryRegexFallback(raw, expectedFirstId)
                if (regexResult.isEmpty()) {
                    // Nothing recovered at all; fall through to the failure below.
                    if (parsed == null) return IngestResult.failure("ingest_unparseable", allRepairs)
                    return IngestResult.failure("ingest_no_entries", allRepairs)
                }
                allRepairs += "بازیابی اضطراری با regex — ${regexResult.size} جمله از متن خراب استخراج شد. این روش معادل parse کامل نیست و ممکن است درس، واژگان، تلفظ و simplified از دست رفته باشد. در صورت امکان خروجی JSON سالم از AI بگیرید."
                allRepairs += "هشدار: تطبیق ممکن است ناقص باشد، چون فقط english و translation بازیابی شد."
                recovered = regexResult
            }
            wasTruncated = true
            // Build minimal root for metadata
            val meta = IngestMetadata(
                source = "book",
                targetLanguage = "Persian",
                level = "B1"
            )
            val normalized = renumberIfNeeded(recovered, expected, expectedFirstId)
            val drift = if (expected.isEmpty()) countOnlyDrift(normalized) else driftReport(expected, normalized, expectedFirstId)
            return IngestResult(
                sentences = normalized,
                metadata = meta,
                checkpoint = checkpointOf(normalized),
                drift = drift,
                repairs = allRepairs,
                truncated = wasTruncated,
            )
        }

        if (parsed == null) return IngestResult.failure("ingest_unparseable", allRepairs)
        if (entries.isEmpty()) return IngestResult.failure("ingest_no_entries", allRepairs)

        val metadata = readMetadata(root!!)
        // Zero-drop rule: every object in `subtitles[]` must either become a
        // sentence or be named here. A long entry (the 300+ character sentence
        // 27 of the reported sample) is never a reason to skip - only an
        // object with no usable field at all is dropped, and its 1-based
        // position is recorded so the status line and logcat can point at it.
        val sentences = ArrayList<BookSentence>(entries.size)
        val droppedPositions = ArrayList<Int>()
        val itemErrors = ArrayList<ItemError>()
        entries.forEachIndexed { position, entry ->
            try {
                val sentence = readSentence(
                    entry,
                    fallbackId = expectedFirstId + position,
                    defaultLevel = metadata.level,
                )
                if (sentence == null) droppedPositions += position + 1 else sentences += sentence
            } catch (error: Exception) {
                // One bad entry aborts nothing: it is named here, in the
                // repairs the status line shows, and on the platform log with
                // its id, and the loop carries on with the next entry.
                val idSnapshot = try {
                    entry.get("id")?.asInt()
                } catch (_: Exception) {
                    null
                }
                itemErrors += ItemError(
                    position = position + 1,
                    idSnapshot = idSnapshot,
                    message = error.message.orEmpty().take(200),
                )
                BookImportLog.e(
                    "Import",
                    "Failed item ID: ${idSnapshot ?: "?"} at subtitles[${position + 1}]: ${error.message}",
                    error,
                )
            }
        }
        if (droppedPositions.isNotEmpty()) {
            val shown = droppedPositions.take(8).joinToString("، ")
            val tail = if (droppedPositions.size > 8) "…" else ""
            allRepairs += "${droppedPositions.size} ورودی هیچ محتوای قابل واردی نداشت و رد شد " +
                "(موقعیت در JSON: $shown$tail). بقیهٔ ورودی‌ها کامل وارد شدند."
        }
        if (itemErrors.isNotEmpty()) {
            val shown = itemErrors.take(8).joinToString("، ") {
                it.idSnapshot?.toString() ?: "موقعیت ${it.position}"
            }
            val tail = if (itemErrors.size > 8) "…" else ""
            allRepairs += "${itemErrors.size} ورودی هنگام خواندن خطا داد و رد شد (id: $shown$tail). " +
                "جزئیات در logcat با تگ Import ثبت شد."
        }
        if (sentences.isEmpty()) return IngestResult.failure("ingest_no_entries", allRepairs)

        val normalized = renumberIfNeeded(sentences, expected, expectedFirstId)
        // Strict parity tripwire: kept + dropped + failed must account for
        // every raw object. This holds by construction (nothing else in this
        // function removes an entry), so a violation is reported loudly -
        // repairs, log, and status line - rather than thrown: throwing would
        // turn a 40-of-41 partial success into a 0-of-41 crash, which helps
        // nobody. The tests assert the same invariant directly.
        val accounted = normalized.size + droppedPositions.size + itemErrors.size
        if (accounted != entries.size) {
            val message = "Parity failure: received ${entries.size} objects but kept " +
                "${normalized.size}, dropped ${droppedPositions.size}, " +
                "failed ${itemErrors.size}"
            allRepairs += "$message — لطفاً این JSON را برای بررسی نگه دارید."
            BookImportLog.e("Import", "$message (positions=$droppedPositions errors=$itemErrors)")
        }
        val drift = if (expected.isEmpty()) {
            countOnlyDrift(normalized)
        } else {
            driftReport(expected, normalized, expectedFirstId)
        }

        return IngestResult(
            sentences = normalized,
            metadata = metadata,
            checkpoint = checkpointOf(normalized),
            drift = drift,
            repairs = allRepairs,
            truncated = wasTruncated,
            receivedEntries = entries.size,
            droppedPositions = droppedPositions,
            itemErrors = itemErrors,
        )
    }

    // ──────── reading ────────

    private fun readMetadata(root: JsonValue): IngestMetadata {
        val meta = root["metadata"] ?: return IngestMetadata()
        return IngestMetadata(
            source = meta.stringOrNull("source") ?: "book",
            bookTitle = meta.decodedText("bookTitle") ?: meta.decodedText("title") ?: "",
            language = meta.stringOrNull("language") ?: "",
            targetLanguage = meta.stringOrNull("targetLanguage") ?: "",
            level = meta.stringOrNull("level").orEmpty(),
            description = meta.decodedText("description") ?: "",
        )
    }

    private fun readSentence(
        entry: JsonValue,
        fallbackId: Int,
        defaultLevel: String,
    ): BookSentence? {
        val english = entry.decodedText("english")
            ?: entry.decodedText("source")
            ?: entry.decodedText("text")
            ?: entry.decodedText("sentence")
        val translation = entry.decodedText("translation") ?: entry.decodedText("persian")
        val lesson = readLesson(entry["lesson"])
        val words = readWords(entry["words"])
        val notes = entry.decodedText("notes")
        val simplified = entry.decodedText("simplified")
        val pronunciation = entry.stringOrNull("pronunciation")
        val difficulty = entry.decodedText("difficulty")
        // Zero-drop rule: an entry is only skipped when it carries NO usable
        // signal at all - no original, no translation, no note, no lesson, no
        // words, no pronunciation. There is deliberately no length limit here:
        // a 300+ character mega-entry is awkward to display, but dropping it
        // silently is what \"sentence 27 is missing everywhere\" looks like.
        // The reader already lists entries it cannot place at the bottom of
        // their own page, so keeping them is always the safer choice.
        if (english.isNullOrBlank() && translation.isNullOrBlank() &&
            notes.isNullOrBlank() && simplified.isNullOrBlank() &&
            pronunciation.isNullOrBlank() && difficulty.isNullOrBlank() &&
            lesson == null && words.isEmpty()
        ) {
            return null
        }

        val id = entry.get("id")?.asInt()?.takeIf { it > 0 } ?: fallbackId
        val start = entry.get("start")?.asDouble()?.toInt() ?: (id - 1)
        val end = entry.get("end")?.asDouble()?.toInt() ?: (start + 1)

        // The schema asks for `location` AND for the two numbers behind it, so
        // a reply that writes only one of them is still usable. Normalising it
        // here - the earliest point in the pipeline - is what keeps a line from
        // ending up under no page at all when the copied slice is gone.
        val location = BookLessonAligner.canonicalLocation(
            location = entry.stringOrNull("location"),
            page = entry.get("page")?.asInt()?.takeIf { it > 0 },
            sentenceInPage = entry.get("sentenceInPage")?.asInt()?.takeIf { it > 0 },
        ) ?: entry.stringOrNull("location").orEmpty()

        return BookSentence(
            id = id,
            start = start,
            end = max(end, start + 1),
            location = location,
            english = english.orEmpty(),
            translation = translation,
            level = entry.stringOrNull("level") ?: defaultLevel.takeIf { it.isNotBlank() },
            difficulty = difficulty,
            pronunciation = pronunciation,
            notes = notes,
            lesson = lesson,
            words = words,
            simplified = simplified,
        )
    }

    private fun readLesson(value: JsonValue?): JsonLesson? {
        if (value == null || value is JsonValue.Null) return null
        val explanation = value.decodedText("explanation")
        val grammar = value.decodedText("grammar")
        val grammarTranslation = value.decodedText("grammarTranslation")
        val structure = value.decodedText("structure")
        if (explanation == null && grammar == null && grammarTranslation == null && structure == null) {
            return null
        }
        return JsonLesson(
            explanation = explanation,
            grammar = grammar,
            grammarTranslation = grammarTranslation,
            structure = structure,
        )
    }

    private fun readWords(value: JsonValue?): List<JsonWord> {
        if (value == null || value is JsonValue.Null) return emptyList()
        return value.asList().mapNotNull { item ->
            val word = item.decodedText("word") ?: return@mapNotNull null
            JsonWord(
                word = word,
                translation = item.decodedText("translation"),
                partOfSpeech = item.decodedText("partOfSpeech"),
                meaningInContext = item.decodedText("meaningInContext"),
                extraExplanation = item.decodedText("extraExplanation"),
                examples = (item["examples"])?.asList()?.mapNotNull { it.asString() }
                    ?.map { HtmlTextUtils.decodeEntities(it.trim()) }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
                pronunciation = item.stringOrNull("pronunciation"),
            )
        }
    }

    /**
     * Some models ignore the "continue the numbering" instruction and start
     * every chunk at 1. When the shape says that is what happened — ids run
     * 1..n while the batch was supposed to start elsewhere — the batch is
     * shifted instead of rejected, so the session keeps a single id space.
     */
    private fun renumberIfNeeded(
        sentences: List<BookSentence>,
        expected: List<SourceSentence>,
        expectedFirstId: Int,
    ): List<BookSentence> {
        if (expectedFirstId <= 1) return sentences
        val firstId = sentences.first().id
        val looksRestarted = firstId == 1 && expected.isNotEmpty() &&
            sentences.size <= expected.size + 2
        if (!looksRestarted) return sentences
        val shift = expectedFirstId - 1
        return sentences.map { sentence ->
            sentence.copy(
                id = sentence.id + shift,
                start = sentence.start + shift,
                end = sentence.end + shift,
            )
        }
    }

    // ──────── drift ────────

    /**
     * Compares what was sent with what came back.
     *
     * Counting alone is not enough: a model that merges sentences 4 and 5 and
     * then splits sentence 9 returns the right total. So ids are checked for
     * gaps and repeats, and each returned sentence is compared with the
     * sentence that was sent under that id using [FuzzyTextAligner], which
     * tolerates the cleanup the model was asked to perform but not a different
     * sentence.
     */
    fun driftReport(
        expected: List<SourceSentence>,
        received: List<BookSentence>,
        expectedFirstId: Int = 1,
    ): DriftReport {
        if (expected.isEmpty()) return countOnlyDrift(received)

        val expectedById = expected.mapIndexed { offset, sentence ->
            (expectedFirstId + offset) to sentence
        }.toMap()

        val seen = LinkedHashMap<Int, Int>()
        received.forEach { seen[it.id] = (seen[it.id] ?: 0) + 1 }

        val missing = expectedById.keys.filter { it !in seen.keys }.sorted()
        val duplicated = seen.filterValues { it > 1 }.keys.sorted()
        val unexpected = seen.keys.filter { it !in expectedById.keys }.sorted()

        val mismatches = ArrayList<SentenceMismatch>()
        for (sentence in received) {
            val source = expectedById[sentence.id] ?: continue
            if (sentence.english.isBlank()) continue
            val score = FuzzyTextAligner.similarity(source.text, sentence.english)
            if (score < MISMATCH_THRESHOLD) {
                mismatches += SentenceMismatch(
                    id = sentence.id,
                    sent = source.text,
                    received = sentence.english,
                    similarity = score,
                )
            }
        }

        val firstDrift = listOfNotNull(
            missing.minOrNull(),
            duplicated.minOrNull(),
            unexpected.minOrNull(),
            mismatches.minByOrNull { it.id }?.id,
        ).minOrNull()

        return DriftReport(
            expectedCount = expected.size,
            receivedCount = received.size,
            missingIds = missing,
            duplicatedIds = duplicated,
            unexpectedIds = unexpected,
            mismatches = mismatches,
            firstDriftId = firstDrift,
        )
    }

    /**
     * The check that is still possible when the source sentences are not at
     * hand (a JSON file imported on its own): ids must be a gap-free,
     * repeat-free run, and every entry must hold exactly one sentence.
     */
    private fun countOnlyDrift(received: List<BookSentence>): DriftReport {
        if (received.isEmpty()) return DriftReport.CLEAN
        val ids = received.map { it.id }
        val duplicated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        val expectedRange = ids.min()..ids.max()
        val missing = expectedRange.filter { it !in ids.toSet() }

        // A merged pair arrives as one entry holding two terminators.
        val merged = received.filter { sentence ->
            countTerminators(sentence.english) > 1
        }.map { sentence ->
            SentenceMismatch(
                id = sentence.id,
                sent = "",
                received = sentence.english,
                similarity = 0.0,
            )
        }

        val firstDrift = listOfNotNull(
            missing.minOrNull(),
            duplicated.minOrNull(),
            merged.minByOrNull { it.id }?.id,
        ).minOrNull()

        return DriftReport(
            expectedCount = expectedRange.count(),
            receivedCount = received.size,
            missingIds = missing,
            duplicatedIds = duplicated,
            unexpectedIds = emptyList(),
            mismatches = merged,
            firstDriftId = firstDrift,
        )
    }

    /** Counts sentence-final punctuation, ignoring "Mr."-style abbreviations. */
    private fun countTerminators(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        var count = 0
        val sentences = SentenceSegmenter.sentences(trimmed)
        for (sentence in sentences) {
            if (sentence.isNotBlank()) count++
        }
        return count
    }

    // ──────── checkpoint, merge, export ────────

    /**
     * The stopping point of a batch: the highest `id`, its `start` and its
     * `location`, with the page/chapter and sentence ordinal parsed out so the
     * chunker can be positioned exactly.
     */
    fun checkpointOf(sentences: List<BookSentence>): BookCheckpoint {
        if (sentences.isEmpty()) return BookCheckpoint.EMPTY
        val last = sentences.maxByOrNull { it.id } ?: return BookCheckpoint.EMPTY
        val location = sentences.filter { it.location.isNotBlank() }
            .maxByOrNull { it.id }?.location.orEmpty()
        val cursor = DocumentChunker.cursorFromLocation(location)
        val unitNumber = if (location.isBlank()) null else cursor.unitIndex + 1
        val sentenceInUnit = if (location.isBlank()) null else (cursor.sentenceInUnit - 1).takeIf { it >= 1 }
        return BookCheckpoint(
            lastId = last.id,
            lastStart = last.start,
            lastLocation = location,
            lastUnitNumber = unitNumber,
            lastSentenceInUnit = sentenceInUnit,
        )
    }

    /**
     * Where the next slice must begin, derived from a checkpoint.
     *
     * This is what makes auto-continuation work after an app restart: the JSON
     * itself carries enough information to place the cursor, so nothing depends
     * on in-memory state surviving.
     */
    fun nextCursor(checkpoint: BookCheckpoint): DocumentChunker.Cursor {
        if (checkpoint.isEmpty || checkpoint.lastLocation.isBlank()) {
            return DocumentChunker.Cursor.START
        }
        return DocumentChunker.cursorFromLocation(checkpoint.lastLocation)
    }

    /**
     * One id the model wrote twice for two different sentences, and the fresh
     * id the session gave the second one so both survive.
     */
    data class RemappedId(val requestedId: Int, val assignedId: Int)

    /** A merged session plus the duplicate ids that had to move. */
    data class MergeResult(
        val sentences: List<BookSentence>,
        val remappedIds: List<RemappedId> = emptyList(),
    )

    /**
     * Merges a batch into the session without ever dropping an entry.
     *
     * Re-importing the same chunk still collapses: when the incoming entry
     * carries the same sentence as the stored one, the richer of the two wins,
     * so re-sending a chunk in a mode that adds lessons upgrades the stored
     * sentences instead of duplicating them.
     *
     * But a model that merged two lines often reuses one id for two DIFFERENT
     * sentences — and the old code kept only one of them, which is how an
     * entry ends up \"missing even from the full list\" while the import
     * reported success. A conflicting duplicate is now kept under a fresh id
     * past the end of the session and recorded in [MergeResult.remappedIds],
     * so the status line can say which id moved where instead of losing it.
     */
    fun merge(existing: List<BookSentence>, incoming: List<BookSentence>): List<BookSentence> =
        mergeDetailed(existing, incoming).sentences

    fun mergeDetailed(existing: List<BookSentence>, incoming: List<BookSentence>): MergeResult {
        if (existing.isEmpty() && incoming.isEmpty()) return MergeResult(emptyList())

        val byId = LinkedHashMap<Int, BookSentence>(existing.size + incoming.size)
        existing.forEach { byId[it.id] = it }
        // Fresh ids start past EVERY id either side carries - not just the
        // stored ones - so a re-numbered entry can never collide with an
        // incoming id that is still waiting later in the same batch.
        var nextId = max(byId.keys.maxOrNull() ?: 0, incoming.maxOfOrNull { it.id } ?: 0) + 1
        val remapped = ArrayList<RemappedId>()
        for (sentence in incoming) {
            val current = byId[sentence.id]
            when {
                current == null -> byId[sentence.id] = sentence
                sameSentence(current, sentence) -> byId[sentence.id] = richer(current, sentence)
                else -> {
                    // Same id, different sentence: keep both. The newcomer
                    // moves past the end of the session rather than replacing
                    // (or being replaced by) the entry that is already there.
                    val fresh = nextId++
                    remapped += RemappedId(requestedId = sentence.id, assignedId = fresh)
                    byId[fresh] = sentence.copy(id = fresh, start = fresh - 1, end = fresh)
                }
            }
        }
        return MergeResult(
            sentences = byId.values.sortedBy { it.id },
            remappedIds = remapped,
        )
    }

    /** Similarity at or above which two entries count as \"the same sentence\". */
    private const val SAME_SENTENCE_SIMILARITY = 0.85

    /**
     * True when both entries carry the same sentence, tolerating the small
     * rewording a model applies between two copies of one chunk.
     *
     * The comparison runs on the original side, falling back to the
     * translation when the original is empty, so re-imports collapse the way
     * they always did while genuinely different sentences sharing one id are
     * recognised as the conflict they are.
     */
    private fun sameSentence(left: BookSentence, right: BookSentence): Boolean {
        val a = left.english.ifBlank { left.translation.orEmpty() }
        val b = right.english.ifBlank { right.translation.orEmpty() }
        if (a.isBlank() || b.isBlank()) return a.isBlank() && b.isBlank()
        if (FuzzyTextAligner.normalize(a) == FuzzyTextAligner.normalize(b)) return true
        return FuzzyTextAligner.similarity(a, b) >= SAME_SENTENCE_SIMILARITY
    }

    /** Field-by-field preference for the entry that actually carries content. */
    private fun richer(current: BookSentence, incoming: BookSentence): BookSentence =
        current.copy(
            start = incoming.start,
            end = incoming.end,
            location = incoming.location.ifBlank { current.location },
            english = incoming.english.ifBlank { current.english },
            translation = incoming.translation?.takeIf { it.isNotBlank() } ?: current.translation,
            level = incoming.level?.takeIf { it.isNotBlank() } ?: current.level,
            difficulty = incoming.difficulty?.takeIf { it.isNotBlank() } ?: current.difficulty,
            pronunciation = incoming.pronunciation?.takeIf { it.isNotBlank() } ?: current.pronunciation,
            notes = incoming.notes?.takeIf { it.isNotBlank() } ?: current.notes,
            lesson = incoming.lesson ?: current.lesson,
            words = if (incoming.words.isNotEmpty()) incoming.words else current.words,
            simplified = incoming.simplified?.takeIf { it.isNotBlank() } ?: current.simplified,
        )

    /**
     * Serialises the whole session back into one `formatVersion: 1` document,
     * byte-compatible with what the app imports, so an exported master file can
     * be re-ingested or shared.
     */
    fun exportMasterJson(
        sentences: List<BookSentence>,
        metadata: IngestMetadata,
    ): String {
        val ordered = sentences.sortedBy { it.id }
        val builder = StringBuilder(1024 + ordered.size * 320)
        builder.appendLine("{")
        builder.appendLine("  \"formatVersion\": 1,")
        builder.appendLine("  \"metadata\": {")
        builder.appendLine("    \"source\": \"${esc(metadata.source.ifBlank { "book" })}\",")
        if (metadata.bookTitle.isNotBlank()) {
            builder.appendLine("    \"bookTitle\": \"${esc(metadata.bookTitle)}\",")
        }
        builder.appendLine("    \"language\": \"${esc(metadata.language)}\",")
        builder.appendLine("    \"targetLanguage\": \"${esc(metadata.targetLanguage)}\",")
        builder.appendLine("    \"level\": \"${esc(metadata.level)}\",")
        builder.appendLine("    \"description\": \"${esc(metadata.description)}\",")
        builder.appendLine("    \"sentenceCount\": ${ordered.size}")
        builder.appendLine("  },")
        builder.appendLine("  \"subtitles\": [")
        ordered.forEachIndexed { index, sentence ->
            builder.append(sentenceJson(sentence))
            builder.appendLine(if (index == ordered.lastIndex) "" else ",")
        }
        builder.appendLine("  ]")
        builder.append("}")
        return builder.toString()
    }

    private fun sentenceJson(sentence: BookSentence): String {
        val builder = StringBuilder(320)
        builder.appendLine("    {")
        builder.appendLine("      \"id\": ${sentence.id},")
        builder.appendLine("      \"start\": ${sentence.start},")
        builder.appendLine("      \"end\": ${sentence.end},")
        builder.appendLine("      \"location\": \"${esc(sentence.location)}\",")
        builder.appendLine("      \"english\": \"${esc(sentence.english)}\",")
        builder.appendLine("      \"translation\": \"${esc(sentence.translation.orEmpty())}\",")
        builder.appendLine("      \"level\": \"${esc(sentence.level.orEmpty())}\",")
        builder.appendLine("      \"difficulty\": \"${esc(sentence.difficulty.orEmpty())}\",")
        builder.appendLine("      \"pronunciation\": \"${esc(sentence.pronunciation.orEmpty())}\",")
        builder.appendLine("      \"notes\": \"${esc(sentence.notes.orEmpty())}\",")
        if (!sentence.simplified.isNullOrBlank()) {
            builder.appendLine("      \"simplified\": \"${esc(sentence.simplified)}\",")
        }
        val lesson = sentence.lesson
        if (lesson != null) {
            builder.appendLine("      \"lesson\": {")
            builder.appendLine("        \"explanation\": \"${esc(lesson.explanation.orEmpty())}\",")
            builder.appendLine("        \"grammar\": \"${esc(lesson.grammar.orEmpty())}\",")
            builder.appendLine("        \"grammarTranslation\": \"${esc(lesson.grammarTranslation.orEmpty())}\",")
            builder.appendLine("        \"structure\": \"${esc(lesson.structure.orEmpty())}\"")
            builder.appendLine("      },")
        }
        builder.appendLine("      \"words\": [")
        sentence.words.forEachIndexed { index, word ->
            builder.appendLine("        {")
            builder.appendLine("          \"word\": \"${esc(word.word.orEmpty())}\",")
            builder.appendLine("          \"translation\": \"${esc(word.translation.orEmpty())}\",")
            builder.appendLine("          \"partOfSpeech\": \"${esc(word.partOfSpeech.orEmpty())}\",")
            builder.appendLine("          \"meaningInContext\": \"${esc(word.meaningInContext.orEmpty())}\",")
            builder.appendLine("          \"extraExplanation\": \"${esc(word.extraExplanation.orEmpty())}\",")
            val examples = word.examples.joinToString(", ") { "\"${esc(it)}\"" }
            builder.appendLine("          \"examples\": [$examples],")
            builder.appendLine("          \"pronunciation\": \"${esc(word.pronunciation.orEmpty())}\"")
            builder.appendLine(if (index == sentence.words.lastIndex) "        }" else "        },")
        }
        builder.appendLine("      ]")
        builder.append("    }")
        return builder.toString()
    }

    private fun esc(text: String): String = JsonRepairEngine.escape(text)

    /**
     * A trimmed string child with its HTML escapes resolved.
     *
     * Book text arrives through HTML often enough that `H&H` is answered as
     * `H&amp;H`, and a doubly escaped reply as `&amp;amp;H`. The reader binds a
     * translation to a line by comparing characters, so the entity has to be
     * resolved before anything else sees the text - see
     * [HtmlTextUtils.decodeEntities].
     */
    private fun JsonValue.decodedText(key: String): String? =
        stringOrNull(key)?.let { HtmlTextUtils.decodeEntities(it) }

    /**
     * Object-by-object salvage when the whole document will not parse.
     *
     * A single broken entry (a bad number, a mangled object) fails the
     * document parse but leaves every other object intact, so each balanced
     * `{...}` span is repaired and parsed on its own and read through the
     * same [readSentence] as the main path - full entries with id, location,
     * lesson and words, not the english+translation pairs the regex pass
     * recovers. Spans that are not entries (the `{"subtitles": ...}` wrapper,
     * nested `lesson` / `words` objects) are recognised by their keys and
     * skipped; spans that still fail are logged with their position instead
     * of vanishing.
     *
     * The scan is `"`-aware (JSON strings) but deliberately not `'`-aware: an
     * apostrophe in pasted prose (`Here's your JSON:`) is far more common than
     * a single-quoted string holding braces, and tracking it would hide the
     * real objects after the prose.
     */
    private fun tryObjectSalvage(raw: String, fallbackFirstId: Int): List<BookSentence> {
        val results = mutableListOf<BookSentence>()
        var fallbackId = fallbackFirstId
        // Skip any preamble before the first structure, the way unwrap() does.
        val scanFrom = raw.indexOfFirst { it == '{' || it == '[' }.takeIf { it >= 0 } ?: return emptyList()
        var index = scanFrom
        // Every `{` pushes its start; every `}` pops one span. Nested spans
        // (the wrapper, an entry's lesson/words) complete before their parent,
        // entries complete in document order, and a truncated tail simply
        // never completes - only whole objects are attempted. `[` / `]` need
        // no tracking: an array never outlives the braces around it.
        val spanStarts = ArrayDeque<Int>()
        var inString = false
        var escaped = false
        while (index < raw.length) {
            val char = raw[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                index++
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> spanStarts.addLast(index)
                '}' -> {
                    if (spanStarts.isNotEmpty()) {
                        val span = raw.substring(spanStarts.removeLast(), index + 1)
                        trySalvageSpan(span, results, fallbackId)?.let { fallbackId = it }
                    }
                }
            }
            index++
        }
        return results
    }

    /**
     * Repairs, parses and reads one salvaged span. Returns the next fallback
     * id when the span became a sentence, null when it was skipped.
     */
    private fun trySalvageSpan(
        span: String,
        results: MutableList<BookSentence>,
        fallbackId: Int,
    ): Int? {
        val parsed = try {
            JsonRepairEngine.repairAndParse(span).first as? JsonValue.Obj
        } catch (error: Exception) {
            BookImportLog.e("Import", "Salvage span would not parse (${span.take(60)}…): ${error.message}", error)
            return null
        } ?: run {
            BookImportLog.e("Import", "Salvage span would not parse (${span.take(60)}…)")
            return null
        }
        // Only entry-shaped objects are accepted: a real entry always carries
        // an id or an english-ish field, while the wrapper and the nested
        // lesson/word objects carry neither.
        val looksLikeEntry = parsed.get("id") != null ||
            parsed.get("english") != null || parsed.get("source") != null ||
            parsed.get("text") != null || parsed.get("sentence") != null
        if (!looksLikeEntry) return null
        return try {
            val sentence = readSentence(parsed, fallbackId = fallbackId, defaultLevel = "")
            if (sentence == null) {
                BookImportLog.e("Import", "Salvage span carried no usable fields (${span.take(60)}…)")
                null
            } else {
                results += sentence
                // Keep the fallback counter ahead of every id handed out, so
                // two id-less spans can never share one.
                maxOf(fallbackId + 1, sentence.id + 1)
            }
        } catch (error: Exception) {
            BookImportLog.e("Import", "Failed salvage span (${span.take(60)}…): ${error.message}", error)
            null
        }
    }

    /**
     * Last-resort extraction when JSON is too broken to parse.
     * Looks for patterns like {"id":1, "english":"...", "translation":"..."}
     * even if inner quotes are unescaped.
     */
    private fun tryRegexFallback(raw: String, fallbackFirstId: Int): List<BookSentence> {
        val results = mutableListOf<BookSentence>()
        // Normalize smart quotes first
        var text = raw.replace('“', '"').replace('”', '"').replace('‘', '\'').replace('’', '\'')
        // Pattern that tolerates unescaped inner quotes by looking for the next key
        // We extract english and translation with a tolerant regex
        val entryRegex = Regex(
            """\{\s*"id"\s*:\s*(\d+)[^}]*?"english"\s*:\s*"((?:\\"|[^"])*?)"\s*,\s*"translation"\s*:\s*"((?:\\"|[^"])*?)"""",
            RegexOption.DOT_MATCHES_ALL
        )
        val simpleRegex = Regex(
            """"english"\s*:\s*"([^"]{3,}?)".*?"translation"\s*:\s*"([^"]{1,}?)"""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        // Try structured entries first
        var matchId = fallbackFirstId
        for (m in entryRegex.findAll(text)) {
            try {
                val id = m.groupValues[1].toIntOrNull() ?: matchId
                val eng = HtmlTextUtils.decodeEntities(m.groupValues[2].replace("\\\"", "\"").trim())
                val trans = HtmlTextUtils.decodeEntities(m.groupValues[3].replace("\\\"", "\"").trim())
                if (eng.length < 2) continue
                results += BookSentence(
                    id = id,
                    start = id - 1,
                    end = id,
                    location = "",
                    english = eng,
                    translation = trans.ifBlank { null },
                )
                matchId = id + 1
            } catch (error: Exception) {
                BookImportLog.e("Import", "Failed regex-salvage match near id $matchId: ${error.message}", error)
                continue
            }
        }
        if (results.size >= 2) return results

        // Fallback to simple pairs
        results.clear()
        var idx = fallbackFirstId
        for (m in simpleRegex.findAll(text)) {
            try {
                val eng = HtmlTextUtils.decodeEntities(m.groupValues[1].trim())
                val trans = HtmlTextUtils.decodeEntities(m.groupValues[2].trim())
                if (eng.length < 3) continue
                if (eng.contains("formatVersion") || eng.contains("metadata")) continue
                results += BookSentence(
                    id = idx,
                    start = idx - 1,
                    end = idx,
                    location = "",
                    english = eng,
                    translation = trans.ifBlank { null },
                )
                idx++
                if (results.size > 100) break
            } catch (error: Exception) {
                BookImportLog.e("Import", "Failed regex-salvage pair #$idx: ${error.message}", error)
                continue
            }
        }
        return results
    }
}
