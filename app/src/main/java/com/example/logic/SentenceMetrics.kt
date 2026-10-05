package com.example.logic

/**
 * How hard one sentence is, reduced to the three states the reader paints as a
 * dot. The JSON prompts ask the model for both a free-text `difficulty` and a
 * CEFR `level`, and either one alone is enough to answer.
 */
enum class DifficultyTone {
    EASY,
    MEDIUM,
    HARD,

    /** Neither field said anything usable; no dot is drawn. */
    UNKNOWN
}

/**
 * The two numbers the reader shows about a sentence or a page: how hard it is,
 * and how long it takes to read.
 *
 * Both are pure functions of data the JSON package already carries, so the
 * reader can show them offline and without an extra AI round trip. The reading
 * speed is the widely used 130 words per minute for a foreign-language
 * learner reading at study pace - the point is a sane estimate, not a
 * stopwatch.
 */
object SentenceMetrics {

    const val WORDS_PER_MINUTE = 130

    /** A readable word: letters and inner apostrophes/hyphens, as elsewhere in the app. */
    private val WORD = Regex("[\\p{L}][\\p{L}'\\u2019-]*")

    fun wordCount(text: String): Int =
        if (text.isBlank()) 0 else WORD.findAll(text).count()

    /** Whole minutes, at least one: "0 دقیقه" would read as a bug. */
    fun readingMinutes(wordCount: Int): Int =
        if (wordCount <= 0) 0 else ((wordCount + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1)

    fun readingMinutes(text: String): Int = readingMinutes(wordCount(text))

    fun readingMinutes(sentences: List<String>): Int =
        readingMinutes(sentences.sumOf { wordCount(it) })

    /**
     * Maps the model's own labels onto a tone.
     *
     * `difficulty` wins when present because it is written for this sentence,
     * and the CEFR level is the fallback: A1/A2 is easy reading, B1/B2 is the
     * normal band the app teaches at, C1/C2 is hard. Anything unrecognised -
     * including a level such as `Z9` - is [DifficultyTone.UNKNOWN] rather than
     * a guess, so the dot never lies.
     */
    fun tone(difficulty: String?, level: String?): DifficultyTone {
        toneFromDifficulty(difficulty)?.let { return it }
        return toneFromLevel(level)
    }

    private fun toneFromDifficulty(difficulty: String?): DifficultyTone? {
        val text = difficulty?.trim()?.lowercase().orEmpty()
        if (text.isEmpty()) return null
        return when {
            text.startsWith("easy") || text.contains("simple") ||
                text.contains("آسان") || text.contains("ساده") -> DifficultyTone.EASY

            text.startsWith("medium") || text.startsWith("moderate") ||
                text.contains("متوسط") -> DifficultyTone.MEDIUM

            text.startsWith("hard") || text.startsWith("difficult") ||
                text.startsWith("advanced") || text.contains("سخت") ||
                text.contains("دشوار") -> DifficultyTone.HARD

            // A level written into the difficulty field ("B2") still counts.
            else -> toneFromLevel(text).takeIf { it != DifficultyTone.UNKNOWN }
        }
    }

    private fun toneFromLevel(level: String?): DifficultyTone {
        val text = level?.trim()?.lowercase().orEmpty()
        if (text.isEmpty()) return DifficultyTone.UNKNOWN
        val code = Regex("\\b([abc][12])\\b").find(text)?.groupValues?.getOrNull(1) ?: return DifficultyTone.UNKNOWN
        return when (code) {
            "a1", "a2" -> DifficultyTone.EASY
            "b1", "b2" -> DifficultyTone.MEDIUM
            "c1", "c2" -> DifficultyTone.HARD
            else -> DifficultyTone.UNKNOWN
        }
    }

    /** Short human label, used as the dot's accessibility description. */
    fun toneLabel(tone: DifficultyTone, fa: Boolean): String = when (tone) {
        DifficultyTone.EASY -> if (fa) "آسان" else "Easy"
        DifficultyTone.MEDIUM -> if (fa) "متوسط" else "Medium"
        DifficultyTone.HARD -> if (fa) "دشوار" else "Hard"
        DifficultyTone.UNKNOWN -> ""
    }

    /**
     * True when an entry holds more than one source line.
     *
     * The prompt asks for exactly one line per entry, and a model that ignores
     * that answers with things like a testimonial signature, a title block and
     * a publisher's blurb in one `english`. Such an entry cannot be matched to
     * any single line, so the reader labels it instead of pretending the
     * translation is missing. Two sentences and a real amount of text is the
     * threshold: a short line that happens to hold two sentences is normal
     * prose, while a merged block is long by construction.
     */
    fun isMergedEntry(english: String): Boolean {
        val text = english.trim()
        if (text.length < MERGED_MIN_CHARS) return false
        return SentenceSegmenter.sentences(text).count { it.isNotBlank() } > 1
    }

    /** Below this, a two-sentence line is ordinary prose rather than a merge. */
    private const val MERGED_MIN_CHARS = 120

    /** "زمان تخمینی مطالعه: ۳ دقیقه" / "Estimated reading time: 3 min". */
    fun readingTimeLabel(minutes: Int, fa: Boolean): String = if (fa) {
        if (minutes <= 0) "زمان تخمینی مطالعه: کمتر از یک دقیقه" else "زمان تخمینی مطالعه: $minutes دقیقه"
    } else {
        if (minutes <= 0) "Estimated reading time: under a minute" else "Estimated reading time: $minutes min"
    }
}
