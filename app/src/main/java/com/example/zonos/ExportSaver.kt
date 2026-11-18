package com.example.zonos

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Saves a file the Studio page exported (its own download button) into
 * the public Download/Langosphere/exports/ folder — via MediaStore on
 * Android 10+ (no storage permission needed), via the legacy public
 * directory before that. Returns a human-readable location.
 */
object ExportSaver {
    const val EXPORTS_RELATIVE_PATH = "Download/${ModelsLocation.APP_DIR_NAME}/exports"
    const val EXPORTS_DISPLAY_PATH = "Downloads/${ModelsLocation.APP_DIR_NAME}/exports"

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
                "${ModelsLocation.APP_DIR_NAME}/exports"
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
}
