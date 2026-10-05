package com.example.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Maps a sentence the AI returned back onto the text it came from.
 *
 * Exact matching does not work here, and cannot be made to work. The prompts
 * ask the model to repair hyphenation, drop page furniture and normalise
 * quotes, so the sentence that comes back is deliberately not byte-identical to
 * the PDF text layer it was extracted from. OCR noise (`rn` for `m`, `l` for
 * `1`) adds another layer of difference on scanned books.
 *
 * So alignment is scored instead: a character-level Levenshtein ratio catches
 * small corruption, a token-overlap ratio survives reordering and missing
 * words, and the two are combined. Anchoring highlights and continuation
 * positions on that score is what keeps them stable across re-ingests.
 *
 * Pure Kotlin, no Android dependency.
 */
object FuzzyTextAligner {

    /** A candidate index with its similarity score. */
    data class Match(val index: Int, val score: Double) {
        val isFound: Boolean get() = index >= 0

        companion object {
            val NONE = Match(index = -1, score = 0.0)
        }
    }

    /** Default accept threshold; tuned to accept AI cleanup, reject a different sentence. */
    const val DEFAULT_THRESHOLD: Double = 0.62

    /**
     * Above this length, the quadratic edit distance is skipped and the token
     * score decides. Long paragraphs are rare and not worth the CPU on device.
     */
    const val MAX_COMPARE_LENGTH: Int = 1200

    /** Lowercase, strip punctuation, collapse whitespace. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace('\u2019', '\'')
            .replace('\u2018', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace(Regex("[^\\p{L}\\p{N}']+"), " ")
            .trim()
            .replace(Regex("\\s{2,}"), " ")

    fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    /** Classic two-row Levenshtein distance: O(n*m) time, O(m) memory. */
    fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in 1..left.length) {
            current[0] = i
            val leftChar = left[i - 1]
            for (j in 1..right.length) {
                val substitution = previous[j - 1] + if (leftChar == right[j - 1]) 0 else 1
                val insertion = current[j - 1] + 1
                val deletion = previous[j] + 1
                current[j] = min(substitution, min(insertion, deletion))
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    /** Edit distance expressed as a 0..1 similarity. */
    fun levenshteinSimilarity(left: String, right: String): Double {
        if (left.isEmpty() && right.isEmpty()) return 1.0
        val longest = max(left.length, right.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(left, right).toDouble() / longest
    }

    /**
     * Token overlap weighted by length agreement.
     *
     * Overlap alone rates "the cat sat" against a whole paragraph containing
     * those words far too highly, so the ratio of lengths damps the score.
     */
    fun tokenSimilarity(left: String, right: String): Double {
        val leftTokens = tokens(left)
        val rightTokens = tokens(right)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0

        val rightCounts = HashMap<String, Int>()
        for (token in rightTokens) rightCounts[token] = (rightCounts[token] ?: 0) + 1

        var shared = 0
        for (token in leftTokens) {
            val remaining = rightCounts[token] ?: 0
            if (remaining > 0) {
                rightCounts[token] = remaining - 1
                shared++
            }
        }

        val overlap = shared.toDouble() / max(leftTokens.size, rightTokens.size)
        val lengthAgreement = 1.0 - abs(leftTokens.size - rightTokens.size).toDouble() /
            max(leftTokens.size, rightTokens.size)
        return overlap * 0.75 + overlap * lengthAgreement * 0.25
    }

    /**
     * Combined similarity in 0..1. Character and token views are averaged
     * because each fails differently: the character view punishes reordering
     * that does not matter, the token view forgives corruption that does.
     */
    fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
        if (a == b) return 1.0

        val tokenScore = tokenSimilarity(a, b)
        if (a.length > MAX_COMPARE_LENGTH || b.length > MAX_COMPARE_LENGTH) return tokenScore
        val charScore = levenshteinSimilarity(a, b)
        return max(tokenScore, (tokenScore + charScore) / 2.0)
    }

    /**
     * Best candidate for [query].
     *
     * @param nearIndex when given, ties are broken towards this index. Books
     *   repeat short sentences constantly ("He nodded."), so the nearest
     *   acceptable match is the right one, not the first in the list.
     */
    fun bestMatch(
        query: String,
        candidates: List<String>,
        threshold: Double = DEFAULT_THRESHOLD,
        nearIndex: Int = -1,
    ): Match {
        if (query.isBlank() || candidates.isEmpty()) return Match.NONE
        var best = Match.NONE
        var bestDistance = Int.MAX_VALUE

        candidates.forEachIndexed { index, candidate ->
            val score = similarity(query, candidate)
            if (score < threshold) return@forEachIndexed
            val distance = if (nearIndex >= 0) abs(index - nearIndex) else 0
            val better = score > best.score + 0.02 ||
                (abs(score - best.score) <= 0.02 && distance < bestDistance)
            if (better) {
                best = Match(index, score)
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Finds the character range in [haystack] that best corresponds to
     * [needle], returning offsets into the ORIGINAL string.
     *
     * The search runs over a normalized copy while keeping an index map back to
     * the original, so the returned range can be used directly as a highlight
     * anchor even though matching ignored punctuation and case. A word-aligned
     * sliding window of roughly the needle's length is scored, and the window
     * is then trimmed to word boundaries.
     */
    fun locate(
        needle: String,
        haystack: String,
        threshold: Double = DEFAULT_THRESHOLD,
    ): IntRange? {
        if (needle.isBlank() || haystack.isBlank()) return null

        // Fast path: the extraction and the AI agreed exactly.
        val exact = haystack.indexOf(needle.trim())
        if (exact >= 0) return exact until (exact + needle.trim().length)

        // Normalized copy plus a map from normalized index to original index.
        val builder = StringBuilder(haystack.length)
        val offsets = IntArray(haystack.length)
        var lastWasSpace = true
        for (index in haystack.indices) {
            val char = haystack[index]
            val mapped = when {
                char.isLetterOrDigit() -> char.lowercaseChar()
                char == '\'' || char == '\u2019' -> '\''
                else -> ' '
            }
            if (mapped == ' ') {
                if (lastWasSpace) continue
                lastWasSpace = true
            } else {
                lastWasSpace = false
            }
            offsets[builder.length] = index
            builder.append(mapped)
        }
        val flat = builder.toString().trimEnd()
        if (flat.isEmpty()) return null

        val target = normalize(needle)
        if (target.isEmpty()) return null

        val direct = flat.indexOf(target)
        if (direct >= 0) {
            val start = offsets[direct]
            val endNormalized = min(direct + target.length - 1, flat.length - 1)
            return start..offsets[endNormalized]
        }

        // Word-aligned window scan.
        val wordStarts = ArrayList<Int>()
        if (flat.isNotEmpty()) wordStarts += 0
        for (index in 1 until flat.length) {
            if (flat[index - 1] == ' ' && flat[index] != ' ') wordStarts += index
        }

        var bestRange: IntRange? = null
        var bestScore = threshold
        val window = target.length

        for (start in wordStarts) {
            if (start >= flat.length) break
            for (scale in listOf(0.8, 1.0, 1.25)) {
                val end = min(flat.length, start + (window * scale).toInt())
                if (end <= start) continue
                val candidate = flat.substring(start, end)
                val score = similarity(target, candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestRange = offsets[start]..offsets[min(end - 1, flat.length - 1)]
                }
            }
        }
        return bestRange
    }
}
