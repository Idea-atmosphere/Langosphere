package com.example.logic

import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitlePackage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Where the material of a quiz came from.
 *
 * The app learns from two places - the book reader and the film subtitles -
 * and both end up in the same JSON envelope, so the only thing that tells them
 * apart is the source they are registered under. The quiz filter is built on
 * this: a learner revising for a book exam should not be asked about a film's
 * dialogue.
 */
enum class QuizSource(val key: String) {
    BOOK("book"),
    MOVIE("movie"),
    /** A learning JSON imported while watching an online clip (online_json/<id>). */
    ONLINE("online");

    companion object {
        fun from(key: String?): QuizSource =
            values().firstOrNull { it.key == key } ?: MOVIE
    }
}

/** One imported package plus where it came from. */
data class QuizMaterial(
    val source: QuizSource,
    val pkg: JsonSubtitlePackage
)

/**
 * The learner's choice in the quiz setup screen.
 *
 * [BOTH] is a filter value rather than a third source: the builder always
 * receives real materials tagged [QuizSource.BOOK] or [QuizSource.MOVIE].
 */
enum class QuizSourceFilter(val key: String) {
    BOOK("book"),
    MOVIE("movie"),
    ONLINE("online"),
    BOTH("both");

    fun matches(source: QuizSource): Boolean = when (this) {
        BOOK -> source == QuizSource.BOOK
        MOVIE -> source == QuizSource.MOVIE
        ONLINE -> source == QuizSource.ONLINE
        BOTH -> true
    }

    companion object {
        fun from(key: String?): QuizSourceFilter =
            values().firstOrNull { it.key == key } ?: BOTH
    }
}

/** What a single beta-quiz question asks about. */
enum class JsonQuizType {
    /** Source-language word → pick its translation. */
    WORD_TO_TRANSLATION,

    /** Translation → pick the source-language word. */
    TRANSLATION_TO_WORD,

    /** Source-language sentence → pick its translation. */
    SENTENCE_TO_TRANSLATION,

    /** Source-language sentence → pick the grammar point it teaches. */
    SENTENCE_TO_GRAMMAR
}

/**
 * One question of the beta "quiz from JSON" section (Leitner tab).
 *
 * [prompt] is what the learner reads, [options] always contains [answer], and
 * [contextSentence] / [note] carry the extra line the JSON package had for the
 * item (the sentence a word appeared in, or its meaning-in-context) so the
 * answer screen can teach something instead of just saying right/wrong.
 */
data class JsonQuizQuestion(
    val id: String,
    val type: JsonQuizType,
    val prompt: String,
    val answer: String,
    val options: List<String>,
    val contextSentence: String? = null,
    val note: String? = null,
    /** The word being asked about, so a wrong answer can go to the Leitner box. */
    val word: String? = null,
    /** Id of the subtitle line the question came from. */
    val subtitleId: String? = null,
    /** Where this question's material came from, for the source badge. */
    val source: QuizSource = QuizSource.MOVIE,
    /**
     * True in the "blur / fast guess" quiz: the prompt is hidden until the
     * learner presses it, so the answer has to come from memory first.
     */
    val blurPrompt: Boolean = false
) {
    /** True when the question text is in the source (learned) language. */
    val promptIsSourceLanguage: Boolean
        get() = type != JsonQuizType.TRANSLATION_TO_WORD
}

/**
 * Builds a multiple-choice quiz out of an imported AI learning package
 * ([JsonSubtitlePackage]) — the "test yourself on the JSON file" feature of the
 * Leitner tab's beta section.
 *
 * Everything comes from data the JSON already carries, so the quiz works
 * offline and needs no AI call:
 *  - `words[].word` + `words[].translation` → word ↔ translation questions,
 *  - `subtitles[].english` + `subtitles[].translation` → sentence questions,
 *  - `lesson.grammar` (+ `grammarTranslation`) → grammar questions.
 *
 * Wrong options are always taken from the same file, which keeps them
 * plausible (same level, same topic) and guarantees they exist.
 */
object JsonQuizBuilder {

    /** How many options a question has when the file offers enough material. */
    private const val OPTIONS_PER_QUESTION = 4

    /** A word/sentence shorter than this makes a useless question. */
    private const val MIN_TEXT_LENGTH = 2

    /**
     * How many questions this package can produce — used by the UI to disable
     * counts the file cannot fill.
     */
    fun availableQuestions(pkg: JsonSubtitlePackage, blurPeek: Boolean = false): Int =
        availableQuestions(listOf(QuizMaterial(QuizSource.MOVIE, pkg)), blurPeek)

    /**
     * The same count for a merged set of materials (book, film or both).
     *
     * This counts the questions [build] can REALLY deal, not a theoretical
     * maximum: an item only becomes a question when the file also offers at
     * least one plausible distractor for it, so a package with a single word
     * honestly reports 0 instead of "3" — the setup screen can then disable
     * the start button instead of opening an empty quiz.
     */
    fun availableQuestions(materials: List<QuizMaterial>, blurPeek: Boolean = false): Int =
        buildPool(materials, Random(1), blurPeek).size

    /** The materials a filter selects; [QuizSourceFilter.BOTH] keeps all. */
    fun filter(materials: List<QuizMaterial>, filter: QuizSourceFilter): List<QuizMaterial> =
        materials.filter { filter.matches(it.source) }

    /**
     * Builds up to [maxQuestions] shuffled questions.
     *
     * @param seed fixes the shuffle, so "try again" can either reproduce the
     *   same quiz (same seed) or deal a new one (a different seed).
     */
    fun build(
        pkg: JsonSubtitlePackage,
        maxQuestions: Int = 12,
        seed: Long = Random.nextLong()
    ): List<JsonQuizQuestion> = build(
        materials = listOf(QuizMaterial(QuizSource.MOVIE, pkg)),
        maxQuestions = maxQuestions,
        seed = seed,
    )

    /**
     * Same quiz, built from one or more tagged packages.
     *
     * The pools are merged before the options are picked, so a "both sources"
     * quiz really mixes book and film material - and the distractors of a book
     * question may well come from a film, which is exactly the kind of
     * discrimination a learner should be able to make.
     *
     * @param blurPeek marks every question as blurred-prompt, for the
     *   "blur / fast guess" quiz type.
     */
    fun build(
        materials: List<QuizMaterial>,
        maxQuestions: Int = 12,
        seed: Long = Random.nextLong(),
        blurPeek: Boolean = false,
    ): List<JsonQuizQuestion> {
        val pool = buildPool(materials, Random(seed), blurPeek)
        if (pool.isEmpty()) return emptyList()
        return pool.shuffled(Random(seed)).take(maxQuestions.coerceAtLeast(1))
    }

    /**
     * Every question the materials can support, unshuffled. [availableQuestions]
     * calls this too, so the count the setup screen shows is exactly the number
     * of questions a run of [build] can produce.
     */
    private fun buildPool(
        materials: List<QuizMaterial>,
        random: Random,
        blurPeek: Boolean,
    ): List<JsonQuizQuestion> {
        val usable = materials.filter { it.pkg.subtitles.isNotEmpty() }
        if (usable.isEmpty()) return emptyList()
        val pool = mutableListOf<JsonQuizQuestion>()

        val words = usable.flatMap { material -> wordItems(material.pkg).map { material.source to it } }
        val sentences = usable.flatMap { material -> sentenceItems(material.pkg).map { material.source to it } }
        val grammar = usable.flatMap { material -> grammarItems(material.pkg).map { material.source to it } }

        // Distractor pools stay global: they are all in the target language and
        // all come from material the learner chose to be quizzed on.
        val wordTranslations = words.map { it.second.translation }.distinct()
        val wordTexts = words.map { it.second.word }.distinct()
        val sentenceTranslations = sentences.map { it.second.translation }.distinct()
        val grammarNames = grammar.map { it.second.answer }.distinct()

        // Word → translation, and the reverse direction.
        words.forEachIndexed { index, (source, item) ->
            val options = buildOptions(item.translation, wordTranslations, random)
            if (options != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "${source.key}-w2t-$index",
                        type = JsonQuizType.WORD_TO_TRANSLATION,
                        prompt = item.word,
                        answer = item.translation,
                        options = options,
                        contextSentence = item.context,
                        note = item.note,
                        word = item.word,
                        subtitleId = item.subtitleId,
                        source = source,
                        blurPrompt = blurPeek
                    )
                )
            }
            val reverseOptions = buildOptions(item.word, wordTexts, random)
            if (reverseOptions != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "${source.key}-t2w-$index",
                        type = JsonQuizType.TRANSLATION_TO_WORD,
                        prompt = item.translation,
                        answer = item.word,
                        options = reverseOptions,
                        contextSentence = item.context,
                        note = item.note,
                        word = item.word,
                        subtitleId = item.subtitleId,
                        source = source,
                        blurPrompt = blurPeek
                    )
                )
            }
        }

        // Sentence → translation.
        sentences.forEachIndexed { index, (source, item) ->
            val options = buildOptions(item.translation, sentenceTranslations, random)
            if (options != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "${source.key}-s2t-$index",
                        type = JsonQuizType.SENTENCE_TO_TRANSLATION,
                        prompt = item.prompt,
                        answer = item.translation,
                        options = options,
                        note = item.note,
                        subtitleId = item.subtitleId,
                        source = source,
                        blurPrompt = blurPeek
                    )
                )
            }
        }

        // Sentence → grammar point.
        grammar.forEachIndexed { index, (source, item) ->
            val options = buildOptions(item.answer, grammarNames, random)
            if (options != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "${source.key}-g-$index",
                        type = JsonQuizType.SENTENCE_TO_GRAMMAR,
                        prompt = item.prompt,
                        answer = item.answer,
                        options = options,
                        note = item.note,
                        subtitleId = item.subtitleId,
                        source = source,
                        blurPrompt = blurPeek
                    )
                )
            }
        }

        // The blur / fast-guess variant is a pure sentence-meaning recall
        // test: the learner sees the sentence, recalls its meaning from
        // memory and only then reveals the options. Word and grammar
        // questions have no place in it — only sentence → translation.
        return if (blurPeek) {
            pool.filter { it.type == JsonQuizType.SENTENCE_TO_TRANSLATION }
        } else {
            pool
        }
    }

    // ── running-deck persistence (the dialog keeps these in rememberSaveable) ──

    /**
     * Serialises a dealt deck to one JSON string, so the quiz dialog can hold
     * it in saved instance state: a trip to Recents (or the system reclaiming
     * the activity) must not eat a running quiz.
     */
    fun encodeState(questions: List<JsonQuizQuestion>): String {
        val array = JSONArray()
        questions.forEach { q ->
            array.put(JSONObject().apply {
                put("id", q.id)
                put("type", q.type.name)
                put("prompt", q.prompt)
                put("answer", q.answer)
                put("options", JSONArray(q.options))
                q.contextSentence?.let { put("context", it) }
                q.note?.let { put("note", it) }
                q.word?.let { put("word", it) }
                q.subtitleId?.let { put("subId", it) }
                put("source", q.source.name)
                put("blur", q.blurPrompt)
            })
        }
        return array.toString()
    }

    /** The inverse of [encodeState]; an unreadable payload yields an empty deck. */
    fun decodeState(json: String): List<JsonQuizQuestion> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val type = runCatching { JsonQuizType.valueOf(o.getString("type")) }.getOrNull()
                ?: return@mapNotNull null
            JsonQuizQuestion(
                id = o.getString("id"),
                type = type,
                prompt = o.getString("prompt"),
                answer = o.getString("answer"),
                options = o.optJSONArray("options")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                } ?: emptyList(),
                contextSentence = o.optString("context").takeIf { it.isNotEmpty() },
                note = o.optString("note").takeIf { it.isNotEmpty() },
                word = o.optString("word").takeIf { it.isNotEmpty() },
                subtitleId = o.optString("subId").takeIf { it.isNotEmpty() },
                source = runCatching { QuizSource.valueOf(o.optString("source")) }
                    .getOrDefault(QuizSource.MOVIE),
                blurPrompt = o.optBoolean("blur", false)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** One learnable item pulled out of the package. */
    private data class QuizItem(
        val prompt: String,
        val answer: String,
        val word: String = answer,
        val translation: String = answer,
        val context: String? = null,
        val note: String? = null,
        val subtitleId: String? = null
    )

    private fun wordItems(pkg: JsonSubtitlePackage): List<QuizItem> {
        val items = mutableListOf<QuizItem>()
        val seen = mutableSetOf<String>()
        for (sub in pkg.subtitles) {
            for (word in sub.words) {
                val text = word.word.trim()
                val translation = word.translation?.trim().orEmpty()
                if (text.length < MIN_TEXT_LENGTH || translation.length < MIN_TEXT_LENGTH) continue
                // One card per distinct word: the same word in ten lines is not
                // ten questions.
                val key = text.lowercase()
                if (!seen.add(key)) continue
                items.add(
                    QuizItem(
                        prompt = text,
                        answer = translation,
                        word = text,
                        translation = translation,
                        context = sub.english.trim().takeIf { it.isNotBlank() },
                        note = word.meaningInContext?.trim()?.takeIf { it.isNotBlank() }
                            ?: word.extraExplanation?.trim()?.takeIf { it.isNotBlank() },
                        subtitleId = sub.id
                    )
                )
            }
        }
        return items
    }

    private fun sentenceItems(pkg: JsonSubtitlePackage): List<QuizItem> {
        val items = mutableListOf<QuizItem>()
        for (sub in pkg.subtitles) {
            val source = sub.english.trim()
            val translation = sub.translation?.trim().orEmpty()
            if (source.length < MIN_TEXT_LENGTH || translation.length < MIN_TEXT_LENGTH) continue
            items.add(
                QuizItem(
                    prompt = source,
                    answer = translation,
                    translation = translation,
                    note = sentenceTeachings(sub),
                    subtitleId = sub.id
                )
            )
        }
        return items
    }

    private fun grammarItems(pkg: JsonSubtitlePackage): List<QuizItem> {
        val items = mutableListOf<QuizItem>()
        for (sub in pkg.subtitles) {
            val lesson = sub.lesson ?: continue
            val grammar = lesson.grammar?.trim().orEmpty()
            if (grammar.length < MIN_TEXT_LENGTH) continue
            items.add(
                QuizItem(
                    prompt = sub.english.trim(),
                    // The translated grammar name is what a learner recognises;
                    // fall back to the English term when the AI omitted it.
                    answer = lesson.grammarTranslation?.trim()?.takeIf { it.isNotBlank() } ?: grammar,
                    note = lesson.explanation?.trim()?.takeIf { it.isNotBlank() },
                    subtitleId = sub.id
                )
            )
        }
        return items.filter { it.prompt.isNotBlank() }
    }

    /**
     * Everything the JSON teaches about one subtitle line: its notes plus the
     * lesson's explanation, grammar point and structure. This is what the
     * quiz's feedback dock shows after an answer, and what a sentence the
     * learner did not know ships to the Leitner box — "the sentence together
     * with its teachings", not a bare translation.
     */
    private fun sentenceTeachings(sub: JsonSubtitle): String? {
        val lines = buildList {
            sub.notes?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            sub.lesson?.let { lesson ->
                lesson.explanation?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                val grammar = listOfNotNull(
                    lesson.grammar?.trim()?.takeIf { it.isNotBlank() },
                    lesson.grammarTranslation?.trim()?.takeIf { it.isNotBlank() }
                ).joinToString(" — ").takeIf { it.isNotBlank() }
                grammar?.let { add(it) }
                lesson.structure?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * The answer plus up to [OPTIONS_PER_QUESTION]-1 distractors from [pool],
     * shuffled. Returns null when the pool is too small to make a real choice.
     */
    private fun buildOptions(answer: String, pool: List<String>, random: Random): List<String>? {
        val distractors = pool
            .filter { it.isNotBlank() && !it.equals(answer, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .shuffled(random)
            .take(OPTIONS_PER_QUESTION - 1)
        if (distractors.isEmpty()) return null
        return (distractors + answer).shuffled(random)
    }
}
