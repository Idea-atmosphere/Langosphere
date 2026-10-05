package com.example.logic

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * A tiny, dependency-free EPUB reader: an .epub is a ZIP of XHTML files, so
 * java.util.zip plus the jsoup parser the app already ships is everything this
 * needs — no new library, which keeps the F-Droid build free of new artifacts.
 *
 * The book is returned as chapters of BLOCKS rather than one big string, so the
 * reader can show a chapter's illustrations in place instead of dropping them,
 * and can still hand plain text to the tap-a-word dictionary layer.
 *
 * EPUB 2 (spine/manifest in the .opf) and EPUB 3 are both handled, because both
 * declare their reading order the same way.
 */
object EpubParser {

    data class Book(
        val title: String,
        val author: String,
        val chapters: List<Chapter>,
    )

    data class Chapter(
        val title: String,
        val blocks: List<Block>,
    ) {
        /** The chapter as plain text, which is what the dictionary/word layer reads. */
        val text: String
            get() = blocks.filterIsInstance<Block.Text>().joinToString("\n\n") { it.text }
    }

    sealed class Block {
        data class Text(val text: String) : Block()
        data class Image(val entry: String) : Block()
    }

    // The last opened book's ZIP, kept so images can be pulled out lazily
    // without re-reading the whole file for every illustration.
    private var cachedUri: String? = null
    private var cachedEntries: Map<String, ByteArray>? = null

    /** True for files the reader should open as an EPUB. */
    fun isEpub(fileName: String?, mimeType: String?): Boolean {
        val name = fileName?.lowercase().orEmpty()
        val mime = mimeType?.lowercase().orEmpty()
        return name.endsWith(".epub") || mime.contains("epub")
    }

    /**
     * Parses [uri] into chapters. Returns null when the file is not a readable
     * EPUB, so the caller can fall back to the plain text/PDF path.
     */
    fun parse(context: Context, uri: Uri): Book? {
        val entries = entriesFor(context, uri) ?: return null

        val opfPath = findOpfPath(entries) ?: return null
        val opfXml = entries[opfPath]?.toString(Charsets.UTF_8) ?: return null
        val opf = Jsoup.parse(opfXml)
        val opfDir = opfPath.substringBeforeLast('/', "")

        val title = metaText(opf, "title").ifBlank { "" }
        val author = metaText(opf, "creator").ifBlank { "" }

        // manifest: id -> href
        val manifest = HashMap<String, String>()
        opf.select("item").forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
        }

        // spine: the reading order
        val spineHrefs = opf.select("itemref").mapNotNull { manifest[it.attr("idref")] }
        val documentHrefs = if (spineHrefs.isNotEmpty()) {
            spineHrefs
        } else {
            // Broken/absent spine: fall back to every XHTML file, in ZIP order.
            entries.keys.filter {
                it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true)
            }
        }

        val chapters = ArrayList<Chapter>()
        documentHrefs.forEachIndexed { index, href ->
            val path = resolve(opfDir, href)
            val bytes = entries[path] ?: entries[href] ?: return@forEachIndexed
            val html = bytes.toString(Charsets.UTF_8)
            val document = Jsoup.parse(html)
            val body = document.body() ?: return@forEachIndexed
            val baseDir = path.substringBeforeLast('/', "")
            val blocks = ArrayList<Block>()
            collectBlocks(body, baseDir, blocks)
            if (blocks.isEmpty()) return@forEachIndexed
            val heading = document.select("h1, h2, h3").firstOrNull()?.text()?.trim().orEmpty()
            val chapterTitle = when {
                heading.isNotEmpty() -> heading
                document.title().isNotBlank() -> document.title().trim()
                else -> ""
            }
            chapters += Chapter(title = chapterTitle, blocks = blocks)
        }

        if (chapters.isEmpty()) return null
        return Book(title = title, author = author, chapters = chapters)
    }

    /** Raw bytes of an image inside the book, for decoding into a bitmap. */
    fun imageBytes(context: Context, uri: Uri, entry: String): ByteArray? {
        val entries = entriesFor(context, uri) ?: return null
        return entries[entry]
    }

    /** Frees the cached ZIP once a book is closed. */
    fun releaseCache() {
        cachedUri = null
        cachedEntries = null
    }

    private fun entriesFor(context: Context, uri: Uri): Map<String, ByteArray>? {
        val key = uri.toString()
        cachedEntries?.let { if (cachedUri == key) return it }
        val entries = readZip(context, uri) ?: return null
        cachedUri = key
        cachedEntries = entries
        return entries
    }

    private fun readZip(context: Context, uri: Uri): Map<String, ByteArray>? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val result = LinkedHashMap<String, ByteArray>()
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = zip.read(chunk)
                        if (read <= 0) break
                        buffer.write(chunk, 0, read)
                        total += read
                        // Hard ceiling so a malformed or enormous file can never
                        // take the whole app's memory with it.
                        if (total > MAX_TOTAL_BYTES) break
                    }
                    result[entry.name] = buffer.toByteArray()
                    zip.closeEntry()
                    if (total > MAX_TOTAL_BYTES) break
                }
            }
            if (result.isEmpty()) null else result
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = entries.entries
            .firstOrNull { it.key.equals("META-INF/container.xml", ignoreCase = true) }
            ?.value
            ?.toString(Charsets.UTF_8)
        val declared = container
            ?.let { Jsoup.parse(it).select("rootfile").firstOrNull()?.attr("full-path") }
            ?.takeIf { it.isNotBlank() }
        if (declared != null && entries.containsKey(declared)) return declared
        return entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    /**
     * Dublin Core metadata such as dc:title. jsoup's HTML parser keeps the
     * namespace in the tag name, so the lookup matches on the part after the
     * colon and works for both `<dc:title>` and a bare `<title>`.
     */
    private fun metaText(opf: org.jsoup.nodes.Document, name: String): String =
        opf.allElements
            .firstOrNull { it.tagName().substringAfterLast(':') == name }
            ?.text()
            ?.trim()
            .orEmpty()

    private val TEXT_TAGS = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6",
        "li", "blockquote", "pre", "td", "th", "dd", "dt", "figcaption",
    )

    /**
     * Walks a chapter body into an ordered list of text paragraphs and images.
     * Anything that is only a wrapper (div/section/article/…) is walked
     * through, so nesting depth does not change the result.
     */
    private fun collectBlocks(element: Element, baseDir: String, out: MutableList<Block>) {
        for (child in element.children()) {
            when (val tag = child.tagName().lowercase()) {
                "img", "image" -> addImage(child, baseDir, out)
                in TEXT_TAGS -> {
                    val text = child.text().trim()
                    if (text.isNotEmpty()) out += Block.Text(text)
                    child.select("img, image").forEach { addImage(it, baseDir, out) }
                }
                "br", "hr", "script", "style", "head" -> Unit
                else -> {
                    // A wrapper that holds nothing but inline text (common in
                    // hand-made EPUBs) still needs to produce a paragraph.
                    if (child.children().isEmpty()) {
                        val text = child.text().trim()
                        if (text.isNotEmpty()) out += Block.Text(text)
                    } else {
                        collectBlocks(child, baseDir, out)
                    }
                    if (tag == "svg") child.select("image").forEach { addImage(it, baseDir, out) }
                }
            }
        }
    }

    private fun addImage(element: Element, baseDir: String, out: MutableList<Block>) {
        val raw = element.attr("src")
            .ifEmpty { element.attr("xlink:href") }
            .ifEmpty { element.attr("href") }
        if (raw.isBlank() || raw.startsWith("http", ignoreCase = true) || raw.startsWith("data:")) return
        val entry = resolve(baseDir, raw)
        if (out.lastOrNull() != Block.Image(entry)) out += Block.Image(entry)
    }

    /** Resolves an EPUB-relative href (including ../ and %20) into a ZIP entry name. */
    private fun resolve(baseDir: String, href: String): String {
        val cleaned = try {
            URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        } catch (e: Exception) {
            href.substringBefore('#')
        }
        if (cleaned.startsWith("/")) return cleaned.trimStart('/')
        val parts = ArrayList<String>()
        if (baseDir.isNotEmpty()) parts.addAll(baseDir.split('/').filter { it.isNotEmpty() })
        cleaned.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private const val MAX_TOTAL_BYTES = 120L * 1024L * 1024L
}
