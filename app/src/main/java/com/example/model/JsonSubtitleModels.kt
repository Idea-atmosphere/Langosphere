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
    val subtitles: List<JsonSubtitle> = emptyList()
) {
    val hasTimings: Boolean get() = subtitles.any { it.start != null && it.end != null }
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
