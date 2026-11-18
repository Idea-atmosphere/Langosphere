package com.example.zonos

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse

/**
 * Serves local model packs to the web engine as /models/....
 *
 * Both WebViews (the hidden player in ZonoEngine and the Studio page in
 * StudioEngine) run on the https://appassets.androidplatform.net origin,
 * and Zyphra's engine resolves its model base to `/models/` on that origin
 * (engine.js `c()`: `new URL('/models/', location.origin)`). This
 * interceptor answers those requests from Download/Langosphere/models/
 * (see store/ModelsLocation — MediaStore-owned files or a user-picked
 * SAF tree, read through ContentResolver streams; raw File access to
 * shared storage is blocked on API 30+). The engine never fetches
 * weights from the network itself.
 *
 * A missing /models/ file is answered with a real 404, NOT
 * null: the worker's manifestMeta() treats 404 as "not present" (edit /
 * aligner / zac-full_mr are optional, so init must continue without
 * them), but a fetch REJECTION throws and kills the whole engine init —
 * and null used to make the WebView try the real network for
 * https://appassets.androidplatform.net/..., which DNS-fails. Only
 * paths outside /models/ (or an empty/unsafe rel) still return null,
 * falling through to the WebViewAssetLoader.
 */
object ModelFileServer {

    /**
     * Outcome of the last /models/ intercept. Null = it succeeded;
     * otherwise the failure, e.g. "resolve-null backbone/manifest.json"
     * or "open-fail backbone/shard-000.bin: <message>". Shown in the
     * TTS section's status line when the engine cannot start. Only
     * /models/ requests touch it (asset requests leave it alone).
     */
    @Volatile
    var lastError: String? = null
        private set

    /** One installed file, as the decision logic sees it: its size plus a way to open its stream. */
    data class FileEntry(val size: Long, val open: () -> java.io.InputStream?)

    /** A served response: status/reason/headers plus the body stream. */
    data class ServedResponse(
        val mime: String,
        val status: Int,
        val reason: String,
        val headers: Map<String, String>,
        val stream: java.io.InputStream
    )

    private fun notFound(rel: String): ServedResponse {
        lastError = "resolve-null $rel"
        return ServedResponse(
            "text/plain", 404, "Not Found", emptyMap(),
            ByteArray(0).inputStream()
        )
    }

    private fun serverError(): ServedResponse {
        // lastError was already set by openWithRetry (open-fail ...);
        // the 500 itself just needs to reach the engine as a response,
        // not as a fetch rejection.
        return ServedResponse(
            "text/plain", 500, "Internal Server Error", emptyMap(),
            ByteArray(0).inputStream()
        )
    }

    /**
     * Pure decision logic for serving one /models/ request — no Android
     * types, so it runs on a plain JVM harness with a fake [lookup].
     * [lookup] returns the file's [FileEntry] when installed, null when
     * missing; the entry's opener returns null when the file cannot be
     * opened. Returns null ONLY for paths outside /models/ (or an
     * empty/unsafe rel) — the caller falls through to the asset loader
     * for those. A missing /models/ file is a real 404; a
     * present-but-unopenable file is a real 500.
     *
     * [method] is "GET" or "HEAD". [requestHeaders] are the request's
     * headers — the engine's pack check probes shards with a 1-byte
     * ranged GET (`bytes=0-0`), which is answered with a real 206
     * Partial Content.
     *
     * HEAD answers with an empty body and NO Content-Length :
     * the old HEAD (Content-Length = full size + empty body) made
     * Chromium reject the fetch through shouldInterceptRequest, so the
     * engine reported every shard missing. GET streams the file; a Range
     * request is answered with 206 + a bounded stream (see RangeSupport).
     * A malformed or unsatisfiable Range falls back to the full file
     * with 200, which the probe also treats as "present".
     *
     * If opening the stream fails (e.g. a stale MediaStore URI
     * from a cached scan), the scan cache is invalidated ONCE, the file
     * is resolved fresh, and the open is retried once before giving up
     * (see openWithRetry).
     */
    internal fun decide(
        path: String?,
        method: String,
        requestHeaders: Map<String, String>,
        lookup: (String) -> FileEntry?
    ): ServedResponse? {
        val p = path ?: return null
        if (!p.startsWith("/models/")) return null
        val rel = p.removePrefix("/models/")
        if (rel.isEmpty() || rel.contains("..")) return null
        val found = lookup(rel) ?: return notFound(rel)
        val mime = when (rel.substringAfterLast('.', "").lowercase()) {
            "json" -> "application/json"
            "bin", "npy", "gguf" -> "application/octet-stream"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
        if (method.equals("HEAD", ignoreCase = true)) {
            // v0.2.7: no Content-Length here — a declared length with an
            // empty body is exactly what made Chromium reject the probe.
            lastError = null
            return ServedResponse(
                mime, 200, "OK",
                mapOf("Accept-Ranges" to "bytes"),
                ByteArray(0).inputStream()
            )
        }
        val fullHeaders = mapOf(
            "Content-Length" to found.size.toString(),
            "Accept-Ranges" to "bytes"
        )
        val rangeHeader = requestHeaders.entries
            .firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
        val range = RangeSupport.parseRange(rangeHeader, found.size)
        if (range == null) {
            val stream = found.open() ?: return serverError()
            lastError = null
            return ServedResponse(mime, 200, "OK", fullHeaders, stream)
        }
        val stream = found.open() ?: return serverError()
        if (!RangeSupport.skipFully(stream, range.start)) {
            // Could not seek (should not happen on a local file): close
            // and serve the full file instead of a broken partial one.
            try {
                stream.close()
            } catch (_: Exception) {
            }
            val full = found.open() ?: return serverError()
            lastError = null
            return ServedResponse(mime, 200, "OK", fullHeaders, full)
        }
        lastError = null
        val rangeHeaders = mapOf(
            "Content-Range" to "bytes ${range.start}-${range.end}/${found.size}",
            "Content-Length" to range.length.toString(),
            "Accept-Ranges" to "bytes"
        )
        return ServedResponse(
            mime, 206, "Partial Content",
            rangeHeaders, RangeSupport.bounded(stream, range.length)
        )
    }

    fun intercept(
        context: Context,
        url: Uri,
        method: String,
        requestHeaders: Map<String, String> = emptyMap()
    ): WebResourceResponse? {
        val served = decide(
            path = url.path,
            method = method,
            requestHeaders = requestHeaders,
            lookup = { rel ->
                val located = ModelsLocation.resolve(context, rel) ?: return@decide null
                FileEntry(located.size) { openWithRetry(context, rel, located) }
            }
        ) ?: return null
        return WebResourceResponse(
            served.mime, "UTF-8", served.status, served.reason,
            served.headers, served.stream
        )
    }

    /**
     * Open [first]'s URI; on failure invalidate the scan cache, resolve
     * the file fresh (a URI that went stale outside the app is replaced
     * by the current MediaStore row) and retry the open once. Returns
     * null only when both attempts fail, with [lastError] set.
     */
    private fun openWithRetry(
        context: Context,
        rel: String,
        first: ModelsLocation.Located
    ): java.io.InputStream? {
        var firstMsg: String? = null
        try {
            return context.contentResolver.openInputStream(first.uri)
        } catch (e: Exception) {
            firstMsg = e.message ?: e.javaClass.simpleName
        }
        ModelsLocation.invalidate()
        val fresh = ModelsLocation.resolve(context, rel)
        if (fresh == null) {
            lastError = "resolve-null $rel (after invalidate+retry; first open failed: $firstMsg)"
            return null
        }
        return try {
            context.contentResolver.openInputStream(fresh.uri)
        } catch (e: Exception) {
            val msg = e.message ?: firstMsg ?: e.javaClass.simpleName
            lastError = "open-fail $rel: $msg"
            null
        }
    }
}
