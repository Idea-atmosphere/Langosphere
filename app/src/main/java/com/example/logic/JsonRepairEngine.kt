package com.example.logic

/**
 * A self-healing JSON front door for AI replies, in the spirit of the
 * `jsonrepair` library but written for exactly the damage this app sees.
 *
 * A chat model does not fail cleanly when it runs out of output budget: it
 * stops mid-token. What arrives is therefore a *prefix* of valid JSON — an
 * unterminated string, a hanging comma, three unclosed braces — plus, very
 * often, a markdown fence, a friendly sentence before the object and the
 * `CHUNK 1-20 DONE` marker after it. Throwing that away loses the nineteen
 * perfectly good sentences that did arrive, which is the worst possible
 * outcome for a learner who has already paid for the tokens.
 *
 * So the pipeline is: unwrap → repair → parse, where every repair is recorded
 * and surfaced in the UI, and the parser is deliberately tolerant.
 *
 * Pure Kotlin with no `org.json` and no Moshi dependency, so it behaves
 * identically on device and in plain JVM unit tests, and so the stable video
 * subtitle parser is never touched by book-side changes.
 *
 * UPDATED: now handles unescaped inner quotes inside Persian/English
 * translations — the #1 cause of "JSON بعد از ترمیم هم خوانده نشد".
 */
object JsonRepairEngine {

    /** What had to be done to make the payload parseable. */
    data class RepairResult(
        val json: String,
        val repairs: List<String>,
        val truncated: Boolean,
    ) {
        val wasRepaired: Boolean get() = repairs.isNotEmpty()
    }

    private val FENCE = Regex("^\\s*```[a-zA-Z]*\\s*", RegexOption.MULTILINE)
    private val CHUNK_MARKER = Regex(
        "CHUNK\\s*\\d+\\s*[-\\u2013]\\s*\\d+\\s*DONE.*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    /**
     * Removes everything that is not JSON: markdown fences, the model's
     * preamble, the trailing chunk marker, and any epilogue after the object.
     */
    fun unwrap(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw.replace("\uFEFF", "")
        text = CHUNK_MARKER.replace(text, "")
        text = FENCE.replace(text, "")
        text = text.replace("```", "")

        val objectStart = text.indexOf('{')
        val arrayStart = text.indexOf('[')
        val start = when {
            objectStart < 0 -> arrayStart
            arrayStart < 0 -> objectStart
            else -> minOf(objectStart, arrayStart)
        }
        if (start < 0) return ""
        val opener = text[start]
        val closer = if (opener == '{') '}' else ']'
        val end = text.lastIndexOf(closer)
        // When the reply was cut off there is no closing brace at all; keep
        // everything from the opener and let repair() close the structures.
        return if (end > start) text.substring(start, end + 1) else text.substring(start)
    }

    /**
     * Repairs a truncated or sloppy payload into parseable JSON.
     *
     * Handled: unterminated strings, raw newlines and tabs inside strings,
     * hanging commas, a dangling `"key":` with no value, a bare `"key"` with no
     * colon, mismatched and unclosed brackets, // or block comments,
     * and unescaped inner double-quotes inside Persian/English translations
     * (the most common reason for "JSON بعد از ترمیم هم خوانده نشد").
     */
    fun repair(raw: String): RepairResult {
        val source = unwrap(raw)
        if (source.isBlank()) {
            return RepairResult("", listOf("No JSON object was found in the text."), true)
        }

        val out = StringBuilder(source.length + 32)
        val stack = ArrayDeque<Char>()
        val repairs = ArrayList<String>()
        var truncated = false
        var inString = false
        var escaped = false
        var index = 0

        while (index < source.length) {
            val char = source[index]

            if (inString) {
                when {
                    escaped -> {
                        out.append(char)
                        escaped = false
                    }
                    char == '\\' -> {
                        out.append(char)
                        escaped = true
                    }
                    char == '"' -> {
                        var look = index + 1
                        while (look < source.length && source[look].isWhitespace()) look++
                        val afterWs = source.getOrNull(look)
                        val isLikelyClosing = afterWs == null || afterWs == ':' || afterWs == ',' || afterWs == '}' || afterWs == ']'
                        if (!isLikelyClosing && afterWs != null) {
                            // In valid JSON a closing quote is ALWAYS followed
                            // by `: , } ]` (whitespace already skipped above),
                            // so a quote followed by anything else - a letter,
                            // a Persian comma, a full stop, a parenthesis - is
                            // an unescaped INNER quote, and the real closer is
                            // looked for ahead. The old code only rescued a
                            // quote followed by a letter or digit, which is
                            // exactly backwards for Persian text: `..."نقل"، ...`
                            // ended the string early, every field after it
                            // shifted, and whole entries came out as garbage -
                            // the cascade that used to push a 41-entry answer
                            // into the lossy regex fallback over one stray
                            // quote in a neighbour entry.
                            val hasLaterCloser = hasLaterStructuralQuote(source, index + 1)
                            if (hasLaterCloser) {
                                out.append("\\\"")
                                addOnce(repairs, "Escaped an inner unescaped quote inside a translation.")
                                index++
                                continue
                            }
                        }
                        out.append(char)
                        inString = false
                    }
                    char == '\n' || char == '\r' -> {
                        out.append("\\n")
                        addOnce(repairs, "Escaped a line break inside a text value.")
                        if (char == '\r' && source.getOrNull(index + 1) == '\n') index++
                    }
                    char == '\t' -> {
                        out.append("\\t")
                        addOnce(repairs, "Escaped a tab inside a text value.")
                    }
                    else -> {
                        if (char.code in 0..0x1F) {
                            out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                            addOnce(repairs, "Escaped control character inside string.")
                        } else {
                            out.append(char)
                        }
                    }
                }
                index++
                continue
            }

            when (char) {
                '"' -> {
                    out.append(char)
                    inString = true
                }
                '\'', '’', '‘', '“', '”' -> {
                    if (isProbablyJsonQuote(source, index)) {
                        out.append('"')
                        inString = true
                        addOnce(repairs, "Normalized smart quote to standard double quote.")
                    } else {
                        out.append(char)
                    }
                }
                '{', '[' -> {
                    stack.addLast(char)
                    out.append(char)
                }
                '}', ']' -> {
                    trimDanglingTail(out, repairs)
                    val expected = if (char == '}') '{' else '['
                    if (stack.isNotEmpty() && stack.last() == expected) {
                        stack.removeLast()
                        out.append(char)
                    } else if (stack.isNotEmpty()) {
                        val open = stack.removeLast()
                        out.append(if (open == '{') '}' else ']')
                        addOnce(repairs, "Corrected a mismatched closing bracket.")
                    } else {
                        addOnce(repairs, "Dropped a stray closing bracket.")
                    }
                }
                '/' -> {
                    val next = source.getOrNull(index + 1)
                    if (next == '/') {
                        while (index < source.length && source[index] != '\n') index++
                        addOnce(repairs, "Removed a comment.")
                        continue
                    }
                    if (next == '*') {
                        val close = source.indexOf("*/", index + 2)
                        index = if (close < 0) source.length else close + 2
                        addOnce(repairs, "Removed a comment.")
                        continue
                    }
                    out.append(char)
                }
                else -> out.append(char)
            }
            index++
        }

        if (inString) {
            out.append('"')
            truncated = true
            repairs += "The reply was cut off inside a text value; it was closed."
        }

        if (stack.isNotEmpty()) {
            truncated = true
            trimDanglingTail(out, repairs)
            while (stack.isNotEmpty()) {
                val open = stack.removeLast()
                out.append(if (open == '{') '}' else ']')
            }
            repairs += "The reply was cut off; the unclosed brackets were closed."
        } else {
            trimTrailingCommas(out, repairs)
        }

        return RepairResult(out.toString().trim(), repairs, truncated)
    }

    private fun isProbablyJsonQuote(source: String, idx: Int): Boolean {
        var back = idx - 1
        while (back >= 0 && source[back].isWhitespace()) back--
        val before = source.getOrNull(back)
        return before == '{' || before == ',' || before == ':' || before == '[' || before == null
    }

    private fun hasLaterStructuralQuote(source: String, from: Int): Boolean {
        var i = from
        var inEsc = false
        while (i < source.length) {
            val c = source[i]
            if (inEsc) {
                inEsc = false
            } else if (c == '\\') {
                inEsc = true
            } else if (c == '"') {
                var look = i + 1
                while (look < source.length && source[look].isWhitespace()) look++
                val after = source.getOrNull(look)
                if (after == ':' || after == ',' || after == '}' || after == ']') return true
            }
            // A book value is routinely hundreds of characters (a 300+ char
            // english plus a 400+ char IPA line), so the window must clear the
            // longest real value, not just a short subtitle line.
            if (i - from > 4000) break
            i++
        }
        return false
    }

    /**
     * Removes whatever incomplete fragment sits at the end of [out] before a
     * closing bracket is written: a hanging comma, a `"key":` whose value never
     * arrived, or a bare `"key"` with no colon.
     */
    private fun trimDanglingTail(out: StringBuilder, repairs: MutableList<String>) {
        var changed = true
        while (changed) {
            changed = false
            trimWhitespace(out)
            if (out.isEmpty()) return

            when (out.last()) {
                ',' -> {
                    out.setLength(out.length - 1)
                    addOnce(repairs, "Removed a trailing comma.")
                    changed = true
                }
                ':' -> {
                    out.setLength(out.length - 1)
                    trimWhitespace(out)
                    if (removeTrailingString(out)) {
                        addOnce(repairs, "Removed a field whose value was missing.")
                    }
                    changed = true
                }
                '"' -> {
                    // A quoted token directly after `{` or `,` is a key with no
                    // value; anything else is a real, complete value.
                    if (isDanglingKey(out)) {
                        removeTrailingString(out)
                        addOnce(repairs, "Removed a field whose value was missing.")
                        changed = true
                    }
                }
            }
        }
    }

    /** True when the string ending at the tail of [out] is a key with no colon. */
    private fun isDanglingKey(out: StringBuilder): Boolean {
        val start = stringStart(out) ?: return false
        var index = start - 1
        while (index >= 0 && out[index].isWhitespace()) index--
        if (index < 0) return false
        return out[index] == '{' || out[index] == ','
    }

    private fun removeTrailingString(out: StringBuilder): Boolean {
        if (out.isEmpty() || out.last() != '"') return false
        val start = stringStart(out) ?: return false
        out.setLength(start)
        trimWhitespace(out)
        if (out.isNotEmpty() && out.last() == ',') out.setLength(out.length - 1)
        return true
    }

    /** Index of the opening quote of the string that ends at the tail of [out]. */
    private fun stringStart(out: StringBuilder): Int? {
        var index = out.length - 2
        while (index >= 0) {
            if (out[index] == '"') {
                var backslashes = 0
                var scan = index - 1
                while (scan >= 0 && out[scan] == '\\') {
                    backslashes++
                    scan--
                }
                if (backslashes % 2 == 0) return index
            }
            index--
        }
        return null
    }

    private fun trimTrailingCommas(out: StringBuilder, repairs: MutableList<String>) {
        trimWhitespace(out)
        while (out.isNotEmpty() && out.last() == ',') {
            out.setLength(out.length - 1)
            addOnce(repairs, "Removed a trailing comma.")
            trimWhitespace(out)
        }
    }

    private fun trimWhitespace(out: StringBuilder) {
        while (out.isNotEmpty() && out.last().isWhitespace()) out.setLength(out.length - 1)
    }

    private fun addOnce(repairs: MutableList<String>, message: String) {
        if (repairs.none { it == message }) repairs += message
    }

    /**
     * Parses repaired JSON into a [JsonValue] tree.
     *
     * Tolerant on purpose: single-quoted strings, unquoted object keys,
     * trailing commas and stray whitespace are all accepted, because models
     * produce all four and none of them change the meaning. Returns null only
     * when there is genuinely no structure to read.
     */
    fun parse(json: String): JsonValue? {
        if (json.isBlank()) return null
        return try {
            val reader = Reader(json)
            reader.skipWhitespace()
            reader.readValue()
        } catch (error: IllegalArgumentException) {
            null
        } catch (error: IndexOutOfBoundsException) {
            null
        }
    }

    /** Repairs and parses in one step. */
    fun repairAndParse(raw: String): Pair<JsonValue?, RepairResult> {
        val result = repair(raw)
        return parse(result.json) to result
    }

    /** Escapes a Kotlin string for inclusion in a JSON document. */
    fun escape(text: String): String {
        val out = StringBuilder(text.length + 8)
        for (char in text) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        return out.toString()
    }

    private class Reader(private val source: String) {
        private var index = 0

        fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        fun readValue(): JsonValue {
            skipWhitespace()
            if (index >= source.length) throw IllegalArgumentException("end of input")
            return when (source[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"', '\'' -> JsonValue.Str(readString(source[index]))
                't', 'T' -> readLiteral("true", JsonValue.Bool(true))
                'f', 'F' -> readLiteral("false", JsonValue.Bool(false))
                'n', 'N' -> readLiteral("null", JsonValue.Null)
                else -> readNumber()
            }
        }

        private fun readLiteral(word: String, value: JsonValue): JsonValue {
            if (source.regionMatches(index, word, 0, word.length, ignoreCase = true)) {
                index += word.length
                return value
            }
            throw IllegalArgumentException("bad literal at $index")
        }

        private fun readObject(): JsonValue {
            index++ // consume '{'
            val entries = LinkedHashMap<String, JsonValue>()
            while (true) {
                skipWhitespace()
                if (index >= source.length) break
                if (source[index] == '}') {
                    index++
                    break
                }
                if (source[index] == ',') {
                    index++
                    continue
                }
                val key = when (source[index]) {
                    '"', '\'' -> readString(source[index])
                    else -> readBareKey()
                }
                skipWhitespace()
                if (index < source.length && source[index] == ':') index++
                skipWhitespace()
                if (index >= source.length) break
                if (source[index] == ',' || source[index] == '}') continue
                entries[key] = readValue()
            }
            return JsonValue.Obj(entries)
        }

        private fun readArray(): JsonValue {
            index++ // consume '['
            val items = ArrayList<JsonValue>()
            while (true) {
                skipWhitespace()
                if (index >= source.length) break
                when (source[index]) {
                    ']' -> {
                        index++
                        return JsonValue.Arr(items)
                    }
                    ',' -> {
                        index++
                        continue
                    }
                    else -> items += readValue()
                }
            }
            return JsonValue.Arr(items)
        }

        private fun readBareKey(): String {
            val start = index
            while (index < source.length && source[index] != ':' && !source[index].isWhitespace()) index++
            if (index == start) throw IllegalArgumentException("empty key at $index")
            return source.substring(start, index)
        }

        private fun readString(quote: Char): String {
            index++ // consume the opening quote
            val out = StringBuilder()
            while (index < source.length) {
                val char = source[index]
                when {
                    char == '\\' -> {
                        index++
                        if (index >= source.length) break
                        when (val escapeChar = source[index]) {
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                val hex = source.substring(
                                    (index + 1).coerceAtMost(source.length),
                                    (index + 5).coerceAtMost(source.length),
                                )
                                val code = hex.toIntOrNull(16)
                                if (code != null && hex.length == 4) {
                                    out.append(code.toChar())
                                    index += 4
                                } else {
                                    out.append("\\u")
                                }
                            }
                            else -> out.append(escapeChar)
                        }
                        index++
                    }
                    char == quote -> {
                        index++
                        return out.toString()
                    }
                    else -> {
                        out.append(char)
                        index++
                    }
                }
            }
            return out.toString()
        }

        private fun readNumber(): JsonValue {
            val start = index
            if (index < source.length && (source[index] == '-' || source[index] == '+')) index++
            while (index < source.length) {
                val char = source[index]
                val isExponentSign = (char == '-' || char == '+') &&
                    index > 0 && (source[index - 1] == 'e' || source[index - 1] == 'E')
                if (char.isDigit() || char == '.' || char == 'e' || char == 'E' || isExponentSign) {
                    index++
                } else {
                    break
                }
            }
            val text = source.substring(start, index)
            val number = text.toDoubleOrNull()
                ?: throw IllegalArgumentException("bad number '$text' at $start")
            return JsonValue.Num(number)
        }
    }
}

/**
 * A minimal JSON tree, independent of `org.json` so the ingest pipeline is
 * testable on the JVM without Robolectric.
 */
sealed class JsonValue {
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    object Null : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue()

    /** Case-insensitive child lookup, because models capitalise keys freely. */
    operator fun get(key: String): JsonValue? {
        val obj = this as? Obj ?: return null
        obj.entries[key]?.let { return it }
        val lower = key.lowercase()
        return obj.entries.entries.firstOrNull { it.key.lowercase() == lower }?.value
    }

    fun asString(): String? = when (this) {
        is Str -> value
        is Num -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Bool -> value.toString()
        else -> null
    }

    fun asInt(): Int? = when (this) {
        is Num -> value.toInt()
        is Str -> value.trim().toDoubleOrNull()?.toInt()
        else -> null
    }

    fun asDouble(): Double? = when (this) {
        is Num -> value
        is Str -> value.trim().toDoubleOrNull()
        else -> null
    }

    fun asList(): List<JsonValue> = when (this) {
        is Arr -> items
        is Null -> emptyList()
        else -> listOf(this)
    }

    /** Trimmed string child, or null when absent or blank. */
    fun stringOrNull(key: String): String? =
        get(key)?.asString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}
