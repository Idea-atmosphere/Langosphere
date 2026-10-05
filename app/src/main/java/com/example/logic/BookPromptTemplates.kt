package com.example.logic

/**
 * The book half of the AI prompt system.
 *
 * The movie/subtitle prompts in [AiPromptTemplates] are a separate, stable
 * file and are deliberately NOT touched by anything here. The two share the
 * output envelope (`formatVersion`, `metadata`, `subtitles[]`) so a book
 * package still imports into the app's existing JSON learning, quiz and
 * Leitner pipelines, but the contracts differ in the way that matters:
 *
 *  - a subtitle cue is anchored to seconds of video; a book entry is anchored
 *    to a page/chapter and a sentence ordinal (`p. 128 s. 1`);
 *  - a subtitle cue is already one short line; a book has to be cut up, and
 *    the unit of that cut is the SENTENCE, not the paragraph.
 *
 * ## Where the rules live (v3)
 *
 * Up to v2 the copied book text repeated the whole contract in front of every
 * chunk: rules, schema, examples. That wasted tokens, buried the actual text,
 * and let two copies of the contract drift apart. From v3 the contract lives
 * here only - the user pastes this prompt once at the top of the chat - and
 * the copied chunk carries nothing but anchors:
 *
 * ```
 * @LANGO v3 | ids 21-35 | pages 12-13 | src=English | dst=Persian | B1
 * [21|p12s1] The house on the hill had been empty for years.
 * [22|p12s2] No one remembered who had built it.
 * @END 21-35
 * ```
 *
 * So the prompt's job is to explain `[id|pXsY]` precisely, and to forbid the
 * two failures that broke the reader: renumbering ids, and inventing
 * `location` values instead of echoing the marker. Both of them make the
 * page-scoped merge in the reader fail, which is what put translations in the
 * "other sentences" bucket and made them surface on the wrong page.
 *
 * ## Dynamic CEFR
 *
 * The level is never baked into the text. [buildTemplate] emits
 * [CEFR_PLACEHOLDER] and [CEFR_GUIDANCE_PLACEHOLDER], and [resolveLevel]
 * substitutes them. One stored template therefore serves A1 through C2 and a
 * level change needs no edit to any prompt text.
 */
object BookPromptTemplates {

    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** Token placeholder for the learner's CEFR level inside an unresolved template. */
    const val CEFR_PLACEHOLDER = "{CEFR_LEVEL}"

    /** Token placeholder for the level-specific teaching guidance. */
    const val CEFR_GUIDANCE_PLACEHOLDER = "{CEFR_GUIDANCE}"

    /** Used when no level is known; never hardcoded inside prompt text. */
    const val DEFAULT_LEVEL = "B1"

    /** Payload contract version, kept in step with [BookSlicePayload.VERSION]. */
    const val PAYLOAD_VERSION = 3

    /** Sentence batch bounds. Below the minimum the round trips are wasteful; above the maximum replies truncate. */
    const val MIN_CHUNK_SENTENCES = 10
    const val MAX_CHUNK_SENTENCES = 20
    const val DEFAULT_CHUNK_SENTENCES = 15

    /** Hard cap on `words` per sentence. A single sentence rarely holds more worth learning. */
    const val MAX_WORDS_PER_SENTENCE = 3

    /** The vocabulary-mining mode has the same cap: it is per sentence, not per paragraph. */
    const val MAX_WORDS_VOCAB_MODE = 3

    /** Sentences per request, offered in Settings. */
    val CHUNK_SIZES = listOf(MIN_CHUNK_SENTENCES, DEFAULT_CHUNK_SENTENCES, MAX_CHUNK_SENTENCES)

    enum class BookPromptMode(val key: String) {
        TRANSLATION_ONLY("book_translation_only"),
        READING_LEARNING("book_reading_learning"),
        VOCAB_MINING("book_vocab_mining"),
        GRAMMAR_COACH("book_grammar_coach"),
        SIMPLIFY("book_simplify"),
        COMPREHENSION("book_comprehension"),
        WORD_ANALYSIS("book_word_analysis"),
    }

    /** True when the mode returns an importable JSON package (vs. a chat answer). */
    fun producesJsonPackage(mode: BookPromptMode): Boolean = when (mode) {
        BookPromptMode.TRANSLATION_ONLY,
        BookPromptMode.READING_LEARNING,
        BookPromptMode.VOCAB_MINING,
        BookPromptMode.SIMPLIFY -> true
        BookPromptMode.GRAMMAR_COACH,
        BookPromptMode.COMPREHENSION,
        BookPromptMode.WORD_ANALYSIS -> false
    }

    fun modeName(mode: BookPromptMode, fa: Boolean): String = when (mode) {
        BookPromptMode.TRANSLATION_ONLY -> if (fa) "فقط ترجمهٔ جمله‌به‌جمله" else "Translation only"
        BookPromptMode.READING_LEARNING -> if (fa) "ترجمه + آموزش کامل" else "Translation + full lesson"
        BookPromptMode.VOCAB_MINING -> if (fa) "استخراج واژگان و لایتنر" else "Vocabulary mining & Leitner"
        BookPromptMode.GRAMMAR_COACH -> if (fa) "مربی گرامر کتاب" else "Grammar coach"
        BookPromptMode.SIMPLIFY -> if (fa) "ساده‌سازی در سطح خودت" else "Graded rewrite (simplify)"
        BookPromptMode.COMPREHENSION -> if (fa) "خلاصه و سوال درک مطلب" else "Summary & comprehension"
        BookPromptMode.WORD_ANALYSIS -> if (fa) "تحلیل یک واژه یا جمله" else "Analyse a word or sentence"
    }

    fun modeDescription(mode: BookPromptMode, fa: Boolean): String = when (mode) {
        BookPromptMode.TRANSLATION_ONLY ->
            if (fa) "جمله‌به‌جمله ترجمه می‌کند؛ سبک‌ترین خروجی برای خواندن دوزبانه."
            else "Sentence-by-sentence translation. The lightest package, for side-by-side reading."
        BookPromptMode.READING_LEARNING ->
            if (fa) "برای هر جمله ترجمه، گرامر، ساختار، تلفظ IPA و ۲ تا ۳ واژهٔ کلیدی می‌دهد."
            else "Per sentence: translation, grammar, structure, IPA and 2-3 key words."
        BookPromptMode.VOCAB_MINING ->
            if (fa) "هر جمله را ترجمه می‌کند و واژگان ارزشمندش را با جملهٔ خودش بیرون می‌کشد؛ مناسب جعبهٔ لایتنر."
            else "Translates every sentence and pulls the worthwhile words with their own sentence as context - ideal for Leitner cards."
        BookPromptMode.GRAMMAR_COACH ->
            if (fa) "جمله‌های سخت متن را موشکافی می‌کند و قالب‌های تکرارشونده را درس می‌دهد (خروجی گفتگویی)."
            else "Dissects the hard sentences and teaches the recurring patterns (chat answer, not a file)."
        BookPromptMode.SIMPLIFY ->
            if (fa) "هر جمله را در سطح زبانی خودت بازنویسی و کنار متن اصلی می‌گذارد (کتاب سطح‌بندی‌شده)."
            else "Rewrites each sentence at your own level and keeps the original beside it (a graded reader)."
        BookPromptMode.COMPREHENSION ->
            if (fa) "خلاصهٔ فصل، پیرنگ، شخصیت‌ها و چند سوال درک مطلب می‌سازد (خروجی گفتگویی)."
            else "Chapter summary, plot, characters and a few comprehension questions (chat answer)."
        BookPromptMode.WORD_ANALYSIS ->
            if (fa) "برای وقتی در وسط خواندن یک واژه یا جمله گیرت می‌کند؛ تحلیل عمیق همان یک مورد."
            else "For when one word or sentence blocks you mid-page: a deep dive on just that item."
    }

    /**
     * Builds the final prompt the user copies into an AI chat, with the level
     * already resolved.
     *
     * @param level CEFR level of the learner (A1…C2); invalid input falls back
     *   to [DEFAULT_LEVEL].
     * @param mode which learning goal, see [BookPromptMode].
     * @param targetLanguage the language explanations and translations use.
     * @param chunkSize how many SENTENCES per request; clamped to
     *   [MIN_CHUNK_SENTENCES]…[MAX_CHUNK_SENTENCES].
     * @param sourceLanguage the language the book is written in; blank means
     *   the model should detect it from the first chunk.
     * @param bookTitle optional, used in metadata and location strings.
     */
    fun buildPrompt(
        level: String,
        mode: BookPromptMode,
        targetLanguage: String,
        chunkSize: Int,
        sourceLanguage: String,
        bookTitle: String = "",
    ): String = resolveLevel(
        template = buildTemplate(
            mode = mode,
            targetLanguage = targetLanguage,
            chunkSize = chunkSize,
            sourceLanguage = sourceLanguage,
            bookTitle = bookTitle,
        ),
        level = level,
    )

    /**
     * Builds the prompt with the level left as [CEFR_PLACEHOLDER].
     *
     * This is the form worth storing and showing in Settings: it is level
     * agnostic, so the same template follows the learner from A1 to C2 and a
     * level change requires no prompt edit.
     */
    fun buildTemplate(
        mode: BookPromptMode,
        targetLanguage: String,
        chunkSize: Int,
        sourceLanguage: String,
        bookTitle: String = "",
    ): String {
        val target = normalizeTarget(targetLanguage)
        val source = normalizeSource(sourceLanguage)
        val sentences = normalizeChunkSize(chunkSize)
        val title = bookTitle.trim()

        return buildString {
            appendLine("You are an expert $source teacher and literary translator.")
            appendLine("Your student reads books in $source and needs help in $target.")
            appendLine("Student CEFR level: $CEFR_PLACEHOLDER. $CEFR_GUIDANCE_PLACEHOLDER")
            if (title.isNotEmpty()) appendLine("Book: \"$title\".")
            appendLine()
            appendLine("Read this whole message once. I will then paste chunks of a book, one chunk per message, and you answer each chunk with one JSON object. The rules never change between chunks, so I will not repeat them.")
            appendLine()
            appendLine("## Task")
            appendLine(taskFor(mode, source, target))
            appendLine()
            appendLine("## How my chunks look (the anchor contract)")
            appendLine(payloadContract())
            appendLine()
            appendLine("## The unit of work is ONE SENTENCE")
            appendLine(sentenceRules(sentences))
            appendLine()
            appendLine("## Cleaning book text")
            appendLine(bookRules(source))
            appendLine()
            appendLine("## Anchoring (this is what makes the app able to show your work)")
            appendLine(syncRules(target))
            appendLine()
            if (producesJsonPackage(mode)) {
                appendLine("## Output format")
                appendLine("Reply with ONE JSON object and nothing else: no greeting, no explanation outside the JSON, no markdown fences.")
                appendLine(schemaFor(mode, source, target, title))
                appendLine()
                appendLine("## Worked example (shape only - do not copy the content)")
                appendLine(workedExample(mode, target))
                appendLine()
                appendLine("## Before you answer, check")
                appendLine(validationChecklist(mode))
            } else {
                appendLine("## Answer style")
                appendLine(chatStyle(mode, source, target))
            }
            appendLine()
            appendLine("## My text")
            appendLine("Everything after the line below is the first chunk: one `@LANGO` header line, then the sentence lines, then `@END`. Answer only for the ids in that chunk and wait for the next one.")
            appendLine("---")
            append("PASTE BOOK SENTENCES HERE")
        }
    }

    /**
     * Substitutes the CEFR placeholders in a template.
     *
     * Safe to call on already-resolved text: it is a plain replacement, so a
     * template that contains no placeholder is returned unchanged.
     */
    fun resolveLevel(template: String, level: String): String {
        val safe = safeLevel(level)
        return template
            .replace(CEFR_GUIDANCE_PLACEHOLDER, levelGuidance(safe))
            .replace(CEFR_PLACEHOLDER, safe)
    }

    /** The nearest valid CEFR level, or [DEFAULT_LEVEL]. */
    fun safeLevel(level: String): String =
        LEVELS.firstOrNull { it.equals(level.trim(), ignoreCase = true) } ?: DEFAULT_LEVEL

    /** Clamps a requested batch size into the range replies can actually hold. */
    fun normalizeChunkSize(chunkSize: Int): Int =
        if (chunkSize <= 0) DEFAULT_CHUNK_SENTENCES
        else chunkSize.coerceIn(MIN_CHUNK_SENTENCES, MAX_CHUNK_SENTENCES)

    // ──────── internals ────────

    private fun normalizeTarget(language: String): String =
        language.trim().ifEmpty { "Persian" }

    private fun normalizeSource(language: String): String =
        language.trim().ifEmpty { "the book's own language" }

    private fun levelGuidance(level: String): String = when (level.uppercase()) {
        "A1" -> "Explain with very short sentences and the most common 1000 words. Translate idioms fully; assume no grammar vocabulary is known."
        "A2" -> "Keep explanations short and concrete. Name tenses in plain words, and give one simple extra example per new structure."
        "B1" -> "Explanations may use basic grammar terminology. Point out phrasal verbs, collocations and register shifts."
        "B2" -> "Use normal grammar terminology. Focus on nuance, connotation, tone and less obvious idioms rather than basic words."
        "C1" -> "Skip elementary help. Concentrate on style, authorial voice, rhetoric, rare or literary vocabulary and cultural references."
        "C2" -> "Treat the student as near-native: etymology, historical usage, literary allusions, translation trade-offs and register subtleties."
        else -> "Explanations may use basic grammar terminology."
    }

    /**
     * The v3 payload contract. This block is the single most important part of
     * the prompt: the app re-anchors every returned entry by id and by the
     * `pXsY` marker, so a model that renumbers or invents locations produces a
     * package the reader cannot place on the page.
     */
    private fun payloadContract(): String = buildString {
        appendLine("Each chunk starts with one header line and ends with an `@END` line:")
        appendLine("```")
        appendLine("@LANGO v$PAYLOAD_VERSION | ids 21-35 | pages 12-13 | src=English | dst=Persian | B1")
        appendLine("[21|p12s1] The house on the hill had been empty for years.")
        appendLine("[22|p12s2] No one remembered who had built it.")
        appendLine("@END 21-35")
        appendLine("```")
        appendLine("- `ids 21-35` is the id range. Return exactly these ids, in this order, one entry each.")
        appendLine("- `[21|p12s1]` is the anchor of that line: id 21, page 12, sentence 1 of page 12. `c4s12` means chapter 4, sentence 12 (EPUB).")
        appendLine("- The anchor is NOT part of the sentence. Strip `[...]` and the following space before you use the text.")
        appendLine("- Turn the marker into `location` mechanically: `p12s1` becomes \"p. 12 s. 1\", `c4s12` becomes \"Ch. 4 s. 12\". Also copy the two numbers into `page` and `sentenceInPage`. Never invent, guess, shift or renumber a location - if you cannot read a marker, still return the id and repeat the marker text you saw.")
        appendLine("- MULTI-PAGE BOUNDARY STRICTNESS: When this chunk spans multiple pages (e.g., from page 10 to page 15), enforce exact page anchoring. The moment markers transition from `p10sX` to `p11s1`, strictly set `page: 11` and reset `sentenceInPage: 1`. Never shift a sentence to the wrong page.")
        appendLine("- 1-TO-1 PARITY: Exactly one JSON entry per input ID. Do not merge two lines; do not split one line into two entries. This preserves side-by-side bilingual layout alignment.")
        appendLine("- `src=` is the book's language; if it says `auto`, detect it from the text and report it in `metadata.language` (use the English name of the language).")
        appendLine("- `dst=` is the language I want translations and explanations in. The trailing code such as `B1` is my CEFR level for this book and overrides the level above if they differ.")
        appendLine()
        appendLine("- A line may be a heading, a caption or an OCR fragment. It still gets its own entry with its own id; never merge it into a neighbour and never drop it.")
        append("- Do not merge front matter. These are THREE entries, never one: `[27|p1s27] -- Jim Williams, late Analog guru, Linear Technology Corp.`, `[28|p1s28] THE ART OF ELECTRONICS Third Edition`, `[29|p1s29] At long last, here is the thoroughly revised and updated third edition.` A merged entry cannot be placed on any single line, so its translation never appears under the line it belongs to.")
    }

    /**
     * The sentence contract: one entry per sentence, ids that line up with my
     * numbering, a hard word cap, and an instruction to shrink the batch
     * rather than truncate it.
     */
    private fun sentenceRules(sentences: Int): String = buildString {
        appendLine("- ONE SENT LINE = ONE ENTRY in `subtitles`. Never merge two lines into one entry and never split one line across two entries.")
        appendLine("- 100% coverage: every id in the range gets an entry with a non-empty `translation`. Nothing is too trivial, too short or too repetitive to translate.")
        appendLine("- Translate the whole line, not a summary of it. Do not drop adjectives, adverbs or function words; keep names and places, and give them IPA in `pronunciation` instead of translating them.")
        appendLine("- Keep dialogue punctuation with the sentence it belongs to. A quoted line plus its `he said` tag is ONE sentence, and it arrives as one line anyway.")
        appendLine("- I send about $sentences lines per chunk (between $MIN_CHUNK_SENTENCES and $MAX_CHUNK_SENTENCES). Finish exactly the chunk you were given, then stop.")
        appendLine("- Budget your reply BEFORE writing it. At most $MAX_WORDS_PER_SENTENCE entries in `words` per sentence, and only genuinely high-impact items. Prefer fewer words with good explanations over padding.")
        append("- If the chunk would not fit in one reply, answer with FEWER entries, close the JSON properly, and say which id you stopped at. A truncated JSON object costs me the whole chunk; a short but valid one costs nothing, because the app imports it and asks you for the rest.")
    }

    /**
     * Text-cleaning rules for extracted book text.
     *
     * De-hyphenation is the load-bearing part: the reader re-finds every page
     * sentence by character matching, so an `english` value that still carries
     * a word broken across a line (`infor-` + line break + `mation`) matches
     * nothing and the translation lands in the wrong bucket. The rule hands
     * that repair to the model explicitly instead of trusting a silent clean.
     */
    private fun bookRules(source: String): String = buildString {
        appendLine("- The lines come from PDF or EPUB text extraction. Clean layout and OCR artefacts silently:")
        appendLine("  1. HYPHENATION FIX: Automatically rejoin words split across line breaks with a hyphen (e.g., \"inves-\\ntigation\" must become \"investigation\"). Never leave broken hyphens inside the `english` field.")
        appendLine("  2. WHITESPACE: Collapse multiple spaces and remove spurious line-breaks inside a sentence.")
        appendLine("  3. STRUCTURAL LINES: A line that contains only a header, chapter title, or bare page number must still keep its ID and marker; return it with literal translation.")
        appendLine("- The `english` field MUST remain identical to the original $source sentence (barring hyphenation fixes). The reader aligns translation and lessons to page sentences using character matching.")
        append("- Never hallucinate, merge sentences, or insert text not present in the chunk.")
    }

    /**
     * Anchor rules for the returned package.
     *
     * Multi-page chunks are the case that produced the page-shift bug: when a
     * copy spans pages 10-15, `page` and `sentenceInPage` must follow the
     * marker at every transition, and the sentence count restarts at 1 on each
     * new page. The 1-to-1 rule is what keeps the bilingual layout aligned -
     * one marker in, exactly one entry out.
     */
    private fun syncRules(target: String): String = buildString {
        appendLine("- MULTI-PAGE BOUNDARY STRICTNESS: When this chunk spans multiple pages (e.g., from page 10 to page 15), enforce exact page anchoring. The moment markers transition from `p10sX` to `p11s1`, strictly set `page: 11` and reset `sentenceInPage: 1`. Never shift a sentence to the wrong page.")
        appendLine("- 1-TO-1 PARITY: Exactly one JSON entry per input ID. Do not merge two lines; do not split one line into two entries. This preserves side-by-side bilingual layout alignment.")
        appendLine("- `id` must equal the anchor ID. `start` = `id` - 1, `end` = `id`.")
        appendLine("- `location` must strictly match the marker format (e.g., \"p. 12 s. 3\").")
        appendLine("- `translation`: Natural, fluent $target translation matching the exact scope of the original sentence.")
    }

    private fun schemaFor(
        mode: BookPromptMode,
        source: String,
        target: String,
        title: String,
    ): String {
        val bookTitleLine = if (title.isEmpty()) "" else "\n    \"bookTitle\": \"$title\","
        val header = """
{
  "formatVersion": 1,
  "metadata": {
    "source": "book",$bookTitleLine
    "language": "$source",
    "targetLanguage": "$target",
    "level": "$CEFR_PLACEHOLDER",
    "chunk": "<the ids range you answered, e.g. 21-35>",
    "description": "<one line about this chunk>"
  },
  "subtitles": [ ... one object per SENTENCE LINE ... ]
}
""".trim()

        val anchors = """
Anchor fields (these replace a video's timeline and are required on every entry):
  "id"             : integer, the id from the line marker.
  "start"          : number, `id` - 1.
  "end"            : number, `id`.
  "location"       : string shown to the reader: "p. <page> s. <ordinal>" or "Ch. <chapter> s. <ordinal>", built mechanically from the marker.
  "page"           : integer page (or chapter) number from the marker.
  "sentenceInPage" : integer sentence ordinal from the marker.
""".trim()

        val pronunciationRule =
            "\"pronunciation\" : STRICT IPA between slashes, e.g. \"/ˈwʊndrəs/\". Respellings such as \"WUN-drus\" are not acceptable; if you cannot give IPA, use \"\"."

        val body = when (mode) {
            BookPromptMode.TRANSLATION_ONLY -> """
Each entry:
  "english"     : the ORIGINAL line in $source, anchor stripped, cleaned only for hyphenation/OCR. Do NOT translate or rewrite it.
  "translation" : the full $target translation of that line.
  "level"       : "$CEFR_PLACEHOLDER".
""".trim()

            BookPromptMode.READING_LEARNING -> """
Each entry:
  "english"       : the ORIGINAL line in $source, anchor stripped, cleaned only for hyphenation/OCR.
  "translation"   : natural, complete $target translation of that line.
  "level"         : CEFR level of this sentence.
  "difficulty"    : "easy" | "medium" | "hard" for a $CEFR_PLACEHOLDER reader.
  $pronunciationRule
  "notes"         : cultural context, wordplay, anything a translation loses, or an OCR reconstruction. "" when there is nothing.
  "lesson": {
      "explanation"        : what this sentence is doing, in $target.
      "grammar"            : the structure worth learning, in $source.
      "grammarTranslation" : the same explanation in $target.
      "structure"          : the skeleton of this sentence, e.g. "By the time + past simple, had + past participle".
  }
  "words": [ at most $MAX_WORDS_PER_SENTENCE objects, only words worth keeping:
      { "word", "translation", "partOfSpeech", "meaningInContext", "extraExplanation", "examples": [2 short sentences], "pronunciation" (IPA) }
  ]
The `lesson` object is what the reader's per-sentence lesson button opens, so fill all four keys for every entry - an entry with a translation but no lesson opens a half-empty sheet.
""".trim()

            BookPromptMode.VOCAB_MINING -> """
One entry for EVERY line you received - the app needs full coverage to keep the page and the JSON in step:
  "english"     : the original line, anchor stripped, cleaned up.
  "translation" : that line in $target - mandatory for every entry, never empty.
  "level"       : CEFR level of the hardest word in it, or of the sentence overall.
  "words"       : 0-$MAX_WORDS_VOCAB_MODE objects mined from THIS line. If nothing is worth keeping, use `[]` but keep the entry.
      { "word", "translation", "partOfSpeech", "meaningInContext", "extraExplanation", "examples": [2 short sentences], "pronunciation" (IPA) }
Rules: no proper nouns, no words clearly below $CEFR_PLACEHOLDER, no repeats across chunks, prefer collocations and idioms over single easy words.
""".trim()

            BookPromptMode.SIMPLIFY -> """
Each entry:
  "english"     : the ORIGINAL line, anchor stripped, cleaned up.
  "simplified"  : the same sentence rewritten in $source at $CEFR_PLACEHOLDER.
  "translation" : $target translation of the simplified version.
  "level"       : "$CEFR_PLACEHOLDER".
  "notes"       : what you had to drop or change to reach $CEFR_PLACEHOLDER. "" when nothing.
""".trim()

            else -> ""
        }

        return listOf(header, anchors, body).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun workedExample(mode: BookPromptMode, target: String): String = when (mode) {
        BookPromptMode.TRANSLATION_ONLY -> """
Chunk in:
@LANGO v$PAYLOAD_VERSION | ids 1-2 | page 3 | src=English | dst=$target | $CEFR_PLACEHOLDER
[1|p3s1] The house on the hill had been empty for years.
[2|p3s2] No one remembered who had built it.
@END 1-2

Reply out:
{
  "formatVersion": 1,
  "metadata": { "source": "book", "language": "English", "targetLanguage": "$target", "level": "$CEFR_PLACEHOLDER", "chunk": "1-2", "description": "Page 3, sentences 1-2" },
  "subtitles": [
    { "id": 1, "start": 0, "end": 1, "location": "p. 3 s. 1", "page": 3, "sentenceInPage": 1, "english": "The house on the hill had been empty for years.", "translation": "<$target translation>", "level": "$CEFR_PLACEHOLDER" },
    { "id": 2, "start": 1, "end": 2, "location": "p. 3 s. 2", "page": 3, "sentenceInPage": 2, "english": "No one remembered who had built it.", "translation": "<$target translation>", "level": "$CEFR_PLACEHOLDER" }
  ]
}
""".trim()

        BookPromptMode.VOCAB_MINING -> """
{
  "formatVersion": 1,
  "metadata": { "source": "book", "language": "English", "targetLanguage": "$target", "level": "$CEFR_PLACEHOLDER", "chunk": "4-4", "description": "Page 3 word harvest" },
  "subtitles": [
    {
      "id": 4, "start": 3, "end": 4, "location": "p. 3 s. 4", "page": 3, "sentenceInPage": 4,
      "english": "He brushed off the accusation with a shrug.",
      "translation": "<$target translation>",
      "level": "$CEFR_PLACEHOLDER",
      "words": [
        { "word": "brush off", "translation": "<$target>", "partOfSpeech": "phrasal verb", "meaningInContext": "to dismiss something as unimportant", "extraExplanation": "Separable; usually used about criticism or advice.", "examples": ["She brushed off my warning.", "Don't brush it off so quickly."], "pronunciation": "/brʌʃ ɒf/" }
      ]
    }
  ]
}
""".trim()

        BookPromptMode.SIMPLIFY -> """
{
  "formatVersion": 1,
  "metadata": { "source": "book", "language": "English", "targetLanguage": "$target", "level": "$CEFR_PLACEHOLDER", "chunk": "1-1", "description": "Page 3, graded rewrite" },
  "subtitles": [
    {
      "id": 1, "start": 0, "end": 1, "location": "p. 3 s. 1", "page": 3, "sentenceInPage": 1,
      "english": "By the time she reached the gate, the rain had stopped.",
      "simplified": "She got to the gate. The rain stopped before that.",
      "translation": "<$target translation of the simplified sentence>",
      "level": "$CEFR_PLACEHOLDER",
      "notes": ""
    }
  ]
}
""".trim()

        else -> """
{
  "formatVersion": 1,
  "metadata": { "source": "book", "language": "English", "targetLanguage": "$target", "level": "$CEFR_PLACEHOLDER", "chunk": "1-1", "description": "Page 128, first sentence" },
  "subtitles": [
    {
      "id": 1, "start": 0, "end": 1, "location": "p. 128 s. 1", "page": 128, "sentenceInPage": 1,
      "english": "By the time she reached the gate, the rain had stopped.",
      "translation": "<$target translation>",
      "level": "$CEFR_PLACEHOLDER",
      "difficulty": "medium",
      "pronunciation": "/ɡeɪt/",
      "notes": "",
      "lesson": {
        "explanation": "<what this sentence is doing, in $target>",
        "grammar": "Past perfect for the earlier of two past events.",
        "grammarTranslation": "<same explanation in $target>",
        "structure": "By the time + past simple, + had + past participle"
      },
      "words": [
        { "word": "gate", "translation": "<$target>", "partOfSpeech": "noun", "meaningInContext": "the entrance in a wall or fence", "extraExplanation": "Not the same as 'door'.", "examples": ["He opened the gate.", "The gate was locked."], "pronunciation": "/ɡeɪt/" },
        { "word": "by the time", "translation": "<$target>", "partOfSpeech": "conjunction", "meaningInContext": "at or before the moment when something happened", "extraExplanation": "Takes a simple tense, never 'will'.", "examples": ["By the time we arrived, it was dark.", "By the time you read this, I will be gone."], "pronunciation": "/baɪ ðə taɪm/" }
      ]
    }
  ]
}
""".trim()
    }

    private fun validationChecklist(mode: BookPromptMode): String = buildString {
        appendLine("- The reply is valid JSON, starts with `{`, ends with `}`, and has no text or fences around it.")
        appendLine("- The id set equals the `ids` range of the chunk, in order, with no gaps and no extras.")
        appendLine("- `start` = `id` - 1 and `end` = `id` on every entry.")
        appendLine("- Every entry carries `location`, `page` and `sentenceInPage` copied mechanically from its marker - no invented pages, and nothing pointing at a page the chunk did not contain.")
        appendLine("- Every entry has a non-empty `translation`, and `english` is still the untranslated original line.")
        appendLine("- No entry contains two source lines, and no source line was split across two entries.")
        appendLine("- Front matter is NOT an exception and no line is \"too fragmentary\" to keep: a title page, a copyright line, a dedication, a heading, a bare page number, a testimonial and its `-- Name, Company` signature each get their OWN entry. A line with no full stop is still one entry, and an `english` value that holds a full stop followed by more text is wrong.")
        appendLine("- Every `pronunciation` is IPA between slashes, never a respelling.")
        if (mode == BookPromptMode.READING_LEARNING) {
            appendLine("- Every entry has a `lesson` object with all four keys filled in.")
            appendLine("- No entry has more than $MAX_WORDS_PER_SENTENCE `words`, and no word repeats within the chunk.")
        }
        if (mode == BookPromptMode.VOCAB_MINING) {
            appendLine("- Every line has an entry with a translation; `words` may be `[]` but the entry must exist.")
            appendLine("- No proper nouns and no words below the stated level in `words`.")
            appendLine("- At most $MAX_WORDS_VOCAB_MODE words per sentence, each with 2 examples and IPA.")
        }
        if (mode == BookPromptMode.SIMPLIFY) {
            appendLine("- Every entry has both `english` and `simplified`, the simplified version is genuinely easier, and the translation matches the simplified one.")
        }
        append("- Quotes and apostrophes inside strings are escaped, and no trailing commas are left behind.")
    }

    private fun chatStyle(mode: BookPromptMode, source: String, target: String): String = when (mode) {
        BookPromptMode.GRAMMAR_COACH -> """
Write in $target, in markdown, and in this order:
1. **Hard sentences** - for each one: the id and marker, the original, a literal reading, then the natural meaning.
2. **Breakdown** - the clauses, what governs what, and why the word order is what it is.
3. **The pattern** - name the structure and give its skeleton, plus two fresh examples at $CEFR_PLACEHOLDER.
4. **This author's habits** - what keeps coming back in this text, so the next pages are easier.
5. **Practice** - 3 short sentences for me to translate, answers at the very end under an "Answers" heading.
Keep grammar terms in $source with the $target term in brackets the first time. Use IPA whenever you show pronunciation.
""".trim()

        BookPromptMode.COMPREHENSION -> """
Write in $target, in markdown, and in this order:
1. **What happens** - 5-8 lines, no spoilers beyond the text I sent.
2. **Main idea** - one sentence.
3. **People** - each character, what they want, and how they speak (formal, rough, archaic…).
4. **Between the lines** - implications, tone, anything culturally or historically assumed.
5. **Words the passage leans on** - up to 8, with a short $target gloss and IPA.
6. **Questions** - 5 comprehension questions at $CEFR_PLACEHOLDER, then their answers under a final "Answers" heading.
""".trim()

        BookPromptMode.WORD_ANALYSIS -> """
Write in $target, in markdown, and keep it tight:
1. **Meaning here** - what it means in this exact sentence, and what it does NOT mean.
2. **Pronunciation** - IPA between slashes and the stressed syllable.
3. **Form** - part of speech, base form, irregularities, and the pieces it is built from when that helps.
4. **Register** - literary, archaic, slang, neutral; would a modern speaker still use it?
5. **Examples** - 3 sentences at $CEFR_PLACEHOLDER, and 2 collocations it usually appears in.
6. **Don't confuse with** - the look-alike words a $target speaker mixes it up with.
If I send a whole sentence instead of a word, do the same but for the sentence, and add a clause-by-clause breakdown.
""".trim()

        else -> "Write in $target and keep the explanation at $CEFR_PLACEHOLDER."
    }

    /** Short usage steps shown next to the generator in Settings. */
    fun usageSteps(fa: Boolean): List<String> = if (fa) listOf(
        "سطح و نوع پرامپت را انتخاب و روی «کپی پرامپت» بزن؛ سطح به‌صورت خودکار در متن جاگذاری می‌شود.",
        "این پرامپت را یک‌بار در ابتدای چت بفرست؛ قواعد داخل همین پرامپت است و لازم نیست با هر تکه دوباره کپی شود.",
        "در بخش کتاب، بازهٔ صفحه یا فصل را انتخاب و «کپی برای هوش مصنوعی» را بزن؛ فقط یک خط سرصفحه و نشانهٔ [شماره|صفحه/جمله] همراه متن می‌رود.",
        "خروجی JSON را وارد کن؛ برنامه هر جمله را با شمارهٔ خودش به صفحهٔ درست برمی‌گرداند و می‌گوید چند جمله جا افتاده است.",
        "تکهٔ بعدی از همان جایی که تمام شده ساخته می‌شود؛ شماره‌گذاری پیوسته می‌ماند.",
    ) else listOf(
        "Pick your level and prompt type, then tap Copy prompt - the level is substituted into the template for you.",
        "Paste it once at the top of an AI chat. The rules live there, so they are not repeated with every chunk.",
        "In the Book tab choose a page or chapter range and tap Copy for AI; only a one-line header and the [id|page/sentence] markers travel with the text.",
        "Import the JSON reply here. Each entry is re-anchored to its own page by id, and any missing ids are reported.",
        "The next chunk is prepared from where the last one stopped, so ids stay unbroken.",
    )
}
