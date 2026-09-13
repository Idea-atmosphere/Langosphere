package com.example.model

/**
 * Data model for the AI subtitle-learning JSON package.
 *
 * This is the app's standard, versioned JSON structure for subtitle learning
 * data. Every field is optional and unknown keys are ignored at parse time,
 * so future JSON versions can add/remove fields without breaking the app —
 * see [com.example.logic.SubtitleJsonParser] for the tolerant parsing rules.
 *
 * Minimal valid file:
 * ```json
 * { "subtitles": [ { "english": "Hello.", "translation": "سلام." } ] }
 * ```
 */
data class JsonSubtitlePackage(
    /** Format version (default 1). Kept so future versions stay distinguishable. */
    val formatVersion: Int = 1,
    val metadata: JsonSubtitleMetadata? = null,
    val subtitles: List<JsonSubtitle> = emptyList(),
    /**
     * The AI answer "chunks" this package was built from, in import order.
     * Empty for a normal single-file import; one entry per `CHUNK x-y` answer
     * that was detected and merged. See [JsonChunkInfo] and
     * [com.example.logic.SubtitleJsonParser.mergePackages].
     */
    val chunks: List<JsonChunkInfo> = emptyList()
) {
    val hasTimings: Boolean get() = subtitles.any { it.start != null && it.end != null }

    /** True when more than one AI answer (CHUNK …) has been merged into this package. */
    val isMerged: Boolean get() = chunks.size > 1
}

/**
 * One AI answer chunk.
 *
 * The JSON prompt templates (see [com.example.logic.AiPromptTemplates]) ask the
 * model to begin every answer with a line like `CHUNK 1-50` BEFORE the JSON, so
 * a long film can be produced 50 cues at a time. The importer detects that
 * marker, strips it (it is not JSON and used to break the import), and keeps it
 * here so that:
 *  - the UI can show which chunks are merged into the loaded package,
 *  - a single chunk can be removed again when the model got one wrong,
 *  - chunks are joined in the right order even when imported out of order.
 *
 * Everything is optional: a marker may be a range ("CHUNK 1-50"), a part number
 * only ("CHUNK 3"), or written in Persian ("بخش ۵۱-۱۰۰").
 */
data class JsonChunkInfo(
    /** First cue id of this chunk, when the marker gave a range. */
    val start: Int? = null,
    /** Last cue id of this chunk, when the marker gave a range. */
    val end: Int? = null,
    /** Chunk/part number when the marker only said which part it is ("CHUNK 3"). */
    val part: Int? = null,
    /** The marker text exactly as the model wrote it, e.g. "CHUNK 1-50". */
    val label: String = "",
    /** Keys of the subtitles that came from this chunk (id, or text when there is no id). */
    val subtitleKeys: List<String> = emptyList(),
    /** File / chat name this chunk was imported from, when known. */
    val sourceName: String = ""
) {
    /** Short human label for chips and lists: "CHUNK 1-50", "CHUNK 3" or the raw text. */
    val shortLabel: String
        get() = when {
            start != null && end != null -> "CHUNK $start-$end"
            part != null -> "CHUNK $part"
            label.isNotBlank() -> label
            else -> "CHUNK"
        }

    /** How many subtitles this marker says it covers, when it is a range. */
    val declaredSize: Int?
        get() = if (start != null && end != null && end >= start) end - start + 1 else null
}

data class JsonSubtitleMetadata(
    val language: String = "",
    val targetLanguage: String = "",
    val level: String = "",
    val description: String = ""
)

data class JsonSubtitle(
    /** Subtitle line ID — used for synchronization when timestamps are absent. */
    val id: String? = null,
    /** Start time in seconds (optional; when present it is used to sync with playback). */
    val start: Double? = null,
    /** End time in seconds (optional). */
    val end: Double? = null,
    /** Original (English) subtitle line. */
    val english: String = "",
    /** Translated subtitle line (e.g. Persian). */
    val translation: String? = null,
    /** Language level of this line (A1..C2) if provided by the AI. */
    val level: String? = null,
    /** Difficulty level (easy / medium / hard or free text). */
    val difficulty: String? = null,
    /** Pronunciation information for the sentence if available. */
    val pronunciation: String? = null,
    /** Free-form learning notes for this line. */
    val notes: String? = null,
    /** AI-generated lesson (grammar / explanation / structure) for this line. */
    val lesson: JsonLesson? = null,
    /** Per-word learning data for the words of this line. */
    val words: List<JsonWord> = emptyList()
)

data class JsonLesson(
    /** AI-generated explanation of the sentence. */
    val explanation: String? = null,
    /** Grammar topic name, e.g. "Present Perfect Continuous". */
    val grammar: String? = null,
    /** Grammar topic name translated to the target language. */
    val grammarTranslation: String? = null,
    /** Sentence-structure explanation. */
    val structure: String? = null
)

data class JsonWord(
    /** The word as it appears in the sentence. */
    val word: String = "",
    /** Translation to the target language. */
    val translation: String? = null,
    /** Word role: noun, verb, adjective, adverb, pronoun, ... */
    val partOfSpeech: String? = null,
    /** Meaning of the word in this specific sentence. */
    val meaningInContext: String? = null,
    /** Additional explanation suitable for the learner's level. */
    val extraExplanation: String? = null,
    /** Additional example sentences. */
    val examples: List<String> = emptyList(),
    /** Pronunciation information (IPA or phonetic) if available. */
    val pronunciation: String? = null
)

/**
 * UI state for the subtitle learning bottom sheet (sentence lesson / word
 * analysis). Built by AppViewModel and rendered by
 * [com.example.ui.components.SubtitleLearningSheet].
 */
data class SubtitleLearningState(
    /** The matched JSON subtitle for the opened sentence, or null when the JSON file has no data for it. */
    val jsonSubtitle: JsonSubtitle? = null,
    /** The English sentence that was clicked. */
    val sentenceEnglish: String = "",
    /** Best available translation (JSON first, then the aligned subtitle file). */
    val translation: String? = null,
    /** Set when the sheet was opened for a specific word (word-analysis mode). */
    val targetWord: String? = null,
    /** JSON learning data for [targetWord], or null when the JSON has no entry. */
    val jsonWord: JsonWord? = null,
    /** Fallback vocabulary (word -> dictionary definition) used when no JSON lesson exists. */
    val fallbackVocab: Map<String, String> = emptyMap()
)
