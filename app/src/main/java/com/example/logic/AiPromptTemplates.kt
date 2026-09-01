package com.example.logic

/**
 * Ready-to-copy AI prompt templates for the "Tutorial & AI Learning" settings
 * section. Every prompt instructs the model to output JSON that is compatible
 * with the app's subtitle-learning JSON parser (see SubtitleJsonParser.kt and
 * model/JsonSubtitleModels.kt), so the generated file can be imported
 * straight into the video player's "JSON subtitle" slot.
 */
object AiPromptTemplates {

    /** Supported CEFR levels, from beginner to native-like. */
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** The three prompt modes offered in the Tutorial section. */
    enum class PromptMode(val key: String) {
        TRANSLATION_ONLY("translation_only"),
        TRANSLATION_LEARNING("translation_learning"),
        WORD_ANALYSIS("word_analysis")
    }

    fun levelDescription(level: String): String = when (level.uppercase()) {
        "A1" -> "A1 Beginner"
        "A2" -> "A2 Elementary"
        "B1" -> "B1 Intermediate"
        "B2" -> "B2 Upper Intermediate"
        "C1" -> "C1 Advanced"
        "C2" -> "C2 Native-like"
        else -> level
    }

    /**
     * Human-readable description of the exact JSON structure the app's parser
     * accepts. Embedded into every generated prompt so the AI always targets
     * the correct format (and only the documented fields, which keeps the
     * parser tolerant to future versions).
     */
    fun jsonSchemaSpec(): String = """
        The output must be a single JSON object with this exact structure:
        {
          "formatVersion": 1,
          "metadata": {
            "language": "English",
            "targetLanguage": "Persian",
            "level": "<CEFR level>",
            "description": "AI generated subtitle learning package"
          },
          "subtitles": [
            {
              "id": 1,                       // line id (number or string)
              "start": 12.4,                 // optional start time in seconds (syncs with playback)
              "end": 15.8,                   // optional end time in seconds
              "english": "original English subtitle line",
              "translation": "translated line",
              "level": "B1",                 // optional per-line level
              "difficulty": "easy|medium|hard", // optional difficulty
              "pronunciation": "optional sentence pronunciation / IPA",
              "notes": "optional learning notes",
              "lesson": {
                "explanation": "educational explanation of the sentence",
                "grammar": "grammar topic name",
                "grammarTranslation": "grammar topic name in the target language",
                "structure": "sentence structure explanation"
              },
              "words": [
                {
                  "word": "working",
                  "translation": "translation of the word",
                  "partOfSpeech": "noun|verb|adjective|adverb|pronoun|preposition|conjunction|phrase|...",
                  "meaningInContext": "meaning of the word in this specific sentence",
                  "extraExplanation": "additional explanation suitable for the learner level",
                  "examples": ["an additional example sentence"],
                  "pronunciation": "optional IPA / phonetic"
                }
              ]
            }
          ]
        }
        Rules:
        - Return ONLY the JSON object (no markdown fences, no comments, no extra text).
        - Keep the order of the input subtitle lines; one subtitle object per input line.
        - Include "start" and "end" (seconds) whenever the input file provides timestamps.
        - All lesson/word fields are optional — include them only when helpful for the level.
        - Unknown extra fields are ignored by the app, but do not invent new required fields.
    """.trimIndent()

    /**
     * Builds the ready-to-copy prompt for the given CEFR level and mode.
     */
    fun buildPrompt(level: String, mode: PromptMode, targetLanguage: String = "Persian"): String {
        val levelName = levelDescription(level)
        val schema = jsonSchemaSpec()
        return when (mode) {
            PromptMode.TRANSLATION_ONLY -> buildTranslationOnlyPrompt(level, levelName, targetLanguage, schema)
            PromptMode.TRANSLATION_LEARNING -> buildTranslationLearningPrompt(level, levelName, targetLanguage, schema)
            PromptMode.WORD_ANALYSIS -> buildWordAnalysisPrompt(level, levelName, targetLanguage)
        }
    }

    /**
     * Mode 1 — Translation Only.
     * The AI only provides the original sentence and its translation, tuned to
     * the selected level's vocabulary.
     */
    private fun buildTranslationOnlyPrompt(
        level: String,
        levelName: String,
        targetLanguage: String,
        schema: String
    ): String = """
        You are a professional subtitle translator creating a learning package for a $levelName ($level) language learner.

        TASK: translate an English subtitle file (SRT/VTT) into $targetLanguage.

        1. Analyze the subtitle file line by line (keep every cue's timing).
        2. Translate each line into natural, level-appropriate $targetLanguage:
           - Use vocabulary and sentence complexity suitable for a $level learner.
        3. Do NOT add grammar lessons, word lists, or explanations — translation only:
           each subtitle must contain ONLY its id, timing, the original English line,
           and the translation.

        $schema

        Only include the fields: id, start, end, english, translation.
    """.trimIndent()

    /**
     * Mode 2 — Translation + Learning.
     * Full educational package: translation, grammar, vocabulary, sentence
     * structure and level-appropriate teaching notes.
     */
    private fun buildTranslationLearningPrompt(
        level: String,
        levelName: String,
        targetLanguage: String,
        schema: String
    ): String = """
        You are an expert English teacher and subtitle translator creating a complete learning package for a $levelName ($level) learner.

        TASK: turn an English subtitle file (SRT/VTT) into a $targetLanguage learning package.

        For EVERY subtitle line provide:
        1. Translation: natural $targetLanguage translation using $level-appropriate language.
        2. Grammar explanation: name the grammar topic (also in $targetLanguage) and explain
           it in simple, $level-appropriate words.
        3. Sentence structure explanation: break the sentence into its parts
           (subject, verb, object, time expression, ...).
        4. Vocabulary learning data: for each important word give its translation,
           word role (noun, verb, adjective, adverb, etc.), its meaning in this
           specific sentence, extra level-appropriate explanation, and at least one
           additional example sentence.
        5. Learning notes: idioms, collocations, or cultural notes a learner at this
           level would benefit from.
        6. Pronunciation: IPA or phonetic hints for hard words/sentences.
        7. Difficulty: rate each line easy / medium / hard.

        Keep explanations short and concrete; never use terminology harder than the
        learner's level. For A1/A2 use very simple words and short sentences; for C1/C2
        you may include nuance and register notes.

        $schema
    """.trimIndent()

    /**
     * Mode 3 — Word Analysis.
     * A reusable prompt the user runs when they click/tap a word: paste the
     * word and its sentence, and the AI returns a JSON word-analysis object.
     */
    private fun buildWordAnalysisPrompt(
        level: String,
        levelName: String,
        targetLanguage: String
    ): String = """
        You are a word-analysis tutor for an English learner at $levelName ($level) level.

        The user will paste an English WORD and its SENTENCE (and optionally its translation).

        TASK: analyze the word and return a single JSON object:
        {
          "word": "the word",
          "translation": "its translation into $targetLanguage",
          "partOfSpeech": "noun|verb|adjective|adverb|pronoun|preposition|conjunction|interjection|phrase|...",
          "meaningInContext": "what the word means in THIS specific sentence",
          "extraExplanation": "a short explanation suitable for a $level learner",
          "examples": ["two or three additional example sentences"],
          "pronunciation": "IPA or phonetic if available"
        }

        Rules:
        - The explanation must be suitable for the learner's level ($level):
          simple words and short sentences for lower levels, nuance allowed for higher levels.
        - Identify the word's role (noun, verb, adjective, adverb, etc.) as used in the sentence.
        - Return ONLY the JSON object (no markdown, no extra text).
    """.trimIndent()
}
