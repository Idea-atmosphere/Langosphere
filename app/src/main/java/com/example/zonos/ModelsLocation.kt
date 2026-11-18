package com.example.zonos

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.provider.MediaStore
import java.io.IOException
import java.io.InputStream

/**
 * Model storage in the PUBLIC Downloads folder.
 *
 *   /storage/emulated/0/Download/Langosphere/models/
 *     backbone/ zac/ voices/ laci/ companions/ + .pack-manifest.json
 *
 * The learner can either let the app download the pack there, or put the
 * files there by hand (browser download, `hf download`, a copy from
 * another device) and point the app at the folder once.
 *
 * Why this shape (scoped storage, no storage permission):
 * - Raw java.io.File writes to shared storage are blocked on API 30+
 *   (EACCES), and MANAGE_EXTERNAL_STORAGE is deliberately NOT requested.
 *   So every write goes through a sanctioned route:
 *     (a) MediaStore.Downloads — files the app inserts are OWNED by the
 *         app, so it can query and read them back with no permission.
 *         This is the default path for the in-app downloader and for zip
 *         imports: no user interaction, files land in Downloads.
 *     (b) A SAF tree the user picked once (ACTION_OPEN_DOCUMENT_TREE on
 *         Download/Langosphere or a parent) — persistable URI permission,
 *         survives app updates. This is the path for files the user put
 *         there themselves (browser download, hf download, an older
 *         install): MediaStore cannot see another app's files, but the
 *         SAF tree enumerates everything regardless of owner. It is also
 *         the ONLY route on Android 9 and older, where MediaStore.Downloads
 *         does not exist (the learner picks the folder once).
 * - Reads go through ContentResolver streams (no File API on shared
 *   storage), so ModelFileServer serves them to the WebView the same way.
 * - Partial downloads (.part) live in the private cache dir, where the
 *   File API and HTTP Range resume work exactly as in v0.2.0; only the
 *   completed, size-verified file is published into Downloads. Pause =
 *   cancel the coroutine (the .part stays); resume continues it.
 * - DownloadManager is NOT used: it has no public pause API, and its
 *   content URIs are awkward to serve from. The in-app HttpURLConnection
 *   downloader keeps progress / pause / resume / verify as before.
 *
 * Survival: files in Downloads are NOT deleted when the app is updated
 * or uninstalled — that is the point. The app-side pack manifest
 * (.pack-manifest.json) lives next to the packs for the same reason.
 */
object ModelsLocation {

    const val APP_DIR_NAME = "Langosphere"

    /** MediaStore RELATIVE_PATH base (no trailing slash). */
    const val MODELS_RELATIVE_PATH = "Download/Langosphere/models"

    /** Display path shown in the UI. */
    const val MODELS_DISPLAY_PATH = "/storage/emulated/0/Download/Langosphere/models"

    /** Shared preferences file of the TTS feature. */
    const val PREFS_NAME = "zonos_tts_prefs"

    /** MediaStore.Downloads exists from Android 10 (API 29) on. */
    val mediaStoreAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Thrown by [publish] when a MediaStore insert landed on a name that
     * another app already owns in the same folder (the provider renames
     * ours to "shard-000 (1).bin", which would never be found by path).
     * The fix is to pick the folder once so the write goes through SAF.
     */
    class NameClashException(relPath: String) : IOException("name clash in Downloads for $relPath")

    /** App-side pack manifest (HF commit + per-file sizes), next to the packs. */
    const val PACK_MANIFEST_NAME = ".pack-manifest.json"

    /** Pack folders of Zyphra's sharded web format (as in ModelPacks.kt). */
    private val PACK_FOLDERS = setOf(
        "backbone", "zac", "voices", "laci", "companions",
        "spk", "encoder", "teacher", "edit", "aligner", "zac-full_mr"
    )

    private const val TREE_URI_KEY = "models_tree_uri"

    enum class Source { MEDIASTORE, SAF_TREE }

    data class Located(val uri: Uri, val size: Long, val source: Source)

    @Volatile
    private var cache: Map<String, Located>? = null

    fun invalidate() {
        cache = null
    }

    // ---- persisted SAF tree ----

    fun persistTree(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission may already be held; the tree Uri is still usable
            // for this session. Persist it anyway.
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(TREE_URI_KEY, treeUri.toString()).apply()
        invalidate()
    }

    fun treeUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TREE_URI_KEY, null) ?: return null
        return try {
            Uri.parse(s)
        } catch (e: Exception) {
            null
        }
    }

    fun clearTree(context: Context) {
        treeUri(context)?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(TREE_URI_KEY).apply()
        invalidate()
    }

    // ---- resolution (cached scan) ----

    /** relPath (e.g. "backbone/shard-000.bin") -> located file, or null. */
    fun resolve(context: Context, relPath: String): Located? {
        var map = cache
        if (map == null) {
            map = scan(context)
            cache = map
        }
        return map[relPath]
    }

    fun openInput(context: Context, relPath: String): InputStream? {
        val loc = resolve(context, relPath) ?: return null
        return try {
            context.contentResolver.openInputStream(loc.uri)
        } catch (e: Exception) {
            null
        }
    }

    fun sizeOf(context: Context, relPath: String): Long? =
        resolve(context, relPath)?.size

    fun readText(context: Context, relPath: String): String? {
        return try {
            openInput(context, relPath)?.bufferedReader().use { it?.readText() }
        } catch (e: Exception) {
            null
        }
    }

    /** Full scan: MediaStore own files first, then the SAF tree (MediaStore wins). */
    fun scan(context: Context): Map<String, Located> {
        val out = HashMap<String, Located>()
        out.putAll(scanMediaStore(context))
        treeUri(context)?.let { tree ->
            scanTree(context, tree).forEach { (rel, loc) -> out.putIfAbsent(rel, loc) }
        }
        return out
    }

    private fun scanMediaStore(context: Context): Map<String, Located> {
        val out = HashMap<String, Located>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return out
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.RELATIVE_PATH,
            MediaStore.Downloads.SIZE
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$MODELS_RELATIVE_PATH/%")
        try {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val relCol = c.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: continue
                    val rel = c.getString(relCol) ?: continue
                    val size = c.getLong(sizeCol)
                    val folder = rel.removePrefix("$MODELS_RELATIVE_PATH/").trimEnd('/')
                    val relPath = if (folder.isEmpty()) name else "$folder/$name"
                    val uri = ContentUris.withAppendedId(collection, id)
                    out[relPath] = Located(uri, size, Source.MEDIASTORE)
                }
            }
        } catch (e: Exception) {
            // MediaStore unavailable: fall through to the SAF tree.
        }
        return out
    }

    /** Enumerate the persisted SAF tree; map tree paths to pack relPaths. */
    private fun scanTree(context: Context, treeUri: Uri): Map<String, Located> {
        val out = HashMap<String, Located>()
        try {
            walkTree(context, treeUri) { docUri, treeRel, size ->
                val rel = mapTreeRel(treeRel) ?: return@walkTree
                out.putIfAbsent(rel, Located(docUri, size, Source.SAF_TREE))
            }
        } catch (e: Exception) {
            // Tree permission revoked or folder deleted: ignore.
        }
        return out
    }

    /**
     * Map a path inside the picked tree to a pack relPath, stripping any
     * leading web/ models/ Langosphere/ (or ZonoLingua/) segments. The
     * picked folder may be Download/, Download/Langosphere/,
     * Download/Langosphere/models/, a single pack folder, or a web/
     * checkout.
     */
    private fun mapTreeRel(treeRel: String): String? {
        var p = treeRel
        var changed: Boolean
        do {
            changed = false
            for (prefix in arrayOf("web/", "models/", "Langosphere/", "ZonoLingua/")) {
                if (p.startsWith(prefix)) {
                    p = p.removePrefix(prefix)
                    changed = true
                }
            }
        } while (changed)
        if (p == PACK_MANIFEST_NAME) return p
        val folder = p.substringBefore('/', "")
        return if (folder in PACK_FOLDERS) p else null
    }

    /**
     * Walk a SAF tree depth-first. [onFile] gets (document Uri,
     * path relative to the tree root, size). Descent rule: at the top
     * level only into folders that can contain packs (pack folders,
     * Langosphere/models/web wrappers, or a folder named like the app /
     * a pack — e.g. an extracted "zonos-core-voices" folder);
     * once inside a pack folder, descend into EVERYTHING, so nested
     * layouts like voices/English/ or voices/Mandarin Chinese/ (v0.2.2)
     * are found. A huge Downloads folder is therefore not walked blind.
     */
    private fun walkTree(
        context: Context,
        treeUri: Uri,
        onFile: (Uri, String, Long) -> Unit
    ) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val stack = ArrayDeque<Pair<String, String>>() // (documentId, relPath)
        stack.add(rootId to "")
        val descendable = PACK_FOLDERS + setOf("Langosphere", "ZonoLingua", "models", "web")
        while (stack.isNotEmpty()) {
            val (docId, rel) = stack.removeFirst()
            val insidePack = rel.split('/').firstOrNull() in PACK_FOLDERS
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null, null, null
            ) ?: continue
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                while (it.moveToNext()) {
                    val childId = it.getString(idCol)
                    val name = it.getString(nameCol) ?: continue
                    val mime = it.getString(mimeCol) ?: ""
                    val size = try {
                        it.getLong(sizeCol)
                    } catch (e: Exception) {
                        -1L
                    }
                    val childRel = if (rel.isEmpty()) name else "$rel/$name"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val lower = name.lowercase()
                        if (insidePack || name in descendable ||
                            "langosphere" in lower || "zonolingua" in lower ||
                            "zonos" in lower || "core" in lower
                        ) {
                            stack.add(childId to childRel)
                        }
                    } else {
                        onFile(docUri, childRel, size)
                    }
                }
            }
        }
    }

    // ---- publish / delete ----

    private fun mimeFor(relPath: String): String = when (relPath.substringAfterLast('.', "")) {
        "json" -> "application/json"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    /**
     * Write [input] to Download/Langosphere/models/<relPath>, overwriting
     * any existing file at that path. Prefers the SAF tree when the file
     * is already there (covers files another app put there), then the
     * app's own MediaStore entry, then creating it in the SAF tree, and
     * finally a fresh MediaStore insert.
     *
     * Throws IOException on failure. If a MediaStore insert lands on a
     * name already owned by another app, the provider renames ours
     * ("shard-000 (1).bin") — that is detected and reported, because the
     * renamed file would not be found by path; the fix is to pick the
     * folder once via "locate" so the write goes through SAF instead.
     */
    @Throws(IOException::class)
    fun publish(context: Context, relPath: String, input: InputStream): Uri {
        val mime = mimeFor(relPath)
        // 1. Already in the SAF tree? Overwrite it there.
        treeFile(context, relPath)?.let { docUri ->
            writeTo(context, docUri, input)
            invalidate()
            return docUri
        }
        // 2. Our own MediaStore entry? Overwrite it.
        mediaEntry(context, relPath)?.let { entryUri ->
            writeTo(context, entryUri, input)
            invalidate()
            return entryUri
        }
        // 3. SAF tree picked? Create the file inside it.
        treeUri(context)?.let { tree ->
            createTreeFile(context, tree, relPath, mime)?.let { docUri ->
                writeTo(context, docUri, input)
                invalidate()
                return docUri
            }
        }
        // 4. Fresh MediaStore insert (default path, no user interaction).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException(
                "Android 10 or newer is needed to write into Downloads automatically; " +
                    "pick the models folder once instead"
            )
        }
        val folder = relPath.substringBeforeLast('/', "")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, relPath.substringAfterLast('/'))
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                if (folder.isEmpty()) "$MODELS_RELATIVE_PATH/" else "$MODELS_RELATIVE_PATH/$folder/"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed for $relPath")
        try {
            writeTo(context, uri, input)
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        } catch (e: Exception) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
        // Detect a provider rename (name clash with another app's file).
        val actualName = try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        if (actualName != null && actualName != relPath.substringAfterLast('/')) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw NameClashException(relPath)
        }
        invalidate()
        return uri
    }

    private fun writeTo(context: Context, uri: Uri, input: InputStream) {
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            input.copyTo(out)
        } ?: throw IOException("openOutputStream failed for $uri")
    }

    /** The app's own MediaStore entry for [relPath], if any. */
    private fun mediaEntry(context: Context, relPath: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val folder = relPath.substringBeforeLast('/', "")
        val name = relPath.substringAfterLast('/')
        val rel = if (folder.isEmpty()) "$MODELS_RELATIVE_PATH/" else "$MODELS_RELATIVE_PATH/$folder/"
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection =
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
        return try {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, arrayOf(rel, name), null
            )?.use { c ->
                if (c.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        c.getLong(0)
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The SAF-tree document for [relPath], if the tree is picked and has it. */
    private fun treeFile(context: Context, relPath: String): Uri? {
        val tree = treeUri(context) ?: return null
        return try {
            scanTree(context, tree)[relPath]?.uri
        } catch (e: Exception) {
            null
        }
    }

    /** Create folders + file for [relPath] inside the SAF tree. */
    private fun createTreeFile(
        context: Context,
        treeUri: Uri,
        relPath: String,
        mime: String
    ): Uri? {
        val segments = relPath.split('/')
        val fileName = segments.last()
        var parentId = DocumentsContract.getTreeDocumentId(treeUri)
        for (folder in segments.dropLast(1)) {
            val next = findOrCreateDir(context, treeUri, parentId, folder) ?: return null
            parentId = DocumentsContract.getDocumentId(next)
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        return try {
            DocumentsContract.createDocument(
                context.contentResolver, parentUri, mime, fileName
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun findOrCreateDir(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        name: String
    ): Uri? {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext()) {
                    if (c.getString(nameCol) == name &&
                        c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idCol))
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return try {
            DocumentsContract.createDocument(
                context.contentResolver, parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR, name
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Delete [relPath] from wherever it lives (MediaStore and/or SAF tree). */
    fun delete(context: Context, relPath: String): Boolean {
        var deleted = false
        resolve(context, relPath)?.let { loc ->
            try {
                when (loc.source) {
                    Source.MEDIASTORE -> {
                        if (context.contentResolver.delete(loc.uri, null, null) > 0) deleted = true
                    }
                    Source.SAF_TREE -> {
                        if (DocumentsContract.deleteDocument(context.contentResolver, loc.uri)) {
                            deleted = true
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
        // A stale duplicate in the other source: remove it too.
        try {
            mediaEntry(context, relPath)?.let { uri ->
                if (context.contentResolver.delete(uri, null, null) > 0) deleted = true
            }
        } catch (e: Exception) {
        }
        try {
            treeFile(context, relPath)?.let { uri ->
                if (DocumentsContract.deleteDocument(context.contentResolver, uri)) deleted = true
            }
        } catch (e: Exception) {
        }
        invalidate()
        return deleted
    }

    fun writeText(context: Context, relPath: String, text: String) {
        publish(context, relPath, text.toByteArray(Charsets.UTF_8).inputStream())
    }
}
