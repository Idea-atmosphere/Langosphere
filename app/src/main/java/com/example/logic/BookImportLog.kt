package com.example.logic

/**
 * The book import pipeline's error channel.
 *
 * The ingest code is pure Kotlin so the JVM unit tests exercise it, and
 * `android.util.Log` throws on a plain JVM. So failures are reported through
 * this hook instead: every skipped or failed entry is always named in
 * [BookJsonIngest.IngestResult] (which the status line shows), and when a
 * handler is installed the same failure is also written to the platform log.
 * The reader installs a logcat handler before each import; tests install
 * their own to assert what was reported.
 */
object BookImportLog {

    /**
     * Receives `(tag, message, cause)`. Null by default, which drops the
     * platform-log copy and keeps only the result lists — safe anywhere,
     * including plain JVM tests.
     */
    var handler: ((tag: String, message: String, cause: Throwable?) -> Unit)? = null

    fun e(tag: String, message: String, cause: Throwable? = null) {
        handler?.invoke(tag, message, cause)
    }
}
