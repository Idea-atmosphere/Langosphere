package com.example.zonos

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resumable single-file download with HTTP Range (v0.2.3) — PURE Kotlin,
 * no Android deps, so it can be exercised on the JVM
 * (see build/tests/resume/run_test.sh).
 *
 * Behaviour:
 * - A partial .part file resumes with `Range: bytes=<downloaded>-`.
 * - A server that ignores Range (200 instead of 206) restarts the file
 *   from zero.
 * - Exact-size verification at the end; the caller publishes the file
 *   and deletes the .part only after that.
 *
 * v0.2.3 fix — HTTP 416 on "Complete the pack": a previous interrupted
 * attempt can leave the .part at EXACTLY the expected size (e.g. the
 * 5928-byte Cillian codes file finished writing but was never published,
 * because the app was killed / the publish step failed). Resuming from
 * it sends `Range: bytes=5928-` for a 5928-byte file and the Hugging
 * Face CDN correctly answers 416 Range Not Satisfiable — and the old
 * code threw that as fatal, so the retry could never succeed (the stale
 * .part stayed). Two guards now:
 *   1. A .part whose size is >= the expected size is treated as stale:
 *      deleted, restart from 0 (the old guard only caught `>`).
 *   2. A 416 answer is handled explicitly: delete the .part, reset to
 *      0 and retry ONCE with a plain GET (no Range header). If the
 *      retry also fails, the error is thrown.
 */
object ResumableDownload {

    /**
     * Opens the HTTP connection for [url]. Overridable for tests: the JVM
     * harness injects a fake CDN (this VM blocks Java TCP, even to
     * localhost, so a real local server cannot be reached from
     * HttpURLConnection here). Production always uses the default.
     */
    fun download(
        url: String,
        expectedSize: Long,
        part: File,
        userAgent: String,
        checkActive: () -> Unit = {},
        onProgress: (downloaded: Long) -> Unit = {},
        openConnection: (String) -> HttpURLConnection =
            { u -> URL(u).openConnection() as HttpURLConnection }
    ) {
        var downloaded = if (part.isFile) part.length() else 0L
        // Guard 1 (v0.2.3): a .part at exactly the expected size — or
        // larger — is stale, never resumable. Resuming from it would
        // send `Range: bytes=<size>-` and the server answers 416.
        if (downloaded >= expectedSize) {
            part.delete()
            downloaded = 0L
        }
        var redirects = 0
        var currentUrl = url
        var retried416 = false
        while (true) {
            checkActive()
            val conn = openConnection(currentUrl).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", userAgent)
                if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location")
                    ?: throw IOException("redirect without Location header")
                conn.disconnect()
                if (++redirects > 5) throw IOException("too many redirects")
                currentUrl = URL(URL(currentUrl), loc).toString()
                continue
            }
            // Guard 2 (v0.2.3): 416 Range Not Satisfiable — the server
            // rejects our resume offset. Drop the .part and retry ONCE
            // with a plain GET (no Range header).
            if (code == 416 && !retried416) {
                conn.disconnect()
                part.delete()
                downloaded = 0L
                retried416 = true
                continue
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                conn.disconnect()
                throw IOException("download failed (HTTP $code)")
            }
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            if (!resumed && downloaded > 0) {
                // Server ignored Range: restart this file from zero.
                part.delete()
                downloaded = 0L
            }
            try {
                conn.inputStream.use { input ->
                    FileOutputStream(part, resumed && downloaded > 0).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            checkActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            onProgress(downloaded)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            break
        }
        if (part.length() != expectedSize) {
            throw IOException("حجم فایل اشتباه است (${part.length()} به‌جای $expectedSize)")
        }
    }
}
