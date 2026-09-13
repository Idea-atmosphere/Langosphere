package com.example.logic

import com.example.model.JsonChunkInfo
import com.example.model.JsonLesson
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitleMetadata
import com.example.model.JsonSubtitlePackage
import com.example.model.JsonWord
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser / validator / builder for the app's standard AI subtitle-learning
 * JSON format (see [com.example.model.JsonSubtitlePackage]).
 *
 * Design goals:
 *  - Tolerant: every field is optional, common alternate key names are
 *    accepted, and unknown keys are silently ignored — so future JSON
 *    versions keep working (the `formatVersion` field only marks versions).
 *  - Validating: [parse] throws a [SubtitleJsonParseException] with a
 *    user-friendly message whenever the input is not usable subtitle data.
 *  - Detection: [looksLikeSubtitleJson] is a cheap structural check used to
 *    auto-detect JSON subtitle content before importing.
 *  - AI-answer aware: models rarely return *only* JSON. The app's own prompt
 *    templates ask them to start each answer with a `CHUNK 1-50` line, and
 *    most of them add markdown fences or a sentence of commentary anyway.
 *    [extractChunks] finds every JSON document in such an answer, keeps the
 *    chunk marker that belongs to it, and [parse] then merges those documents
 *    into one package — so a whole AI answer can be pasted in as-is.
 */
object SubtitleJsonParser {

    /** Exception with a user-friendly message, safe to show in a toast/dialog. */
    class SubtitleJsonParseException(message: String) : Exception(message)

    private const val KEY_FORMAT_VERSION = "formatVersion"
    private const val KEY_SUBTITLES = "subtitles"
    private const val KEY_METADATA = "metadata"

    // Accepted alternate key names (first match wins) for the subtitle fields.
    private val ENGLISH_KEYS = arrayOf("english", "text", "original", "source", "subtitle")
    private val TRANSLATION_KEYS = arrayOf("translation", "fa", "persian", "target", "translated", "translatedText")
    private val ID_KEYS = arrayOf("id", "index", "num", "number")
    private val START_KEYS = arrayOf("start", "startTime", "from", "startSeconds")
    private val END_KEYS = arrayOf("end", "endTime", "to", "endSeconds")
    private val LEVEL_KEYS = arrayOf("level", "cefrLevel", "levelCode")
    private val DIFFICULTY_KEYS = arrayOf("difficulty", "difficultyLevel")
    private val PRONUNCIATION_KEYS = arrayOf("pronunciation", "ipa", "phonetic", "phonetics")
    private val NOTES_KEYS = arrayOf("notes", "note", "learningNotes", "learningNote")
    private val POS_KEYS = arrayOf("partOfSpeech", "pos", "wordRole", "type")
    // "meaning" is intentionally NOT in this list: it is also accepted as an
    // alternate key for a word's translation (see parseWordObject below), so
    // if a JSON document only sets "meaning", that value is the translation,
    // not the meaningInContext. Keeping "meaning" here made both fields read
    // the same key and silently return the translation for meaningInContext.
    private val MEANING_KEYS = arrayOf("meaningInContext", "contextMeaning", "meaningHere")

    /**
     * Cheap structural auto-detection: is this text likely a subtitle-learning
     * JSON document? (Root object/array that mentions subtitles + text keys.)
     * The real validation happens in [parse].
     *
     * The text does NOT have to be pure JSON: a `CHUNK 1-50` line, markdown
     * fences and a sentence of commentary around the document are stripped
     * first (see [extractJsonBody]), because that is exactly what an AI answer
     * looks like.
     */
    fun looksLikeSubtitleJson(text: String): Boolean {
        val t = stripBom(text).trim()
        if (t.isEmpty()) return false
        if (!t.contains('{') && !t.contains('[')) return false
        // Fast path: a clean JSON file/paste needs no extraction. This matters
        // because the paste dialog re-runs detection on every keystroke.
        val body = if (t.startsWith("{") || t.startsWith("[")) t else extractJsonBody(t)
        if (!body.startsWith("{") && !body.startsWith("[")) return false
        val lower = body.lowercase()
        return lower.contains("\"$KEY_SUBTITLES\"") &&
            (lower.contains("\"english\"") || lower.contains("\"translation\"") ||
                lower.contains("\"text\"") || lower.contains("\"fa\""))
    }

    // ── AI answer chunks: marker detection + JSON extraction ──

    /** A JSON document found inside an AI answer, with the chunk marker that belongs to it. */
    data class FoundChunk(val marker: JsonChunkInfo?, val json: String)

    /**
     * `CHUNK 1-50` / `chunk: 51-100` / `بخش ۵۱-۱۰۰` / `part 3 to 20` …
     * Digits are normalized to ASCII first, so Persian and Arabic numerals work.
     */
    // NOTE: \b only works for the Latin words — Java/Kotlin regex treats
    // Arabic-script letters as non-word characters, so a Persian marker like
    // "بخش ۵۱-۱۰۰" needs its own alternative without word boundaries.
    private const val CHUNK_WORDS = "(?:\\b(?:chunks?|parts?)|(?:بخش|قسمت|پاره|بند))"

    private val CHUNK_RANGE_REGEX = Regex(
        "(?i)$CHUNK_WORDS\\s*[:#_]?\\s*([0-9]+)\\s*(?:-{1,3}|–|—|─|\\.\\.|to|تا)\\s*([0-9]+)"
    )

    /** Fallback for a marker that only names the part: `CHUNK 3`. */
    private val CHUNK_PART_REGEX = Regex(
        "(?i)$CHUNK_WORDS\\s*[:#_]?\\s*([0-9]+)"
    )

    /** Markdown fence lines (``` / ```json) — removed before looking for JSON. */
    private val FENCE_LINE_REGEX = Regex("(?m)^\\s*`{3,}[a-zA-Z0-9_+-]*\\s*$")

    private val JSON_OPEN_CHARS = charArrayOf('{', '[')

    /** Converts Persian/Arabic-Indic digits to ASCII so one regex covers all of them. */
    private fun normalizeDigits(text: String): String {
        if (text.none { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' }) return text
        val out = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                in '\u0660'..'\u0669' -> out.append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> out.append('0' + (c - '\u06F0'))
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * Reads a chunk marker out of a piece of prose (the text an AI writes
     * before or after its JSON). Returns null when there is none.
     */
    fun chunkMarker(prose: String): JsonChunkInfo? {
        if (prose.isBlank()) return null
        val text = normalizeDigits(prose)
        CHUNK_RANGE_REGEX.find(text)?.let { match ->
            val start = match.groupValues[1].toIntOrNull()
            val end = match.groupValues[2].toIntOrNull()
            if (start != null && end != null) {
                return JsonChunkInfo(
                    start = minOf(start, end),
                    end = maxOf(start, end),
                    label = match.value.trim()
                )
            }
        }
        CHUNK_PART_REGEX.find(text)?.let { match ->
            val part = match.groupValues[1].toIntOrNull()
            if (part != null) {
                return JsonChunkInfo(part = part, label = match.value.trim())
            }
        }
        return null
    }

    /**
     * Detects the chunk marker of a whole AI answer without parsing it — used
     * for the live "CHUNK 1-50 detected" hint in the paste dialog. Only the
     * head and the tail of the text are scanned, so it stays cheap while the
     * user is still typing a very long answer.
     */
    fun detectChunkMarker(text: String, scanChars: Int = 800): JsonChunkInfo? {
        val t = normalizeDigits(stripBom(text))
        if (t.isBlank()) return null
        if (t.length <= scanChars * 2) return chunkMarker(t)
        chunkMarker(t.substring(0, scanChars))?.let { return it }
        return chunkMarker(t.substring(t.length - scanChars))
    }

    /** True when the text carries a `CHUNK …` marker anywhere near its edges. */
    fun hasChunkMarker(text: String): Boolean = detectChunkMarker(text) != null

    /**
     * Finds every complete JSON document inside an AI answer, each paired with
     * the chunk marker written in the prose right before it (or, for a single
     * document, right after it — models do both).
     *
     * Prose, markdown fences and stray brackets are skipped: a candidate has to
     * be brace-balanced AND parse as JSON to be accepted, so a line like
     * "[here is the file]" never fools it.
     */
    fun extractChunks(text: String): List<FoundChunk> {
        // NOTE: digits are normalized only inside chunkMarker(), never here —
        // rewriting Persian digits in the JSON itself would corrupt the text.
        val cleaned = stripCodeFences(stripBom(text))
        val found = mutableListOf<FoundChunk>()
        var searchFrom = 0
        var proseFrom = 0
        var lastJsonEnd = -1

        while (searchFrom < cleaned.length) {
            val open = cleaned.indexOfAny(JSON_OPEN_CHARS, searchFrom)
            if (open < 0) break
            val close = findMatchingClose(cleaned, open)
            if (close < 0) {
                // Unbalanced: the answer was cut off mid-JSON. Keep the rest as
                // one candidate so [parse] can report a precise syntax error.
                val body = cleaned.substring(open).trim()
                if (body.isNotEmpty() && found.isEmpty()) {
                    found.add(FoundChunk(chunkMarker(cleaned.substring(proseFrom, open)), body))
                    lastJsonEnd = cleaned.length
                }
                break
            }
            val body = cleaned.substring(open, close + 1)
            if (isParsableJson(body)) {
                found.add(FoundChunk(chunkMarker(cleaned.substring(proseFrom, open)), body))
                lastJsonEnd = close + 1
            }
            searchFrom = close + 1
            proseFrom = searchFrom
        }

        // A single document with its marker written AFTER the JSON.
        if (found.size == 1 && found[0].marker == null && lastJsonEnd in 0 until cleaned.length) {
            chunkMarker(cleaned.substring(lastJsonEnd))?.let { trailing ->
                found[0] = found[0].copy(marker = trailing)
            }
        }
        return found
    }

    /**
     * The first JSON document of an AI answer, without the chunk line, fences
     * or commentary around it. Falls back to "everything from the first brace"
     * when no complete document exists, so truncated answers still produce a
     * meaningful parse error instead of being silently emptied.
     */
    fun extractJsonBody(text: String): String {
        val chunks = extractChunks(text)
        if (chunks.isNotEmpty()) return chunks[0].json
        val cleaned = stripCodeFences(stripBom(text)).trim()
        val start = cleaned.indexOfAny(JSON_OPEN_CHARS)
        return if (start < 0) cleaned else cleaned.substring(start)
    }

    private fun stripCodeFences(text: String): String =
        if (!text.contains("```")) text else text.replace(FENCE_LINE_REGEX, "")

    /** Index of the bracket that closes the document starting at [open], or -1. */
    private fun findMatchingClose(text: String, open: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth <= 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun isParsableJson(text: String): Boolean = try {
        val t = text.trim()
        if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        true
    } catch (e: Exception) {
        false
    }

    // ── merging chunks ──

    /**
     * Identity of a subtitle line, used to join chunks without duplicating the
     * cues they have in common: the original cue id when there is one (the
     * prompts insist the model never renumbers), otherwise the source text.
     */
    fun subtitleKey(sub: JsonSubtitle): String {
        val id = sub.id?.trim().orEmpty()
        if (id.isNotEmpty()) return "id:$id"
        return "text:${sub.english.trim().lowercase()}"
    }

    private fun numericId(sub: JsonSubtitle): Int? = sub.id?.trim()?.toIntOrNull()

    /**
     * Joins two packages into one, in cue order, keeping the newest version of
     * any cue both contain. This is what turns "CHUNK 1-50" + "CHUNK 51-100"
     * answers into a single importable file.
     */
    fun mergePackages(base: JsonSubtitlePackage, incoming: JsonSubtitlePackage): JsonSubtitlePackage {
        if (base.subtitles.isEmpty()) return incoming
        if (incoming.subtitles.isEmpty()) return base

        val joined = LinkedHashMap<String, JsonSubtitle>()
        base.subtitles.forEach { joined[subtitleKey(it)] = it }
        incoming.subtitles.forEach { joined[subtitleKey(it)] = it }

        var subtitles = joined.values.toList()
        // Sort only when every cue has a numeric id, so chunk 51-100 imported
        // before chunk 1-50 still ends up in the right order. Text-keyed
        // packages keep their import order instead.
        if (subtitles.isNotEmpty() && subtitles.all { numericId(it) != null }) {
            subtitles = subtitles.sortedBy { numericId(it) ?: 0 }
        }

        return JsonSubtitlePackage(
            formatVersion = maxOf(base.formatVersion, incoming.formatVersion),
            metadata = mergeMetadata(base.metadata, incoming.metadata),
            subtitles = subtitles,
            chunks = mergeChunkLists(base.chunks, incoming.chunks)
        )
    }

    private fun mergeMetadata(
        base: JsonSubtitleMetadata?,
        incoming: JsonSubtitleMetadata?
    ): JsonSubtitleMetadata? {
        if (base == null) return incoming
        if (incoming == null) return base
        return JsonSubtitleMetadata(
            language = base.language.ifBlank { incoming.language },
            targetLanguage = base.targetLanguage.ifBlank { incoming.targetLanguage },
            level = base.level.ifBlank { incoming.level },
            description = base.description.ifBlank { incoming.description }
        )
    }

    private fun mergeChunkLists(
        base: List<JsonChunkInfo>,
        incoming: List<JsonChunkInfo>
    ): List<JsonChunkInfo> {
        val all = mutableListOf<JsonChunkInfo>()
        (base + incoming).forEach { chunk ->
            // Re-importing the same chunk updates it in place instead of
            // listing it twice.
            val index = all.indexOfFirst { it.label == chunk.label && it.start == chunk.start && it.end == chunk.end }
            if (index >= 0) all[index] = chunk else all.add(chunk)
        }
        return all.sortedWith(
            compareBy({ it.start ?: Int.MAX_VALUE }, { it.part ?: Int.MAX_VALUE }, { it.label })
        )
    }

    /**
     * Removes one merged chunk again — the "oops, that answer was wrong"
     * button. Returns null when nothing is left, which tells the caller to
     * clear the JSON slot completely.
     */
    fun removeChunk(pkg: JsonSubtitlePackage, chunk: JsonChunkInfo): JsonSubtitlePackage? {
        val keys = chunk.subtitleKeys.toSet()
        val remaining = if (keys.isNotEmpty()) {
            pkg.subtitles.filter { subtitleKey(it) !in keys }
        } else {
            // No recorded keys (an older import): fall back to the declared id range.
            val start = chunk.start
            val end = chunk.end
            pkg.subtitles.filter { sub ->
                val id = numericId(sub)
                id == null || start == null || end == null || id !in start..end
            }
        }
        val chunks = pkg.chunks.filter { it != chunk }
        if (remaining.isEmpty()) return null
        return pkg.copy(subtitles = remaining, chunks = chunks)
    }

    /** Full validation: parses the text and returns true only when it is usable subtitle data. */
    fun isValidSubtitleJson(text: String): Boolean = try {
        parse(text); true
    } catch (_: SubtitleJsonParseException) {
        false
    }

    /**
     * Parses a subtitle-learning JSON document into a
     * [JsonSubtitlePackage]. Throws [SubtitleJsonParseException] with a
     * user-friendly message on invalid input.
     *
     * Accepted shapes (flexible by design):
     *  - { "formatVersion": 1, "metadata": {...}, "subtitles": [ {...}, ... ] }
     *  - [ {...}, {...} ]  — a bare array of subtitle objects
     *  - { "data": { "subtitles": [...] } }  — wrapped in a "data" object
     *  - a whole AI answer: a `CHUNK 1-50` line, ```json fences, a sentence of
     *    commentary, or even several chunk documents in one paste — every JSON
     *    document found is parsed and the results are merged into one package
     *    (see [mergePackages]), with each chunk recorded in
     *    [JsonSubtitlePackage.chunks].
     */
    fun parse(text: String): JsonSubtitlePackage {
        val raw = stripBom(text)
        if (raw.trim().isEmpty()) {
            throw SubtitleJsonParseException("The JSON content is empty.")
        }

        // Fast path: one balanced document with no CHUNK marker around it (the
        // normal case for a saved file) is parsed exactly once.
        val trimmed = raw.trim()
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) &&
            detectChunkMarker(trimmed) == null &&
            findMatchingClose(trimmed, 0) == trimmed.length - 1
        ) {
            return parseDocument(trimmed, null)
        }

        val documents = extractChunks(raw)
        if (documents.isEmpty()) {
            // No complete JSON document at all: report the syntax error of
            // whatever is left from the first brace (truncated answers, or
            // text that simply is not JSON).
            throwForBody(extractJsonBody(raw))
        }

        var result: JsonSubtitlePackage? = null
        var lastError: SubtitleJsonParseException? = null
        for (document in documents) {
            try {
                val pkg = parseDocument(document.json, document.marker)
                result = result?.let { mergePackages(it, pkg) } ?: pkg
            } catch (e: SubtitleJsonParseException) {
                lastError = e
            }
        }
        return result
            ?: throw (lastError ?: SubtitleJsonParseException(NO_SUBTITLES_ERROR))
    }

    private const val NO_SUBTITLES_ERROR =
        "No subtitles found. The JSON must contain a \"subtitles\" array of subtitle objects."

    /** Always throws: used when no complete JSON document could be extracted. */
    private fun throwForBody(body: String): Nothing {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
            throw SubtitleJsonParseException(
                "Invalid JSON syntax: no JSON object was found in the text."
            )
        }
        try {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        } catch (e: Exception) {
            throw SubtitleJsonParseException("Invalid JSON syntax: ${e.message ?: "parse error"}")
        }
        throw SubtitleJsonParseException(NO_SUBTITLES_ERROR)
    }

    /** Parses one JSON document and attaches the chunk marker it came with. */
    private fun parseDocument(json: String, marker: JsonChunkInfo?): JsonSubtitlePackage {
        val root: Any = try {
            if (json.startsWith("[")) JSONArray(json) else JSONObject(json)
        } catch (e: Exception) {
            throw SubtitleJsonParseException("Invalid JSON syntax: ${e.message ?: "parse error"}")
        }

        val subtitles = mutableListOf<JsonSubtitle>()
        var metadata: JsonSubtitleMetadata? = null
        var version = 1

        when (root) {
            is JSONArray -> {
                for (i in 0 until root.length()) {
                    parseSubtitleItem(root.opt(i))?.let { subtitles.add(it) }
                }
            }
            is JSONObject -> {
                version = optInt(root, arrayOf(KEY_FORMAT_VERSION, "version")) ?: 1
                val metaObj = root.optJSONObject(KEY_METADATA)
                    ?: root.optJSONObject("data")?.optJSONObject(KEY_METADATA)
                if (metaObj != null) metadata = parseMetadata(metaObj)

                // Accept "subtitles" at root or wrapped inside a "data" object.
                var array = root.optJSONArray(KEY_SUBTITLES)
                if (array == null) array = root.optJSONObject("data")?.optJSONArray(KEY_SUBTITLES)
                if (array != null) {
                    for (i in 0 until array.length()) {
                        parseSubtitleItem(array.opt(i))?.let { subtitles.add(it) }
                    }
                }
            }
        }

        if (subtitles.isEmpty()) {
            throw SubtitleJsonParseException(NO_SUBTITLES_ERROR)
        }
        if (subtitles.none { it.english.isNotBlank() }) {
            throw SubtitleJsonParseException(
                "No English subtitle text found. Each subtitle needs an \"english\" (or \"text\") field."
            )
        }

        // Remember which lines came from this chunk, so one bad AI answer can be
        // removed again without touching the rest of the merged file.
        val chunks = listOfNotNull(
            marker?.copy(subtitleKeys = subtitles.map { subtitleKey(it) })
        )
        // A chunk marker stored inside the file itself (written by serialize).
        val stored = parseStoredChunks(root)
        return JsonSubtitlePackage(
            formatVersion = version,
            metadata = metadata,
            subtitles = subtitles,
            chunks = if (chunks.isNotEmpty()) chunks else stored
        )
    }

    /** Reads the "chunks" bookkeeping array written by [serialize]. */
    private fun parseStoredChunks(root: Any): List<JsonChunkInfo> {
        val obj = root as? JSONObject ?: return emptyList()
        val array = obj.optJSONArray("chunks") ?: return emptyList()
        val result = mutableListOf<JsonChunkInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val keys = when (val idArray = item.optJSONArray("subtitleKeys")) {
                null -> emptyList()
                else -> (0 until idArray.length()).mapNotNull { idArray.optString(it).takeIf { k -> k.isNotBlank() } }
            }
            val label = item.optString("label", "")
            val start = if (item.isNull("start")) null else item.optString("start").toIntOrNull()
            val end = if (item.isNull("end")) null else item.optString("end").toIntOrNull()
            val part = if (item.isNull("part")) null else item.optString("part").toIntOrNull()
            result.add(
                JsonChunkInfo(
                    start = start,
                    end = end,
                    part = part,
                    label = label,
                    subtitleKeys = keys,
                    sourceName = item.optString("sourceName", "")
                )
            )
        }
        return result
    }

    // ── private parsing helpers ──

    private fun parseMetadata(obj: JSONObject): JsonSubtitleMetadata = JsonSubtitleMetadata(
        language = optString(obj, arrayOf("language", "sourceLanguage", "lang")) ?: "",
        targetLanguage = optString(obj, arrayOf("targetLanguage", "targetLang", "destinationLanguage")) ?: "",
        level = optString(obj, arrayOf("level", "cefrLevel")) ?: "",
        description = optString(obj, arrayOf("description", "desc", "title")) ?: ""
    )

    private fun parseSubtitleItem(item: Any?): JsonSubtitle? = when (item) {
        null -> null
        is String -> if (item.isNotBlank()) JsonSubtitle(english = item.trim()) else null
        // Keep every subtitle object, even when it has neither english nor
        // translation text: dropping it here used to hide the specific
        // "missing english text" validation error behind a generic "no
        // subtitles found" error when that item was the only one present.
        is JSONObject -> parseSubtitleObject(item)
        else -> null
    }

    private fun parseSubtitleObject(obj: JSONObject): JsonSubtitle {
        val english = optString(obj, ENGLISH_KEYS) ?: ""
        val translation = optString(obj, TRANSLATION_KEYS)

        val lessonObj = obj.optJSONObject("lesson")
        val lesson = if (lessonObj != null) JsonLesson(
            explanation = optString(lessonObj, arrayOf("explanation", "lesson", "teachingNotes", "teachingNote")),
            grammar = optString(lessonObj, arrayOf("grammar", "grammarTopic", "grammarTitle")),
            grammarTranslation = optString(lessonObj, arrayOf("grammarTranslation", "grammarFa")),
            structure = optString(lessonObj, arrayOf("structure", "sentenceStructure", "structureExplanation"))
        ) else null

        return JsonSubtitle(
            id = optAnyToString(obj, ID_KEYS),
            start = optDouble(obj, START_KEYS),
            end = optDouble(obj, END_KEYS),
            english = english,
            translation = translation,
            level = optString(obj, LEVEL_KEYS),
            difficulty = optString(obj, DIFFICULTY_KEYS),
            pronunciation = optString(obj, PRONUNCIATION_KEYS),
            notes = optString(obj, NOTES_KEYS),
            lesson = lesson,
            words = parseWords(obj.opt("words"))
        )
    }

    /**
     * Parses the "words" field of a subtitle. Accepted shapes:
     *  - [ { "word": "...", "translation": "...", ... }, ... ]
     *  - { "working": { "translation": "...", ... }, ... }  (word-keyed object)
     *  - [ "hello", "world" ]  (bare word strings)
     */
    private fun parseWords(raw: Any?): List<JsonWord> {
        val result = mutableListOf<JsonWord>()
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val item = raw.opt(i)
                    when (item) {
                        is String -> if (item.isNotBlank()) result.add(JsonWord(word = item.trim()))
                        is JSONObject -> result.add(parseWordObject(item))
                    }
                }
            }
            is JSONObject -> {
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = raw.opt(key)
                    when (value) {
                        is String -> result.add(JsonWord(word = key, translation = value))
                        is JSONObject -> result.add(parseWordObject(value, fallbackWord = key))
                    }
                }
            }
        }
        return result
    }

    private fun parseWordObject(obj: JSONObject, fallbackWord: String = ""): JsonWord {
        val word = optString(obj, arrayOf("word", "term", "value")) ?: fallbackWord
        val examples: List<String> = when (val ex = obj.opt("examples")) {
            is JSONArray -> (0 until ex.length()).mapNotNull { ex.optString(it).takeIf { s -> s.isNotBlank() } }
            is String -> ex.split("|", "\n").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
        return JsonWord(
            word = word.trim(),
            translation = optString(obj, arrayOf("translation", "meaning", "fa", "persian")),
            partOfSpeech = optString(obj, POS_KEYS),
            meaningInContext = optString(obj, MEANING_KEYS),
            extraExplanation = optString(obj, arrayOf("extraExplanation", "explanation", "note", "detail")),
            examples = examples,
            pronunciation = optString(obj, PRONUNCIATION_KEYS)
        )
    }

    // ── small JSON helpers (first non-null value wins) ──

    private fun optString(obj: JSONObject, keys: Array<String>): String? {
        for (key in keys) {
            val v = obj.opt(key) ?: continue
            if (v == JSONObject.NULL) continue
            val s = v.toString().trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun optDouble(obj: JSONObject, keys: Array<String>): Double? {
        for (key in keys) {
            val v = obj.opt(key) ?: continue
            if (v == JSONObject.NULL) continue
            val d = (v as? Number)?.toDouble() ?: v.toString().toDoubleOrNull()
            if (d != null) return d
        }
        return null
    }

    private fun optInt(obj: JSONObject, keys: Array<String>): Int? {
        for (key in keys) {
            val v = obj.opt(key) ?: continue
            if (v == JSONObject.NULL) continue
            val i = (v as? Number)?.toInt() ?: v.toString().toIntOrNull()
            if (i != null) return i
        }
        return null
    }

    /** Reads a field that may be a number or a string and returns it as a string (for IDs). */
    private fun optAnyToString(obj: JSONObject, keys: Array<String>): String? {
        for (key in keys) {
            val v = obj.opt(key) ?: continue
            if (v == JSONObject.NULL) continue
            val s = v.toString().trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun stripBom(text: String): String =
        if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text

    // ── serialization (used to persist time shifts back to the JSON file) ──

    /**
     * Serializes a [JsonSubtitlePackage] back to the app's standard JSON
     * format. Used to persist runtime edits (e.g. subtitle time shifts) to
     * saved_sub_json.json so they survive app restarts. The output is
     * guaranteed to round-trip through [parse].
     */
    fun serialize(pkg: JsonSubtitlePackage): String {
        val meta = JSONObject().apply {
            pkg.metadata?.let { m ->
                if (m.language.isNotBlank()) put("language", m.language)
                if (m.targetLanguage.isNotBlank()) put("targetLanguage", m.targetLanguage)
                if (m.level.isNotBlank()) put("level", m.level)
                if (m.description.isNotBlank()) put("description", m.description)
            }
        }
        val subs = JSONArray()
        for (s in pkg.subtitles) {
            val item = JSONObject().apply {
                s.id?.let { put("id", it) }
                s.start?.let { put("start", it) }
                s.end?.let { put("end", it) }
                if (s.english.isNotBlank()) put("english", s.english)
                s.translation?.takeIf { it.isNotBlank() }?.let { put("translation", it) }
                s.level?.let { put("level", it) }
                s.difficulty?.let { put("difficulty", it) }
                s.pronunciation?.let { put("pronunciation", it) }
                s.notes?.let { put("notes", it) }
                s.lesson?.let { lesson ->
                    put("lesson", JSONObject().apply {
                        lesson.explanation?.let { put("explanation", it) }
                        lesson.grammar?.let { put("grammar", it) }
                        lesson.grammarTranslation?.let { put("grammarTranslation", it) }
                        lesson.structure?.let { put("structure", it) }
                    })
                }
                if (s.words.isNotEmpty()) {
                    val words = JSONArray()
                    for (w in s.words) {
                        words.put(JSONObject().apply {
                            put("word", w.word)
                            w.translation?.let { put("translation", it) }
                            w.partOfSpeech?.let { put("partOfSpeech", it) }
                            w.meaningInContext?.let { put("meaningInContext", it) }
                            w.extraExplanation?.let { put("extraExplanation", it) }
                            if (w.examples.isNotEmpty()) {
                                val ex = JSONArray()
                                w.examples.forEach { ex.put(it) }
                                put("examples", ex)
                            }
                            w.pronunciation?.let { put("pronunciation", it) }
                        })
                    }
                    put("words", words)
                }
            }
            subs.put(item)
        }
        val root = JSONObject().apply {
            put(KEY_FORMAT_VERSION, pkg.formatVersion)
            if (meta.length() > 0) put(KEY_METADATA, meta)
            put(KEY_SUBTITLES, subs)
            // Chunk bookkeeping (which AI answer each line came from) is saved
            // with the file, so merged chunks survive an app restart and can
            // still be removed one by one. Unknown to older app versions, and
            // ignored by every other JSON tool.
            if (pkg.chunks.isNotEmpty()) {
                val chunks = JSONArray()
                for (chunk in pkg.chunks) {
                    chunks.put(JSONObject().apply {
                        chunk.start?.let { put("start", it) }
                        chunk.end?.let { put("end", it) }
                        chunk.part?.let { put("part", it) }
                        if (chunk.label.isNotBlank()) put("label", chunk.label)
                        if (chunk.sourceName.isNotBlank()) put("sourceName", chunk.sourceName)
                        if (chunk.subtitleKeys.isNotEmpty()) {
                            val keys = JSONArray()
                            chunk.subtitleKeys.forEach { keys.put(it) }
                            put("subtitleKeys", keys)
                        }
                    })
                }
                put("chunks", chunks)
            }
        }
        return root.toString(2)
    }

    // ── builders (sample data + prompt schema examples) ──

    /**
     * Builds a small sample package matching the app's standard format, used
     * as the "sample JSON" in the import dialog and in the prompt templates.
     */
    fun buildSamplePackage(level: String = "B1"): JsonSubtitlePackage = JsonSubtitlePackage(
        formatVersion = 1,
        metadata = JsonSubtitleMetadata(
            language = "English",
            targetLanguage = "Persian",
            level = level,
            description = "AI generated subtitle learning package"
        ),
        subtitles = listOf(
            JsonSubtitle(
                id = "1",
                start = 12.4,
                end = 15.8,
                english = "I have been working here for five years.",
                translation = "من پنج سال است که اینجا کار می‌کنم.",
                level = "B1",
                difficulty = "medium",
                lesson = JsonLesson(
                    explanation = "Present perfect continuous is used for actions that started in the past and continue until now.",
                    grammar = "Present Perfect Continuous",
                    grammarTranslation = "حال کامل استمراری",
                    structure = "Subject + have/has + been + verb(-ing) + time expression."
                ),
                words = listOf(
                    JsonWord(
                        word = "working",
                        translation = "کار کردن",
                        partOfSpeech = "verb",
                        meaningInContext = "Doing a job in this place.",
                        extraExplanation = "Used to describe an ongoing activity.",
                        examples = listOf("She is working on a new project."),
                        pronunciation = "/ˈwɜːrkɪŋ/"
                    )
                )
            ),
            JsonSubtitle(
                id = "2",
                start = 16.1,
                end = 19.2,
                english = "Time flies when you love what you do.",
                translation = "وقتی عاشق کاری باشی، زمان زود می‌گذرد.",
                level = "B1",
                difficulty = "easy",
                notes = "\"Time flies\" is a common idiom meaning time passes quickly.",
                lesson = JsonLesson(
                    explanation = "A common saying: when you enjoy an activity, time seems to pass quickly.",
                    grammar = "Simple Present (idiomatic expression)",
                    structure = "Subject + verb + when-clause."
                )
            )
        )
    )

    /** Serializes the sample package to a pretty-printed JSON string for display/import. */
    fun buildSampleJsonString(level: String = "B1"): String = serialize(buildSamplePackage(level))
}
