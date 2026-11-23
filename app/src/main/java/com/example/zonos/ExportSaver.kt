package com.example.zonos

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Saves a file the Studio page exported (its own download button) into
 * the public Download/Langosphere/models/exports/ folder — inside the
 * models folder, so everything the TTS feature writes sits under one
 * tree — via MediaStore on Android 10+ (no storage permission needed),
 * via the legacy public directory before that. Returns a human-readable
 * location.
 *
 * Builds before this one saved to Download/Langosphere/exports/. The
 * first save of a session moves the app's own files from there into the
 * new folder (MediaStore lets an app relocate the files it owns by
 * updating RELATIVE_PATH); files of another app or of an uninstalled
 * build are not ours to touch and stay where they are. The empty old
 * folder is left behind — MediaStore has no API to remove a directory.
 *
 * ModelsLocation's scan covers Download/Langosphere/models/%, so the
 * exports turn up in its map under "exports/<name>"; nothing looks those
 * keys up and the SAF walk does not descend into a folder of that name.
 */
object ExportSaver {
    const val EXPORTS_FOLDER = "exports"
    const val EXPORTS_RELATIVE_PATH = "${ModelsLocation.MODELS_RELATIVE_PATH}/$EXPORTS_FOLDER"
    const val EXPORTS_DISPLAY_PATH = "Downloads/${ModelsLocation.APP_DIR_NAME}/models/$EXPORTS_FOLDER"

    /** Where exports went before they moved into the models folder. */
    const val LEGACY_EXPORTS_RELATIVE_PATH = "Download/${ModelsLocation.APP_DIR_NAME}/$EXPORTS_FOLDER"

    @Volatile
    private var legacyChecked = false

    fun sanitizeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "export.wav" }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".zip", ignoreCase = true) -> "application/zip"
        name.endsWith(".wav", ignoreCase = true) -> "audio/x-wav"
        name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        name.endsWith(".json", ignoreCase = true) -> "application/json"
        else -> "application/octet-stream"
    }

    @Throws(IOException::class)
    fun saveBytes(context: Context, name: String, bytes: ByteArray): String {
        val safe = sanitizeName(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            moveLegacyExports(context)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safe)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(safe))
                put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORTS_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("could not create a file in Downloads")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("could not write the export file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) { }
                throw e
            }
            // The provider may have renamed the file ("x (1).wav"); report the real name.
            val realName = try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Exception) { null } ?: safe
            return "$EXPORTS_DISPLAY_PATH/$realName"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "${ModelsLocation.APP_DIR_NAME}/models/$EXPORTS_FOLDER"
            )
            if (!dir.exists() && !dir.mkdirs()) throw IOException("could not create $dir")
            var dest = File(dir, safe)
            var n = 1
            val dot = safe.lastIndexOf('.')
            val stem = if (dot > 0) safe.substring(0, dot) else safe
            val ext = if (dot > 0) safe.substring(dot) else ""
            while (dest.exists()) dest = File(dir, "$stem (${++n})$ext")
            dest.writeBytes(bytes)
            return dest.absolutePath
        }
    }

    /**
     * One-time (per process) move of the app's own files from the old
     * Download/Langosphere/exports/ into models/exports/. Best effort:
     * any failure leaves the file where it is and never blocks a save.
     */
    private fun moveLegacyExports(context: Context) {
        if (legacyChecked) return
        legacyChecked = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val ids = ArrayList<Long>()
        try {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf("$LEGACY_EXPORTS_RELATIVE_PATH/"),
                null
            )?.use { c ->
                while (c.moveToNext()) ids.add(c.getLong(0))
            }
        } catch (_: Exception) {
            return
        }
        if (ids.isEmpty()) return
        val target = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$EXPORTS_RELATIVE_PATH/")
        }
        for (id in ids) {
            try {
                resolver.update(ContentUris.withAppendedId(collection, id), target, null, null)
            } catch (_: Exception) {
                // Not movable (pending, locked, odd provider): leave it.
            }
        }
        ModelsLocation.invalidate()
    }
}
