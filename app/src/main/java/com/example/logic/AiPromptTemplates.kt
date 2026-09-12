package com.example.logic

/**
 * Ready-to-copy AI prompt templates for the "Tutorial & AI Learning" settings
 * section. Every prompt instructs the model to output JSON that is compatible
 * with the app's subtitle-learning JSON parser (see SubtitleJsonParser.kt and
 * model/JsonSubtitleModels.kt), so the generated file can be imported
 * straight into the video player's "JSON subtitle" slot.
 *
 * The templates carry four things that models reliably need and that the old
 * single-paragraph prompts were missing:
 *  1. a schema WITHOUT inline comments (the old one said "no comments" while
 *     showing `// comments` inside the JSON, which made models emit them),
 *  2. a short worked example (one SRT cue in, one JSON object out),
 *  3. chunking rules, because a feature film has 800-1500 cues and no model
 *     can answer that in one message,
 *  4. a final validation checklist, which is what actually prevents the
 *     truncated / fenced / trailing-comma output that fails the parser.
 */
object AiPromptTemplates {

    /** Supported CEFR levels, from beginner to native-like. */
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** Suggested "cues per request" values for long subtitle files. */
    val CHUNK_SIZES = listOf(25, 50, 100, 200)

    /** The prompt modes offered in the Tutorial section. */
    enum class PromptMode(val key: String) {
        TRANSLATION_ONLY("translation_only"),
        TRANSLATION_LEARNING("translation_learning"),
        VOCAB_PRONUNCIATION("vocab_pronunciation"),
        GRAMMAR_COACH("grammar_coach"),
        LEITNER_CARDS("leitner_cards"),
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

    /** Whether the mode produces an importable subtitle package. */
    fun producesSubtitlePackage(mode: PromptMode): Boolean = mode != PromptMode.WORD_ANALYSIS

    /**
     * Cleans up a language the learner typed for use in a prompt: trimmed, and
     * otherwise used EXACTLY as written. Nothing renames "آلمانی" to "German"
     * or "de" to "German" — how a language is spelled is the learner's choice,
     * and the model on the other end understands either.
     */
    fun normalizeLanguage(raw: String?): String = raw?.trim().orEmpty()

    /** The app's target-language setting, or the built-in default when blank. */
    fun normalizeTargetLanguage(raw: String?): String =
        normalizeLanguage(raw).ifEmpty { "Persian" }

    /** The app's source-language setting, or the built-in default when blank. */
    fun normalizeSourceLanguage(raw: String?): String =
        normalizeLanguage(raw).ifEmpty { "English" }

    /** Per-level writing rules. This is what actually makes output level-appropriate. */
    fun levelGuidance(
        level: String,
        targetLanguage: String,
        sourceLanguage: String = "English"
    ): String = when (level.uppercase()) {
        "A1" -> """
            LEVEL RULES (A1):
            - Assume a vocabulary of about 1000 words. Explain everything else.
            - Write every explanation in $targetLanguage, in short simple sentences.
            - Never use grammar jargon. Say "past form of the verb", not "preterite".
            - Pick 4-8 words per line, including very common verbs and pronouns.
        """.trimIndent()
        "A2" -> """
            LEVEL RULES (A2):
            - Assume about 2000 known words. Explanations in $targetLanguage.
            - Basic grammar names are fine (present perfect, comparative) but always
              add a one-line plain explanation next to the name.
            - Pick 4-7 words per line; include useful phrasal verbs.
        """.trimIndent()
        "B1" -> """
            LEVEL RULES (B1):
            - Assume about 3500 known words. Explanations mainly in $targetLanguage.
            - Standard grammar terminology is fine.
            - Skip obvious function words. Pick 3-6 words per line: less common verbs,
              collocations, phrasal verbs and idioms.
        """.trimIndent()
        "B2" -> """
            LEVEL RULES (B2):
            - Assume about 5000 known words. Explanations may mix $sourceLanguage and $targetLanguage.
            - Focus on nuance: connotation, formality, near-synonyms.
            - Pick 3-5 items per line, favouring idioms and collocations over single words.
        """.trimIndent()
        "C1" -> """
            LEVEL RULES (C1):
            - Explanations mostly in $sourceLanguage; use $targetLanguage only for tricky nuance.
            - Cover register, irony, implicature and stylistic choice.
            - Pick 2-4 items per line: only rare, idiomatic or culturally loaded ones.
        """.trimIndent()
        "C2" -> """
            LEVEL RULES (C2):
            - Explanations in $sourceLanguage; $targetLanguage only where the nuance is untranslatable.
            - Discuss dialect, era, sociolect, wordplay and authorial intent.
            - Pick 1-3 items per line. Skip anything an educated native would know.
        """.trimIndent()
        else -> "LEVEL RULES: write explanations suitable for a $level learner, in $targetLanguage."
    }

    /**
     * The exact JSON structure the app's parser accepts, with NO inline comments
     * (models copy comments into their output, which breaks strict JSON), plus a
     * separate field reference.
     */
    fun jsonSchemaSpec(
        targetLanguage: String = "Persian",
        level: String = "B1",
        sourceLanguage: String = "English"
    ): String = """
        OUTPUT SHAPE - return exactly this structure:
        {
          "formatVersion": 1,
          "metadata": {
            "language": "$sourceLanguage",
            "targetLanguage": "$targetLanguage",
            "level": "$level",
            "description": "Langosphere learning package"
          },
          "subtitles": [
            {
              "id": 1,
              "start": 12.4,
              "end": 15.8,
              "english": "original $sourceLanguage subtitle line",
              "translation": "the translated line",
              "level": "$level",
              "difficulty": "medium",
              "pronunciation": "sentence level phonetic hint",
              "notes": "short learning note",
              "lesson": {
                "explanation": "what this sentence teaches",
                "grammar": "grammar topic name",
                "grammarTranslation": "grammar topic name in $targetLanguage",
                "structure": "subject + verb + object breakdown"
              },
              "words": [
                {
                  "word": "waiting",
                  "translation": "word translation",
                  "partOfSpeech": "verb",
                  "meaningInContext": "what it means in THIS sentence",
                  "extraExplanation": "extra explanation for the level",
                  "examples": ["one more example sentence"],
                  "pronunciation": "IPA or phonetic"
                }
              ]
            }
          ]
        }

        FIELD REFERENCE:
        - id: the cue number from the source file. Number or string. Never renumber.
        - start / end: seconds as plain numbers (12.4), NOT "00:00:12,400".
        - english: the source line in $sourceLanguage, unchanged. Keep it even when
          translating. The KEY is always named "english" - that is the app's field
          name for "the original line", whatever language that line is in.
        - translation: the $targetLanguage line.
        - level / difficulty: optional per line. difficulty is easy, medium or hard.
        - pronunciation / notes: optional strings.
        - lesson: optional object with explanation, grammar, grammarTranslation, structure.
        - words: optional array; each item needs at least word and translation.
        - partOfSpeech: noun, verb, adjective, adverb, pronoun, preposition,
          conjunction, interjection, phrase, idiom.
        - Extra unknown fields are ignored by the app, but do not invent required ones.
        - Omit any field you cannot fill well. Never send an empty string.
    """.trimIndent()

    /**
     * A trimmed-down variant of [jsonSchemaSpec] for translation-only output:
     * only the fields that mode actually asks for (id/start/end/english/translation).
     * The full schema mentions grammar/words in its field reference, which used to
     * leak into the translation-only prompt even though that mode explicitly says
     * not to include lessons or word lists.
     */
    fun minimalJsonSchemaSpec(
        targetLanguage: String = "Persian",
        level: String = "B1",
        sourceLanguage: String = "English"
    ): String = """
        OUTPUT SHAPE - return exactly this structure:
        {
          "formatVersion": 1,
          "metadata": {
            "language": "$sourceLanguage",
            "targetLanguage": "$targetLanguage",
            "level": "$level",
            "description": "Langosphere learning package"
          },
          "subtitles": [
            {
              "id": 1,
              "start": 12.4,
              "end": 15.8,
              "english": "original $sourceLanguage subtitle line",
              "translation": "the translated line"
            }
          ]
        }

        FIELD REFERENCE:
        - id: the cue number from the source file. Number or string. Never renumber.
        - start / end: seconds as plain numbers (12.4), NOT "00:00:12,400".
        - english: the source line in $sourceLanguage, unchanged. The KEY is always
          named "english"; that is the app's field name for "the original line".
        - translation: the $targetLanguage line.
        - Extra unknown fields are ignored by the app, but do not invent required ones.
        - Omit any field you cannot fill well. Never send an empty string.
    """.trimIndent()

    /**
     * One cue in, one JSON object out. Short examples raise format compliance a
     * lot. The sample cue stays an English one (it is the app's own sample data)
     * and a note tells the model to keep the exact same shape for any other
     * source language.
     */
    fun workedExample(targetLanguage: String, sourceLanguage: String = "English"): String = """
        WORKED EXAMPLE${if (sourceLanguage == "English") "" else " (shown with an English line - use the same shape for your $sourceLanguage lines, and keep the key name \"english\")"}
        Input cue:
        42
        00:03:11,120 --> 00:03:13,480
        I've been waiting for this my whole life.

        Correct output object:
        {
          "id": 42,
          "start": 191.12,
          "end": 193.48,
          "english": "I've been waiting for this my whole life.",
          "translation": "<the same sentence in $targetLanguage>",
          "difficulty": "medium",
          "lesson": {
            "grammar": "Present perfect continuous",
            "grammarTranslation": "<grammar name in $targetLanguage>",
            "explanation": "An action that started in the past and still continues.",
            "structure": "subject + have/has been + verb-ing + object + time phrase"
          },
          "words": [
            {
              "word": "waiting",
              "translation": "<translation>",
              "partOfSpeech": "verb",
              "meaningInContext": "staying in place until something happens",
              "examples": ["She is waiting for the bus."],
              "pronunciation": "/ˈweɪ.tɪŋ/"
            }
          ]
        }
        Note how 00:03:11,120 became 191.12 seconds.
    """.trimIndent()

    /** Long-file handling. Without this, models silently truncate the JSON. */
    fun chunkingRules(chunkSize: Int): String = """
        LONG FILES - READ THIS CAREFULLY:
        - A feature film has 800-1500 cues. Do not attempt the whole file at once.
        - Process $chunkSize cues per answer, in file order, then STOP and wait for
          the word "continue" before doing the next $chunkSize.
        - Each answer must be a COMPLETE, VALID JSON object on its own, with the same
          formatVersion and metadata, so it can be imported by itself.
        - Keep the original cue ids so the chunks can be joined in order.
        - N input cues means exactly N subtitle objects: never merge, split, skip or
          summarise cues, even if two cues are one sentence.
        - If you are running out of room, close the JSON after the last COMPLETE object
          and state the id you stopped at. A truncated JSON is useless to the app.
        - Begin EVERY answer with the id range you are covering, on its own line
          BEFORE the JSON, in exactly this form: CHUNK 1-$chunkSize
          Use the real first and last cue ids of that answer, so the next answer
          starts where this one ended, for example CHUNK 51-100.
          The app detects this marker, removes it and merges every chunk of the
          film into one file by itself - so never skip it, never re-spell it and
          never put it inside the JSON.
    """.trimIndent()

    /** Final self-check. This removes most parser failures. */
    fun validationChecklist(sourceLanguage: String = "English"): String = """
        CHECK BEFORE YOU ANSWER:
        - Apart from the CHUNK marker line your answer is only the JSON: it starts
          with { and ends with }. No markdown fences, no commentary inside or after
          it, and no // or /* */ comments anywhere.
        - Valid strict JSON: double quotes only, no trailing commas, no NaN,
          numbers unquoted, all braces and brackets balanced.
        - Every input cue appears exactly once, in order, with its original id.
        - start and end are numbers in seconds.
        - No field is an empty string; omit it instead.
        - Apostrophes and quotes inside text are escaped correctly.
        - The translation has no leftover $sourceLanguage except proper nouns.
    """.trimIndent()

    /**
     * Builds the ready-to-copy prompt for the given CEFR level and mode.
     *
     * Both sides of the language pair are configurable (Settings ▸ Tutorial &
     * AI Learning): [sourceLanguage] is the language of the subtitle/text being
     * learned and [targetLanguage] is the language everything is explained and
     * translated into. Each one goes into the prompt exactly as the learner
     * typed it; both default to the pair the app shipped with.
     */
    fun buildPrompt(
        level: String,
        mode: PromptMode,
        targetLanguage: String = "Persian",
        chunkSize: Int = 50,
        sourceLanguage: String = "English"
    ): String {
        val normalizedLevel = level.uppercase()
        val levelName = levelDescription(normalizedLevel)
        val lang = normalizeTargetLanguage(targetLanguage)
        val src = normalizeSourceLanguage(sourceLanguage)
        return when (mode) {
            PromptMode.TRANSLATION_ONLY -> translationOnly(normalizedLevel, levelName, lang, src, chunkSize)
            PromptMode.TRANSLATION_LEARNING -> translationLearning(normalizedLevel, levelName, lang, src, chunkSize)
            PromptMode.VOCAB_PRONUNCIATION -> vocabPronunciation(normalizedLevel, levelName, lang, src, chunkSize)
            PromptMode.GRAMMAR_COACH -> grammarCoach(normalizedLevel, levelName, lang, src, chunkSize)
            PromptMode.LEITNER_CARDS -> leitnerCards(normalizedLevel, levelName, lang, src, chunkSize)
            PromptMode.WORD_ANALYSIS -> wordAnalysis(normalizedLevel, levelName, lang, src)
        }
    }

    // Mode 1 - translation only. Cheapest and fastest; nothing but the lines.
    private fun translationOnly(
        level: String,
        levelName: String,
        lang: String,
        src: String,
        chunkSize: Int
    ): String = """
        You are a professional subtitle translator preparing a file for a $levelName ($level) learner of $src.

        TASK: translate a $src subtitle file (SRT or VTT) into $lang.

        RULES:
        - Translate cue by cue, keeping every original timing.
        - Natural, idiomatic $lang. Translate meaning, not words.
        - Match the register: slang stays slangy, formal stays formal.
        - Keep names, brands and on-screen text as they are.
        - Use vocabulary and sentence length suitable for a $level learner.
        - Do NOT add lesson notes, vocabulary lists or explanations in this mode.
        - Include ONLY these fields per subtitle: id, start, end, english, translation.

        ${minimalJsonSchemaSpec(lang, level, src)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist(src)}
    """.trimIndent()

    // Mode 2 - the full learning package the app is built around.
    private fun translationLearning(
        level: String,
        levelName: String,
        lang: String,
        src: String,
        chunkSize: Int
    ): String = """
        You are an expert $src teacher and subtitle translator building a complete learning package for a $levelName ($level) learner whose language is $lang.

        TASK: turn a $src subtitle file (SRT or VTT) into a Langosphere JSON learning package.

        FOR EVERY CUE PROVIDE:
        1. translation - natural $lang, level appropriate, same register as the original.
        2. lesson.grammar - the single most useful grammar point in this line, plus
           lesson.grammarTranslation with that name in $lang.
        3. lesson.explanation - two or three sentences on what the line teaches.
        4. lesson.structure - break the sentence into its parts, for example
           "subject + have been + verb-ing + time phrase".
        5. words - the vocabulary items worth learning here. For each: translation, partOfSpeech,
           meaningInContext (its meaning in THIS line, not the dictionary entry),
           extraExplanation, at least one fresh example sentence, and pronunciation.
        6. notes - idiom, collocation, culture or slang note when there is one.
        7. pronunciation - only for genuinely hard words or contracted speech.
        8. difficulty - easy, medium or hard for this learner.

        QUALITY RULES:
        - meaningInContext is the most valuable field. Never copy the dictionary gloss.
        - Skip words the learner certainly knows at this level.
        - Example sentences must be new, short and about everyday situations.
        - When a line is only "Yeah." or a name, just translate it and omit lesson and words.
        - Never invent grammar that is not in the sentence. Omit lesson if there is none.
        - The "english" field always holds the ORIGINAL $src line, whatever language it is in.

        ${levelGuidance(level, lang, src)}

        ${jsonSchemaSpec(lang, level, src)}

        ${workedExample(lang, src)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist(src)}
    """.trimIndent()

    // Mode 3 - listening and pronunciation, which is what the player's listen mode needs.
    private fun vocabPronunciation(
        level: String,
        levelName: String,
        lang: String,
        src: String,
        chunkSize: Int
    ): String = """
        You are a pronunciation and listening coach for a $levelName ($level) learner of $src whose language is $lang.

        The learner watches films with the subtitles HIDDEN and reveals a line only after
        trying to hear it. Your job is to explain why a line is hard to HEAR, not to read.

        FOR EVERY CUE PROVIDE:
        1. translation - natural $lang.
        2. pronunciation - the whole line as it is really said at speed, with IPA and with
           linking and reductions marked, for example "wanna", "gonna", "lemme",
           "kinda", "I've been" spoken as /aɪv bɪn/.
        3. words - every word that is hard to hear. For each: translation, partOfSpeech,
           meaningInContext, pronunciation with IPA and the stressed syllable marked,
           and extraExplanation naming the listening trap: weak form, elision, flapped t,
           silent letter, minimal pair, homophone or unexpected stress.
        4. notes - the listening traps of the line, and any minimal pair worth drilling,
           for example "can vs can't", "leave vs live".
        5. difficulty - how hard the line is to HEAR, not to read.
        6. lesson - keep it short here: only lesson.explanation, and only when the
           difficulty comes from connected speech rather than vocabulary.

        RULES:
        - Always use standard IPA for $src and mark primary stress with the ' symbol.
        - Mention contractions and swallowed sounds explicitly. This is the point of the mode.
        - Do not fill words with easy items; three well-chosen words beat ten obvious ones.
        - Where a listening trap only exists in $src, say so instead of inventing one.

        ${levelGuidance(level, lang, src)}

        ${jsonSchemaSpec(lang, level, src)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist(src)}
    """.trimIndent()

    // Mode 4 - grammar first, with a small drill per line.
    private fun grammarCoach(
        level: String,
        levelName: String,
        lang: String,
        src: String,
        chunkSize: Int
    ): String = """
        You are a grammar coach for a $levelName ($level) learner of $src whose language is $lang.

        TASK: turn a $src subtitle file into a grammar course, one point per line.

        FOR EVERY CUE PROVIDE:
        1. translation - natural $lang.
        2. lesson.grammar - exactly ONE $src grammar point, the most useful one in the line.
        3. lesson.grammarTranslation - that grammar name in $lang.
        4. lesson.explanation - the rule in plain words: when it is used, how it is formed,
           and the mistake a $lang speaker typically makes with it.
        5. lesson.structure - the pattern with slots, for example
           "if + past simple, would + base verb", then the same pattern filled from this line.
        6. notes - one micro drill: a short question the learner can answer in their head,
           with the answer in brackets. Example: "Make it negative. (I haven't been waiting.)"
        7. difficulty - grammatical difficulty for this level.
        8. words - only words needed to understand the grammar point, at most three.

        RULES:
        - Do not repeat the same grammar point on consecutive lines. If a line has nothing
          new, pick a smaller detail such as article use, word order or preposition choice.
        - Contrast $src with $lang whenever the two differ, because that is where errors come from.
        - When a line truly has no grammar to teach, translate it and omit lesson.

        ${levelGuidance(level, lang, src)}

        ${jsonSchemaSpec(lang, level, src)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist(src)}
    """.trimIndent()

    // Mode 5 - flashcard harvest, still in the importable package format.
    private fun leitnerCards(
        level: String,
        levelName: String,
        lang: String,
        src: String,
        chunkSize: Int
    ): String = """
        You are building spaced repetition flashcards for a $levelName ($level) learner of $src whose language is $lang, from a $src subtitle file.

        TASK: keep ONLY the cues that contain something worth memorising, and turn the
        chosen items into flashcard material inside the Langosphere JSON format.

        SELECTION RULES:
        - Skip greetings, names, filler and anything an average $level learner knows.
        - Keep a cue when it contains a useful idiom, phrasal verb, collocation, or a word
          in the mid frequency band, which is exactly where progress happens.
        - At most three items per cue, and at most one card per distinct word in the whole file.
        - Prefer the whole chunk of language ("take it personally") over the bare word.

        FOR EACH KEPT CUE PROVIDE:
        1. english and translation - the line is the card's context sentence, so keep it.
           "english" holds the original $src line, "translation" its $lang version.
        2. words - the card items. For each:
           - word: the card front, the $src expression exactly as a learner should recall it.
           - translation: the card back in $lang, short and memorable. No essays.
           - meaningInContext: the meaning in this scene.
           - extraExplanation: how to remember it. Word family, literal image, false friend
             warning against $lang, or a common collocation.
           - examples: two short $src sentences in different situations.
           - pronunciation: IPA with stress.
           - partOfSpeech: including phrase or idiom when it is a multi word item.
        3. difficulty - how hard the item is to remember.
        4. notes - a one line memory hook when there is a good one.
        5. Omit lesson unless the grammar is part of the expression.

        Keep the original cue ids of the kept lines. Dropping cues is expected in this mode,
        and only here.

        ${levelGuidance(level, lang, src)}

        ${jsonSchemaSpec(lang, level, src)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist(src)}
    """.trimIndent()

    // Mode 6 - single word, for pasting into a chat while watching.
    private fun wordAnalysis(
        level: String,
        levelName: String,
        lang: String,
        src: String
    ): String = """
        You are a word analysis tutor for a $levelName ($level) learner of $src whose language is $lang.

        The user pastes a $src WORD together with the SENTENCE it appeared in, and
        sometimes the $lang translation of that sentence.

        TASK: analyse the word AS USED IN THAT SENTENCE and return one JSON object:
        {
          "word": "the word, in its dictionary form, with the form used in the sentence in brackets",
          "translation": "its $lang translation in this sense",
          "partOfSpeech": "noun, verb, adjective, adverb, pronoun, preposition, conjunction, interjection, phrase or idiom",
          "meaningInContext": "what it means in THIS sentence",
          "extraExplanation": "a short explanation for a $level learner, in $lang",
          "examples": ["two or three fresh $src example sentences"],
          "pronunciation": "IPA with primary stress marked"
        }

        RULES:
        - The word may have several meanings. Choose the one this sentence uses, and say so
          in one clause if the word is famously ambiguous.
        - If the word is part of a phrasal verb or idiom in this sentence, analyse the whole
          expression and put it in word, because the parts alone are misleading.
        - Mention a false friend against $lang whenever one exists.
        - Examples must be new, short and everyday. Never reuse the input sentence.
        - Explanations suitable for $level: simple and short at low levels, nuanced at high levels.
        - Return ONLY the JSON object. No markdown fences, no extra text, no comments.
    """.trimIndent()
}
