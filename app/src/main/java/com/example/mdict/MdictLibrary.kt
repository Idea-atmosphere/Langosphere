package com.example.mdict

import java.io.Closeable
import java.io.File

/**
 * All MDict files the user imported, kept as plain files in one private
 * directory (`filesDir/dicts/`) under their original names, so an MDX and
 * its MDD resources pair up by basename exactly like on a desktop:
 *
 *  - `Oxford.mdx` + `Oxford.mdd`, `Oxford.1.mdd`, `Oxford.2.mdd` → one
 *    [Dictionary] whose own MDDs are tried first;
 *  - an MDD with no matching MDX (e.g. a common `Sound.mdd`) is shared: every
 *    dictionary falls back to it for resources;
 *  - a file that fails to open is reported in [errors] and skipped, so one
 *    bad download never hides the other dictionaries.
 *
 * Pure Kotlin (java.io only) so it is unit-tested on the JVM. All mutation
 * goes through [reload]; readers work on immutable snapshots, so a lookup
 * racing a reload sees either the old or the new set, never a half state.
 */
class MdictLibrary(val dir: File) : Closeable {

    /** One entry found for a looked-up word. */
    data class Hit(
        /** Imported file name of the MDX (what the popup's filter chips use). */
        val fileName: String,
        /** Dictionary title from the MDX header. */
        val title: String,
        /** Entry HTML, redirects already followed. */
        val html: String
    )

    @Volatile
    var dictionaries: List<Dictionary> = emptyList()
        private set

    /** Files that failed to open in the last [reload], by file name. */
    @Volatile
    var errors: Map<String, MdictException> = emptyMap()
        private set

    @Volatile
    private var openedMdds: List<MddFile> = emptyList()

    private val lock = Any()

    val isEmpty: Boolean get() = dictionaries.isEmpty()

    /** Every .mdx/.mdd file in [dir], sorted by name. */
    fun files(): List<File> = dir.listFiles()
        ?.filter { it.isFile && isMdictName(it.name) }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

    /** Where an imported file with display name [displayName] lives (or will live). */
    fun fileFor(displayName: String): File = File(dir, sanitizeFileName(displayName))

    /** Closes everything and opens every file in [dir] again. */
    fun reload() {
        synchronized(lock) {
            closeLocked()
            dir.mkdirs()
            val all = files()
            val failures = LinkedHashMap<String, MdictException>()

            val mddByName = LinkedHashMap<String, MddFile>()
            for (f in all) {
                if (!f.name.endsWith(".mdd", ignoreCase = true)) continue
                try {
                    mddByName[f.name] = openMdd(f)
                } catch (e: MdictException) {
                    failures[f.name] = e
                }
            }

            val dicts = ArrayList<Dictionary>()
            val claimedMdds = HashSet<String>()
            val mdxFiles = all.filter { it.name.endsWith(".mdx", ignoreCase = true) }
            val ownMddsOf = HashMap<String, List<String>>()
            for (f in mdxFiles) {
                val base = baseName(f.name)
                val own = mddByName.keys.filter { belongsTo(it, base) }
                ownMddsOf[f.name] = own
                claimedMdds.addAll(own)
            }
            val sharedMdds = mddByName.keys.filter { it !in claimedMdds }
            for (f in mdxFiles) {
                try {
                    val mdx = openMdx(f)
                    val own = ownMddsOf[f.name].orEmpty()
                    val ordered = (own + sharedMdds).mapNotNull { mddByName[it] }
                    dicts.add(Dictionary(mdx, ordered, fileName = f.name))
                } catch (e: MdictException) {
                    failures[f.name] = e
                }
            }

            openedMdds = mddByName.values.toList()
            dictionaries = dicts
            errors = failures
        }
    }

    /**
     * Every entry for [word] across all dictionaries, in import order.
     * Each dictionary is asked for the word as given and then, when it is
     * case-sensitive, for its lowercase / Capitalized / UPPERCASE forms,
     * because the popup lowercases words while many dictionaries keep proper
     * nouns and acronyms capitalised. (A case-insensitive MDX already
     * matches any casing by itself, so it is asked only once.)
     */
    fun lookup(word: String): List<Hit> {
        val w = word.trim()
        if (w.isEmpty()) return emptyList()
        val dicts = dictionaries
        if (dicts.isEmpty()) return emptyList()
        val hits = ArrayList<Hit>()
        for (dict in dicts) {
            val variants = if (dict.caseSensitive) caseVariants(w) else listOf(w)
            for (candidate in variants) {
                val texts = try {
                    dict.lookupAll(candidate)
                } catch (e: Exception) {
                    emptyList()
                }
                if (texts.isNotEmpty()) {
                    for (html in texts) hits.add(Hit(dict.fileName, dict.title, html))
                    break
                }
            }
        }
        return hits
    }

    /** First resource found for [path] across own and shared MDDs (for dict:// URLs). */
    fun resource(path: String): ByteArray? {
        val dicts = dictionaries
        for (dict in dicts) {
            try {
                dict.resource(path)?.let { return it }
            } catch (e: Exception) {
                // Try the next dictionary.
            }
        }
        if (dicts.isEmpty()) {
            // MDD-only library (no MDX imported yet): still serve resources.
            return try {
                Dictionary.findResource(openedMdds, path)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /** Prefix suggestions merged across dictionaries, deduped, at most [limit]. */
    fun suggestions(prefix: String, limit: Int = 30): List<String> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val seen = LinkedHashSet<String>()
        for (dict in dictionaries) {
            val list = try {
                dict.suggestions(prefix, limit)
            } catch (e: Exception) {
                emptyList()
            }
            for (s in list) {
                if (seen.size >= limit) return seen.toList()
                seen.add(s)
            }
        }
        return seen.toList()
    }

    /** Distinct headwords of every open dictionary (for "learn from dictionary"). */
    fun headwords(): List<String> {
        val seen = LinkedHashSet<String>()
        for (dict in dictionaries) seen.addAll(dict.headwords())
        return seen.toList()
    }

    override fun close() {
        synchronized(lock) { closeLocked() }
    }

    private fun closeLocked() {
        for (d in dictionaries) closeQuietly(d)
        for (m in openedMdds) closeQuietly(m)
        dictionaries = emptyList()
        openedMdds = emptyList()
        errors = emptyMap()
    }

    private fun closeQuietly(c: Closeable) {
        try {
            c.close()
        } catch (e: Exception) {
        }
    }

    companion object {
        fun isMdictName(name: String): Boolean =
            name.endsWith(".mdx", ignoreCase = true) || name.endsWith(".mdd", ignoreCase = true)

        /** `Oxford.1.mdd` → `Oxford`; `Oxford.mdx` → `Oxford`. */
        fun baseName(fileName: String): String {
            var base = fileName.substringBeforeLast('.')
            // MDict numbers extra resource files: name.mdd, name.1.mdd, name.2.mdd …
            val tail = base.substringAfterLast('.', "")
            if (tail.isNotEmpty() && tail.all { it.isDigit() } && base.contains('.')) {
                base = base.substringBeforeLast('.')
            }
            return base
        }

        /** True when [mddName] is one of the MDDs belonging to the MDX with [mdxBase]. */
        fun belongsTo(mddName: String, mdxBase: String): Boolean =
            baseName(mddName).equals(mdxBase, ignoreCase = true)

        /**
         * A safe file name for an imported dictionary: path separators and
         * control characters removed, original name otherwise preserved so
         * MDX/MDD pairing by basename keeps working.
         */
        fun sanitizeFileName(displayName: String): String {
            val cleaned = displayName.trim()
                .replace('/', '_')
                .replace('\\', '_')
                .filter { it >= ' ' && it != '\u007f' }
                .trimStart('.')
            val name = cleaned.ifEmpty { "dictionary" }
            return if (isMdictName(name)) name else "$name.mdx"
        }

        internal fun caseVariants(word: String): List<String> {
            val out = LinkedHashSet<String>()
            out.add(word)
            out.add(word.lowercase())
            out.add(word.lowercase().replaceFirstChar { it.uppercaseChar() })
            out.add(word.uppercase())
            return out.toList()
        }

        private fun openMdx(file: File): MdxFile = try {
            MdxFile(file)
        } catch (e: MdictException) {
            throw e
        } catch (e: Exception) {
            throw MdictException(MdictException.Kind.CORRUPT, e.javaClass.simpleName)
        }

        private fun openMdd(file: File): MddFile = try {
            MddFile(file)
        } catch (e: MdictException) {
            throw e
        } catch (e: Exception) {
            throw MdictException(MdictException.Kind.CORRUPT, e.javaClass.simpleName)
        }
    }
}
