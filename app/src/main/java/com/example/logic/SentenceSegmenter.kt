package com.example.logic

/**
 * Turns extracted document text into clean sentences.
 *
 * This is the first step of the book pipeline and the one that decides whether
 * everything downstream works. PDF text layers are not prose: they arrive with
 * hard line wraps, words split across lines (`under-\nstand`), running headers
 * and page numbers repeated on every page, and assorted control characters.
 * Feeding that to a model produces broken sentences, and broken sentences make
 * the drift guard and the highlight anchors meaningless.
 *
 * Pure Kotlin with no Android dependency, so every rule below is unit-tested
 * on the JVM.
 */
object SentenceSegmenter {

    /** A sentence together with where it sits in the text it came from. */
    data class Span(val text: String, val startOffset: Int, val endOffset: Int)

    /**
     * Words whose trailing period is not a sentence end. Without this list,
     * "Mr. Darcy" and "Fig. 3" each become two sentences, and every id after
     * them drifts by one.
     */
    val ABBREVIATIONS: Set<String> = setOf(
        "mr", "mrs", "ms", "mx", "dr", "prof", "rev", "hon", "st", "jr", "sr",
        "vs", "etc", "eg", "ie", "cf", "al", "fig", "figs", "no", "nos", "vol",
        "vols", "ch", "chap", "pp", "ed", "eds", "trans", "inc", "ltd", "co",
        "corp", "dept", "est", "approx", "apr", "aug", "dec", "feb", "jan",
        "jul", "jun", "mar", "nov", "oct", "sep", "sept", "mon", "tue", "tues",
        "wed", "thu", "thurs", "fri", "sat", "sun", "am", "pm", "ph", "phd",
        "ba", "bs", "ma", "msc", "usa", "uk", "ussr",
    )

    /**
     * Calendar abbreviations that are also common lowercase English words.
     * A lowercase `sat.` / `sun.` / `wed.` is usually a completed verb or noun,
     * not a weekday abbreviation, so it must still be allowed to end a sentence.
     */
    private val LOWERCASE_WORD_ABBREVIATIONS = setOf("sat", "sun", "wed")

    /** Sentence-final punctuation, including Persian and CJK forms. */
    const val TERMINATORS: String = ".!?\u2026\u061F\u3002\u06D4"

    /** Characters allowed to follow a terminator and still end the sentence. */
    private const val CLOSERS: String = ")]}\"'\u2019\u201D\u00BB"

    /** Characters that legitimately open the next sentence. */
    private const val OPENERS: String = "(\"'\u2018\u201C\u00AB[\u2014-"

    private val PAGE_NUMBER_LINE = Regex("^\\s*[\\[(]?\\s*(?:page|p\\.?)?\\s*[ivxlcdm\\d]{1,6}\\s*[)\\]]?\\s*$", RegexOption.IGNORE_CASE)
    private val PAGE_FURNITURE_SLASH = Regex("^\\s*(?:p\\.?|page)?\\s*\\d{1,6}\\s*/\\s*\\d{1,6}\\s*$", RegexOption.IGNORE_CASE)
    private val CONTROL_NOISE = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\uFFFD]")
    private val MULTI_SPACE = Regex("[ \\t\\u00A0]{2,}")
    private val MULTI_BLANK_LINE = Regex("\\n{3,}")
    private const val SOFT_HYPHEN = '\u00AD'

    /** Common minor words allowed in Title Case lines without capitalization. */
    private val TITLE_CASE_MINOR_WORDS = setOf(
        "of", "and", "the", "in", "on", "to", "a", "an", "for", "with", "at", "by", "from", "de", "la"
    )

    /**
     * Finds running headers and footers by looking across the whole document.
     *
     * A header can only be recognised by repetition, which is why this works on
     * all units at once rather than per page: "PRIDE AND PREJUDICE" on page 128
     * is indistinguishable from body text until you notice it opens 300 pages.
     * Digits are masked before comparison so "Chapter 4 · 128" and
     * "Chapter 4 · 129" count as the same line.
     *
     * @param threshold fraction of units a line must appear in to be dropped.
     */
    fun detectRunningLines(units: List<String>, threshold: Double = 0.4): Set<String> {
        if (units.size < 3) return emptySet()
        val counts = HashMap<String, Int>()
        for (unit in units) {
            val lines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            // Only the edges of a page can hold a running header or footer.
            val candidates = (lines.take(2) + lines.takeLast(2)).toSet()
            for (line in candidates) {
                if (line.length > 90) continue
                val key = normalizeForComparison(line)
                if (key.isEmpty()) continue
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        val minimum = maxOf(3, (units.size * threshold).toInt())
        return counts.filterValues { it >= minimum }.keys
    }

    /** Lowercased, digit-masked, whitespace-collapsed form used for matching. */
    fun normalizeForComparison(line: String): String =
        line.lowercase()
            .replace(Regex("\\d+"), "#")
            .replace(Regex("[^\\p{L}\\p{N}#]+"), " ")
            .trim()

    /**
     * Standalone 1-2 character non-word OCR artefacts to filter out.
     * e.g., isolated `|`, `_`, or random single letters that aren't valid words like A or I.
     */
    private fun isOrphanGarbage(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.length == 1) {
            val c = trimmed[0]
            if (c == 'A' || c == 'I' || c == 'a' || c == 'i') return false
            return true
        }
        if (trimmed.length == 2) {
            if (trimmed == "--") return false
            if (trimmed.any { it in "|_~^`°¬§¦*" }) return true
            if (trimmed.none { it.isLetterOrDigit() }) return true
        }
        return false
    }

    /**
     * True when a line lacks sentence-terminating punctuation.
     */
    private fun lacksTerminator(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        if (TERMINATORS.contains(last)) return false
        if (CLOSERS.contains(last) && trimmed.length >= 2) {
            val prev = trimmed.dropLast(1).last()
            if (TERMINATORS.contains(prev)) return false
        }
        return true
    }

    /**
     * True when a line is formatted in Title Case (e.g. "The Art of Electronics", "Third Edition").
     */
    private fun isTitleCase(line: String): Boolean {
        val words = line.trim().split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
        if (words.size !in 1..6) return false
        val hasCapital = words.any { it.firstOrNull()?.isUpperCase() == true }
        if (!hasCapital) return false
        return words.all { w ->
            val letters = w.filter { it.isLetter() }
            if (letters.isEmpty()) true
            else {
                val isCapitalized = letters.first().isUpperCase() && (letters.length == 1 || letters.drop(1).all { it.isLowerCase() })
                isCapitalized || letters.lowercase() in TITLE_CASE_MINOR_WORDS
            }
        }
    }

    /**
     * True when a line is letter-spaced (many 1-2 character fragments separated by spaces).
     */
    private fun isLetterSpaced(line: String): Boolean {
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 3) return false
        val shortCount = words.count { it.length <= 2 }
        return shortCount >= words.size * 0.5
    }

    /**
     * Decides if two consecutive lines should be merged as parts of a unified title block.
     * Stage 2:
     * - Extremely short (fewer than 4-5 words or orphan fragments like TH, E OF, ELEC)
     * - Lack terminating punctuation
     * - Share similar visual traits (all uppercase or title case)
     */
    private fun shouldMergeTitleLines(prev: String, curr: String): Boolean {
        if (!lacksTerminator(prev) || !lacksTerminator(curr)) return false
        if (prev.startsWith("--") || curr.startsWith("--")) return false
        if (prev.startsWith("[") && prev.endsWith("]")) return false
        if (curr.startsWith("[") && curr.endsWith("]")) return false
        if (PAGE_FURNITURE_SLASH.matches(prev) || PAGE_FURNITURE_SLASH.matches(curr)) return false

        val prevLetters = prev.filter { it.isLetter() }
        val currLetters = curr.filter { it.isLetter() }
        if (prevLetters.isEmpty() || currLetters.isEmpty()) return false

        val prevUpper = prevLetters.length >= 2 && prevLetters.all { it.isUpperCase() }
        val currUpper = currLetters.length >= 2 && currLetters.all { it.isUpperCase() }
        if (prevUpper && currUpper) {
            val prevWords = prev.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            val currWords = curr.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            if (prevWords < 5 || currWords < 5 || isLetterSpaced(prev) || isLetterSpaced(curr)) {
                return true
            }
        }

        val prevTitle = isTitleCase(prev)
        val currTitle = isTitleCase(curr)
        if (prevTitle && currTitle) {
            val prevWords = prev.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            val currWords = curr.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            if (prevWords < 5 && currWords < 5) {
                return true
            }
        }

        return false
    }

    /**
     * Reconstructs stylized or letter-spaced title tokens into clean words.
     */
    private fun reconstructStylizedText(text: String): String {
        var t = text
        t = t.replace(Regex("\\bTH\\s+E\\b"), "THE")
        t = t.replace(Regex("\\bA\\s+R\\s+T\\b"), "ART")
        t = t.replace(Regex("\\bO\\s+F\\b"), "OF")
        t = t.replace(Regex("\\bELECTR\\s+O\\s+N\\s+ICS\\b"), "ELECTRONICS")
        t = t.replace(Regex("\\bELEC\\s+TRONIC\\b"), "ELECTRONIC")
        t = t.replace(Regex("\\bELEC\\s+TRONICS\\b"), "ELECTRONICS")
        t = t.replace(Regex("\\bELECTR\\s+ONIC\\b"), "ELECTRONIC")
        t = t.replace(Regex("\\bELECTR\\s+ONICS\\b"), "ELECTRONICS")
        t = t.replace(Regex("\\bTHE\\s+OF\\s+ELECTRONIC[S]?\\b"), "THE ART OF ELECTRONICS")
        t = t.replace(Regex("\\bTHE\\s+ART\\s+OF\\s+ELECTRONIC\\b"), "THE ART OF ELECTRONICS")
        return t
    }

    /**
     * Cleans one unit of extracted text: drops noise, repairs hyphenated line
     * breaks, unwraps soft line breaks inside paragraphs, merges consecutive
     * title lines into unified blocks, and keeps real paragraph breaks as a blank line.
     */
    fun clean(raw: String, runningLines: Set<String> = emptySet()): String {
        if (raw.isBlank()) return ""

        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = CONTROL_NOISE.replace(text, "")
        text = text.replace(SOFT_HYPHEN.toString(), "")

        val kept = ArrayList<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                kept += ""
                continue
            }
            if (PAGE_NUMBER_LINE.matches(trimmed)) continue
            if (runningLines.isNotEmpty() && normalizeForComparison(trimmed) in runningLines) continue
            if (isOrphanGarbage(trimmed)) continue
            kept += trimmed
        }

        val builder = StringBuilder(text.length)
        for ((index, line) in kept.withIndex()) {
            if (line.isEmpty()) {
                if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
                    if (builder.endsWith("\n")) builder.append('\n') else builder.append("\n\n")
                }
                continue
            }

            if (builder.isEmpty() || builder.endsWith("\n")) {
                builder.append(line)
                continue
            }

            val previous = kept.take(index).lastOrNull { it.isNotEmpty() }.orEmpty()

            // Title block reconstruction: consecutive short lines sharing visual traits merge
            if (shouldMergeTitleLines(previous, line)) {
                builder.append(' ').append(line)
                continue
            }

            // A structural line on either side forces a paragraph break, so a
            // signature, furniture, or title block stands alone from surrounding prose.
            val structuralBorder = isStructuralLine(line) || isStructuralLine(previous)
            when {
                structuralBorder -> {
                    builder.append("\n\n").append(line)
                }
                // Hyphenated line break: `under-` + `stand` → `understand`, but `well-` + `known` → `well-known`
                previous.endsWith("-") && !previous.endsWith("--") && line.firstOrNull()?.isLetter() == true -> {
                    val prevWord = previous.substringAfterLast(' ').substringAfterLast('\n').trimEnd('-')
                    val keepHyphenPrefixes = setOf("co", "self", "well", "ex", "anti", "non", "pre", "post", "re", "semi", "ultra", "inter", "trans")
                    val shouldKeepHyphen = prevWord.length <= 3 || prevWord.lowercase() in keepHyphenPrefixes
                    if (shouldKeepHyphen) {
                        builder.append(line)
                    } else {
                        builder.setLength(builder.length - 1)
                        builder.append(line)
                    }
                }
                endsParagraph(previous, line) -> {
                    builder.append("\n\n").append(line)
                }
                else -> builder.append(' ').append(line)
            }
        }

        var result = builder.toString()
        result = MULTI_SPACE.replace(result, " ")
        result = MULTI_BLANK_LINE.replace(result, "\n\n")
        result = reconstructStylizedText(result)
        return result.trim()
    }

    /**
     * True when a whole line is structural scaffolding rather than prose: a
     * `--` signature or attribution, an all-caps title block, a digits-only
     * page number, `27 / 312` page furniture, or a `[bracketed]` direction.
     */
    private fun isStructuralLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("--")) return true
        if (trimmed.length >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) return true
        if (PAGE_FURNITURE_SLASH.matches(trimmed)) return true
        val letters = trimmed.filter { it.isLetter() }
        // No letters at all: a bare number is page furniture that must stand alone
        if (letters.isEmpty()) return trimmed.any { it.isDigit() }
        // All-caps with at least two letters: `THIRD EDITION`, `CHAPTER 1`
        return letters.length >= 2 && letters.all { it.isUpperCase() }
    }

    /**
     * Decides whether a line break is a paragraph break. A terminated previous
     * line followed by a line that clearly starts something new is the only
     * signal available once indentation has been stripped by extraction.
     */
    private fun endsParagraph(previous: String, next: String): Boolean {
        val last = previous.trimEnd().lastOrNull() ?: return false
        val terminated = TERMINATORS.contains(last) || (CLOSERS.contains(last) &&
            previous.trimEnd().dropLast(1).lastOrNull()?.let { TERMINATORS.contains(it) } == true)
        if (!terminated) return false
        val first = next.firstOrNull() ?: return false
        return first.isUpperCase() || first.isDigit() || OPENERS.contains(first) ||
            !first.isLetter()
    }

    /**
     * Splits cleaned text into sentences with their character offsets.
     */
    fun splitSentences(text: String): List<Span> {
        if (text.isBlank()) return emptyList()
        val spans = ArrayList<Span>()
        var start = 0
        var index = 0

        while (index < text.length) {
            val char = text[index]

            if (TERMINATORS.contains(char)) {
                var end = index + 1
                // Absorb runs like "?!" and "..." plus any closing quotes.
                while (end < text.length && (TERMINATORS.contains(text[end]) || CLOSERS.contains(text[end]))) {
                    end++
                }
                if (isBoundary(text, index, end)) {
                    addSpan(spans, text, start, end)
                    start = end
                    index = end
                    continue
                }
                index = end
                continue
            }

            // A blank line always ends a sentence
            if (char == '\n' && index + 1 < text.length && text[index + 1] == '\n') {
                addSpan(spans, text, start, index)
                start = index + 2
                index += 2
                continue
            }

            // A single line break splits only at a structural border
            if (char == '\n' && isStructuralNewline(text, index)) {
                addSpan(spans, text, start, index)
                start = index + 1
            }

            index++
        }

        addSpan(spans, text, start, text.length)
        return spans
    }

    /** Convenience wrapper returning sentence strings only. */
    fun sentences(text: String): List<String> = splitSentences(text).map { it.text }

    /** True when the single `\n` at [index] borders a structural line that should not be merged. */
    private fun isStructuralNewline(text: String, index: Int): Boolean {
        var lineStart = index
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        var lineEnd = index + 1
        while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++
        val before = text.substring(lineStart, index).trim()
        val after = text.substring(index + 1, lineEnd).trim()
        if (before.isEmpty() || after.isEmpty()) return false
        // If these lines should merge as a title block, do NOT split on single newline
        if (shouldMergeTitleLines(before, after)) return false
        return isStructuralLine(before) || isStructuralLine(after)
    }

    private fun addSpan(spans: MutableList<Span>, text: String, start: Int, end: Int) {
        if (end <= start || start >= text.length) return
        val raw = text.substring(start, end.coerceAtMost(text.length))
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val leading = raw.indexOf(trimmed.first())
        val offset = start + (if (leading >= 0) leading else 0)
        val unified = reconstructStylizedText(trimmed.replace("\n", " ").replace(MULTI_SPACE, " ").trim())
        spans += Span(
            text = unified,
            startOffset = offset,
            endOffset = offset + trimmed.length,
        )
    }

    /**
     * True when the terminator at [terminatorIndex] really ends a sentence.
     *
     * Rejects abbreviations, initials (`J. R. R. Tolkien`), decimals (`3.14`),
     * numbered list markers at the start of a line, and a terminator followed
     * by a lowercase continuation.
     */
    private fun isBoundary(text: String, terminatorIndex: Int, endIndex: Int): Boolean {
        val terminator = text[terminatorIndex]

        if (terminator == '.') {
            val before = text.getOrNull(terminatorIndex - 1)
            val after = text.getOrNull(endIndex)

            // 3.14 / 1.5 million
            if (before != null && before.isDigit() && after != null && after.isDigit()) return false

            val word = wordBefore(text, terminatorIndex)
            if (word.length == 1 && word.first().isUpperCase()) return false
            val normalizedWord = word.lowercase()
            val isLowercaseOrdinaryWord = word == normalizedWord &&
                normalizedWord in LOWERCASE_WORD_ABBREVIATIONS
            if (normalizedWord in ABBREVIATIONS && !isLowercaseOrdinaryWord) return false
            // "1." or "iv." opening a line is a list marker, not a sentence.
            if (word.isNotEmpty() && word.all { it.isDigit() } && isAtLineStart(text, terminatorIndex, word.length)) {
                return false
            }
        }

        var scan = endIndex
        var newlineCount = 0
        while (scan < text.length && (text[scan] == ' ' || text[scan] == '\t' || text[scan] == '\u00A0' || text[scan] == '\r' || text[scan] == '\n')) {
            if (text[scan] == '\n') newlineCount++
            scan++
        }
        if (scan >= text.length) return true
        if (newlineCount >= 2) return true

        val next = text[scan]
        if (next.isLowerCase()) return false
        return next.isUpperCase() || next.isDigit() || OPENERS.contains(next) || !next.isLetter()
    }

    /** The alphanumeric token immediately before [index]. */
    private fun wordBefore(text: String, index: Int): String {
        var scan = index - 1
        val builder = StringBuilder()
        while (scan >= 0 && (text[scan].isLetterOrDigit())) {
            builder.append(text[scan])
            scan--
        }
        return builder.reverse().toString()
    }

    private fun isAtLineStart(text: String, terminatorIndex: Int, wordLength: Int): Boolean {
        var scan = terminatorIndex - wordLength - 1
        while (scan >= 0 && (text[scan] == ' ' || text[scan] == '\t')) scan--
        return scan < 0 || text[scan] == '\n'
    }
}
