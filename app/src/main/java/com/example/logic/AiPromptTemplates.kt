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

    /** Display title for a mode. Kept here so no new string resources are needed. */
    fun modeTitle(mode: PromptMode, isEn: Boolean): String = when (mode) {
        PromptMode.TRANSLATION_ONLY -> if (isEn) "Translation only" else "فقط ترجمه"
        PromptMode.TRANSLATION_LEARNING -> if (isEn) "Translation + full lesson" else "ترجمه + درس کامل"
        PromptMode.VOCAB_PRONUNCIATION -> if (isEn) "Vocabulary & pronunciation" else "واژگان و تلفظ"
        PromptMode.GRAMMAR_COACH -> if (isEn) "Grammar coach" else "مربی گرامر"
        PromptMode.LEITNER_CARDS -> if (isEn) "Leitner flashcards" else "کارت‌های لایتنر"
        PromptMode.WORD_ANALYSIS -> if (isEn) "Single word analysis" else "تحلیل یک واژه"
    }

    /** Short description for a mode. */
    fun modeDescription(mode: PromptMode, isEn: Boolean): String = when (mode) {
        PromptMode.TRANSLATION_ONLY ->
            if (isEn) "Fastest and cheapest: line, timing, translation. Nothing else."
            else "سریع‌ترین و ارزان‌ترین: خط، زمان‌بندی و ترجمه. همین."
        PromptMode.TRANSLATION_LEARNING ->
            if (isEn) "The complete package: translation, grammar, structure, words, notes."
            else "بسته‌ی کامل: ترجمه، گرامر، ساختار، واژگان و یادداشت."
        PromptMode.VOCAB_PRONUNCIATION ->
            if (isEn) "For listen mode: IPA, stress, fast-speech forms and listening traps."
            else "برای حالت گوش کن: تلفظ، تکیه، شکل محاوره‌ای و دام‌های شنیداری."
        PromptMode.GRAMMAR_COACH ->
            if (isEn) "One grammar point per line, explained and drilled step by step."
            else "هر خط یک نکته‌ی گرامری، با توضیح و تمرین پله‌به‌پله."
        PromptMode.LEITNER_CARDS ->
            if (isEn) "Only the words worth memorizing, written as flashcard fronts and backs."
            else "فقط واژه‌های ارزش حفز‌کردن، در قالب رو و پشت کارت."
        PromptMode.WORD_ANALYSIS ->
            if (isEn) "Paste one word and its sentence, get a single word-analysis object."
            else "یک واژه و جمله‌اش را می‌دهی، تحلیل کامل می‌گیری."
    }

    /** Whether the mode produces an importable subtitle package. */
    fun producesSubtitlePackage(mode: PromptMode): Boolean = mode != PromptMode.WORD_ANALYSIS

    /** Step-by-step usage instructions shown in the tutorial dialog. */
    fun usageSteps(isEn: Boolean): List<String> = if (isEn) {
        listOf(
            "Pick your level and a mode, then copy the prompt.",
            "Open any AI chat, paste the prompt, then attach or paste your .srt file.",
            "Save the JSON answer as a .json file (or copy it).",
            "In the app: import section > JSON subtitle > select file or paste.",
            "For long films, ask for the next chunk until the file is finished."
        )
    } else {
        listOf(
            "سطح و حالت را انتخاب کن و پرامپت را کپی کن.",
            "در هر چت هوش مصنوعی، پرامپت را پیست کن و بعد فایل srt را بفرست.",
            "جواب JSON را در یک فایل json ذخیره کن یا کپی کن.",
            "در برنامه: بخش افزودن فایل ▸ زیرنویس JSON ▸ انتخاب فایل یا پیست.",
            "برای فیلم‌های بلند، تا تمام شدن فایل بخش بعدی را بخواه."
        )
    }

    /** Maps the app's target-language setting to an English language name. */
    fun normalizeTargetLanguage(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "Persian"
        return when (value.lowercase()) {
            "fa", "fa-ir", "persian", "farsi", "فارسی", "پارسی" -> "Persian"
            "en", "english", "انگلیسی" -> "English"
            "ar", "arabic", "عربی" -> "Arabic"
            "tr", "turkish", "ترکی", "ترکی استانبولی" -> "Turkish"
            "de", "german", "آلمانی", "المانی" -> "German"
            "fr", "french", "فرانسوی" -> "French"
            "es", "spanish", "اسپانیایی" -> "Spanish"
            "ru", "russian", "روسی" -> "Russian"
            else -> value
        }
    }

    /** Per-level writing rules. This is what actually makes output level-appropriate. */
    fun levelGuidance(level: String, targetLanguage: String): String = when (level.uppercase()) {
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
            - Assume about 5000 known words. Explanations may mix English and $targetLanguage.
            - Focus on nuance: connotation, formality, near-synonyms.
            - Pick 3-5 items per line, favouring idioms and collocations over single words.
        """.trimIndent()
        "C1" -> """
            LEVEL RULES (C1):
            - Explanations mostly in English; use $targetLanguage only for tricky nuance.
            - Cover register, irony, implicature and stylistic choice.
            - Pick 2-4 items per line: only rare, idiomatic or culturally loaded ones.
        """.trimIndent()
        "C2" -> """
            LEVEL RULES (C2):
            - Explanations in English; $targetLanguage only where the nuance is untranslatable.
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
    fun jsonSchemaSpec(targetLanguage: String = "Persian", level: String = "B1"): String = """
        OUTPUT SHAPE - return exactly this structure:
        {
          "formatVersion": 1,
          "metadata": {
            "language": "English",
            "targetLanguage": "$targetLanguage",
            "level": "$level",
            "description": "Langosphere learning package"
          },
          "subtitles": [
            {
              "id": 1,
              "start": 12.4,
              "end": 15.8,
              "english": "original English subtitle line",
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
        - english: the source line, unchanged. Keep it even when translating.
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

    /** One cue in, one JSON object out. Short examples raise format compliance a lot. */
    fun workedExample(targetLanguage: String): String = """
        WORKED EXAMPLE
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
        - Begin each answer with the id range you are covering, on a line BEFORE the
          JSON, in this form: CHUNK 1-$chunkSize
    """.trimIndent()

    /** Final self-check. This removes most parser failures. */
    fun validationChecklist(): String = """
        CHECK BEFORE YOU ANSWER:
        - The JSON starts with { and ends with }. No markdown fences, no commentary
          inside or after it, and no // or /* */ comments anywhere.
        - Valid strict JSON: double quotes only, no trailing commas, no NaN,
          numbers unquoted, all braces and brackets balanced.
        - Every input cue appears exactly once, in order, with its original id.
        - start and end are numbers in seconds.
        - No field is an empty string; omit it instead.
        - Apostrophes and quotes inside text are escaped correctly.
        - The translation has no leftover English except proper nouns.
    """.trimIndent()

    /**
     * Builds the ready-to-copy prompt for the given CEFR level and mode.
     */
    fun buildPrompt(
        level: String,
        mode: PromptMode,
        targetLanguage: String = "Persian",
        chunkSize: Int = 50
    ): String {
        val normalizedLevel = level.uppercase()
        val levelName = levelDescription(normalizedLevel)
        val lang = normalizeTargetLanguage(targetLanguage)
        return when (mode) {
            PromptMode.TRANSLATION_ONLY -> translationOnly(normalizedLevel, levelName, lang, chunkSize)
            PromptMode.TRANSLATION_LEARNING -> translationLearning(normalizedLevel, levelName, lang, chunkSize)
            PromptMode.VOCAB_PRONUNCIATION -> vocabPronunciation(normalizedLevel, levelName, lang, chunkSize)
            PromptMode.GRAMMAR_COACH -> grammarCoach(normalizedLevel, levelName, lang, chunkSize)
            PromptMode.LEITNER_CARDS -> leitnerCards(normalizedLevel, levelName, lang, chunkSize)
            PromptMode.WORD_ANALYSIS -> wordAnalysis(normalizedLevel, levelName, lang)
        }
    }

    // Mode 1 - translation only. Cheapest and fastest; nothing but the lines.
    private fun translationOnly(
        level: String,
        levelName: String,
        lang: String,
        chunkSize: Int
    ): String = """
        You are a professional subtitle translator preparing a file for a $levelName ($level) learner of English.

        TASK: translate an English subtitle file (SRT or VTT) into $lang.

        RULES:
        - Translate cue by cue, keeping every original timing.
        - Natural, idiomatic $lang. Translate meaning, not words.
        - Match the register: slang stays slangy, formal stays formal.
        - Keep names, brands and on-screen text as they are.
        - Use vocabulary and sentence length suitable for a $level learner.
        - Do NOT add grammar notes, word lists or explanations in this mode.
        - Include ONLY these fields per subtitle: id, start, end, english, translation.

        ${jsonSchemaSpec(lang, level)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist()}
    """.trimIndent()

    // Mode 2 - the full learning package the app is built around.
    private fun translationLearning(
        level: String,
        levelName: String,
        lang: String,
        chunkSize: Int
    ): String = """
        You are an expert English teacher and subtitle translator building a complete learning package for a $levelName ($level) learner whose language is $lang.

        TASK: turn an English subtitle file (SRT or VTT) into a Langosphere JSON learning package.

        FOR EVERY CUE PROVIDE:
        1. translation - natural $lang, level appropriate, same register as the original.
        2. lesson.grammar - the single most useful grammar point in this line, plus
           lesson.grammarTranslation with that name in $lang.
        3. lesson.explanation - two or three sentences on what the line teaches.
        4. lesson.structure - break the sentence into its parts, for example
           "subject + have been + verb-ing + time phrase".
        5. words - the items worth learning here. For each: translation, partOfSpeech,
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

        ${levelGuidance(level, lang)}

        ${jsonSchemaSpec(lang, level)}

        ${workedExample(lang)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist()}
    """.trimIndent()

    // Mode 3 - listening and pronunciation, which is what the player's listen mode needs.
    private fun vocabPronunciation(
        level: String,
        levelName: String,
        lang: String,
        chunkSize: Int
    ): String = """
        You are a pronunciation and listening coach for a $levelName ($level) learner of English whose language is $lang.

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
        - Always use standard IPA and mark primary stress with the ' symbol.
        - Mention contractions and swallowed sounds explicitly. This is the point of the mode.
        - Do not fill words with easy items; three well-chosen words beat ten obvious ones.

        ${levelGuidance(level, lang)}

        ${jsonSchemaSpec(lang, level)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist()}
    """.trimIndent()

    // Mode 4 - grammar first, with a small drill per line.
    private fun grammarCoach(
        level: String,
        levelName: String,
        lang: String,
        chunkSize: Int
    ): String = """
        You are a grammar coach for a $levelName ($level) learner of English whose language is $lang.

        TASK: turn an English subtitle file into a grammar course, one point per line.

        FOR EVERY CUE PROVIDE:
        1. translation - natural $lang.
        2. lesson.grammar - exactly ONE grammar point, the most useful one in the line.
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
        - Contrast with $lang when the languages differ, because that is where errors come from.
        - When a line truly has no grammar to teach, translate it and omit lesson.

        ${levelGuidance(level, lang)}

        ${jsonSchemaSpec(lang, level)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist()}
    """.trimIndent()

    // Mode 5 - flashcard harvest, still in the importable package format.
    private fun leitnerCards(
        level: String,
        levelName: String,
        lang: String,
        chunkSize: Int
    ): String = """
        You are building spaced repetition flashcards for a $levelName ($level) learner of English whose language is $lang, from an English subtitle file.

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
        2. words - the card items. For each:
           - word: the card front, the expression exactly as a learner should recall it.
           - translation: the card back, short and memorable. No essays.
           - meaningInContext: the meaning in this scene.
           - extraExplanation: how to remember it. Word family, literal image, false friend
             warning against $lang, or a common collocation.
           - examples: two short sentences in different situations.
           - pronunciation: IPA with stress.
           - partOfSpeech: including phrase or idiom when it is a multi word item.
        3. difficulty - how hard the item is to remember.
        4. notes - a one line memory hook when there is a good one.
        5. Omit lesson unless the grammar is part of the expression.

        Keep the original cue ids of the kept lines. Dropping cues is expected in this mode,
        and only here.

        ${levelGuidance(level, lang)}

        ${jsonSchemaSpec(lang, level)}

        ${chunkingRules(chunkSize)}

        ${validationChecklist()}
    """.trimIndent()

    // Mode 6 - single word, for pasting into a chat while watching.
    private fun wordAnalysis(
        level: String,
        levelName: String,
        lang: String
    ): String = """
        You are a word analysis tutor for a $levelName ($level) learner of English whose language is $lang.

        The user pastes an English WORD together with the SENTENCE it appeared in, and
        sometimes the translation of that sentence.

        TASK: analyse the word AS USED IN THAT SENTENCE and return one JSON object:
        {
          "word": "the word, in its dictionary form, with the form used in the sentence in brackets",
          "translation": "its $lang translation in this sense",
          "partOfSpeech": "noun, verb, adjective, adverb, pronoun, preposition, conjunction, interjection, phrase or idiom",
          "meaningInContext": "what it means in THIS sentence",
          "extraExplanation": "a short explanation for a $level learner",
          "examples": ["two or three fresh example sentences"],
          "pronunciation": "IPA with primary stress marked"
        }

        RULES:
        - The word may have several meanings. Choose the one this sentence uses, and say so
          in one clause if the word is famously ambiguous.
        - If the word is part of a phrasal verb or idiom in this sentence, analyse the whole
          expression and put it in word, because the parts alone are misleading.
        - Mention a false friend against $lang whenever one exists.
        - Examples must be new, short and everyday. Never reuse the input sentence.
        - ${'$'}Explanations suitable for $level: simple and short at low levels, nuanced at high levels.
        - Return ONLY the JSON object. No markdown fences, no extra text, no comments.
    """.trimIndent()
}
