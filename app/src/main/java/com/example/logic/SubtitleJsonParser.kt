package com.example.logic

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
    private val MEANING_KEYS = arrayOf("meaningInContext", "meaning", "contextMeaning", "meaningHere")

    /**
     * Cheap structural auto-detection: is this text likely a subtitle-learning
     * JSON document? (Root object/array that mentions subtitles + text keys.)
     * The real validation happens in [parse].
     */
    fun looksLikeSubtitleJson(text: String): Boolean {
        val t = stripBom(text).trim()
        if (t.isEmpty()) return false
        if (!t.startsWith("{") && !t.startsWith("[")) return false
        val lower = t.lowercase()
        return lower.contains("\"$KEY_SUBTITLES\"") &&
            (lower.contains("\"english\"") || lower.contains("\"translation\"") ||
                lower.contains("\"text\"") || lower.contains("\"fa\""))
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
     */
    fun parse(text: String): JsonSubtitlePackage {
        val trimmed = stripBom(text).trim()
        if (trimmed.isEmpty()) {
            throw SubtitleJsonParseException("The JSON content is empty.")
        }

        val root: Any = try {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
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
            throw SubtitleJsonParseException(
                "No subtitles found. The JSON must contain a \"subtitles\" array of subtitle objects."
            )
        }
        if (subtitles.none { it.english.isNotBlank() }) {
            throw SubtitleJsonParseException(
                "No English subtitle text found. Each subtitle needs an \"english\" (or \"text\") field."
            )
        }
        return JsonSubtitlePackage(formatVersion = version, metadata = metadata, subtitles = subtitles)
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
        is JSONObject -> {
            val sub = parseSubtitleObject(item)
            // Skip entries that carry neither original text nor a translation.
            if (sub.english.isBlank() && sub.translation.isNullOrBlank()) null else sub
        }
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
