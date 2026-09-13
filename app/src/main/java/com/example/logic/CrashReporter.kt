package com.example.logic

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a local file and lets the user hand that
 * file to somebody who can read it.
 *
 * The handler used to be installed inside MainActivity.onCreate, which meant
 * anything that failed before that point left no trace, and the report was
 * written somewhere only adb or root could reach. It is now installed by
 * [com.example.LangosphereApp] as the very first thing the process does, and
 * the report is surfaced in the app itself on the next launch.
 *
 * Nothing here is sent anywhere: the report stays on the device until the
 * user explicitly shares it.
 */
object CrashReporter {

    private const val TAG = "CrashLogger"

    private const val FILE_NAME = "crash_log.txt"
    private const val PREFS_NAME = "crash_prefs"
    private const val KEY_PENDING = "pending_crash"

    private const val SEPARATOR = "=== CRASH "

    /** Keep the log from growing without bound on a device that crashes often. */
    private const val MAX_FILE_BYTES = 512L * 1024L

    /** Upper bound on what the report screen renders, so a huge trace cannot stall the UI. */
    private const val MAX_REPORT_CHARS = 40_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let reporting a crash cause a second one.
            try {
                record(appContext, thread, throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun crashFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * The most recent report, if the app died since it was last acknowledged.
     * Returns null when there is nothing to show.
     */
    fun pendingReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return null

        val file = crashFile(context)
        if (!file.exists()) {
            markHandled(context)
            return null
        }

        val text = try {
            file.readText()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read ${file.absolutePath}: ${e.message}")
            return null
        }

        val lastReportStart = text.lastIndexOf(SEPARATOR)
        val lastReport = if (lastReportStart >= 0) text.substring(lastReportStart) else text
        return lastReport.takeLast(MAX_REPORT_CHARS).trim().ifBlank { null }
    }

    /** The user has seen the report; stop showing it on every launch. */
    fun markHandled(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, false)
            .apply()
    }

    /** Hand the log file to whatever app the user picks (mail, chat, notes...). */
    fun share(context: Context) {
        val file = crashFile(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Langosphere crash report")
        }

        var attached = false
        try {
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                attached = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach the crash file: ${e.message}")
        }

        if (!attached) {
            // Fall back to plain text so sharing always works.
            intent.putExtra(Intent.EXTRA_TEXT, pendingReport(context) ?: "No crash report found.")
        }

        try {
            context.startActivity(
                Intent.createChooser(intent, "Share crash report")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "No app available to share the crash report: ${e.message}")
        }
    }

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashFile(context)
        if (file.length() > MAX_FILE_BYTES) {
            file.delete()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            append(SEPARATOR).append(timestamp).append(" ===\n")
            append("App: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread: ").append(thread.name).append('\n')
            append(throwable.stackTraceToString())
            append("=== END ===\n\n")
        }

        file.appendText(report)

        // commit(), not apply(): the process is about to be killed and an
        // asynchronous write would be lost, which is precisely the case where
        // the flag matters.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .commit()

        Log.e(TAG, "Crash logged to ${file.absolutePath}", throwable)
    }
}
