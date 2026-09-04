package com.example.logic

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * The words the learner has marked as "I already know this".
 *
 * Two things come out of this: the app stops pushing them as study material,
 * and it can tell you how much of a film you already understand — which is
 * the single most motivating number a language learner can see.
 *
 * State-backed so Compose re-reads it automatically after a change.
 */
object KnownWordsStore {

    private const val PREFS_NAME = "known_words"
    private const val KEY_WORDS = "words"

    private val wordsState = mutableStateOf<Set<String>>(emptySet())
    private var loaded = false

    /**
     * Function words everybody knows from day one. Counting them as unknown
     * would make every coverage number meaninglessly low.
     */
    private val BASIC_WORDS: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "if", "so", "of", "to", "in", "on", "at", "by",
        "for", "with", "from", "as", "into", "about", "over", "under", "out", "up", "down",
        "i", "me", "my", "mine", "you", "your", "yours", "he", "him", "his", "she", "her",
        "hers", "it", "its", "we", "us", "our", "ours", "they", "them", "their", "theirs",
        "this", "that", "these", "those", "who", "whom", "whose", "which", "what", "where",
        "when", "why", "how", "am", "is", "are", "was", "were", "be", "been", "being", "do",
        "does", "did", "done", "have", "has", "had", "can", "could", "will", "would", "shall",
        "should", "may", "might", "must", "not", "no", "yes", "yeah", "ok", "okay", "oh", "hey",
        "hi", "here", "there", "now", "then", "very", "too", "just", "all", "any", "some",
        "more", "most", "much", "many", "one", "two", "three", "go", "going", "get", "got",
        "know", "like", "want", "say", "said", "see", "saw", "come", "came", "good", "bad",
        "time", "day", "man", "thing", "let", "gonna", "wanna", "don", "doesn", "didn", "isn",
        "aren", "wasn", "weren", "can", "couldn", "won", "wouldn", "shouldn", "ll", "re", "ve", "em"
    )

    data class UnknownWord(val word: String, val count: Int)

    data class CoverageStats(
        val totalTokens: Int,
        val knownTokens: Int,
        val uniqueWords: Int,
        val knownUnique: Int,
        val topUnknown: List<UnknownWord>
    ) {
        /** Share of the running words in the file the learner can follow. */
        val percent: Int
            get() = if (totalTokens == 0) 0 else (knownTokens * 100) / totalTokens
    }

    val words: Set<String>
        get() = wordsState.value

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        wordsState.value = try {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WORDS, "") ?: ""
            if (raw.isBlank()) emptySet()
            else raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        }
    }

    fun normalize(word: String): String =
        word.lowercase().trim().trim('\'', '’', '-', '.', ',', '!', '?', '"')

    fun isKnown(word: String): Boolean {
        val key = normalize(word)
        if (key.isEmpty()) return true
        return BASIC_WORDS.contains(key) || wordsState.value.contains(key)
    }

    fun markKnown(context: Context, word: String) {
        val key = normalize(word)
        if (key.isEmpty()) return
        ensureLoaded(context)
        if (wordsState.value.contains(key)) return
        val updated = wordsState.value + key
        wordsState.value = updated
        persist(context, updated)
    }

    fun unmarkKnown(context: Context, word: String) {
        val key = normalize(word)
        ensureLoaded(context)
        if (!wordsState.value.contains(key)) return
        val updated = wordsState.value - key
        wordsState.value = updated
        persist(context, updated)
    }

    fun toggle(context: Context, word: String) {
        if (wordsState.value.contains(normalize(word))) unmarkKnown(context, word)
        else markKnown(context, word)
    }

    fun clear(context: Context) {
        wordsState.value = emptySet()
        persist(context, emptySet())
    }

    private fun persist(context: Context, values: Set<String>) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_WORDS, values.joinToString("\n"))
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * How much of these lines the learner already understands, plus the
     * unknown words that would buy the most comprehension if learned next
     * (ranked by how often they occur).
     */
    fun computeCoverage(texts: List<String>, unknownLimit: Int = 12): CoverageStats {
        if (texts.isEmpty()) return CoverageStats(0, 0, 0, 0, emptyList())
        val counts = HashMap<String, Int>()
        var total = 0
        var known = 0
        for (text in texts) {
            if (text.isBlank()) continue
            for (rawToken in text.split(Regex("[^A-Za-z'’]+"))) {
                val token = normalize(rawToken)
                if (token.length < 2) continue
                total++
                if (isKnown(token)) {
                    known++
                } else {
                    counts[token] = (counts[token] ?: 0) + 1
                }
            }
        }
        val unique = counts.size + wordsState.value.size
        val topUnknown = counts.entries
            .sortedByDescending { it.value }
            .take(unknownLimit)
            .map { UnknownWord(it.key, it.value) }
        return CoverageStats(
            totalTokens = total,
            knownTokens = known,
            uniqueWords = unique,
            knownUnique = wordsState.value.size,
            topUnknown = topUnknown
        )
    }
}
