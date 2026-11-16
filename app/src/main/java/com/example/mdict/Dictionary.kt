package com.example.mdict

import java.io.Closeable

/**
 * One dictionary: an .mdx file plus the .mdd resource files that serve it
 * (same-basename MDDs first, then any shared MDDs such as a common
 * Sound.mdd). Lookup follows `@@@LINK=` / `@@LINK=` redirects (with a loop
 * guard); resources are looked up by path with backslash/forward-slash
 * normalisation, because MDD keys are Windows-style paths like
 * \images\logo.png.
 */
class Dictionary(
    private val mdx: MdxFile,
    private val mdds: List<MddFile>,
    /** Display name of the imported .mdx file (what the UI filters by). */
    val fileName: String = mdx.file.name
) : Closeable {

    /** Title from the MDX header, or the file name without extension. */
    val title: String get() = mdx.title

    val numEntries: Int get() = mdx.numEntries

    /** KeyCaseSensitive from the MDX header (false = the file matches any casing itself). */
    val caseSensitive: Boolean get() = mdx.caseSensitive

    /** Every headword of the MDX in file order. */
    fun headwords(): List<String> = mdx.headwords()

    /** Entry HTML for [word] (first stored entry), following redirects, or null. */
    fun lookup(word: String): String? = lookupAll(word).firstOrNull()

    /**
     * HTML of EVERY entry stored under [word] (an MDX may hold "test" twice,
     * noun and verb), each with redirects followed. Empty when absent.
     */
    fun lookupAll(word: String): List<String> {
        val texts = mdx.lookupTextAll(word)
        if (texts.isEmpty()) return emptyList()
        val out = ArrayList<String>(texts.size)
        for (text in texts) {
            val resolved = followRedirects(text, word)
            if (resolved != null) out.add(resolved)
        }
        return out
    }

    private fun followRedirects(firstText: String, firstWord: String): String? {
        var text = firstText
        val seen = HashSet<String>()
        seen.add(firstWord)
        repeat(10) {
            val target = redirectTarget(text) ?: return text
            if (!seen.add(target)) return null // Redirect loop.
            text = mdx.lookupText(target) ?: return null
        }
        return null
    }

    fun suggestions(prefix: String, limit: Int): List<String> =
        mdx.suggestions(prefix, limit)

    /**
     * Raw resource bytes for a path like "pic.png", "/images/logo.png" or
     * "images\logo.png". MDD keys are Windows-style paths like
     * \images\logo.png, so the path is normalised (leading slashes off,
     * backslashes unified) and tried exactly first; on a miss, a
     * case-insensitive scan of the MDD keys runs, because real dictionaries
     * mix casings between entry HTML and MDD keys.
     */
    fun resource(path: String): ByteArray? = findResource(mdds, path)

    /** Closes the MDX only; MDDs may be shared and are closed by the owner. */
    override fun close() {
        mdx.close()
    }

    companion object {
        /**
         * Raw resource bytes for [path] from the first of [mdds] that has it.
         * The path is normalised (leading slashes off, backslashes unified)
         * and tried exactly first; on a miss, a case-insensitive scan of the
         * MDD keys runs, because real dictionaries mix casings between entry
         * HTML and MDD keys.
         */
        fun findResource(mdds: List<MddFile>, path: String): ByteArray? {
            if (mdds.isEmpty()) return null
            val clean = path
                .substringBefore('?').substringBefore('#')
                .trimStart('/', '\\')
                .replace('\\', '/')
            if (clean.isEmpty()) return null
            val exact = listOf(
                "\\" + clean.replace('/', '\\'),
                clean,
                clean.replace('/', '\\')
            ).distinct()
            for (mdd in mdds) {
                for (candidate in exact) {
                    mdd.lookupBytes(candidate)?.let { return it }
                }
            }
            val lower = clean.lowercase()
            for (mdd in mdds) {
                for (entry in mdd.keyList) {
                    val keyNorm = entry.key
                        .trimStart('/', '\\')
                        .replace('\\', '/')
                        .lowercase()
                    if (keyNorm == lower) {
                        mdd.lookupBytes(entry.key)?.let { return it }
                    }
                }
            }
            return null
        }

        /**
         * Target headword of an MDict redirect entry, or null when [text] is
         * a normal entry. MDict writes `@@@LINK=word`; some converters emit
         * `@@LINK=word`. Trailing NUL/CR/LF (common in real files) is removed.
         */
        fun redirectTarget(text: String): String? {
            val trimmed = text.trim().trimStart('\u0000', '\uFEFF')
            val body = when {
                trimmed.startsWith("@@@LINK=") -> trimmed.removePrefix("@@@LINK=")
                trimmed.startsWith("@@LINK=") -> trimmed.removePrefix("@@LINK=")
                else -> return null
            }
            val target = body.replace(Regex("[\\n\\r\\u0000]"), "").trim()
            return target.ifEmpty { null }
        }
    }
}
