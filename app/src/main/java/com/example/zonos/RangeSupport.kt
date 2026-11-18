package com.example.zonos

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Pure-Kotlin byte-range support for ModelFileServer (v0.2.7). No Android
 * imports, so it runs on a plain-JVM harness.
 *
 * Why this exists: the engine's pack check probes every shard with a
 * 1-byte ranged GET (`bytes=0-0`). The old interceptor answered Range
 * requests with the FULL file and 200, and answered HEAD with
 * Content-Length + an empty body — through shouldInterceptRequest that
 * HEAD combination makes Chromium reject the fetch, so zono.js reported
 * every shard missing even though the files are all present (v0.2.6 log
 * proved Kotlin-side reads work: OPEN TEST 5/5). A real 206 Partial
 * Content with a 1-byte body is what the probe needs.
 */
object RangeSupport {

    /** Inclusive byte range [start, end]. */
    data class ByteRange(val start: Long, val end: Long) {
        val length: Long get() = end - start + 1
    }

    /**
     * Parse a single-range "Range" header value against a file of [size]
     * bytes. Accepts "bytes=a-b", "bytes=a-" and "bytes=-n" (suffix).
     * Returns null when there is no Range header, the header is malformed,
     * or the range is unsatisfiable — the caller then serves the full
     * file with 200, which the engine's probe also treats as "present".
     * An end past EOF is clamped to size-1 (RFC 9110 §14.1.2).
     */
    fun parseRange(header: String?, size: Long): ByteRange? {
        if (header == null || size <= 0) return null
        val h = header.trim()
        if (!h.startsWith("bytes=")) return null
        val spec = h.removePrefix("bytes=").trim()
        val parts = spec.split('-')
        if (parts.size != 2) return null
        return try {
            when {
                parts[0].isEmpty() -> {
                    // Suffix range: last N bytes.
                    val n = parts[1].toLong()
                    if (n <= 0) return null
                    ByteRange(maxOf(0L, size - n), size - 1)
                }
                parts[1].isEmpty() -> {
                    val start = parts[0].toLong()
                    if (start < 0 || start >= size) return null
                    ByteRange(start, size - 1)
                }
                else -> {
                    val start = parts[0].toLong()
                    val end = parts[1].toLong()
                    if (start < 0 || end < start || start >= size) return null
                    ByteRange(start, minOf(end, size - 1))
                }
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Skip exactly [n] bytes, looping because InputStream.skip() may skip
     * fewer bytes than asked (and some streams return 0). Falls back to
     * read-and-discard when skip() stalls. Returns false on EOF before
     * [n] bytes were consumed.
     */
    fun skipFully(stream: InputStream, n: Long): Boolean {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            val toRead = minOf(left, buf.size.toLong()).toInt()
            val read = stream.read(buf, 0, toRead)
            if (read <= 0) return false
            left -= read
        }
        return true
    }

    /** Wrap [inner] so it yields at most [length] bytes, then EOF. */
    fun bounded(inner: InputStream, length: Long): InputStream =
        BoundedInputStream(inner, length)

    private class BoundedInputStream(
        inner: InputStream,
        private var remaining: Long
    ) : FilterInputStream(inner) {

        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = super.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun skip(n: Long): Long {
            val s = super.skip(minOf(n, remaining))
            if (s > 0) remaining -= s
            return s
        }

        override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()
    }
}
