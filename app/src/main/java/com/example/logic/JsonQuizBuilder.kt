package com.example.logic

import com.example.model.JsonSubtitlePackage
import kotlin.random.Random

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
    val subtitleId: String? = null
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
    fun availableQuestions(pkg: JsonSubtitlePackage): Int {
        val words = wordItems(pkg).size
        val sentences = sentenceItems(pkg).size
        val grammar = grammarItems(pkg).size
        // Each word can be asked in both directions.
        return (words * 2) + sentences + grammar
    }

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
    ): List<JsonQuizQuestion> {
        val random = Random(seed)
        val pool = mutableListOf<JsonQuizQuestion>()

        val words = wordItems(pkg)
        val sentences = sentenceItems(pkg)
        val grammar = grammarItems(pkg)

        val wordTranslations = words.map { it.translation }.distinct()
        val wordTexts = words.map { it.word }.distinct()
        val sentenceTranslations = sentences.map { it.translation }.distinct()
        val grammarNames = grammar.map { it.answer }.distinct()

        // Word → translation, and the reverse direction.
        words.forEachIndexed { index, item ->
            val options = buildOptions(item.translation, wordTranslations, random)
            if (options != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "w2t-$index",
                        type = JsonQuizType.WORD_TO_TRANSLATION,
                        prompt = item.word,
                        answer = item.translation,
                        options = options,
                        contextSentence = item.context,
                        note = item.note,
                        word = item.word,
                        subtitleId = item.subtitleId
                    )
                )
            }
            val reverseOptions = buildOptions(item.word, wordTexts, random)
            if (reverseOptions != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "t2w-$index",
                        type = JsonQuizType.TRANSLATION_TO_WORD,
                        prompt = item.translation,
                        answer = item.word,
                        options = reverseOptions,
                        contextSentence = item.context,
                        note = item.note,
                        word = item.word,
                        subtitleId = item.subtitleId
                    )
                )
            }
        }

        // Sentence → translation.
        sentences.forEachIndexed { index, item ->
            val options = buildOptions(item.translation, sentenceTranslations, random)
            if (options != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "s2t-$index",
                        type = JsonQuizType.SENTENCE_TO_TRANSLATION,
                        prompt = item.prompt,
                        answer = item.translation,
                        options = options,
                        note = item.note,
                        subtitleId = item.subtitleId
                    )
                )
            }
        }

        // Sentence → grammar point.
        grammar.forEachIndexed { index, item ->
            val options = buildOptions(item.answer, grammarNames, random)
            if (options != null) {
                pool.add(
                    JsonQuizQuestion(
                        id = "g-$index",
                        type = JsonQuizType.SENTENCE_TO_GRAMMAR,
                        prompt = item.prompt,
                        answer = item.answer,
                        options = options,
                        note = item.note,
                        subtitleId = item.subtitleId
                    )
                )
            }
        }

        if (pool.isEmpty()) return emptyList()
        return pool.shuffled(random).take(maxQuestions.coerceAtLeast(1))
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
                    note = sub.notes?.trim()?.takeIf { it.isNotBlank() },
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
