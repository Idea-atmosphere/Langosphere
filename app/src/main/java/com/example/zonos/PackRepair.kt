package com.example.zonos

/*
 * Per-folder pack repair status — PURE Kotlin, no Android deps, so plain
 * JVM unit tests cover it.
 *
 * Why this exists: a user may have fetched only backbone/ + zac/ (18
 * files, ~878 MB) through another tool. CORE is 145 files: backbone 12 +
 * zac 6 + voices 82 + laci 44 + companions/steer.gguf 1. The engine
 * worker REQUIRES voices/voices.json + every voice codes/embedding file
 * at init (fetch throws on 404), so backbone+zac alone can never start
 * the engine even though they are 98% of the bytes. The UI shows exactly
 * which folders are missing and offers to download ONLY the missing
 * files (~21 MB), never the 878 MB again.
 */

/** One top-level folder inside a pack (e.g. "voices" inside CORE). */
data class FolderStatus(
    val folder: String,
    val presentFiles: Int,
    val totalFiles: Int,
    val installedBytes: Long,
    val totalBytes: Long,
    val missing: List<PackFile>
) {
    val complete: Boolean get() = missing.isEmpty()
    val missingBytes: Long get() = totalBytes - installedBytes
}

/**
 * Per-folder status for a pack.
 * [sizes] maps relPath ("backbone/shard-000.bin") to the on-disk size,
 * or null when the file is absent. A file counts as present ONLY when
 * its size matches the expected size exactly (a wrong-size file is
 * missing and must be re-downloaded).
 *
 * Folders are grouped by TOP-LEVEL folder for display: voices/ stays
 * one row (0/82) even though its files live in nested language
 * subfolders (voices/English/, voices/Mandarin Chinese/, ...).
 */
fun folderStatuses(def: PackDef, sizes: Map<String, Long?>): List<FolderStatus> {
    val byFolder = LinkedHashMap<String, MutableList<PackFile>>()
    for (f in def.files) byFolder.getOrPut(f.folder.substringBefore('/')) { ArrayList() }.add(f)
    return byFolder.map { (folder, files) ->
        var present = 0
        var bytes = 0L
        val missing = ArrayList<PackFile>()
        for (f in files) {
            if (sizes[f.relPath] != null && sizes[f.relPath] == f.size) {
                present++
                bytes += f.size
            } else {
                missing.add(f)
            }
        }
        FolderStatus(
            folder = folder,
            presentFiles = present,
            totalFiles = files.size,
            installedBytes = bytes,
            totalBytes = files.sumOf { it.size },
            missing = missing
        )
    }
}

/** Files of [def] that are absent or have the wrong size. */
fun missingFiles(def: PackDef, sizes: Map<String, Long?>): List<PackFile> =
    def.files.filter { sizes[it.relPath] == null || sizes[it.relPath] != it.size }

fun missingBytes(def: PackDef, sizes: Map<String, Long?>): Long =
    missingFiles(def, sizes).sumOf { it.size }
