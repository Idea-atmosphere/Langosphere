package com.example.zonos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Model pack storage + download + import.
 *
 * Models live in the PUBLIC Downloads folder:
 *   /storage/emulated/0/Download/Langosphere/models/
 * (see store/ModelsLocation for the scoped-storage mechanics: MediaStore
 * inserts for app downloads, a user-picked SAF tree for files another app
 * put there). They are NEVER in the APK and NEVER in cache/ — so an app
 * update (small APK only) leaves them untouched, and even uninstalling
 * the app leaves them on disk. Layout mirrors Zyphra's sharded web
 * format as-is:
 *   models/<folder>/manifest.json + shard-*.bin (lora-*.bin for edit)
 * The WebView serves them to the engine as /models/... (see
 * engine/ModelFileServer); the engine itself never downloads from HF.
 *
 * Partial downloads (.part) live in the private cache dir, where HTTP
 * Range resume works; only the completed, size-verified file is
 * published into Downloads.
 *
 * Verification: every file must exist with the exact size from the pack
 * table, and every folder's manifest.json must parse and list the expected
 * shards. The engine's Cache Storage copy ('zonos3-web-models-v6') is a
 * second, engine-internal copy; deleting a pack must also clear it (the
 * controller calls ZonoEngine/StudioEngine.clearModelCache() after a delete).
 *
 * Packs already on the phone (e.g. in Download/ZonoLingua/models/ from the
 * stand-alone app) survive: pick the folder once with "locate" and they are
 * found with no re-download.
 */
class ModelPackManager(private val context: Context) {

    companion object {
        /** HF repo commit the pack file lists were resolved from. */
        const val HF_COMMIT = "d2d142a86b1e6aeb4252679673c86ecd86177bf8"
        const val HF_COMMIT_DATE = "2026-11-14"
        private const val USER_AGENT = "Langosphere-TTS/1.0"
    }

    /** A pack failed its post-download verification; [problems] are technical details. */
    class VerifyFailedException(val problems: List<String>) :
        IOException("pack verification failed: ${problems.firstOrNull() ?: "unknown"}")

    /** Display path shown in the UI (models live in public Downloads). */
    val modelsDisplayPath: String get() = ModelsLocation.MODELS_DISPLAY_PATH

    private val partsDir: File get() = File(context.cacheDir, ".parts").also { it.mkdirs() }

    /**
     * v0.2.6 model-update safety: how many files the last download run
     * REPLACED where a file already existed on disk with a DIFFERENT
     * size (a genuine content change, e.g. Zyphra re-issuing a shard).
     * Fresh installs (no previous file) do not count. The ViewModel
     * reads this after a successful download+verify and clears the
     * engine/Studio model caches once, so the worker's Cache Storage
     * never keeps serving stale shards at the same URLs. Reset at the
     * start of every run; only read on the success path.
     */
    @Volatile
    var lastReplacedChangedCount: Int = 0
        private set

    /**
     * App-side pack manifest (the web manifests carry no version field, so
     * stale-pack detection lives here): HF commit + per-file sizes +
     * install time, written after every successful download/import.
     * Stored at Download/Langosphere/models/.pack-manifest.json — next to
     * the packs, so it survives app updates AND uninstalls like they do.
     */
    fun recordInstalled(def: PackDef) {
        try {
            val root = try {
                JSONObject(
                    ModelsLocation.readText(context, ModelsLocation.PACK_MANIFEST_NAME) ?: "{}"
                )
            } catch (e: Exception) {
                JSONObject()
            }
            val files = JSONObject()
            for (f in def.files) files.put(f.relPath, f.size)
            root.put(def.id, JSONObject().apply {
                put("hfCommit", HF_COMMIT)
                put("hfCommitDate", HF_COMMIT_DATE)
                put("installedAt", System.currentTimeMillis())
                put("totalBytes", def.totalBytes)
                put("files", files)
            })
            ModelsLocation.writeText(
                context, ModelsLocation.PACK_MANIFEST_NAME, root.toString()
            )
        } catch (e: Exception) {
            // Record-keeping must never fail an install.
        }
    }

    /** HF commit recorded for an installed pack, or null if never recorded. */
    fun recordedCommit(packId: String): String? {
        return try {
            val text = ModelsLocation.readText(context, ModelsLocation.PACK_MANIFEST_NAME)
                ?: return null
            JSONObject(text).optJSONObject(packId)?.optString("hfCommit")
        } catch (e: Exception) {
            null
        }
    }

    /** The optional full_mr decoder pack is not offered by this app. */
    fun fullMrAvailable(): Boolean = false

    data class PackStatus(
        val def: PackDef,
        val installed: Boolean,
        val installedBytes: Long,
        val presentFiles: Int,
        val missing: List<PackFile>
    )

    fun status(def: PackDef): PackStatus {
        var bytes = 0L
        var present = 0
        val missing = ArrayList<PackFile>()
        for (f in def.files) {
            val size = ModelsLocation.sizeOf(context, f.relPath)
            if (size != null && size == f.size) {
                bytes += f.size
                present++
            } else {
                missing.add(f)
            }
        }
        return PackStatus(def, missing.isEmpty(), bytes, present, missing)
    }

    fun allStatuses(): List<PackStatus> = MODEL_PACKS.map { status(it) }

    fun coreInstalled(): Boolean = status(CORE_PACK).installed

    /**
     * Per-folder repair status (v0.2.2): which folders of a pack are
     * complete and which are missing, so the UI can show e.g.
     * backbone 12/12, zac 6/6, voices 0/82 — instead of a bare
     * "files incomplete". Delegates to the pure logic in PackRepair.kt.
     */
    fun folderStatuses(def: PackDef): List<FolderStatus> {
        val sizes = HashMap<String, Long?>()
        for (f in def.files) sizes[f.relPath] = ModelsLocation.sizeOf(context, f.relPath)
        return folderStatuses(def, sizes)
    }

    /** Files of a pack that are absent or have the wrong size. */
    fun missingFiles(def: PackDef): List<PackFile> {
        val sizes = HashMap<String, Long?>()
        for (f in def.files) sizes[f.relPath] = ModelsLocation.sizeOf(context, f.relPath)
        return missingFiles(def, sizes)
    }

    sealed class VerifyResult {
        data class Ok(val packId: String) : VerifyResult()
        data class Bad(val packId: String, val problems: List<String>) : VerifyResult()
    }

    /** Size check (PACKS.md) + manifest.json parses and lists the expected shards. */
    fun verifyPack(def: PackDef): VerifyResult {
        val problems = ArrayList<String>()
        val st = status(def)
        for (f in st.missing) {
            val size = ModelsLocation.sizeOf(context, f.relPath)
            problems.add(
                if (size == null) "missing: ${f.relPath}"
                else "wrong size: ${f.relPath} ($size instead of ${f.size})"
            )
        }
        // Only folders that ship a manifest.json get a manifest check.
        // (voices/ has voices.json, laci/ has index.json, companions/ has
        // steer.gguf — no manifest.json there; the engine requires
        // format === "zonos3-web-v1" for the folders that do have one.)
        for (folder in def.folders) {
            val hasManifest = def.files.any { it.folder == folder && it.name == "manifest.json" }
            if (!hasManifest) continue
            val manifestText = ModelsLocation.readText(context, "$folder/manifest.json")
            if (manifestText == null) {
                problems.add("no manifest: $folder/manifest.json")
                continue
            }
            try {
                val json = JSONObject(manifestText)
                if (json.optString("format") != "zonos3-web-v1") {
                    problems.add("wrong manifest format: $folder (${json.optString("format")})")
                }
                val shards = json.optJSONArray("shards")
                if (shards == null) {
                    problems.add("manifest lists no shards: $folder")
                } else {
                    val listed = HashSet<String>()
                    for (i in 0 until shards.length()) {
                        val entry = shards.opt(i)
                        when (entry) {
                            is String -> listed.add(entry)
                            is org.json.JSONArray -> if (entry.length() > 0) listed.add(entry.optString(0))
                            is JSONObject -> listed.add(entry.optString("name"))
                        }
                    }
                    val expected = def.files.filter { it.folder == folder }
                        .map { it.name }.filter { it != "manifest.json" }.toSet()
                    val missingInManifest = expected - listed
                    if (missingInManifest.isNotEmpty()) {
                        problems.add("manifest $folder lacks shards: ${missingInManifest.joinToString(", ")}")
                    }
                }
            } catch (e: Exception) {
                problems.add("corrupt manifest: $folder/manifest.json")
            }
        }
        return if (problems.isEmpty()) VerifyResult.Ok(def.id) else VerifyResult.Bad(def.id, problems)
    }

    /** Delete every file that belongs to this pack. Returns bytes freed. */
    fun deletePack(def: PackDef): Long {
        var freed = 0L
        for (f in def.files) {
            val size = ModelsLocation.sizeOf(context, f.relPath) ?: 0L
            if (ModelsLocation.delete(context, f.relPath)) freed += size
        }
        try {
            val text = ModelsLocation.readText(context, ModelsLocation.PACK_MANIFEST_NAME)
            if (text != null) {
                val root = JSONObject(text)
                root.remove(def.id)
                ModelsLocation.writeText(
                    context, ModelsLocation.PACK_MANIFEST_NAME, root.toString()
                )
            }
        } catch (e: Exception) {
            // Record-keeping must never fail a delete.
        }
        return freed
    }

    // ---- Download (in-app, resumable with Range) ----

    /**
     * Download one pack file by file. Partial downloads live as .part
     * files in the private cache dir and resume with HTTP Range; each
     * completed file is published into Download/Langosphere/models/.
     * [onProgress] gets (doneBytesOverall, totalBytes, currentFileName).
     * Throws on network/HTTP/verification failure; partial .part files are
     * kept so the next run resumes.
     */
    suspend fun downloadPack(
        def: PackDef,
        onProgress: (done: Long, total: Long, file: String) -> Unit
    ) {
        lastReplacedChangedCount = 0
        downloadFiles(def, def.files, def.totalBytes, onProgress)
        val result = verifyPack(def)
        if (result is VerifyResult.Bad) throw VerifyFailedException(result.problems)
        recordInstalled(def)
    }

    /**
     * "Complete the pack": download ONLY the files that are
     * absent or have the wrong size — e.g. a user who fetched only
     * backbone+zac gets just voices+laci+steer (~21 MB), never the
     * 878 MB they already have. Progress totals cover the missing
     * bytes only; already-present files are skipped, never re-fetched.
     */
    suspend fun downloadMissing(
        def: PackDef,
        onProgress: (done: Long, total: Long, file: String) -> Unit
    ) {
        lastReplacedChangedCount = 0
        val missing = missingFiles(def)
        if (missing.isEmpty()) {
            recordInstalled(def)
            return
        }
        val total = missing.sumOf { it.size }
        downloadFiles(def, missing, total, onProgress)
        val result = verifyPack(def)
        if (result is VerifyResult.Bad) throw VerifyFailedException(result.problems)
        recordInstalled(def)
    }

    /** Shared downloader: fetch [files] in order, skipping exact-size ones. */
    private suspend fun downloadFiles(
        def: PackDef,
        files: List<PackFile>,
        total: Long,
        onProgress: (done: Long, total: Long, file: String) -> Unit
    ) {
        var done = 0L
        for (f in files) {
            currentCoroutineContext().ensureActive()
            val existing = ModelsLocation.sizeOf(context, f.relPath)
            if (existing != null && existing == f.size) {
                done += f.size
                onProgress(done, total, f.name)
                continue
            }
            // v0.2.6: a previous file existed with a DIFFERENT size — this
            // run genuinely replaces its content (not a fresh install).
            if (existing != null) lastReplacedChangedCount++
            val part = File(partsDir, f.relPath.replace('/', '_') + ".part")
            downloadOne(f, part, total, done, onProgress)
            // Publish the completed file into Downloads, then drop the .part.
            // v0.2.3: if publish throws, the .part must go too — otherwise
            // the next run would resume from a complete-but-unpublished
            // part and hit the same stale-part 416 trap.
            try {
                part.inputStream().use { input ->
                    ModelsLocation.publish(context, f.relPath, input)
                }
            } catch (e: Exception) {
                part.delete()
                throw e
            }
            part.delete()
            done += f.size
            onProgress(done, total, f.name)
        }
    }

    /**
     * Download one file, resuming from its .part in the cache dir.
     * The HTTP loop lives in the pure [ResumableDownload] helper
     * (v0.2.3: stale .part at exactly the expected size is discarded,
     * and HTTP 416 is retried once with a plain GET — see there).
     * Throws on network/HTTP/verification failure.
     */
    private suspend fun downloadOne(
        f: PackFile,
        part: File,
        total: Long,
        baseDone: Long,
        onProgress: (Long, Long, String) -> Unit
    ) {
        // Capture the coroutine context here (suspend call); the plain
        // checkActive callback then just calls ensureActive() on it.
        val ctx = currentCoroutineContext()
        ResumableDownload.download(
            url = f.url,
            expectedSize = f.size,
            part = part,
            userAgent = USER_AGENT,
            checkActive = { ctx.ensureActive() },
            onProgress = { downloaded -> onProgress(baseDone + downloaded, total, f.name) }
        )
    }

    // ---- Import (SAF) ----

    data class ImportSummary(val imported: Int, val skipped: Int, val bytes: Long)

    private val knownPaths: Map<String, PackFile> by lazy {
        MODEL_PACKS.flatMap { it.files }.associateBy { it.relPath }
    }

    /**
     * Import a .zip pack file. Accepts zips whose entries are either
     * `backbone/...`, `voices/English/...` (nested voices, v0.2.2) or
     * `web/backbone/...` (one leading `web/` is stripped). A single
     * leading wrapper folder (e.g. an extracted
     * "zonos-core-voices/voices/English/...") is also stripped when the
     * remainder is a known pack path.
     * Only known pack paths are extracted; anything else is skipped.
     * Path traversal entries are rejected. Files land in
     * Download/Langosphere/models/ (see ModelsLocation.publish).
     */
    suspend fun importZip(uri: Uri): ImportSummary {
        var imported = 0
        var skipped = 0
        var bytes = 0L
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    val rawName = entry.name.trimStart('/')
                    if (entry.isDirectory || rawName.isEmpty()) {
                        zip.closeEntry()
                        continue
                    }
                    val rel = matchZipPath(rawName.removePrefix("web/"))
                    val known = rel?.let { knownPaths[it] }
                    if (known == null || rel.contains("..") || rel.startsWith("/")) {
                        skipped++
                        zip.closeEntry()
                        continue
                    }
                    ModelsLocation.publish(context, rel, zip)
                    bytes += known.size
                    imported++
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("could not open the zip file")
        return ImportSummary(imported, skipped, bytes)
    }

    /** Resolve a zip entry name to a known pack relPath, if any. */
    private fun matchZipPath(name: String): String? {
        if (name in knownPaths) return name
        // One leading wrapper folder (e.g. "zonos-core-voices/").
        val stripped = name.substringAfter('/', "")
        if (stripped.isNotEmpty() && stripped in knownPaths) return stripped
        return null
    }

    /**
     * Import a folder tree picked with SAF ACTION_OPEN_DOCUMENT_TREE.
     * The tree root may be the `web/` folder itself, a `models/` folder, or
     * a single pack folder — relative paths are matched against known pack
     * paths, so extra files are skipped, not copied. Files are COPIED into
     * Download/Langosphere/models/ (the source folder is left untouched).
     */
    suspend fun importTree(treeUri: Uri): ImportSummary {
        var imported = 0
        var skipped = 0
        var bytes = 0L
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val stack = ArrayDeque<Pair<String, String>>() // (documentId, relPath)
        stack.add(rootId to "")
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (docId, rel) = stack.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            ) ?: continue
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    val childId = it.getString(idCol)
                    val name = it.getString(nameCol) ?: continue
                    val mime = it.getString(mimeCol) ?: ""
                    val childRel = if (rel.isEmpty()) name else "$rel/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Descend unless this subtree cannot contain a pack path.
                        stack.add(childId to childRel)
                    } else {
                        val match = matchTreePath(childRel)
                        if (match == null) {
                            skipped++
                            continue
                        }
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        context.contentResolver.openInputStream(docUri)?.use { input ->
                            ModelsLocation.publish(context, match.relPath, input)
                        } ?: run { skipped++; return@run }
                        bytes += match.size
                        imported++
                    }
                }
            }
        }
        return ImportSummary(imported, skipped, bytes)
    }

    /**
     * A tree path matches if it ends with a known pack path (the picked
     * folder may be `web/`, `models/`, or a pack folder itself).
     * e.g. `web/backbone/shard-000.bin` -> `backbone/shard-000.bin`.
     */
    private fun matchTreePath(path: String): PackFile? {
        knownPaths[path]?.let { return it }
        val stripped = path.removePrefix("web/").removePrefix("models/")
        knownPaths[stripped]?.let { return it }
        // One leading wrapper folder (e.g. an extracted part-2 zip folder).
        val once = path.substringAfter('/', "")
        if (once.isNotEmpty()) knownPaths[once]?.let { return it }
        // Single pack folder as root: `shard-000.bin` alone is ambiguous
        // (many folders have one) — only accept it if the parent dir name
        // is part of the path, which it is not here. Reject.
        return null
    }

    /**
     * "Locate" an already-downloaded models folder: remember the SAF tree
     * (persistable permission) so the resolver can serve the files that
     * are already in Download/Langosphere/models/ — NO copy, NO download.
     * This is how a fresh install re-finds packs put there by a browser,
     * by `hf download`, or by the stand-alone ZonoLingua app. Returns how
     * many known pack files were found in the tree.
     */
    fun locateTree(treeUri: Uri): Int {
        ModelsLocation.persistTree(context, treeUri)
        val found = ModelsLocation.scan(context).keys.count { it in knownPaths }
        return found
    }
}
