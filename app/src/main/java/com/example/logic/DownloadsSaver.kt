package com.example.logic

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Writes a text file into the device's public Downloads folder — via
 * MediaStore on Android 10+ (no storage permission needed), via the legacy
 * public directory before that. Returns a human-readable location for the
 * confirmation toast.
 */
object DownloadsSaver {
    fun saveText(context: Context, displayName: String, text: String, mimeType: String = "text/plain"): String {
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").ifBlank { "langosphere.txt" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create a file in Downloads")
            resolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("Could not write the file")
            "Downloads/$safeName"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, safeName)
            dest.writeText(text, Charsets.UTF_8)
            dest.absolutePath
        }
    }
}
